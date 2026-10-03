package com.rrajath.expander.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class GoogleDriveSync(private val accessToken: String) {
    private val gson = Gson()

    data class RemoteFile(val id: String, val name: String, val size: Long, val version: String?) {
        fun needsDownload(seen: Map<String, String>): Boolean = version == null || seen[id] != version
    }

    fun accountId(): String {
        val root = JsonParser.parseString(get("$API/about?fields=user(permissionId)")).asJsonObject
        return root.getAsJsonObject("user")?.get("permissionId")?.asString
            ?.takeIf(String::isNotBlank) ?: error("Google Drive did not identify the account")
    }

    fun list(): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val query = "name contains 'snippetdeck-sync-v1-' and trashed = false"
            val url = "$API/files?spaces=appDataFolder&q=${encoded(query)}" +
                "&fields=nextPageToken,files(id,name,size,version)&pageSize=100" +
                (pageToken?.let { "&pageToken=${encoded(it)}" } ?: "")
            val root = JsonParser.parseString(get(url)).asJsonObject
            root.getAsJsonArray("files")?.forEach { element ->
                val item = element.asJsonObject
                val name = item.get("name").asString
                if (name.matches(Regex("snippetdeck-sync-v1-[0-9a-f-]{36}\\.json"))) {
                    files += RemoteFile(item.get("id").asString, name,
                        item.get("size")?.asLong ?: 0,
                        item.get("version")?.takeUnless { it.isJsonNull }?.asString?.takeIf(String::isNotBlank))
                }
            }
            require(files.size <= 50) { "Too many SnippetDeck devices in this Drive" }
            pageToken = root.get("nextPageToken")?.takeUnless { it.isJsonNull }?.asString
        } while (pageToken != null)
        return files
    }

    fun read(file: RemoteFile): SyncReplica {
        require(file.size in 1..MAX_BYTES.toLong()) { "Cloud library is too large" }
        val body = get("$API/files/${encoded(file.id)}?alt=media")
        val replica = gson.fromJson(body, SyncReplica::class.java)
        require(replica.format == "snippetdeck-sync" && replica.schemaVersion == 1) {
            "Unsupported cloud library version"
        }
        require(replica.deviceId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) {
            "Invalid cloud device ID"
        }
        require(file.name == "snippetdeck-sync-v1-${replica.deviceId}.json") {
            "Cloud library does not match its device"
        }
        require(replica.entries.size <= 20_000 && replica.sequence >= 0) { "Invalid cloud library" }
        for ((key, versions) in replica.entries) {
            require(versions.isNotEmpty() && versions.size <= 50 && key == syncKey(key)) {
                "Invalid cloud record"
            }
            versions.forEach { version ->
                require(version.clock.isNotEmpty() && version.clock.size <= 50 &&
                    version.clock.values.all { it > 0 } &&
                    (version.value == null || syncKey(version.value.trigger) == key)) {
                    "Invalid cloud record"
                }
            }
        }
        return replica
    }

    fun write(replica: SyncReplica, fileId: String?): String {
        val text = gson.toJson(replica)
        require(text.toByteArray().size <= MAX_BYTES) { "Cloud library is too large" }
        if (fileId != null) {
            request("PATCH", "$UPLOAD/files/${encoded(fileId)}?uploadType=media",
                text.toByteArray(), "application/json")
            return fileId
        }
        val boundary = "snippetdeck-${replica.deviceId}"
        val metadata = gson.toJson(mapOf(
            "name" to "snippetdeck-sync-v1-${replica.deviceId}.json",
            "parents" to listOf("appDataFolder"),
            "mimeType" to "application/json",
        ))
        val body = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            "$metadata\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n" +
            "$text\r\n--$boundary--\r\n").toByteArray(StandardCharsets.UTF_8)
        val result = request("POST", "$UPLOAD/files?uploadType=multipart&fields=id",
            body, "multipart/related; boundary=$boundary")
        return JsonParser.parseString(result).asJsonObject.get("id").asString
    }

    private fun get(url: String): String = request("GET", url, null, null)

    private fun request(method: String, url: String, body: ByteArray?, contentType: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            // Android's HttpURLConnection does not accept PATCH directly.
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) {
                error("Google Drive returned HTTP ${connection.responseCode}")
            }
            val bytes = connection.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
            require(bytes.size <= MAX_BYTES) { "Google Drive response is too large" }
            return String(bytes, StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val MAX_BYTES = 6_000_000
        private fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}

internal class SyncStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "snippetdeck-sync.json")
    private val disabled = File(context.noBackupFilesDir, "snippetdeck-sync-disabled")
    private val gson = Gson()

    fun enabled(): Boolean = file.exists() && !disabled.exists()
    fun hasHistory(): Boolean = file.exists()

    fun load(): SyncLocalState? {
        if (!file.exists()) return null
        require(file.length() in 1..12_000_000) { "Local sync state is too large" }
        return gson.fromJson(file.readText(), SyncLocalState::class.java).also { state ->
            require(state.replica.format == "snippetdeck-sync" && state.replica.schemaVersion == 1) {
                "Unsupported local sync state"
            }
        }
    }

    fun save(state: SyncLocalState) {
        val json = gson.toJson(state)
        require(json.toByteArray().size <= 12_000_000) { "Local sync state is too large" }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json)
        check(tmp.renameTo(file)) { "Cannot save sync state" }
        if (disabled.exists()) check(disabled.delete()) { "Cannot enable sync" }
    }

    fun disconnect() {
        disabled.writeText("off")
    }

    fun reset() {
        disabled.writeText("off")
        if (file.exists()) check(file.delete()) { "Cannot reset sync history" }
    }
}

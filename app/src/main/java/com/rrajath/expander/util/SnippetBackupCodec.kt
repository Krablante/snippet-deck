package com.rrajath.expander.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.domain.TriggerUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SnippetBackupCodec {
    const val TEXT_HEADER = "SNIPPETDECK_BACKUP_V2"
    const val LEGACY_TEXT_HEADER = "SNIPPETDECK_BACKUP_V1"
    const val MAX_BACKUP_BYTES = 2_000_000
    const val MAX_SNIPPETS = 10_000

    private const val FORMAT = "snippetdeck-backup"
    private const val SCHEMA_VERSION = 2

    private val prettyGson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun encodeJson(snippets: List<Snippet>): String {
        require(snippets.size <= MAX_SNIPPETS) { "Too many snippets to export" }
        val envelope = BackupEnvelope(
            format = FORMAT,
            schemaVersion = SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            snippets = snippets.map { snippet ->
                BackupSnippet(
                    trigger = snippet.trigger,
                    expansion = snippet.expansion,
                    aliases = snippet.aliases,
                    enabled = snippet.isEnabled,
                    createdAt = snippet.createdAt,
                    updatedAt = snippet.updatedAt
                )
            }
        )
        return prettyGson.toJson(envelope).also(::requireValidSize)
    }

    fun decodeJson(input: String): List<Snippet> {
        requireValidSize(input)
        val parsed = runCatching { JsonParser.parseString(input) }
            .getOrElse { throw BackupFormatException("Backup is not valid JSON", it) }
        val snippetsJson = when {
            parsed.isJsonArray -> parsed.asJsonArray // Legacy raw-list exports.
            parsed.isJsonObject -> {
                val root = parsed.asJsonObject
                validateEnvelope(root)
                root.getAsJsonArray("snippets")
                    ?: throw BackupFormatException("Backup does not contain snippets")
            }
            else -> throw BackupFormatException("Backup must be a JSON object or array")
        }
        if (snippetsJson.size() > MAX_SNIPPETS) {
            throw BackupFormatException("Backup contains too many snippets")
        }

        val now = System.currentTimeMillis()
        val seenTriggers = mutableSetOf<String>()
        return snippetsJson.mapIndexed { index, element ->
            val item = runCatching { element.asJsonObject }
                .getOrElse { throw BackupFormatException("Snippet ${index + 1} is invalid", it) }
            val trigger = TriggerUtils.normalize(item.string("trigger"))
            val triggerError = TriggerUtils.validationError(trigger)
            if (triggerError != null) {
                throw BackupFormatException("Snippet ${index + 1}: $triggerError")
            }
            if (!seenTriggers.add(trigger.lowercase())) {
                throw BackupFormatException("Duplicate trigger: $trigger")
            }

            val aliases = item.get("aliases")
                ?.takeUnless { it.isJsonNull }
                ?.let { aliasesElement ->
                    if (!aliasesElement.isJsonArray) {
                        throw BackupFormatException("Snippet $trigger has invalid aliases")
                    }
                    aliasesElement.asJsonArray.map { aliasElement ->
                        TriggerUtils.normalizeAlias(aliasElement.asString)
                    }
                }
                .orEmpty()

            aliases.forEach { alias ->
                TriggerUtils.aliasValidationError(alias)?.let { error ->
                    throw BackupFormatException("Alias $alias: $error")
                }
                if (alias.equals(trigger, ignoreCase = true)) {
                    throw BackupFormatException("Alias duplicates primary trigger: $trigger")
                }
                if (!seenTriggers.add(alias.lowercase())) {
                    throw BackupFormatException("Duplicate trigger or alias: $alias")
                }
            }

            val expansion = item.string("expansion")
            if (expansion.isBlank()) {
                throw BackupFormatException("Snippet $trigger has empty expansion text")
            }

            Snippet(
                id = 0,
                trigger = trigger,
                expansion = expansion,
                aliases = aliases,
                isEnabled = item.booleanOrNull("enabled")
                    ?: item.booleanOrNull("isEnabled")
                    ?: true,
                createdAt = item.longOrNull("createdAt") ?: now,
                updatedAt = item.longOrNull("updatedAt") ?: now
            )
        }
    }

    fun encodeText(snippets: List<Snippet>): String {
        val json = encodeJson(snippets)
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write(json.toByteArray(StandardCharsets.UTF_8))
            }
            output.toByteArray()
        }
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        return "$TEXT_HEADER\n$payload"
    }

    fun decodeText(input: String): List<Snippet> {
        val trimmed = input.trim()
        val hasTextHeader = trimmed.startsWith(TEXT_HEADER) ||
            trimmed.startsWith(LEGACY_TEXT_HEADER)
        if (!hasTextHeader) {
            // Raw JSON remains convenient for advanced users and old file backups.
            return decodeJson(trimmed)
        }

        val payload = trimmed.substringAfter('\n', missingDelimiterValue = "")
            .filterNot(Char::isWhitespace)
        if (payload.isBlank()) throw BackupFormatException("Text backup payload is missing")
        if (payload.length > MAX_BACKUP_BYTES * 2) {
            throw BackupFormatException("Text backup is too large")
        }

        val compressed = runCatching { Base64.getUrlDecoder().decode(payload) }
            .getOrElse { throw BackupFormatException("Text backup payload is damaged", it) }
        val json = runCatching { decompressLimited(compressed) }
            .getOrElse { error ->
                if (error is BackupFormatException) throw error
                throw BackupFormatException("Text backup cannot be decompressed", error)
            }
        return decodeJson(json)
    }

    private fun validateEnvelope(root: JsonObject) {
        if (!root.has("snippets")) throw BackupFormatException("Backup does not contain snippets")

        val format = root.stringOrNull("format")
        if (format != null && format != FORMAT) {
            throw BackupFormatException("Unsupported backup format: $format")
        }

        val schemaVersion = root.intOrNull("schemaVersion")
        if (schemaVersion != null && schemaVersion > SCHEMA_VERSION) {
            throw BackupFormatException("Backup was created by a newer SnippetDeck version")
        }
        // Missing format/schemaVersion is accepted for legacy v1.0 JSON exports.
    }

    private fun decompressLimited(compressed: ByteArray): String {
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BACKUP_BYTES) {
                    throw BackupFormatException("Backup is too large")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun requireValidSize(value: String) {
        if (value.toByteArray(StandardCharsets.UTF_8).size > MAX_BACKUP_BYTES) {
            throw BackupFormatException("Backup is too large")
        }
    }

    private fun JsonObject.string(name: String): String =
        stringOrNull(name) ?: throw BackupFormatException("Missing field: $name")

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.booleanOrNull(name: String): Boolean? =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean

    private fun JsonObject.longOrNull(name: String): Long? =
        get(name)?.takeUnless { it.isJsonNull }?.asLong

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.takeUnless { it.isJsonNull }?.asInt

    private data class BackupEnvelope(
        val format: String,
        val schemaVersion: Int,
        val exportedAt: Long,
        val snippets: List<BackupSnippet>
    )

    private data class BackupSnippet(
        val trigger: String,
        val expansion: String,
        val aliases: List<String>,
        val enabled: Boolean,
        val createdAt: Long,
        val updatedAt: Long
    )
}

class BackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

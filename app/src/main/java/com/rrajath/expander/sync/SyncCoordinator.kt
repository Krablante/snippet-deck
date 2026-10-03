package com.rrajath.expander.sync

import android.content.Context
import com.google.gson.Gson
import com.rrajath.expander.data.AppDatabase
import com.rrajath.expander.data.Snippet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

internal sealed interface SyncOutcome {
    data class Done(val count: Int) : SyncOutcome
    data class Conflict(val triggers: List<String>) : SyncOutcome
}

internal class SyncCoordinator(context: Context) {
    private val store = SyncStore(context)
    private val dao = AppDatabase.getDatabase(context).snippetDao()
    private val mutex = Mutex()

    fun connected(): Boolean = store.enabled()
    fun hasHistory(): Boolean = store.hasHistory()

    suspend fun sync(accessToken: String): SyncOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val drive = GoogleDriveSync(accessToken)
            val account = drive.accountId()
            val previous = store.load()
            require(previous == null || previous.accountId == account) {
                "This device was connected to a different Google account. Use Switch Google account to clear its sync history first."
            }
            val library = dao.getAllSnippetsOnce()
            val local = SyncLibrary.collectChanges(previous ?: SyncLibrary.empty(account), library)
            val files = drive.list()
            require(files.map { it.name }.distinct().size == files.size) { "Duplicate device files in Google Drive" }
            val ownFiles = files.filter { it.name == "snippetdeck-sync-v1-${local.replica.deviceId}.json" }
            require(ownFiles.size <= 1) { "Duplicate device files in Google Drive" }
            val identities = mutableSetOf<String>()
            val peerFiles = files.filterNot { it in ownFiles }
            val seen = local.seenFiles.orEmpty().filterKeys { id -> peerFiles.any { it.id == id } }.toMutableMap()
            val others = peerFiles.asSequence()
                .filter { it.needsDownload(seen) }.map { file ->
                    drive.read(file).also { replica ->
                        require(identities.add(replica.deviceId)) { "Duplicate device identities in Google Drive" }
                        file.version?.let { seen[file.id] = it }
                    }
                }
            val merged = SyncLibrary.merge(local.replica, others)
            val newLibrary = merged.snippets
            if (newLibrary != null && !sameLibrary(library, newLibrary)) {
                dao.replaceIfUnchanged(library, newLibrary)
            }
            val state = local.copy(
                replica = merged.replica,
                baseline = (newLibrary ?: library).map(Snippet::forSync),
                fileId = ownFiles.singleOrNull()?.id,
                seenFiles = seen,
            )
            if (state != previous) store.save(state)
            if (newLibrary == null) return@withContext SyncOutcome.Conflict(merged.conflicts)
            val hash = fingerprint(state.replica)
            if (state.fileId == null || state.lastUploadedHash != hash) {
                val fileId = drive.write(state.replica, state.fileId)
                store.save(state.copy(fileId = fileId, lastUploadedHash = hash))
            }
            SyncOutcome.Done(newLibrary.size)
        }
    }

    suspend fun keepThisDevice(accessToken: String): SyncOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val drive = GoogleDriveSync(accessToken)
            val previous = store.load() ?: error("Connect Google Drive first")
            require(previous.accountId == drive.accountId()) { "Google account changed" }
            val library = dao.getAllSnippetsOnce()
            val files = drive.list()
            val ownFiles = files.filter { it.name == "snippetdeck-sync-v1-${previous.replica.deviceId}.json" }
            require(ownFiles.size <= 1) { "Duplicate device files in Google Drive" }
            val merged = SyncLibrary.merge(previous.replica, files.asSequence().filterNot { it in ownFiles }.map(drive::read), validate = false)
            val chosen = SyncLibrary.keepLocal(previous.copy(replica = merged.replica), library)
                .copy(fileId = ownFiles.singleOrNull()?.id)
            store.save(chosen)
            store.save(chosen.copy(
                fileId = drive.write(chosen.replica, chosen.fileId),
                lastUploadedHash = fingerprint(chosen.replica),
            ))
            SyncOutcome.Done(library.size)
        }
    }

    suspend fun useOtherDevice(accessToken: String): SyncOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val drive = GoogleDriveSync(accessToken)
            val previous = store.load() ?: error("Connect Google Drive first")
            val current = dao.getAllSnippetsOnce()
            require(previous.accountId == drive.accountId()) { "Google account changed" }
            val files = drive.list()
            val own = files.filter { it.name == "snippetdeck-sync-v1-${previous.replica.deviceId}.json" }
            require(own.size <= 1) { "Duplicate device files in Google Drive" }
            val otherFiles = files.filterNot { it in own }
            require(otherFiles.size == 1) { "Use the preferred device to resolve a conflict across multiple devices" }
            val other = drive.read(otherFiles.single())
            val chosen = SyncLibrary.merge(other, emptyList()).snippets
                ?: error("The other device has unresolved conflicts")
            val merged = SyncLibrary.merge(previous.replica, listOf(other), validate = false)
            val resolved = SyncLibrary.keepLocal(previous.copy(replica = merged.replica), chosen)
                .copy(fileId = own.singleOrNull()?.id)
            if (!sameLibrary(current, chosen)) dao.replaceIfUnchanged(current, chosen)
            store.save(resolved)
            store.save(resolved.copy(
                fileId = drive.write(resolved.replica, resolved.fileId),
                lastUploadedHash = fingerprint(resolved.replica),
            ))
            SyncOutcome.Done(chosen.size)
        }
    }

    suspend fun disconnect() = mutex.withLock {
        withContext(Dispatchers.IO) { store.disconnect() }
    }

    suspend fun reset() = mutex.withLock {
        withContext(Dispatchers.IO) { store.reset() }
    }

    private fun sameLibrary(a: List<Snippet>, b: List<Snippet>): Boolean =
        a.size == b.size && a.associate { syncKey(it.trigger) to it.forSync() } ==
        b.associate { syncKey(it.trigger) to it.forSync() }

    private fun fingerprint(replica: SyncReplica): String =
        MessageDigest.getInstance("SHA-256").digest(Gson().toJson(replica).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

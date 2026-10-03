package com.rrajath.expander.sync

import com.rrajath.expander.data.Snippet
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SyncLibraryTest {
    private fun snippet(trigger: String, expansion: String) =
        Snippet(trigger = trigger, expansion = expansion, createdAt = 1, updatedAt = 1)

    @Test
    fun `concurrent edit and deletion require a choice and stale copies cannot restore deletion`() {
        val original = snippet("!shared", "Original")
        val first = SyncLibrary.collectChanges(SyncLibrary.empty("account"), listOf(original))
        val second = SyncLibrary.empty("account").let { state ->
            val received = SyncLibrary.merge(state.replica, listOf(first.replica))
            state.copy(replica = received.replica, baseline = listOf(original.forSync()))
        }
        val changed = SyncLibrary.collectChanges(first, listOf(original.copy(expansion = "Changed")))
        val deleted = SyncLibrary.collectChanges(second, emptyList())
        val conflict = SyncLibrary.merge(changed.replica, listOf(deleted.replica))
        assertEquals(listOf("!shared"), conflict.conflicts)
        assertNull(conflict.snippets)
        assertEquals(2, conflict.replica.entries.getValue("!shared").size)
        assertThrows(PendingSyncConflict::class.java) {
            SyncLibrary.collectChanges(changed.copy(replica = conflict.replica), listOf(original.copy(expansion = "My revised draft")))
        }
        val resolved = SyncLibrary.keepLocal(changed.copy(replica = conflict.replica), emptyList())
        val received = SyncLibrary.merge(first.replica, listOf(resolved.replica))
        assertEquals(emptyList<Snippet>(), received.snippets)
        assertNull(received.replica.entries.getValue("!shared").single().value)
    }

    @Test
    fun `independent changes merge but colliding aliases preserve the local library`() {
        val first = SyncLibrary.collectChanges(SyncLibrary.empty("account"), listOf(snippet("!one", "One")))
        val second = SyncLibrary.collectChanges(SyncLibrary.empty("account"), listOf(snippet("!two", "Two")))
        assertEquals(2, SyncLibrary.merge(first.replica, listOf(second.replica)).snippets?.size)
        val a = SyncLibrary.collectChanges(first, listOf(snippet("!one", "One").copy(aliases = listOf("shared"))))
        val b = SyncLibrary.collectChanges(second, listOf(snippet("!two", "Two").copy(aliases = listOf("SHARED"))))
        val collision = SyncLibrary.merge(a.replica, listOf(b.replica))
        assertEquals(listOf("SHARED"), collision.conflicts)
        assertNull(collision.snippets)
        val resolved = SyncLibrary.keepLocal(a.copy(replica = collision.replica), listOf(snippet("!one", "One")))
        assertEquals(1, SyncLibrary.merge(resolved.replica, listOf(b.replica)).snippets?.size)
    }

    @Test
    fun `full library merges without a serialization round trip`() {
        val library = (1..10_000).map { snippet("!item$it", "Value") }
        val state = SyncLibrary.collectChanges(SyncLibrary.empty("account"), library)
        val merged = SyncLibrary.merge(state.replica, (1..10).asSequence().map { state.replica })
        assertEquals(10_000, merged.snippets?.size)
        assertTrue(merged.replica.entries.values.all { it.size == 1 })
    }

    @Test
    fun `android reads a desktop deletion record`() {
        val json = """{"format":"snippetdeck-sync","schemaVersion":1,"deviceId":"device-a","sequence":1,"entries":{"!gone":[{"clock":{"device-a":1},"value":null}]}}"""
        val replica = Gson().fromJson(json, SyncReplica::class.java)
        assertNull(replica.entries.getValue("!gone").single().value)
        assertEquals(1L, replica.entries.getValue("!gone").single().clock.getValue("device-a"))
    }

    @Test
    fun `remote revision avoids unchanged transfers and old metadata still reads peers`() {
        val file = GoogleDriveSync.RemoteFile("file", "peer", 100, "2")
        assertFalse(file.needsDownload(mapOf("file" to "2")))
        assertTrue(file.needsDownload(mapOf("file" to "1")))
        assertTrue(file.needsDownload(emptyMap()))
        assertTrue(file.copy(version = null).needsDownload(mapOf("file" to "2")))
        val state = SyncLibrary.empty("account")
        val legacy = Gson().fromJson(Gson().toJson(state), SyncLocalState::class.java)
        assertTrue(legacy.seenFiles.orEmpty().isEmpty())
    }
}

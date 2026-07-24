package com.rrajath.expander.util

import com.rrajath.expander.data.Snippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetBackupCodecTest {

    private val snippets = listOf(
        Snippet(
            id = 12,
            trigger = "!u",
            expansion = "Priority: urgent\nTASK: finish it",
            aliases = listOf("urgent", "priority"),
            isEnabled = true,
            createdAt = 100,
            updatedAt = 200
        ),
        Snippet(
            id = 13,
            trigger = "!ru",
            expansion = "Привет, мир 👋",
            isEnabled = false,
            createdAt = 300,
            updatedAt = 400
        )
    )

    @Test
    fun `json round trip preserves complete snippet state`() {
        val restored = SnippetBackupCodec.decodeJson(
            SnippetBackupCodec.encodeJson(snippets)
        )

        assertEquals(2, restored.size)
        assertEquals(0L, restored[0].id)
        assertEquals("!u", restored[0].trigger)
        assertEquals(snippets[0].expansion, restored[0].expansion)
        assertEquals(snippets[0].aliases, restored[0].aliases)
        assertTrue(restored[0].isEnabled)
        assertEquals(100L, restored[0].createdAt)
        assertEquals(200L, restored[0].updatedAt)
        assertEquals(false, restored[1].isEnabled)
    }

    @Test
    fun `text round trip handles multiline unicode and compact envelope`() {
        val backupText = SnippetBackupCodec.encodeText(snippets)
        val restored = SnippetBackupCodec.decodeText(backupText)

        assertTrue(backupText.startsWith(SnippetBackupCodec.TEXT_HEADER))
        assertEquals(snippets.map { it.expansion }, restored.map { it.expansion })
        assertEquals(snippets.map { it.isEnabled }, restored.map { it.isEnabled })
    }

    @Test
    fun `legacy v1 json export remains importable`() {
        val legacy = """
            {
              "version": "1.0",
              "exportDate": "Jul 18, 2026",
              "snippets": [
                {
                  "id": 99,
                  "trigger": "old",
                  "expansion": "Legacy value",
                  "isEnabled": false,
                  "createdAt": 10,
                  "updatedAt": 20
                }
              ]
            }
        """.trimIndent()

        val restored = SnippetBackupCodec.decodeJson(legacy)

        assertEquals("!old", restored.single().trigger)
        assertEquals("Legacy value", restored.single().expansion)
        assertEquals(false, restored.single().isEnabled)
    }

    @Test
    fun `legacy raw snippet array remains importable`() {
        val legacy = """
            [
              {
                "id": 7,
                "trigger": "!array",
                "expansion": "Raw legacy export",
                "isEnabled": true,
                "createdAt": 30,
                "updatedAt": 40
              }
            ]
        """.trimIndent()

        val restored = SnippetBackupCodec.decodeJson(legacy)

        assertEquals("!array", restored.single().trigger)
        assertEquals("Raw legacy export", restored.single().expansion)
        assertTrue(restored.single().aliases.isEmpty())
    }

    @Test
    fun `legacy text header remains importable`() {
        val legacyText = SnippetBackupCodec.encodeText(snippets)
            .replaceFirst(
                SnippetBackupCodec.TEXT_HEADER,
                SnippetBackupCodec.LEGACY_TEXT_HEADER
            )

        val restored = SnippetBackupCodec.decodeText(legacyText)

        assertEquals(snippets.map { it.aliases }, restored.map { it.aliases })
    }

    @Test
    fun `empty backup is valid and can clear a library`() {
        assertTrue(
            SnippetBackupCodec.decodeText(
                SnippetBackupCodec.encodeText(emptyList())
            ).isEmpty()
        )
    }

    @Test
    fun `damaged text backup is rejected`() {
        assertThrows(BackupFormatException::class.java) {
            SnippetBackupCodec.decodeText(
                "${SnippetBackupCodec.TEXT_HEADER}\nnot-valid-base64"
            )
        }
    }

    @Test
    fun `duplicate triggers are rejected case insensitively`() {
        val duplicateJson = """
            {
              "format": "snippetdeck-backup",
              "schemaVersion": 1,
              "snippets": [
                {"trigger": "!u", "expansion": "One"},
                {"trigger": "!U", "expansion": "Two"}
              ]
            }
        """.trimIndent()

        assertThrows(BackupFormatException::class.java) {
            SnippetBackupCodec.decodeJson(duplicateJson)
        }
    }

    @Test
    fun `alias cannot collide with another primary trigger`() {
        val duplicateJson = """
            {
              "format": "snippetdeck-backup",
              "schemaVersion": 2,
              "snippets": [
                {"trigger": "!one", "expansion": "One", "aliases": ["!two"]},
                {"trigger": "!TWO", "expansion": "Two", "aliases": []}
              ]
            }
        """.trimIndent()

        assertThrows(BackupFormatException::class.java) {
            SnippetBackupCodec.decodeJson(duplicateJson)
        }
    }
}

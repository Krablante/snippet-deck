package com.rrajath.expander.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorExpansionEngineTest {

    @Test
    fun `ignores oversized token without accepting its suffix`() {
        val text = "x".repeat(100_000) + "!u "
        assertNull(CursorExpansionEngine.findTriggerBeforeCursor(text, text.length, text.length))
        val supplementary = "!" + "😀".repeat(39) + " "
        assertEquals(supplementary.dropLast(1), CursorExpansionEngine.findTriggerBeforeCursor(supplementary, supplementary.length, supplementary.length)?.typedTrigger)
    }

    @Test
    fun `undo restores trigger after deleting supplementary character`() {
        val occurrence = CursorExpansionEngine.findTriggerBeforeCursor("!u suffix", 3, 3)!!
        val result = CursorExpansionEngine.expand(occurrence, "Done 😀")
        val undo = CursorExpansionEngine.undoAfterBackspace("Done suffix", 5, 5, result.history)
        assertEquals("!usuffix", undo?.text)
        assertEquals(2, undo?.cursor)
    }

    @Test
    fun `placeholder processing preserves unknown patterns and handles repeated values`() {
        val result = SnippetProcessor.process("{{year}} ".repeat(10_000))
        val year = result.substringBefore(' ')
        assertTrue(year.matches(Regex("\\d{4}")))
        assertEquals("$year ".repeat(10_000), result)
        assertEquals("{{unknown}} {{ date:invalid }}", SnippetProcessor.process("{{unknown}} {{ date:invalid }}"))
    }

    @Test
    fun `expands trigger at end of text`() {
        val text = "Priority: !u "
        val occurrence = CursorExpansionEngine.findTriggerBeforeCursor(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length
        )

        assertNotNull(occurrence)
        val result = CursorExpansionEngine.expand(occurrence!!, "urgent")
        assertEquals("Priority: urgent", result.edit.text)
        assertEquals(result.edit.text.length, result.edit.cursor)
    }

    @Test
    fun `expands trigger before cursor and preserves text after cursor`() {
        val text = "Priority: !u \nTASK: existing text"
        val cursor = text.indexOf('\n')
        val occurrence = CursorExpansionEngine.findTriggerBeforeCursor(
            text = text,
            selectionStart = cursor,
            selectionEnd = cursor
        )

        assertNotNull(occurrence)
        val result = CursorExpansionEngine.expand(occurrence!!, "urgent")
        assertEquals("Priority: urgent\nTASK: existing text", result.edit.text)
        assertEquals("Priority: urgent".length, result.edit.cursor)
    }

    @Test
    fun `uses token before cursor rather than last token in field`() {
        val text = "!u already written text"
        val cursor = "!u ".length
        val occurrence = CursorExpansionEngine.findTriggerBeforeCursor(
            text = text,
            selectionStart = cursor,
            selectionEnd = cursor
        )

        assertEquals("!u", occurrence?.typedTrigger)
        assertEquals("already written text", occurrence?.textAfterCursor)
    }

    @Test
    fun `does not expand while text range is selected`() {
        val text = "Priority: !u selected"
        assertNull(
            CursorExpansionEngine.findTriggerBeforeCursor(
                text = text,
                selectionStart = 10,
                selectionEnd = 13
            )
        )
    }

    @Test
    fun `falls back to end of field when node does not expose selection`() {
        val text = "Priority: !u "
        val occurrence = CursorExpansionEngine.findTriggerBeforeCursor(
            text = text,
            selectionStart = -1,
            selectionEnd = -1
        )

        assertEquals("!u", occurrence?.typedTrigger)
    }

    @Test
    fun `undo in middle restores trigger and preserves suffix`() {
        val text = "Priority: !u \nTASK: existing text"
        val cursor = text.indexOf('\n')
        val occurrence = CursorExpansionEngine.findTriggerBeforeCursor(
            text = text,
            selectionStart = cursor,
            selectionEnd = cursor
        )!!
        val expanded = CursorExpansionEngine.expand(occurrence, "urgent")
        val afterBackspace = "Priority: urgen\nTASK: existing text"

        val undo = CursorExpansionEngine.undoAfterBackspace(
            currentText = afterBackspace,
            selectionStart = "Priority: urgen".length,
            selectionEnd = "Priority: urgen".length,
            history = expanded.history
        )

        assertEquals("Priority: !u\nTASK: existing text", undo?.text)
        assertEquals("Priority: !u".length, undo?.cursor)
    }

    @Test
    fun `undo ignores deletion away from expanded cursor`() {
        val occurrence = CursorExpansionEngine.findTriggerBeforeCursor(
            text = "Prefix !u suffix",
            selectionStart = "Prefix !u ".length,
            selectionEnd = "Prefix !u ".length
        )!!
        val history = CursorExpansionEngine.expand(occurrence, "urgent").history

        assertNull(
            CursorExpansionEngine.undoAfterBackspace(
                currentText = "Prefix urgenuffix",
                selectionStart = "Prefix urgenuffix".length,
                selectionEnd = "Prefix urgenuffix".length,
                history = history
            )
        )
    }
}

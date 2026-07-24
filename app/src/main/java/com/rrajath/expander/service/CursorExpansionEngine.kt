package com.rrajath.expander.service

/**
 * Pure cursor-aware text editing used by [TextExpansionService].
 *
 * The delimiter that triggers expansion is consumed, matching the historic end-of-field
 * behavior. Text after the cursor is preserved verbatim.
 */
internal object CursorExpansionEngine {

    data class TriggerOccurrence(
        val textBeforeTrigger: String,
        val typedTrigger: String,
        val textAfterCursor: String
    )

    data class TextEdit(
        val text: String,
        val cursor: Int
    )

    data class ExpansionHistory(
        val textBeforeTrigger: String,
        val typedTrigger: String,
        val expansion: String,
        val textAfterCursor: String
    )

    data class ExpansionResult(
        val edit: TextEdit,
        val history: ExpansionHistory
    )

    fun findTriggerBeforeCursor(
        text: String,
        selectionStart: Int,
        selectionEnd: Int
    ): TriggerOccurrence? {
        val cursor = resolveCursor(text, selectionStart, selectionEnd) ?: return null
        if (cursor == 0 || text[cursor - 1] != ' ') return null

        val triggerEnd = cursor - 1
        var triggerStart = triggerEnd
        while (triggerStart > 0 && !text[triggerStart - 1].isWhitespace()) {
            triggerStart--
        }
        if (triggerStart == triggerEnd) return null

        return TriggerOccurrence(
            textBeforeTrigger = text.substring(0, triggerStart),
            typedTrigger = text.substring(triggerStart, triggerEnd),
            textAfterCursor = text.substring(cursor)
        )
    }

    fun expand(occurrence: TriggerOccurrence, expansion: String): ExpansionResult {
        val edit = TextEdit(
            text = occurrence.textBeforeTrigger + expansion + occurrence.textAfterCursor,
            cursor = occurrence.textBeforeTrigger.length + expansion.length
        )
        return ExpansionResult(
            edit = edit,
            history = ExpansionHistory(
                textBeforeTrigger = occurrence.textBeforeTrigger,
                typedTrigger = occurrence.typedTrigger,
                expansion = expansion,
                textAfterCursor = occurrence.textAfterCursor
            )
        )
    }

    fun undoAfterBackspace(
        currentText: String,
        selectionStart: Int,
        selectionEnd: Int,
        history: ExpansionHistory
    ): TextEdit? {
        if (history.expansion.isEmpty()) return null

        val expectedText = history.textBeforeTrigger +
            history.expansion.dropLast(1) +
            history.textAfterCursor
        if (currentText != expectedText) return null

        val expectedCursor = history.textBeforeTrigger.length + history.expansion.length - 1
        val cursorMatches = when {
            selectionStart >= 0 && selectionEnd >= 0 ->
                selectionStart == selectionEnd && selectionStart == expectedCursor
            selectionStart < 0 && selectionEnd < 0 -> history.textAfterCursor.isEmpty()
            else -> false
        }
        if (!cursorMatches) return null

        return TextEdit(
            text = history.textBeforeTrigger + history.typedTrigger + history.textAfterCursor,
            cursor = history.textBeforeTrigger.length + history.typedTrigger.length
        )
    }

    private fun resolveCursor(text: String, selectionStart: Int, selectionEnd: Int): Int? {
        if (selectionStart in 0..text.length && selectionEnd == selectionStart) {
            return selectionStart
        }

        // Some editable nodes do not expose selection. Preserve the old end-of-field behavior.
        if (selectionStart < 0 && selectionEnd < 0 && text.endsWith(' ')) {
            return text.length
        }

        return null
    }
}

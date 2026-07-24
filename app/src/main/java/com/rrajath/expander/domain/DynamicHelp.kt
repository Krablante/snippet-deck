package com.rrajath.expander.domain

import com.rrajath.expander.data.Snippet

object DynamicHelp {
    fun asVirtualSnippet(snippets: List<Snippet>): Snippet = Snippet(
        id = Long.MIN_VALUE,
        trigger = TriggerUtils.HELP_TRIGGER,
        expansion = build(snippets),
        isEnabled = true
    )

    fun build(snippets: List<Snippet>): String {
        val enabled = snippets
            .asSequence()
            .filter(Snippet::isEnabled)
            .filterNot { it.trigger.equals(TriggerUtils.HELP_TRIGGER, ignoreCase = true) }
            .sortedBy { it.trigger.lowercase() }
            .toList()

        if (enabled.isEmpty()) {
            return "SnippetDeck\n\nNo enabled snippets yet. Add one in the app."
        }

        return buildString {
            appendLine("SnippetDeck snippets")
            appendLine()
            enabled.forEach { snippet ->
                val aliases = snippet.aliases
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = " (", postfix = ")")
                    .orEmpty()
                val preview = snippet.expansion
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                    .trim()
                    .ifBlank { "<empty>" }
                    .let { if (it.length <= 72) it else it.take(71) + "…" }
                appendLine("${snippet.trigger}$aliases — $preview")
            }
        }.trimEnd()
    }
}

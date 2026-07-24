package com.rrajath.expander.domain

object TriggerUtils {
    const val HELP_TRIGGER = "!help"
    const val MAX_TRIGGER_LENGTH = 40

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("!") -> trimmed
            else -> "!$trimmed"
        }
    }

    fun validationError(raw: String, allowHelp: Boolean = false): String? {
        val trigger = normalize(raw)
        return exactValidationError(trigger, allowHelp)
    }

    fun normalizeAlias(raw: String): String = raw.trim()

    fun aliasValidationError(raw: String, allowHelp: Boolean = false): String? {
        val alias = normalizeAlias(raw)
        return exactValidationError(alias, allowHelp)
    }

    private fun exactValidationError(trigger: String, allowHelp: Boolean): String? {
        return when {
            trigger.isBlank() -> "Trigger cannot be empty"
            trigger.length > MAX_TRIGGER_LENGTH -> "Trigger must be at most $MAX_TRIGGER_LENGTH characters"
            trigger.any(Char::isWhitespace) -> "Trigger cannot contain spaces"
            !allowHelp && trigger.equals(HELP_TRIGGER, ignoreCase = true) ->
                "$HELP_TRIGGER is generated automatically"
            else -> null
        }
    }

    fun parseAliases(raw: String): List<String> = raw
        .split(ALIAS_SEPARATOR)
        .asSequence()
        .map(::normalizeAlias)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .toList()

    fun aliasesValidationError(raw: String, primaryTrigger: String): String? {
        val primary = normalize(primaryTrigger)
        val aliases = raw
            .split(ALIAS_SEPARATOR)
            .asSequence()
            .map(::normalizeAlias)
            .filter(String::isNotBlank)
            .toList()

        aliases.forEach { alias ->
            aliasValidationError(alias)?.let { return "Alias $alias: $it" }
            if (alias.equals(primary, ignoreCase = true)) {
                return "Alias duplicates the primary trigger"
            }
        }
        return null
    }

    fun conflictingTrigger(
        primaryTrigger: String,
        aliases: List<String>,
        reservedTriggers: Set<String>
    ): String? = allTriggers(primaryTrigger, aliases)
        .firstOrNull { it.lowercase() in reservedTriggers }

    fun matches(
        candidate: String,
        primaryTrigger: String,
        aliases: List<String>
    ): Boolean = allTriggers(primaryTrigger, aliases)
        .any { it.equals(candidate, ignoreCase = true) }

    fun allTriggers(primaryTrigger: String, aliases: List<String>): List<String> =
        listOf(normalize(primaryTrigger)) + aliases.map(::normalizeAlias)

    private val ALIAS_SEPARATOR = Regex("[,;\\n]")
}

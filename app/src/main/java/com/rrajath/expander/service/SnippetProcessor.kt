package com.rrajath.expander.service

import java.text.SimpleDateFormat
import java.util.*

object SnippetProcessor {

    private val dynamicPlaceholderRegex = Regex("""\{\{([^}]+)\}\}""")

    /**
     * Processes a snippet expansion, replacing dynamic placeholders with actual values.
     *
     * Supported placeholders:
     * - {{date}} - Current date in yyyy-MM-dd format
     * - {{time}} - Current time in HH:mm:ss format
     * - {{datetime}} - Current date and time in yyyy-MM-dd HH:mm:ss format
     * - {{day}} - Day of week (short form, e.g., Mon, Tue)
     * - {{day_long}} - Day of week (long form, e.g., Monday, Tuesday)
     * - {{month}} - Month (short form, e.g., Jan, Feb)
     * - {{month_long}} - Month (long form, e.g., January, February)
     * - {{year}} - Full year (e.g., 2026)
     * - {{year_short}} - Two-digit year (e.g., 26)
     * - {{week_num}} - Week number (e.g., 3)
     * - {{date:format}} - Custom date format (e.g., {{date:dd/MM/yyyy}})
     * - {{time:format}} - Custom time format (e.g., {{time:hh:mm a}})
     */
    fun process(expansion: String): String {
        val now = Date()
        val locale = Locale.getDefault()
        val formatted = mutableMapOf<String, String?>()
        return dynamicPlaceholderRegex.replace(expansion) { match ->
            val content = match.groupValues[1].trim()
            val pattern = when (content) {
                "date" -> "yyyy-MM-dd"
                "time" -> "HH:mm:ss"
                "datetime" -> "yyyy-MM-dd HH:mm:ss"
                "day" -> "EEE"
                "day_long" -> "EEEE"
                "month" -> "MMM"
                "month_long" -> "MMMM"
                "year" -> "yyyy"
                "year_short" -> "yy"
                "week_num" -> "w"
                else -> content.takeIf { it.startsWith("date:") || it.startsWith("time:") }
                    ?.substring(5)?.trim()
            }
            if (pattern.isNullOrEmpty()) match.value else {
                if (!formatted.containsKey(pattern)) {
                    formatted[pattern] = runCatching { SimpleDateFormat(pattern, locale).format(now) }.getOrNull()
                }
                formatted[pattern] ?: match.value
            }
        }
    }
}

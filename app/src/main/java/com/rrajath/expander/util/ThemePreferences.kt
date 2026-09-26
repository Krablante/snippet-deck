package com.rrajath.expander.util

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(
    val storageValue: String,
    val isDark: Boolean,
) {
    WHITE("WHITE", isDark = false),
    BLACK("BLACK", isDark = true),
    SEPIA("SEPIA", isDark = false),
    ;

    companion object {
        internal fun fromStorageValue(value: String?, systemDark: Boolean): ThemeMode = when (value) {
            WHITE.storageValue,
            "LIGHT",
            -> WHITE

            BLACK.storageValue,
            "DARK",
            -> BLACK

            SEPIA.storageValue -> SEPIA
            "SYSTEM" -> if (systemDark) BLACK else WHITE
            else -> WHITE
        }
    }
}

object ThemePreferences {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_REDUCE_TRANSPARENCY = "reduce_transparency"

    private val _themeMode = MutableStateFlow(ThemeMode.WHITE)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    private val _reduceTransparency = MutableStateFlow(false)
    val reduceTransparency: StateFlow<Boolean> = _reduceTransparency.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_THEME_MODE, null)
        val systemDark = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        _themeMode.value = ThemeMode.fromStorageValue(savedTheme, systemDark)
        _reduceTransparency.value = prefs.getBoolean(KEY_REDUCE_TRANSPARENCY, false)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
    }

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_THEME_MODE, null)
        val systemDark = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return ThemeMode.fromStorageValue(savedTheme, systemDark)
    }

    fun setReduceTransparency(context: Context, reduced: Boolean) {
        _reduceTransparency.value = reduced
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REDUCE_TRANSPARENCY, reduced).apply()
    }
}

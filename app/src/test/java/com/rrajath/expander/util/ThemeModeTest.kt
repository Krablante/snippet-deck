package com.rrajath.expander.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun currentStorageValuesRoundTrip() {
        ThemeMode.entries.forEach { theme ->
            assertEquals(
                theme,
                ThemeMode.fromStorageValue(theme.storageValue, systemDark = false),
            )
        }
    }

    @Test
    fun legacyExplicitThemesMigrateToWhiteAndBlack() {
        assertEquals(ThemeMode.WHITE, ThemeMode.fromStorageValue("LIGHT", systemDark = true))
        assertEquals(ThemeMode.BLACK, ThemeMode.fromStorageValue("DARK", systemDark = false))
    }

    @Test
    fun legacySystemThemeResolvesOnceFromCurrentSystemMode() {
        assertEquals(ThemeMode.WHITE, ThemeMode.fromStorageValue("SYSTEM", systemDark = false))
        assertEquals(ThemeMode.BLACK, ThemeMode.fromStorageValue("SYSTEM", systemDark = true))
    }

    @Test
    fun missingOrUnknownThemeFallsBackToWhite() {
        assertEquals(ThemeMode.WHITE, ThemeMode.fromStorageValue(null, systemDark = true))
        assertEquals(ThemeMode.WHITE, ThemeMode.fromStorageValue("UNKNOWN", systemDark = true))
    }
}

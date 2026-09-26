package com.rrajath.expander.ui.theme

import androidx.compose.ui.graphics.Color
import com.rrajath.expander.util.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetDeckThemeTest {
    @Test
    fun chalkThemeUsesWarmCanvasAndMutedControlColor() {
        val scheme = snippetDeckColorScheme(ThemeMode.WHITE)

        assertEquals(Color(0xFFF6F3ED), scheme.background)
        assertEquals(Color(0xFF505477), scheme.primary)
        assertNotEquals(scheme.primaryContainer, scheme.background)
        assertFalse(ThemeMode.WHITE.isDark)
    }

    @Test
    fun inkThemeUsesDarkCanvasAndLightText() {
        val scheme = snippetDeckColorScheme(ThemeMode.BLACK)

        assertEquals(Color(0xFF1B1B23), scheme.background)
        assertEquals(Color(0xFFF1EFF2), scheme.onBackground)
        assertTrue(ThemeMode.BLACK.isDark)
    }

    @Test
    fun parchmentThemeUsesWarmCanvasAndPlumAccent() {
        val scheme = snippetDeckColorScheme(ThemeMode.SEPIA)

        assertEquals(Color(0xFFEEE4D5), scheme.background)
        assertEquals(Color(0xFF714857), scheme.primary)
        assertFalse(ThemeMode.SEPIA.isDark)
    }
}

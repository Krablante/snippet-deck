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
    fun whiteThemeUsesNeutralCanvasAndCard() {
        val scheme = snippetDeckColorScheme(ThemeMode.WHITE)

        assertEquals(Color(0xFFFCFCFA), scheme.background)
        assertEquals(Color(0xFFF4F6F3), scheme.surfaceVariant)
        assertNotEquals(scheme.primaryContainer, scheme.background)
        assertFalse(ThemeMode.WHITE.isDark)
    }

    @Test
    fun blackThemeUsesTextoryDarkCanvas() {
        val scheme = snippetDeckColorScheme(ThemeMode.BLACK)

        assertEquals(Color(0xFF101512), scheme.background)
        assertEquals(Color(0xFFE5ECE7), scheme.onBackground)
        assertTrue(ThemeMode.BLACK.isDark)
    }

    @Test
    fun sepiaThemeUsesWarmCanvasAndBrownAccent() {
        val scheme = snippetDeckColorScheme(ThemeMode.SEPIA)

        assertEquals(Color(0xFFF3E8D6), scheme.background)
        assertEquals(Color(0xFF8A603D), scheme.primary)
        assertFalse(ThemeMode.SEPIA.isDark)
    }
}

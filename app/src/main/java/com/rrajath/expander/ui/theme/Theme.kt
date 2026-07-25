package com.rrajath.expander.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rrajath.expander.util.ThemeMode

internal data class SnippetDeckColors(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val inkMuted: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val accentHighlight: Color,
    val card: Color,
    val error: Color,
    val errorHighlight: Color,
)

internal val WhiteSnippetDeckColors = SnippetDeckColors(
    canvas = Color(0xFFFCFCFA),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF202320),
    inkMuted = Color(0xFF6E746E),
    border = Color(0xFFE7EAE6),
    accent = Color(0xFF48785A),
    onAccent = Color.White,
    accentHighlight = Color(0xFFE2F2E4),
    card = Color(0xFFF4F6F3),
    error = Color(0xFF985F63),
    errorHighlight = Color(0xFFF8E9E9),
)

internal val BlackSnippetDeckColors = SnippetDeckColors(
    canvas = Color(0xFF101512),
    surface = Color(0xFF181E1A),
    ink = Color(0xFFE5ECE7),
    inkMuted = Color(0xFFA8B5AC),
    border = Color(0xFF344039),
    accent = Color(0xFF82C995),
    onAccent = Color(0xFF102418),
    accentHighlight = Color(0xFF24452D),
    card = Color(0xFF1B221D),
    error = Color(0xFFE3A0A4),
    errorHighlight = Color(0xFF4A292D),
)

internal val SepiaSnippetDeckColors = SnippetDeckColors(
    canvas = Color(0xFFF3E8D6),
    surface = Color(0xFFFFF8EC),
    ink = Color(0xFF49392E),
    inkMuted = Color(0xFF756455),
    border = Color(0xFFDCC9AF),
    accent = Color(0xFF8A603D),
    onAccent = Color(0xFFFFF8EC),
    accentHighlight = Color(0xFFEAD9BE),
    card = Color(0xFFF8EEDC),
    error = Color(0xFF98534B),
    errorHighlight = Color(0xFFF1DDD5),
)

internal fun snippetDeckColors(themeMode: ThemeMode): SnippetDeckColors = when (themeMode) {
    ThemeMode.WHITE -> WhiteSnippetDeckColors
    ThemeMode.BLACK -> BlackSnippetDeckColors
    ThemeMode.SEPIA -> SepiaSnippetDeckColors
}

internal fun snippetDeckColorScheme(themeMode: ThemeMode): ColorScheme {
    val colors = snippetDeckColors(themeMode)
    val base = if (themeMode.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentHighlight,
        onPrimaryContainer = colors.ink,
        secondary = colors.accent,
        onSecondary = colors.onAccent,
        secondaryContainer = colors.accentHighlight,
        onSecondaryContainer = colors.ink,
        tertiary = colors.accent,
        onTertiary = colors.onAccent,
        background = colors.canvas,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.card,
        onSurfaceVariant = colors.inkMuted,
        surfaceTint = colors.accent,
        outline = colors.border,
        outlineVariant = colors.border,
        error = colors.error,
        onError = colors.onAccent,
        errorContainer = colors.errorHighlight,
        onErrorContainer = colors.ink,
        surfaceDim = colors.canvas,
        surfaceBright = colors.surface,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.card,
        surfaceContainerHighest = colors.card,
    )
}

@Composable
fun SnippetDeckTheme(
    themeMode: ThemeMode = ThemeMode.WHITE,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = snippetDeckColorScheme(themeMode),
        typography = Typography,
        content = content,
    )
}

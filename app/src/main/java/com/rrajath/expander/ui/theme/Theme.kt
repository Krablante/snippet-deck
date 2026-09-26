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
    canvas = Color(0xFFF6F3ED),
    surface = Color(0xFFFFFCF7),
    ink = Color(0xFF282832),
    inkMuted = Color(0xFF666570),
    border = Color(0xFFDCD8D1),
    accent = Color(0xFF505477),
    onAccent = Color.White,
    accentHighlight = Color(0xFFE8E6ED),
    card = Color(0xFFECE9E4),
    error = Color(0xFFA03E45),
    errorHighlight = Color(0xFFF5E5E4),
)

internal val BlackSnippetDeckColors = SnippetDeckColors(
    canvas = Color(0xFF1B1B23),
    surface = Color(0xFF24242D),
    ink = Color(0xFFF1EFF2),
    inkMuted = Color(0xFFB6B3C1),
    border = Color(0xFF44434E),
    accent = Color(0xFFC8C8EE),
    onAccent = Color(0xFF222238),
    accentHighlight = Color(0xFF393947),
    card = Color(0xFF32323D),
    error = Color(0xFFF1A6AA),
    errorHighlight = Color(0xFF4B3039),
)

internal val SepiaSnippetDeckColors = SnippetDeckColors(
    canvas = Color(0xFFEEE4D5),
    surface = Color(0xFFF8EFDF),
    ink = Color(0xFF382C2C),
    inkMuted = Color(0xFF6E5B58),
    border = Color(0xFFD4C4B1),
    accent = Color(0xFF714857),
    onAccent = Color(0xFFFFFAF5),
    accentHighlight = Color(0xFFE8D6D0),
    card = Color(0xFFE9DCCB),
    error = Color(0xFF9C403D),
    errorHighlight = Color(0xFFF1DAD3),
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

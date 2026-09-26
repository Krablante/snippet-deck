package com.rrajath.expander.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass

@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassBlock(
    hazeState: HazeState,
    opaque: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    accent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.35f
    val depth = if (opaque) 0.dp else 14.dp
    val style = GlassStyle.regular.then {
        backgroundColor(colors.background)
        tint(if (dark) colors.background.copy(alpha = if (accent) 0.45f else 0.6f)
            else colors.primary.copy(alpha = if (accent) 0.34f else 0.22f))
        shape(shape)
        surfaceProfile(SurfaceProfile.Lip)
        specularIntensity(if (focused) 1f else 0.9f)
        ambientResponse(0.7f)
        edgeShadow(Color.Black.copy(alpha = 0.28f))
        chromaticAberrationStrength(0.08f)
    }

    Box(modifier = modifier.shadow(depth, shape, ambientColor = colors.primary.copy(alpha = 0.22f), spotColor = colors.onSurface.copy(alpha = 0.18f))) {
        val backdrop = if (opaque) {
            Modifier.background(if (accent) colors.primary else colors.surface, shape)
                .border(1.dp, colors.outlineVariant, shape)
        } else {
            Modifier.hazeGlass(
                input = HazeInput.Sources(hazeState, retention = HazeSourceRetention.ClearWhenUnavailable),
                style = style,
            )
        }
        Box(Modifier.matchParentSize().then(backdrop))
        if (!opaque) {
            val glaze = Brush.linearGradient(
                0f to colors.primary.copy(alpha = if (accent) 0.26f else if (dark) 0.09f else 0.19f),
                0.5f to colors.surface.copy(alpha = 0.02f),
                1f to colors.primary.copy(alpha = if (accent) 0.20f else if (dark) 0.06f else 0.12f),
            )
            val rim = if (focused) colors.primary.copy(alpha = 0.8f) else colors.outline.copy(alpha = 0.55f)
            Box(Modifier.matchParentSize().background(glaze, shape).border(1.5.dp, rim, shape))
        }
        content()
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hazeState: HazeState,
    opaque: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    GlassBlock(
        hazeState = hazeState,
        opaque = opaque,
        shape = shape,
        focused = focused,
        modifier = modifier.height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "Search snippets" },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Search snippets", color = colors.onSurfaceVariant)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

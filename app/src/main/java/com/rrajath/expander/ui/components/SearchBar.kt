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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

@Composable
fun Modifier.glassControl(
    hazeState: HazeState,
    opaque: Boolean,
    shape: RoundedCornerShape,
    focused: Boolean = false,
    tintAlpha: Float = 0.4f,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val edge = if (focused) SolidColor(colors.primary.copy(alpha = 0.8f)) else Brush.linearGradient(
        listOf(
            colors.surface.copy(alpha = 0.9f),
            colors.outline.copy(alpha = 0.4f),
            colors.onSurface.copy(alpha = 0.12f),
        )
    )
    val material = if (opaque) {
        Modifier.background(colors.surface, shape)
    } else {
        Modifier.hazeChild(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(colors.surface.copy(alpha = tintAlpha)),
                blurRadius = 16.dp,
                noiseFactor = 0.04f,
                fallbackTint = HazeTint(colors.surface.copy(alpha = 0.96f)),
            ),
        )
    }
    return this.clip(shape).then(material).border(1.dp, edge, shape)
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
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier.height(52.dp).glassControl(hazeState, opaque, shape, focused),
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

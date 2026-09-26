package com.rrajath.expander.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.service.TextExpansionService
import com.rrajath.expander.ui.components.SearchBar

@Composable
fun SnippetListScreen(
    snippets: List<Snippet>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSnippetClick: (Long) -> Unit,
    onSnippetDelete: (Snippet) -> Unit,
    onSnippetToggle: (Snippet) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityEnabled by remember { mutableStateOf(TextExpansionService.isAccessibilityServiceEnabled(context)) }
    var serviceEnabled by remember { mutableStateOf(TextExpansionService.isServiceEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = TextExpansionService.isAccessibilityServiceEnabled(context)
                serviceEnabled = TextExpansionService.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 116.dp, bottom = 96.dp),
        ) {
            if (!accessibilityEnabled || !serviceEnabled) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(colors.errorContainer)
                            .padding(start = 18.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (accessibilityEnabled) "Text expansion is paused" else "Text expansion is off", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            if (accessibilityEnabled) onSettingsClick()
                            else context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) {
                            Text("Enable")
                        }
                    }
                }
            }
            item {
                Text(
                    text = if (searchQuery.isEmpty()) "${snippets.size} ${if (snippets.size == 1) "snippet" else "snippets"}" else "${snippets.size} found",
                    modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (snippets.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isEmpty()) "No snippets yet. Tap + to add one." else "No matching snippets",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 52.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                items(snippets, key = { it.id }) { snippet ->
                    SnippetItem(
                        snippet = snippet,
                        onClick = { onSnippetClick(snippet.id) },
                        onDelete = { onSnippetDelete(snippet) },
                        onToggle = { onSnippetToggle(snippet) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(colors.background, colors.background.copy(alpha = 0.96f), colors.background.copy(alpha = 0f)))
            ).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                Text("SnippetDeck", modifier = Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.onBackground)
                }
            }
            SearchBar(query = searchQuery, onQueryChange = onSearchQueryChange, modifier = Modifier.fillMaxWidth())
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add snippet")
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SnippetItem(
    snippet: Snippet,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${snippet.trigger}?") },
            text = { Text("This snippet will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snippet.trigger,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (snippet.isEnabled) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!snippet.isEnabled) {
                    Text("  Off", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            if (snippet.aliases.isNotEmpty()) {
                Text(
                    snippet.aliases.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                snippet.expansion.replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options for ${snippet.trigger}", tint = colors.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(if (snippet.isEnabled) "Turn off" else "Turn on") }, onClick = { menuOpen = false; onToggle() })
                DropdownMenuItem(text = { Text("Delete", color = colors.error) }, onClick = { menuOpen = false; confirmDelete = true })
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 20.dp), color = colors.outlineVariant.copy(alpha = 0.65f))
}

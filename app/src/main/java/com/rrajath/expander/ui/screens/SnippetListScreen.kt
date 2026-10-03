package com.rrajath.expander.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.service.TextExpansionService
import com.rrajath.expander.ui.components.SearchBar
import com.rrajath.expander.ui.components.GlassBlock
import com.rrajath.expander.sync.SyncUiState
import com.rrajath.expander.util.ThemePreferences
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
internal fun SnippetListScreen(
    snippets: List<Snippet>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSnippetClick: (Long) -> Unit,
    onSnippetDelete: (Snippet) -> Unit,
    onSnippetToggle: (Snippet) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    syncState: SyncUiState,
    syncConnected: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityEnabled by remember { mutableStateOf(TextExpansionService.isAccessibilityServiceEnabled(context)) }
    var serviceEnabled by remember { mutableStateOf(TextExpansionService.isServiceEnabled(context)) }
    var highContrast by remember {
        mutableStateOf(Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = TextExpansionService.isAccessibilityServiceEnabled(context)
                serviceEnabled = TextExpansionService.isServiceEnabled(context)
                highContrast = Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val colors = MaterialTheme.colorScheme
    val reduceTransparency by ThemePreferences.reduceTransparency.collectAsState()
    val opaqueControls = reduceTransparency || highContrast
    val hazeState = rememberHazeState()

    Box(modifier = modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                .then(if (opaqueControls) Modifier else Modifier.hazeSource(state = hazeState)),
            contentPadding = PaddingValues(top = 58.dp, bottom = 94.dp),
        ) {
            if (!accessibilityEnabled || !serviceEnabled) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(colors.errorContainer)
                            .padding(start = 18.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (accessibilityEnabled) "Text expansion is paused" else "Text expansion is off", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer)
                        TextButton(onClick = {
                            if (accessibilityEnabled) onSettingsClick()
                            else context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) {
                            Text("Enable", color = colors.onErrorContainer)
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
                        syncConnected = syncConnected,
                    )
                }
            }
        }

        GlassBlock(
            hazeState = hazeState,
            opaque = opaqueControls,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SnippetDeck",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val label = when {
                    syncState is SyncUiState.Conflict -> "Resolve"
                    syncState is SyncUiState.Failed -> "Retry"
                    syncState is SyncUiState.Working -> "Syncing"
                    !syncConnected -> "Connect"
                    syncState is SyncUiState.Synced -> "Synced"
                    else -> "Sync"
                }
                TextButton(
                    onClick = { if (syncState is SyncUiState.Conflict) onSettingsClick() else onSyncClick() },
                    enabled = syncState !is SyncUiState.Working,
                    modifier = Modifier.height(48.dp).widthIn(min = 64.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    if (syncState is SyncUiState.Working) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (syncState is SyncUiState.Failed || syncState is SyncUiState.Conflict) colors.error else colors.onSurface,
                        )
                    }
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.onBackground)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hazeState = hazeState,
                opaque = opaqueControls,
                modifier = Modifier.weight(1f),
            )
            GlassBlock(
                hazeState = hazeState,
                opaque = opaqueControls,
                shape = RoundedCornerShape(20.dp),
                accent = true,
                modifier = Modifier.size(52.dp).clickable(role = Role.Button, onClick = onAddClick),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add snippet",
                    tint = if (opaqueControls) colors.onPrimary else colors.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun SnippetItem(
    snippet: Snippet,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    syncConnected: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${snippet.trigger}?") },
            text = { Text(if (syncConnected) "This snippet will be removed from this device and your other devices on their next sync." else "This snippet will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp)
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
                snippet.expansion.take(160).replace('\n', ' '),
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

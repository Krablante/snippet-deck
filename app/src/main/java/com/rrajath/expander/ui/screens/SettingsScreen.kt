package com.rrajath.expander.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rrajath.expander.service.TextExpansionService
import com.rrajath.expander.sync.SyncUiState
import com.rrajath.expander.ui.theme.snippetDeckColors
import com.rrajath.expander.update.UpdateUiState
import com.rrajath.expander.update.updateStatusText
import com.rrajath.expander.util.ThemeMode
import com.rrajath.expander.util.ThemePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onCopyTextClick: () -> Unit,
    onImportText: (String) -> Unit,
    onThemeChanged: () -> Unit = {},
    snippetCount: Int = 0,
    syncState: SyncUiState = SyncUiState.Off,
    syncConnected: Boolean = false,
    syncHasHistory: Boolean = false,
    onSync: () -> Unit = {},
    onDisconnectSync: () -> Unit = {},
    onResetSync: () -> Unit = {},
    onReplaceCloud: () -> Unit = {},
    onUseOtherDevice: () -> Unit = {},
    updateState: UpdateUiState = UpdateUiState.Idle,
    onCheckForUpdates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var serviceEnabled by remember { mutableStateOf(TextExpansionService.isServiceEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceEnabled = TextExpansionService.isServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var currentTheme by remember { mutableStateOf(ThemePreferences.getThemeMode(context)) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTextImportDialog by remember { mutableStateOf(false) }
    var showReplaceCloudDialog by remember { mutableStateOf(false) }
    var showUseOtherDialog by remember { mutableStateOf(false) }
    var showResetSyncDialog by remember { mutableStateOf(false) }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).versionName
        }.getOrNull().orEmpty()
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { theme ->
                currentTheme = theme
                ThemePreferences.setThemeMode(context, theme)
                onThemeChanged()
                showThemeDialog = false
            }
        )
    }

    if (showTextImportDialog) {
        TextBackupImportDialog(
            onDismiss = { showTextImportDialog = false },
            onImport = { backupText ->
                showTextImportDialog = false
                onImportText(backupText)
            }
        )
    }

    if (showReplaceCloudDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceCloudDialog = false },
            title = { Text("Use this device's library?") },
            text = { Text("This will replace conflicting cloud versions with the $snippetCount snippets on this device, including deletions. Other devices keep their local data until their next sync. Export a backup first if you might need the other versions.") },
            confirmButton = {
                Button(onClick = { showReplaceCloudDialog = false; onReplaceCloud() }) {
                    Text("Use this device")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceCloudDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showUseOtherDialog) {
        AlertDialog(
            onDismissRequest = { showUseOtherDialog = false },
            title = { Text("Use the other device's library?") },
            text = { Text("Your local snippets will be replaced by the copy saved in Google Drive by the other device, including deletions. Export a backup first if you need this device's edits.") },
            confirmButton = {
                Button(onClick = { showUseOtherDialog = false; onUseOtherDevice() }) {
                    Text("Use other device")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUseOtherDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showResetSyncDialog) {
        AlertDialog(
            onDismissRequest = { showResetSyncDialog = false },
            title = { Text("Switch Google accounts?") },
            text = { Text("First revoke SnippetDeck access in your Google Account's connected apps, then connect a different account here. This clears only this device's sync history, not its snippets or the cloud files. Reconnecting the same account may bring back old deleted snippets.") },
            confirmButton = {
                Button(onClick = { showResetSyncDialog = false; onResetSync() }) { Text("Reset sync history") }
            },
            dismissButton = {
                TextButton(onClick = { showResetSyncDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Text expansion", style = MaterialTheme.typography.bodyLarge)
                    Text(if (serviceEnabled) "On" else "Paused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = serviceEnabled, onCheckedChange = {
                    serviceEnabled = it
                    TextExpansionService.setServiceEnabled(context, it)
                })
            }
            SettingsItem(
                title = "Accessibility Settings",
                subtitle = "System permission",
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
            SettingsSection("Appearance")
            SettingsItem(
                title = "Theme",
                subtitle = when (currentTheme) {
                    ThemeMode.WHITE -> "Chalk"
                    ThemeMode.BLACK -> "Ink"
                    ThemeMode.SEPIA -> "Parchment"
                },
                onClick = { showThemeDialog = true }
            )
            SettingsSection("Google Drive")
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(when (syncState) {
                    SyncUiState.Off -> "Not connected"
                    SyncUiState.Ready -> "Connected"
                    SyncUiState.Working -> "Syncing…"
                    is SyncUiState.Synced -> "Up to date · ${syncState.count} snippets"
                    is SyncUiState.Conflict -> "Conflicts: ${syncState.triggers.joinToString()}"
                    is SyncUiState.Failed -> "Sync failed: ${syncState.message}"
                }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onSync, enabled = syncState != SyncUiState.Working) {
                    Text(if (syncConnected) "Sync now" else "Connect")
                }
            }
            if (!syncConnected) {
                Text("Optional · your snippets in Google Drive are readable by Google", modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (syncState is SyncUiState.Conflict) {
                SettingsItem("Use this device's library", "Resolve conflicting edits", { showReplaceCloudDialog = true })
                SettingsItem("Use other device's library", "Replace this device's copy", { showUseOtherDialog = true })
            }
            if (syncConnected) SettingsItem("Disconnect Google Drive", "Keep snippets on this device", onDisconnectSync)
            if (syncHasHistory) SettingsItem("Switch Google account", "Clear this device's sync history", { showResetSyncDialog = true })

            SettingsSection("Backup & transfer")
            SettingsItem("Export backup file", "JSON", onExportClick)
            SettingsItem("Import backup file", "Replaces the library after confirmation", onImportClick)
            SettingsItem("Copy backup text", "For notes or messages", onCopyTextClick)
            SettingsItem("Paste backup text", "Replaces the library after confirmation", { showTextImportDialog = true })

            SettingsSection("About")
            Text("SnippetDeck · $versionName", modifier = Modifier.padding(start = 20.dp, top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            UpdateSettingsAction(state = updateState, installedVersion = versionName, onClick = onCheckForUpdates)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        title,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 5.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UpdateSettingsAction(
    state: UpdateUiState,
    installedVersion: String,
    onClick: () -> Unit,
) {
    val enabled = state !is UpdateUiState.Checking &&
        state !is UpdateUiState.Downloading &&
        state !is UpdateUiState.UnsupportedBuild

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Check for updates",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = updateStatusText(state, installedVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        if (state is UpdateUiState.Checking || state is UpdateUiState.Downloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun TextBackupImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    val context = LocalContext.current
    var backupText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste backup text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste the complete text beginning with SNIPPETDECK_BACKUP_V2. Older V1 backups are also accepted. You will review the snippet count before replacement.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = backupText,
                    onValueChange = { backupText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Backup text") },
                    minLines = 6,
                    maxLines = 10
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            backupText = clipboard.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                .orEmpty()
                        }
                    ) {
                        Text("Paste clipboard")
                    }
                    Text(
                        text = "${backupText.length} chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(backupText) },
                enabled = backupText.isNotBlank()
            ) {
                Text("Review import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 20.dp)
    ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        selected = currentTheme == theme,
                        onClick = { onThemeSelected(theme) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ThemeOption(
    theme: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    val preview = snippetDeckColors(theme)
    val (title, description) = when (theme) {
        ThemeMode.WHITE -> "Chalk" to "Warm and light"
        ThemeMode.BLACK -> "Ink" to "Quiet and dark"
        ThemeMode.SEPIA -> "Parchment" to "Soft and earthy"
    }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 9.dp, end = 4.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = preview.canvas, shape = RoundedCornerShape(9.dp)) {
                Box(modifier = Modifier.size(width = 48.dp, height = 34.dp).padding(10.dp).background(preview.accent, RoundedCornerShape(5.dp)))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

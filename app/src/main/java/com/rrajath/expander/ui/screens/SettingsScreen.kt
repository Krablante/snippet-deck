package com.rrajath.expander.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rrajath.expander.service.TextExpansionService
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
    updateState: UpdateUiState = UpdateUiState.Idle,
    onCheckForUpdates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(TextExpansionService.isServiceEnabled(context)) }
    var currentTheme by remember { mutableStateOf(ThemePreferences.getThemeMode(context)) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTextImportDialog by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Service Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Text Expansion",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (serviceEnabled) "Service is active" else "Service is disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = serviceEnabled,
                            onCheckedChange = {
                                serviceEnabled = it
                                TextExpansionService.setServiceEnabled(context, it)
                            }
                        )
                    }
                }
            }

            // Accessibility Settings
            SettingsItem(
                title = "Accessibility Settings",
                subtitle = "Grant accessibility permission",
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            HorizontalDivider()

            // Appearance Section
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            SettingsItem(
                title = "Theme",
                subtitle = when (currentTheme) {
                    ThemeMode.WHITE -> "White"
                    ThemeMode.BLACK -> "Black"
                    ThemeMode.SEPIA -> "Sepia Paper"
                },
                onClick = { showThemeDialog = true }
            )

            HorizontalDivider()

            // Import/Export Section
            Text(
                text = "Backup & transfer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Move the complete library between phones. Import replaces local snippets after confirmation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                BackupAction(
                    badge = "FILE",
                    title = "Export backup file",
                    subtitle = "Readable JSON for long-term storage",
                    onClick = onExportClick
                )
                HorizontalDivider()
                BackupAction(
                    badge = "FILE",
                    title = "Import backup file",
                    subtitle = "Restore JSON exported by SnippetDeck",
                    onClick = onImportClick
                )
                HorizontalDivider()
                BackupAction(
                    badge = "TEXT",
                    title = "Copy backup text",
                    subtitle = "Compact text for Saved Messages or notes",
                    onClick = onCopyTextClick
                )
                HorizontalDivider()
                BackupAction(
                    badge = "TEXT",
                    title = "Paste backup text",
                    subtitle = "Restore text copied from another phone",
                    onClick = { showTextImportDialog = true }
                )
            }

            HorizontalDivider()

            // About Section
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SnippetDeck",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "A text expansion tool that works system-wide using accessibility services.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    UpdateSettingsAction(
                        state = updateState,
                        installedVersion = versionName,
                        onClick = onCheckForUpdates,
                    )
                }
            }
        }
    }
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
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = "GITHUB",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Check for updates",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun BackupAction(
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = badge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 4.dp)
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
        ThemeMode.WHITE -> "White" to "Clean neutral canvas"
        ThemeMode.BLACK -> "Black" to "Deep low-light palette"
        ThemeMode.SEPIA -> "Sepia Paper" to "Warm book-like paper"
    }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 9.dp, end = 4.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(preview.canvas),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 31.dp, height = 19.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(preview.surface),
                )
                Box(
                    modifier = Modifier
                        .padding(end = 5.dp, bottom = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(preview.accent)
                        .align(Alignment.BottomEnd),
                )
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

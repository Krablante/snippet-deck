package com.rrajath.expander.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rrajath.expander.update.UpdateUiState
import java.util.Locale

@Composable
internal fun UpdateDialog(
    state: UpdateUiState,
    onDownloadAndInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Update available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SnippetDeck ${state.release.version}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.release.title != "SnippetDeck ${state.release.version}") {
                        Text(
                            text = state.release.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Verified GitHub APK · ${formatBytes(state.release.asset.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDownloadAndInstall) { Text("Download & install") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading ${state.release.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${state.progress}% · The APK will be verified before Android opens the installer.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onCancelDownload) { Text("Cancel") }
            },
        )

        is UpdateUiState.InstallPermissionRequired -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Allow app installation") },
            text = {
                Text(
                    "Android needs one-time permission for SnippetDeck to hand the verified APK " +
                        "to the system installer. No silent installation is possible.",
                )
            },
            confirmButton = {
                TextButton(onClick = onOpenInstallPermission) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            },
        )

        is UpdateUiState.DownloadFailed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Update failed") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDownloadAndInstall) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            },
        )

        UpdateUiState.Idle,
        UpdateUiState.UnsupportedBuild,
        UpdateUiState.Checking,
        UpdateUiState.Offline,
        is UpdateUiState.Current,
        is UpdateUiState.CheckFailed,
        -> Unit
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.ROOT, bytes.toDouble() / (1024L * 1024L))
    bytes >= 1024L -> "%.0f KB".format(Locale.ROOT, bytes.toDouble() / 1024L)
    else -> "$bytes B"
}

package com.rrajath.expander.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rrajath.expander.ui.SnippetViewModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.rrajath.expander.sync.SyncUiState
import com.rrajath.expander.sync.SyncViewModel
import com.rrajath.expander.ui.screens.AddEditSnippetScreen
import com.rrajath.expander.ui.screens.SettingsScreen
import com.rrajath.expander.ui.screens.SnippetListScreen
import com.rrajath.expander.update.UpdateUiState
import com.rrajath.expander.util.ImportExportManager
import com.rrajath.expander.util.SnippetBackupCodec
import com.rrajath.expander.domain.TriggerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen(val route: String) {
    object SnippetList : Screen("snippet_list")
    object AddSnippet : Screen("add_snippet?prefillExpansion={prefillExpansion}") {
        fun createRoute(prefillExpansion: String? = null): String =
            if (prefillExpansion == null) {
                "add_snippet"
            } else {
                "add_snippet?prefillExpansion=${Uri.encode(prefillExpansion)}"
            }
    }
    object EditSnippet : Screen("edit_snippet/{snippetId}") {
        fun createRoute(snippetId: Long) = "edit_snippet/$snippetId"
    }
    object Settings : Screen("settings")
}

private data class PendingImport(
    val snippets: List<com.rrajath.expander.data.Snippet>,
    val source: String
)

@Composable
internal fun NavGraph(
    navController: NavHostController,
    initialExpansion: String? = null,
    updateState: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    viewModel: SnippetViewModel = viewModel(),
    syncViewModel: SyncViewModel = viewModel(),
) {
    // Navigate to Add Snippet when launched via ACTION_PROCESS_TEXT.
    // MainActivity gets a fresh instance per PROCESS_TEXT launch, so firing
    // once per composition is fine.
    LaunchedEffect(Unit) {
        if (initialExpansion != null) {
            navController.navigate(Screen.AddSnippet.createRoute(initialExpansion))
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by syncViewModel.state.collectAsState()
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    var syncChoice by remember { mutableStateOf(SyncViewModel.SyncChoice.MERGE) }
    val authorizeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            syncViewModel.fail("Google Drive access was cancelled")
        } else {
            runCatching { authorizationClient.getAuthorizationResultFromIntent(result.data) }
                .onSuccess { authorization ->
                    val token = authorization.accessToken
                    if (token == null) syncViewModel.fail("Google Drive did not grant access")
                    else syncViewModel.syncWith(token, syncChoice)
                }
                .onFailure { syncViewModel.fail(it.message ?: "Google Drive authorization failed") }
        }
    }

    fun syncWithGoogle(choice: SyncViewModel.SyncChoice = SyncViewModel.SyncChoice.MERGE) {
        syncChoice = choice
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata")))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { authorization ->
                if (authorization.hasResolution()) {
                    val pending = authorization.pendingIntent
                    if (pending == null) syncViewModel.fail("Google Drive authorization is unavailable")
                    else authorizeLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    val token = authorization.accessToken
                    if (token == null) syncViewModel.fail("Google Drive did not grant access")
                    else syncViewModel.syncWith(token, choice)
                }
            }
            .addOnFailureListener { syncViewModel.fail(it.message ?: "Google Drive authorization failed") }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var lastPull by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner, initialExpansion) {
        fun onForeground() {
            val now = SystemClock.elapsedRealtime()
            if (initialExpansion == null && syncViewModel.connected() && now - lastPull > 60_000) {
                lastPull = now
                syncWithGoogle()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onForeground()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snippets by viewModel.snippets.collectAsState()
    val allSnippets by viewModel.allSnippets.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val result = ImportExportManager.exportSnippets(context, allSnippets, it)
                result.onSuccess {
                    Toast.makeText(context, "Snippets exported successfully", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val result = ImportExportManager.importSnippets(context, it)
                result.onSuccess { importedSnippets ->
                    pendingImport = PendingImport(importedSnippets, "file")
                }.onFailure { error ->
                    Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    pendingImport?.let { importData ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace local snippets?") },
            text = {
                Text(
                    "The ${importData.source} backup contains ${importData.snippets.size} snippets. " +
                        "It will replace all ${allSnippets.size} local snippets, including enabled and disabled states. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.replaceAllSnippets(importData.snippets) {
                            Toast.makeText(
                                context,
                                "Restored ${importData.snippets.size} snippets",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (syncViewModel.connected()) syncWithGoogle()
                        }
                        pendingImport = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Replace all")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = Screen.SnippetList.route
    ) {
        composable(Screen.SnippetList.route) {
            SnippetListScreen(
                snippets = snippets,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSnippetClick = { snippetId ->
                    navController.navigate(Screen.EditSnippet.createRoute(snippetId))
                },
                onSnippetDelete = { snippet ->
                    viewModel.deleteSnippet(snippet) {
                        if (syncViewModel.connected()) syncWithGoogle()
                    }
                },
                onSnippetToggle = { snippet ->
                    viewModel.toggleSnippetEnabled(snippet) {
                        if (syncViewModel.connected()) syncWithGoogle()
                    }
                },
                onAddClick = {
                    navController.navigate(Screen.AddSnippet.createRoute())
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.AddSnippet.route,
            arguments = listOf(
                navArgument("prefillExpansion") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val prefillExpansion = backStackEntry.arguments?.getString("prefillExpansion")
            AddEditSnippetScreen(
                snippet = null,
                reservedTriggers = allSnippets
                    .flatMap { TriggerUtils.allTriggers(it.trigger, it.aliases) }
                    .map(String::lowercase)
                    .toSet(),
                initialExpansion = prefillExpansion,
                onSave = { trigger, expansion, aliases ->
                    viewModel.insertSnippet(trigger, expansion, aliases) {
                        navController.popBackStack()
                        if (syncViewModel.connected()) syncWithGoogle()
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.EditSnippet.route,
            arguments = listOf(
                navArgument("snippetId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val snippetId = backStackEntry.arguments?.getLong("snippetId") ?: return@composable
            var snippet by remember { mutableStateOf<com.rrajath.expander.data.Snippet?>(null) }

            LaunchedEffect(snippetId) {
                viewModel.getSnippetById(snippetId) { result ->
                    snippet = result
                }
            }

            snippet?.let { currentSnippet ->
                AddEditSnippetScreen(
                    snippet = currentSnippet,
                    reservedTriggers = allSnippets
                        .asSequence()
                        .filterNot { it.id == snippetId }
                        .flatMap { TriggerUtils.allTriggers(it.trigger, it.aliases).asSequence() }
                        .map(String::lowercase)
                        .toSet(),
                    onSave = { trigger, expansion, aliases ->
                        val updatedSnippet = currentSnippet.copy(
                            trigger = trigger,
                            expansion = expansion,
                            aliases = aliases
                        )
                        viewModel.updateSnippet(updatedSnippet) {
                            navController.popBackStack()
                            if (syncViewModel.connected()) syncWithGoogle()
                        }
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                updateState = updateState,
                syncState = syncState,
                syncConnected = syncViewModel.connected(),
                syncHasHistory = syncViewModel.hasHistory(),
                snippetCount = allSnippets.size,
                onSync = { syncWithGoogle() },
                onDisconnectSync = syncViewModel::disconnect,
                onResetSync = syncViewModel::reset,
                onReplaceCloud = { syncWithGoogle(SyncViewModel.SyncChoice.THIS_DEVICE) },
                onUseOtherDevice = { syncWithGoogle(SyncViewModel.SyncChoice.OTHER_DEVICE) },
                onCheckForUpdates = onCheckForUpdates,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onExportClick = {
                    exportLauncher.launch(ImportExportManager.createExportFileName())
                },
                onImportClick = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
                    )
                },
                onCopyTextClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.Default) {
                                SnippetBackupCodec.encodeText(allSnippets)
                            }
                        }.onSuccess { backupText ->
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("SnippetDeck backup", backupText)
                            )
                            Toast.makeText(
                                context,
                                "Backup copied: ${allSnippets.size} snippets, ${backupText.length} characters",
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                "Copy failed: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onImportText = { backupText ->
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.Default) {
                                SnippetBackupCodec.decodeText(backupText)
                            }
                        }.onSuccess { importedSnippets ->
                            pendingImport = PendingImport(importedSnippets, "text")
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                "Text import failed: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )
        }
    }
}

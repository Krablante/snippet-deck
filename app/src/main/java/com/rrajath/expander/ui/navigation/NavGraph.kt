package com.rrajath.expander.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rrajath.expander.ui.SnippetViewModel
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
    viewModel: SnippetViewModel = viewModel()
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
                onSnippetDelete = viewModel::deleteSnippet,
                onSnippetToggle = viewModel::toggleSnippetEnabled,
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

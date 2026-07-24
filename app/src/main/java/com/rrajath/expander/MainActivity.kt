package com.rrajath.expander

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.rrajath.expander.ui.components.UpdateDialog
import com.rrajath.expander.ui.navigation.NavGraph
import com.rrajath.expander.ui.theme.SnippetDeckTheme
import com.rrajath.expander.update.UpdateViewModel
import com.rrajath.expander.util.ProcessTextHelper
import com.rrajath.expander.util.ThemeMode
import com.rrajath.expander.util.ThemePreferences

class MainActivity : ComponentActivity() {
    private val updateViewModel by viewModels<UpdateViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.init(this)
        enableEdgeToEdge()

        val initialExpansion = ProcessTextHelper.extractSelectedText(
            intent?.action,
            intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        )

        setContent {
            val themeMode by ThemePreferences.themeMode.collectAsState()
            val updateState by updateViewModel.state.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()

            LaunchedEffect(intent?.action) {
                if (intent?.action == Intent.ACTION_MAIN) {
                    updateViewModel.checkOnStartup()
                }
            }

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }

            SnippetDeckTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    initialExpansion = initialExpansion,
                    updateState = updateState,
                    onCheckForUpdates = updateViewModel::checkForUpdates,
                )
                UpdateDialog(
                    state = updateState,
                    onDownloadAndInstall = updateViewModel::downloadAndInstall,
                    onCancelDownload = updateViewModel::cancelDownload,
                    onOpenInstallPermission = updateViewModel::openInstallPermissionSettings,
                    onDismiss = updateViewModel::dismiss,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateViewModel.onHostResumed()
    }
}

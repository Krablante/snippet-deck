package com.rrajath.expander

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.rrajath.expander.ui.components.UpdateDialog
import com.rrajath.expander.ui.navigation.NavGraph
import com.rrajath.expander.ui.theme.SnippetDeckTheme
import com.rrajath.expander.update.UpdateViewModel
import com.rrajath.expander.util.ProcessTextHelper
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

            LaunchedEffect(intent?.action) {
                if (intent?.action == Intent.ACTION_MAIN) {
                    updateViewModel.checkOnStartup()
                }
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    val lightSystemBars = !themeMode.isDark
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }

            SnippetDeckTheme(themeMode = themeMode) {
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

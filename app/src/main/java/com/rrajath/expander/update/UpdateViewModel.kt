package com.rrajath.expander.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object UnsupportedBuild : UpdateUiState
    data object Checking : UpdateUiState
    data object Offline : UpdateUiState
    data class Current(val version: String) : UpdateUiState
    data class CheckFailed(val message: String) : UpdateUiState
    data class Available(val release: GitHubRelease) : UpdateUiState
    data class Downloading(val release: GitHubRelease, val progress: Int) : UpdateUiState
    data class InstallPermissionRequired(
        val release: GitHubRelease,
        val apkFile: File,
    ) : UpdateUiState

    data class DownloadFailed(
        val release: GitHubRelease,
        val message: String,
    ) : UpdateUiState
}

internal class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updater = GitHubReleaseUpdater(application)
    private val _state = MutableStateFlow<UpdateUiState>(
        if (updater.isOfficialBuild()) UpdateUiState.Idle else UpdateUiState.UnsupportedBuild,
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var operation: Job? = null
    private var activeCheckIsSilent = false
    private var startupCheckAttempted = false

    fun checkOnStartup() {
        if (startupCheckAttempted) return
        startupCheckAttempted = true
        if (!updater.isOfficialBuild() || !updater.hasValidatedInternet()) return
        startUpdateCheck(silent = true)
    }

    fun checkForUpdates() {
        if (!updater.isOfficialBuild()) {
            _state.value = UpdateUiState.UnsupportedBuild
            return
        }
        if (operation?.isActive == true) {
            if (activeCheckIsSilent) {
                activeCheckIsSilent = false
                _state.value = UpdateUiState.Checking
            }
            return
        }
        if (!updater.hasValidatedInternet()) {
            _state.value = UpdateUiState.Offline
            return
        }
        startUpdateCheck(silent = false)
    }

    private fun startUpdateCheck(silent: Boolean) {
        activeCheckIsSilent = silent
        if (!silent) _state.value = UpdateUiState.Checking
        operation = viewModelScope.launch {
            try {
                val release = updater.fetchLatestRelease()
                val current = updater.installedVersion()
                    ?: throw UpdateException("The installed app has an invalid version.")
                _state.value = resolveUpdateCheckState(release, current, activeCheckIsSilent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = if (activeCheckIsSilent) {
                    UpdateUiState.Idle
                } else {
                    UpdateUiState.CheckFailed(
                        error.userMessage("Could not check GitHub for updates. Tap to retry."),
                    )
                }
            } finally {
                operation = null
            }
        }
    }

    fun downloadAndInstall() {
        val release = when (val current = _state.value) {
            is UpdateUiState.Available -> current.release
            is UpdateUiState.DownloadFailed -> current.release
            else -> return
        }
        if (operation?.isActive == true) return

        activeCheckIsSilent = false
        _state.value = UpdateUiState.Downloading(release, progress = 0)
        operation = viewModelScope.launch {
            try {
                val apkFile = updater.downloadAndVerify(release) { progress ->
                    _state.value = UpdateUiState.Downloading(release, progress)
                }
                launchInstaller(release, apkFile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = UpdateUiState.DownloadFailed(
                    release = release,
                    message = error.userMessage("Could not download and verify the update."),
                )
            } finally {
                operation = null
            }
        }
    }

    fun cancelDownload() {
        if (_state.value !is UpdateUiState.Downloading) return
        operation?.cancel()
        operation = null
        _state.value = UpdateUiState.Idle
    }

    fun openInstallPermissionSettings() {
        val pending = _state.value as? UpdateUiState.InstallPermissionRequired ?: return
        if (UpdateInstaller.canInstallPackages(getApplication())) {
            launchInstaller(pending.release, pending.apkFile)
            return
        }
        try {
            UpdateInstaller.openPermissionSettings(getApplication())
        } catch (error: Exception) {
            _state.value = UpdateUiState.DownloadFailed(
                pending.release,
                error.userMessage("Could not open the install permission settings."),
            )
        }
    }

    fun onHostResumed() {
        val pending = _state.value as? UpdateUiState.InstallPermissionRequired ?: return
        if (UpdateInstaller.canInstallPackages(getApplication())) {
            launchInstaller(pending.release, pending.apkFile)
        }
    }

    fun dismiss() {
        if (_state.value !is UpdateUiState.Downloading) {
            _state.value = if (updater.isOfficialBuild()) {
                UpdateUiState.Idle
            } else {
                UpdateUiState.UnsupportedBuild
            }
        }
    }

    private fun launchInstaller(release: GitHubRelease, apkFile: File) {
        try {
            _state.value = when (UpdateInstaller.launch(getApplication(), apkFile)) {
                InstallLaunchResult.INSTALLER -> UpdateUiState.Idle
                InstallLaunchResult.PERMISSION_SETTINGS -> {
                    UpdateUiState.InstallPermissionRequired(release, apkFile)
                }
            }
        } catch (error: Exception) {
            _state.value = UpdateUiState.DownloadFailed(
                release,
                error.userMessage("Could not open the Android package installer."),
            )
        }
    }

    private fun Exception.userMessage(fallback: String): String =
        (this as? UpdateException)?.message?.takeIf(String::isNotBlank) ?: fallback
}

internal fun resolveUpdateCheckState(
    release: GitHubRelease,
    current: SemanticVersion,
    silent: Boolean,
): UpdateUiState = when {
    release.version > current -> UpdateUiState.Available(release)
    silent -> UpdateUiState.Idle
    else -> UpdateUiState.Current(current.toString())
}

internal fun updateStatusText(state: UpdateUiState, installedVersion: String): String = when (state) {
    UpdateUiState.Idle -> "GitHub Releases · Version $installedVersion"
    UpdateUiState.UnsupportedBuild -> "Available in official GitHub releases"
    UpdateUiState.Checking -> "Checking GitHub Releases…"
    UpdateUiState.Offline -> "No verified internet connection · Tap to retry"
    is UpdateUiState.Current -> "Version ${state.version} is up to date"
    is UpdateUiState.CheckFailed -> state.message
    is UpdateUiState.Available -> "Version ${state.release.version} is available"
    is UpdateUiState.Downloading -> "Downloading ${state.release.version} · ${state.progress}%"
    is UpdateUiState.InstallPermissionRequired -> "Version ${state.release.version} is ready to install"
    is UpdateUiState.DownloadFailed -> state.message
}

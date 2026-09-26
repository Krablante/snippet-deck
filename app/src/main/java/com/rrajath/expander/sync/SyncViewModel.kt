package com.rrajath.expander.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface SyncUiState {
    data object Off : SyncUiState
    data object Ready : SyncUiState
    data object Working : SyncUiState
    data class Synced(val count: Int) : SyncUiState
    data class Conflict(val triggers: List<String>) : SyncUiState
    data class Failed(val message: String) : SyncUiState
}

internal class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val sync = SyncCoordinator(application)
    private val _state = MutableStateFlow<SyncUiState>(
        if (runCatching(sync::connected).getOrDefault(false)) SyncUiState.Ready else SyncUiState.Off
    )
    val state: StateFlow<SyncUiState> = _state.asStateFlow()
    private var pendingSync: Pair<String, SyncChoice>? = null

    enum class SyncChoice { MERGE, THIS_DEVICE, OTHER_DEVICE }

    fun connected() = runCatching(sync::connected).getOrDefault(false)
    fun hasHistory() = sync.hasHistory()

    fun syncWith(accessToken: String, choice: SyncChoice = SyncChoice.MERGE) {
        if (_state.value == SyncUiState.Working) {
            pendingSync = accessToken to choice
            return
        }
        _state.value = SyncUiState.Working
        viewModelScope.launch {
            runCatching {
                when (choice) {
                    SyncChoice.MERGE -> sync.sync(accessToken)
                    SyncChoice.THIS_DEVICE -> sync.keepThisDevice(accessToken)
                    SyncChoice.OTHER_DEVICE -> sync.useOtherDevice(accessToken)
                }
            }.onSuccess { result ->
                _state.value = when (result) {
                    is SyncOutcome.Done -> SyncUiState.Synced(result.count)
                    is SyncOutcome.Conflict -> SyncUiState.Conflict(result.triggers)
                }
            }.onFailure { error ->
                _state.value = SyncUiState.Failed(error.message ?: "Google Drive sync failed")
            }
            pendingSync?.also { (token, nextChoice) ->
                pendingSync = null
                syncWith(token, nextChoice)
            }
        }
    }

    fun fail(error: String) {
        _state.value = SyncUiState.Failed(error)
    }

    fun disconnect() {
        viewModelScope.launch {
            runCatching { sync.disconnect() }
                .onSuccess { _state.value = SyncUiState.Off }
                .onFailure { _state.value = SyncUiState.Failed(it.message ?: "Cannot disconnect") }
        }
    }

    fun reset() {
        viewModelScope.launch {
            runCatching { sync.reset() }
                .onSuccess { _state.value = SyncUiState.Off }
                .onFailure { _state.value = SyncUiState.Failed(it.message ?: "Cannot reset sync history") }
        }
    }
}

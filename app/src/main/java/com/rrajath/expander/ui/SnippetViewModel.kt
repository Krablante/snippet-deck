package com.rrajath.expander.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.expander.data.AppDatabase
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.data.SnippetRepository
import com.rrajath.expander.domain.TriggerUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SnippetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SnippetRepository(
        AppDatabase.getDatabase(application).snippetDao()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allSnippets: StateFlow<List<Snippet>> = repository.getAllSnippets()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val snippets: StateFlow<List<Snippet>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                repository.getAllSnippets()
            } else {
                repository.searchSnippets(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun insertSnippet(
        trigger: String,
        expansion: String,
        aliases: List<String> = emptyList(),
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val snippet = repository.saveByTrigger(
                trigger = TriggerUtils.normalize(trigger),
                expansion = expansion,
                aliases = aliases
            )
            onComplete(snippet.id)
        }
    }

    fun updateSnippet(snippet: Snippet, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val updatedSnippet = snippet.copy(
                trigger = TriggerUtils.normalize(snippet.trigger),
                updatedAt = System.currentTimeMillis()
            )
            repository.update(updatedSnippet)
            onComplete()
        }
    }

    fun deleteSnippet(snippet: Snippet) {
        viewModelScope.launch {
            repository.delete(snippet)
        }
    }

    fun getSnippetById(id: Long, onResult: (Snippet?) -> Unit) {
        viewModelScope.launch {
            val snippet = repository.getSnippetById(id)
            onResult(snippet)
        }
    }

    fun toggleSnippetEnabled(snippet: Snippet) {
        viewModelScope.launch {
            val updated = snippet.copy(
                isEnabled = !snippet.isEnabled,
                updatedAt = System.currentTimeMillis()
            )
            repository.update(updated)
        }
    }

    fun replaceAllSnippets(snippets: List<Snippet>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.replaceAll(snippets)
            _searchQuery.value = ""
            onComplete()
        }
    }
}

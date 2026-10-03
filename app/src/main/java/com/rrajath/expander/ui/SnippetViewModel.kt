package com.rrajath.expander.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.expander.data.AppDatabase
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.data.SnippetRepository
import com.rrajath.expander.domain.TriggerUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers

class SnippetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SnippetRepository(
        AppDatabase.getDatabase(application).snippetDao()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _errors = MutableSharedFlow<String>()
    val errors = _errors.asSharedFlow()

    private fun change(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _errors.emit(error.message ?: "Cannot save library")
            }
        }
    }

    val allSnippets: StateFlow<List<Snippet>> = repository.getAllSnippets()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val snippets: StateFlow<List<Snippet>> = combine(allSnippets, _searchQuery) { library, query ->
        if (query.isEmpty()) library else library.filter { snippet ->
            snippet.trigger.contains(query, ignoreCase = true) ||
                snippet.expansion.contains(query, ignoreCase = true) ||
                snippet.aliases.any { it.contains(query, ignoreCase = true) }
        }
    }
        .flowOn(Dispatchers.Default)
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
        change {
            val snippet = repository.create(
                trigger = TriggerUtils.normalize(trigger),
                expansion = expansion,
                aliases = aliases
            )
            onComplete(snippet.id)
        }
    }

    fun updateSnippet(snippet: Snippet, expected: Snippet, onComplete: () -> Unit = {}) {
        change {
            val updatedSnippet = snippet.copy(
                trigger = TriggerUtils.normalize(snippet.trigger),
                updatedAt = System.currentTimeMillis()
            )
            repository.update(updatedSnippet, expected)
            onComplete()
        }
    }

    fun deleteSnippet(snippet: Snippet, onComplete: () -> Unit = {}) {
        change {
            repository.delete(snippet)
            onComplete()
        }
    }

    fun getSnippetById(id: Long, onResult: (Snippet?) -> Unit) {
        change {
            val snippet = repository.getSnippetById(id)
            onResult(snippet)
        }
    }

    fun toggleSnippetEnabled(snippet: Snippet, onComplete: () -> Unit = {}) {
        change {
            val updated = snippet.copy(
                isEnabled = !snippet.isEnabled,
                updatedAt = System.currentTimeMillis()
            )
            repository.update(updated, snippet)
            onComplete()
        }
    }

    fun replaceAllSnippets(snippets: List<Snippet>, onComplete: () -> Unit = {}) {
        change {
            repository.replaceAll(snippets)
            _searchQuery.value = ""
            onComplete()
        }
    }
}

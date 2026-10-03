package com.rrajath.expander.data

import kotlinx.coroutines.flow.Flow
import com.rrajath.expander.domain.TriggerUtils

class SnippetRepository(private val snippetDao: SnippetDao) {

    fun getAllSnippets(): Flow<List<Snippet>> = snippetDao.getAllSnippets()

    fun getEnabledSnippets(): Flow<List<Snippet>> = snippetDao.getEnabledSnippets()

    suspend fun getAllSnippetsOnce(): List<Snippet> = snippetDao.getAllSnippetsOnce()

    suspend fun getSnippetById(id: Long): Snippet? = snippetDao.getSnippetById(id)

    suspend fun update(snippet: Snippet, expected: Snippet) =
        snippetDao.saveChecked(snippet, expected)

    suspend fun delete(snippet: Snippet) = snippetDao.deleteChecked(snippet)

    suspend fun replaceAll(snippets: List<Snippet>) = snippetDao.replaceAll(snippets)

    suspend fun create(
        trigger: String,
        expansion: String,
        aliases: List<String> = emptyList()
    ): Snippet {
        val now = System.currentTimeMillis()
        val snippet = Snippet(
            trigger = TriggerUtils.normalize(trigger),
            expansion = expansion,
            aliases = aliases.map(TriggerUtils::normalizeAlias),
            isEnabled = true,
            createdAt = now,
            updatedAt = now
        )
        return snippet.copy(id = snippetDao.saveChecked(snippet))
    }
}

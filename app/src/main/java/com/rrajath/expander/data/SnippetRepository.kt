package com.rrajath.expander.data

import kotlinx.coroutines.flow.Flow

class SnippetRepository(private val snippetDao: SnippetDao) {

    fun getAllSnippets(): Flow<List<Snippet>> = snippetDao.getAllSnippets()

    fun getEnabledSnippets(): Flow<List<Snippet>> = snippetDao.getEnabledSnippets()

    suspend fun getAllSnippetsOnce(): List<Snippet> = snippetDao.getAllSnippetsOnce()

    suspend fun getSnippetById(id: Long): Snippet? = snippetDao.getSnippetById(id)

    suspend fun getSnippetByTrigger(trigger: String): Snippet? =
        snippetDao.getSnippetByTrigger(trigger)

    fun searchSnippets(query: String): Flow<List<Snippet>> = snippetDao.searchSnippets(query)

    suspend fun insert(snippet: Snippet): Long = snippetDao.insert(snippet)

    suspend fun update(snippet: Snippet) = snippetDao.update(snippet)

    suspend fun delete(snippet: Snippet) = snippetDao.delete(snippet)

    suspend fun deleteById(id: Long) = snippetDao.deleteById(id)

    suspend fun deleteAll() = snippetDao.deleteAll()

    suspend fun replaceAll(snippets: List<Snippet>) = snippetDao.replaceAll(snippets)

    suspend fun saveByTrigger(
        trigger: String,
        expansion: String,
        aliases: List<String> = emptyList()
    ): Snippet {
        val now = System.currentTimeMillis()
        val existing = snippetDao.getSnippetByTrigger(trigger)
        return if (existing == null) {
            val snippet = Snippet(
                trigger = trigger,
                expansion = expansion,
                aliases = aliases,
                isEnabled = true,
                createdAt = now,
                updatedAt = now
            )
            snippet.copy(id = snippetDao.insert(snippet))
        } else {
            existing.copy(
                trigger = trigger,
                expansion = expansion,
                aliases = aliases,
                updatedAt = now
            ).also { snippetDao.update(it) }
        }
    }
}

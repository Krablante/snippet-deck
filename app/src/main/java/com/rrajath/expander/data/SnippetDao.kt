package com.rrajath.expander.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.rrajath.expander.util.SnippetBackupCodec
import com.rrajath.expander.domain.TriggerUtils

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY updatedAt DESC")
    fun getAllSnippets(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE isEnabled = 1")
    fun getEnabledSnippets(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets ORDER BY trigger COLLATE NOCASE ASC")
    suspend fun getAllSnippetsOnce(): List<Snippet>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun getSnippetById(id: Long): Snippet?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snippet: Snippet): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(snippets: List<Snippet>)

    @Update
    suspend fun update(snippet: Snippet)

    @Delete
    suspend fun delete(snippet: Snippet)

    @Query("DELETE FROM snippets")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(snippets: List<Snippet>) {
        SnippetBackupCodec.validateLibrary(snippets)
        deleteAll()
        insertAll(snippets.map { it.copy(id = 0) })
    }

    @Transaction
    suspend fun saveChecked(snippet: Snippet, expected: Snippet? = null): Long {
        val current = getAllSnippetsOnce()
        if (expected != null) {
            check(current.find { it.id == expected.id } == expected) {
                "Snippet changed while editing. Reopen it before saving."
            }
        }
        val next = current.filterNot { expected != null && it.id == expected.id } + snippet
        SnippetBackupCodec.validateLibrary(next)
        return if (expected == null) insert(snippet.copy(id = 0)) else {
            update(snippet)
            snippet.id
        }
    }

    @Transaction
    suspend fun deleteChecked(expected: Snippet) {
        check(getSnippetById(expected.id) == expected) {
            "Snippet changed. Reopen it before deleting."
        }
        delete(expected)
    }

    @Transaction
    suspend fun replaceIfUnchanged(expected: List<Snippet>, snippets: List<Snippet>) {
        check(getAllSnippetsOnce() == expected) { "Library changed while syncing; retry" }
        val existing = expected.associateBy { TriggerUtils.matchKey(it.trigger) }
        val next = snippets.map { snippet ->
            snippet.copy(id = existing[TriggerUtils.matchKey(snippet.trigger)]?.id ?: 0)
        }
        SnippetBackupCodec.validateLibrary(next)
        val retained = next.map { it.id }.toSet()
        expected.filterNot { it.id in retained }.forEach { delete(it) }
        for (snippet in next) {
            if (snippet.id == 0L) insert(snippet)
            else if (snippet != existing[TriggerUtils.matchKey(snippet.trigger)]) update(snippet)
        }
    }
}

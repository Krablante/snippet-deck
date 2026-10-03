package com.rrajath.expander

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.rrajath.expander.data.AppDatabase
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.util.ImportExportManager
import com.rrajath.expander.util.SnippetBackupCodec
import android.net.Uri
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnippetDatabaseTest {
    @Test
    fun debugVariantUsesIsolatedApplicationId() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.rrajath.expander.debug", appContext.packageName)
    }

    @Test
    fun writesRejectCollisionsAndStaleEditorsWithoutChangingTheLibrary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.snippetDao()
            val first = Snippet(trigger = "!Привет", expansion = "One", aliases = listOf("alias"))
            val saved = first.copy(id = dao.saveChecked(first))
            assertTrue(runCatching { dao.saveChecked(Snippet(trigger = "!other", expansion = "Two", aliases = listOf("ALIAS"))) }.isFailure)
            assertTrue(runCatching { dao.saveChecked(Snippet(trigger = "!ПРИВЕТ", expansion = "Two")) }.isFailure)
            val edited = saved.copy(expansion = "Changed", updatedAt = saved.updatedAt + 1)
            dao.saveChecked(edited, saved)
            assertTrue(runCatching { dao.saveChecked(saved.copy(expansion = "Stale"), saved) }.isFailure)
            assertTrue(runCatching { dao.deleteChecked(saved) }.isFailure)
            assertEquals(listOf(edited), dao.getAllSnippetsOnce())
        } finally {
            database.close()
        }
    }

    @Test
    fun syncKeepsUnchangedRowsEditableAndOnlyAppliesChangedRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.snippetDao()
            val first = Snippet(trigger = "!one", expansion = "One")
            val second = Snippet(trigger = "!two", expansion = "Two")
            dao.saveChecked(first)
            dao.saveChecked(second)
            val before = dao.getAllSnippetsOnce()
            val expected = before.first { it.trigger == "!one" }
            val remote = before.map { it.copy(id = 0) } + Snippet(trigger = "!three", expansion = "Three")
            dao.replaceIfUnchanged(before, remote)
            assertEquals(expected, dao.getSnippetById(expected.id))
            dao.saveChecked(expected.copy(expansion = "My draft"), expected)
            val current = dao.getAllSnippetsOnce()
            dao.replaceIfUnchanged(current, current.filterNot { it.trigger == "!two" }.map { it.copy(id = 0) })
            assertEquals(setOf("!one", "!three"), dao.getAllSnippetsOnce().map { it.trigger }.toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationPreservesLegacySnippets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "snippetdeck-migration-test.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, 0, null).use { db ->
                db.execSQL("CREATE TABLE snippets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, trigger TEXT NOT NULL, expansion TEXT NOT NULL, isEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO snippets VALUES (1, '!legacy', 'Keep me', 1, 10, 20)")
                db.version = 1
            }
            val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_1_2).build()
            try {
                val snippet = database.snippetDao().getAllSnippetsOnce().single()
                assertEquals("Keep me", snippet.expansion)
                assertEquals(emptyList<String>(), snippet.aliases)
                assertEquals(1L, snippet.id)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun fileImportReadsBothPortableFormats() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = listOf(Snippet(trigger = "!test", expansion = "Привет 😀", createdAt = 1, updatedAt = 1))
        val file = File.createTempFile("snippetdeck-backup-", ".txt", context.cacheDir)
        try {
            for (text in listOf(SnippetBackupCodec.encodeJson(library), SnippetBackupCodec.encodeText(library))) {
                file.writeText(text)
                assertEquals(library, ImportExportManager.importSnippets(context, Uri.fromFile(file)).getOrThrow())
            }
        } finally {
            file.delete()
        }
    }
}

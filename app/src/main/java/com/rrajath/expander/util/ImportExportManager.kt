package com.rrajath.expander.util

import android.content.Context
import android.net.Uri
import com.rrajath.expander.data.Snippet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImportExportManager {

    suspend fun exportSnippets(
        context: Context,
        snippets: List<Snippet>,
        uri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = SnippetBackupCodec.encodeJson(snippets)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer -> writer.write(json) }
            } ?: error("Cannot open the selected file")
        }
    }

    suspend fun importSnippets(
        context: Context,
        uri: Uri
    ): Result<List<Snippet>> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use(::readLimited)
                ?: error("Cannot open the selected file")
            SnippetBackupCodec.decodeText(text)
        }
    }

    fun createExportFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "snippet_deck_backup_$timestamp.json"
    }

    private fun readLimited(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > SnippetBackupCodec.MAX_BACKUP_BYTES * 2) {
                throw BackupFormatException("Backup is too large")
            }
            output.write(buffer, 0, read)
        }
        return SnippetBackupCodec.decodeUtf8(output.toByteArray())
    }
}

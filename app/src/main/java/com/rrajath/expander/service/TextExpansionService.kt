package com.rrajath.expander.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.bundleOf
import com.rrajath.expander.data.AppDatabase
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.data.SnippetRepository
import com.rrajath.expander.domain.DynamicHelp
import com.rrajath.expander.domain.TriggerUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class TextExpansionService : AccessibilityService() {

    private lateinit var repository: SnippetRepository
    private lateinit var prefs: SharedPreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var snippetsCache: List<Snippet> = emptyList()
    private var lastExpansion: CursorExpansionEngine.ExpansionHistory? = null
    private var pendingAppliedText: String? = null

    companion object {
        private const val PREFS_NAME = "expander_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"

        fun isServiceEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, true)
        }

        fun setServiceEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVICE_ENABLED, enabled)
                .apply()
        }

        /**
         * Checks if the accessibility service is actually enabled in system settings.
         * This is different from isServiceEnabled which only checks our internal preference.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${TextExpansionService::class.java.name}"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(expectedComponentName) == true
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(applicationContext)
        repository = SnippetRepository(database.snippetDao())
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Load snippets into cache
        serviceScope.launch {
            repository.getEnabledSnippets().collect { snippets ->
                snippetsCache = snippets + DynamicHelp.asVirtualSnippet(snippets)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isServiceEnabled(this)) return

        // Only process text change events
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val source = event.source ?: return

        try {
            val currentText = source.text?.toString() ?: ""
            val selectionStart = source.textSelectionStart
            val selectionEnd = source.textSelectionEnd

            // Ignore the text-change event emitted by our own ACTION_SET_TEXT.
            pendingAppliedText?.let { appliedText ->
                pendingAppliedText = null
                if (currentText == appliedText) return
            }

            val undoEdit = lastExpansion?.let { history ->
                CursorExpansionEngine.undoAfterBackspace(
                    currentText = currentText,
                    selectionStart = selectionStart,
                    selectionEnd = selectionEnd,
                    history = history
                )
            }
            if (undoEdit != null) {
                lastExpansion = null
                applyTextEdit(source, undoEdit)
                return
            }
            // Undo is only valid for the first user edit after an expansion.
            lastExpansion = null

            processTextForExpansion(
                source = source,
                text = currentText,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd
            )
        } catch (e: Exception) {
            // Silently handle errors to avoid service crashes
        }
    }

    private fun processTextForExpansion(
        source: AccessibilityNodeInfo,
        text: String,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        val occurrence = CursorExpansionEngine.findTriggerBeforeCursor(
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd
        ) ?: return

        val matchingSnippet = snippetsCache.firstOrNull { snippet ->
            TriggerUtils.matches(
                candidate = occurrence.typedTrigger,
                primaryTrigger = snippet.trigger,
                aliases = snippet.aliases
            )
        } ?: return

        val processedExpansion = SnippetProcessor.process(matchingSnippet.expansion)
        val result = CursorExpansionEngine.expand(occurrence, processedExpansion)
        lastExpansion = if (applyTextEdit(source, result.edit)) result.history else null
    }

    private fun applyTextEdit(
        source: AccessibilityNodeInfo,
        edit: CursorExpansionEngine.TextEdit
    ): Boolean {
        pendingAppliedText = edit.text

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, edit.text)
        }
        val textApplied = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (!textApplied) {
            pendingAppliedText = null
            return false
        }

        arguments.clear()
        arguments.putInt(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
            edit.cursor
        )
        arguments.putInt(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
            edit.cursor
        )
        source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
        return true
    }

    override fun onInterrupt() {
        // Called when the service is interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

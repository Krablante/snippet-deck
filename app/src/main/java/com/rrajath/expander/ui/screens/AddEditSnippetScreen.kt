package com.rrajath.expander.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.domain.TriggerUtils

@Composable
fun AddEditSnippetScreen(
    snippet: Snippet?,
    reservedTriggers: Set<String>,
    onSave: (String, String, List<String>) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialExpansion: String? = null,
) {
    var trigger by remember(snippet?.id) { mutableStateOf(snippet?.trigger.orEmpty()) }
    var aliasesText by remember(snippet?.id) { mutableStateOf(snippet?.aliases?.joinToString("; ").orEmpty()) }
    var expansion by remember(snippet?.id) { mutableStateOf(snippet?.expansion ?: initialExpansion.orEmpty()) }
    var triggerError by remember { mutableStateOf<String?>(null) }
    var aliasesError by remember { mutableStateOf<String?>(null) }
    var expansionError by remember { mutableStateOf<String?>(null) }
    var showPlaceholders by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val changed = trigger != snippet?.trigger.orEmpty() ||
        aliasesText != snippet?.aliases?.joinToString("; ").orEmpty() ||
        expansion != (snippet?.expansion ?: initialExpansion.orEmpty())
    BackHandler(enabled = changed) { confirmDiscard = true }

    fun save() {
        val normalized = TriggerUtils.normalize(trigger)
        val aliases = TriggerUtils.parseAliases(aliasesText)
        triggerError = TriggerUtils.validationError(trigger)
        aliasesError = TriggerUtils.aliasesValidationError(aliasesText, normalized)
        TriggerUtils.conflictingTrigger(normalized, aliases, reservedTriggers)?.let { conflict ->
            if (conflict.equals(normalized, ignoreCase = true)) triggerError = "$conflict is already used by another snippet"
            else aliasesError = "$conflict is already used by another snippet"
        }
        expansionError = if (expansion.isBlank()) "Expansion cannot be empty" else null
        if (triggerError == null && aliasesError == null && expansionError == null) onSave(normalized, expansion, aliases)
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onNavigateBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 76.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = trigger,
                onValueChange = { trigger = it; triggerError = null; aliasesError = null },
                label = { Text("Trigger") },
                placeholder = { Text("!review") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                isError = triggerError != null,
                supportingText = when {
                    triggerError != null -> { { Text(triggerError.orEmpty()) } }
                    trigger.isNotBlank() && !trigger.trim().startsWith("!") -> { { Text("Saved as ${TriggerUtils.normalize(trigger)}") } }
                    else -> null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = aliasesText,
                onValueChange = { aliasesText = it; aliasesError = null },
                label = { Text("Aliases · optional") },
                placeholder = { Text("rv; feedback") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                isError = aliasesError != null,
                supportingText = {
                    Text(aliasesError ?: "Used as typed · separate with commas")
                },
            )
            OutlinedTextField(
                value = expansion,
                onValueChange = { expansion = it; expansionError = null },
                label = { Text("Expansion") },
                placeholder = { Text("Text to insert") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 188.dp),
                minLines = 5,
                maxLines = 16,
                isError = expansionError != null,
                supportingText = expansionError?.let { error -> { Text(error) } },
            )
            Box {
                TextButton(onClick = { showPlaceholders = true }) { Text("Insert date or time  +") }
                DropdownMenu(expanded = showPlaceholders, onDismissRequest = { showPlaceholders = false }) {
                    listOf("date", "time", "datetime").forEach { name ->
                        DropdownMenuItem(
                            text = { Text("{{$name}}", fontFamily = FontFamily.Monospace) },
                            onClick = { expansion += "{{$name}}"; showPlaceholders = false },
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(colors.background, colors.background.copy(alpha = 0.96f), colors.background.copy(alpha = 0.86f)))
            ).padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (changed) confirmDiscard = true else onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
            }
            Text(if (snippet == null) "New snippet" else "Edit snippet", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
            TextButton(onClick = ::save) { Text("Save") }
        }
    }
}

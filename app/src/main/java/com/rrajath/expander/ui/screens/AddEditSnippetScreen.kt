package com.rrajath.expander.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rrajath.expander.domain.TriggerUtils
import com.rrajath.expander.data.Snippet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSnippetScreen(
    snippet: Snippet?,
    reservedTriggers: Set<String>,
    onSave: (String, String, List<String>) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialExpansion: String? = null
) {
    var trigger by remember { mutableStateOf(snippet?.trigger ?: "") }
    var aliasesText by remember {
        mutableStateOf(snippet?.aliases?.joinToString("; ").orEmpty())
    }
    var expansion by remember { mutableStateOf(snippet?.expansion ?: initialExpansion ?: "") }
    var triggerError by remember { mutableStateOf<String?>(null) }
    var aliasesError by remember { mutableStateOf<String?>(null) }
    var expansionError by remember { mutableStateOf<String?>(null) }

    val isEditMode = snippet != null
    val title = if (isEditMode) "Edit Snippet" else "Add Snippet"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Trigger field
            OutlinedTextField(
                value = trigger,
                onValueChange = {
                    trigger = it
                    triggerError = null
                    aliasesError = null
                },
                label = { Text("Trigger") },
                placeholder = { Text("e.g., !email") },
                modifier = Modifier.fillMaxWidth(),
                isError = triggerError != null,
                supportingText = {
                    if (triggerError != null) {
                        Text(
                            text = triggerError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("The shortcut that triggers expansion")
                    }
                },
                singleLine = true
            )

            OutlinedTextField(
                value = aliasesText,
                onValueChange = {
                    aliasesText = it
                    aliasesError = null
                },
                label = { Text("Aliases (optional)") },
                placeholder = { Text("e.g., kk; mail; !contact") },
                modifier = Modifier.fillMaxWidth(),
                isError = aliasesError != null,
                supportingText = {
                    if (aliasesError != null) {
                        Text(
                            text = aliasesError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        val aliasCount = TriggerUtils.parseAliases(aliasesText).size
                        Text(
                            if (aliasCount == 0) {
                                "Used exactly as entered; separate with comma, semicolon, or new line"
                            } else {
                                "$aliasCount ${if (aliasCount == 1) "alias" else "aliases"}"
                            }
                        )
                    }
                },
                minLines = 1,
                maxLines = 3
            )

            // Expansion field
            OutlinedTextField(
                value = expansion,
                onValueChange = {
                    expansion = it
                    expansionError = null
                },
                label = { Text("Expansion") },
                placeholder = { Text("e.g., john.doe@example.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                isError = expansionError != null,
                supportingText = {
                    if (expansionError != null) {
                        Text(
                            text = expansionError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("The text that replaces the trigger")
                    }
                },
                minLines = 3,
                maxLines = 10
            )

            // Dynamic snippets help card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Dynamic Placeholders",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = """
                            • {{date}} - Current date (yyyy-MM-dd)
                            • {{time}} - Current time (HH:mm:ss)
                            • {{datetime}} - Date and time
                            • {{day}} - Day (Mon, Tue, Wed)
                            • {{day_long}} - Day (Monday, Tuesday)
                            • {{month}} - Month (Jan, Feb, Mar)
                            • {{month_long}} - Month (January, February)
                            • {{year}} - Year (2026)
                            • {{year_short}} - Year (26)
                            • {{week_num}} - Week number (1-52)
                            • {{date:dd/MM/yyyy}} - Custom format
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = {
                    // Validation
                    var hasError = false
                    val normalizedTrigger = TriggerUtils.normalize(trigger)
                    val aliases = TriggerUtils.parseAliases(aliasesText)

                    TriggerUtils.validationError(trigger)?.let { error ->
                        triggerError = error
                        hasError = true
                    }

                    TriggerUtils.aliasesValidationError(
                        raw = aliasesText,
                        primaryTrigger = normalizedTrigger
                    )?.let { error ->
                        aliasesError = error
                        hasError = true
                    }

                    TriggerUtils.conflictingTrigger(
                        primaryTrigger = normalizedTrigger,
                        aliases = aliases,
                        reservedTriggers = reservedTriggers
                    )?.let { conflict ->
                        if (conflict.equals(normalizedTrigger, ignoreCase = true)) {
                            triggerError = "$conflict is already used by another snippet"
                        } else {
                            aliasesError = "$conflict is already used by another snippet"
                        }
                        hasError = true
                    }

                    if (expansion.isBlank()) {
                        expansionError = "Expansion cannot be empty"
                        hasError = true
                    }

                    if (!hasError) {
                        onSave(normalizedTrigger, expansion, aliases)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(if (isEditMode) "Save Changes" else "Add Snippet")
            }
        }
    }
}

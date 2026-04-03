package com.synapse.app.feature.workflow.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.synapse.app.domain.actions.WorkflowActionRegistry
import com.synapse.app.domain.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun ActionBlockEditor(
    action: WorkflowAction,
    index: Int,
    totalCount: Int,
    availableNotes: List<SavedNote> = emptyList(),
    availableWorkflows: List<WorkflowTemplate> = emptyList(),
    onUpdate: (WorkflowAction) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(action.type.displayName, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = action.label.ifBlank { "${action.type.displayName} #${index + 1}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move up", modifier = Modifier.size(18.dp))
                    }
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move down", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Toggle details", modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            // Collapsed summary
            if (!expanded) {
                val summary = actionSummary(action)
                if (summary.isNotBlank()) {
                    Text(
                        summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(4.dp))

                    // Label (common to all types)
                    OutlinedTextField(
                        value = action.label,
                        onValueChange = { onUpdate(action.copy(label = it)) },
                        label = { Text("Label (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Type-specific editor
                    when (action.type) {
                        WorkflowActionType.FETCH_URL -> FetchUrlEditor(action, onUpdate)
                        WorkflowActionType.PASTE_TEXT -> PasteTextEditor(action, onUpdate)
                        WorkflowActionType.FETCH_RSS_FEED -> RssFeedEditor(action, onUpdate)
                        WorkflowActionType.FETCH_API_GET -> ApiGetEditor(action, onUpdate)
                        WorkflowActionType.USE_SAVED_NOTE -> SavedNoteEditor(action, onUpdate, availableNotes)
                        WorkflowActionType.USE_PREVIOUS_OUTPUT -> PreviousOutputEditor(action, onUpdate, availableWorkflows)
                        WorkflowActionType.FETCH_RSS_MULTI -> MultiFeedRssEditor(action, onUpdate)
                    }

                    // Per-action instruction (common to all types)
                    OutlinedTextField(
                        value = action.instruction,
                        onValueChange = { onUpdate(action.copy(instruction = it)) },
                        label = { Text("Per-source instruction (optional)") },
                        placeholder = { Text("e.g. Summarize as a market update") },
                        minLines = 2, maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Type-specific editors ──

@Composable
private fun FetchUrlEditor(action: WorkflowAction, onUpdate: (WorkflowAction) -> Unit) {
    OutlinedTextField(
        value = action.sourceData,
        onValueChange = { onUpdate(action.copy(sourceData = it)) },
        label = { Text("URL") },
        placeholder = { Text("https://...") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = action.sourceData.isNotBlank() && !action.sourceData.startsWith("http")
    )
    if (action.sourceData.isNotBlank() && !action.sourceData.startsWith("http")) {
        Text("URL must start with http:// or https://", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PasteTextEditor(action: WorkflowAction, onUpdate: (WorkflowAction) -> Unit) {
    OutlinedTextField(
        value = action.sourceData,
        onValueChange = { onUpdate(action.copy(sourceData = it)) },
        label = { Text("Content") },
        placeholder = { Text("Paste text here...") },
        minLines = 3, maxLines = 8,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RssFeedEditor(action: WorkflowAction, onUpdate: (WorkflowAction) -> Unit) {
    val config = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<RssFeedConfig>(action.extraConfig) }
            catch (_: Exception) { RssFeedConfig() }
        } else RssFeedConfig()
    }

    fun updateConfig(newConfig: RssFeedConfig) {
        onUpdate(action.copy(extraConfig = json.encodeToString(newConfig)))
    }

    OutlinedTextField(
        value = action.sourceData,
        onValueChange = { onUpdate(action.copy(sourceData = it)) },
        label = { Text("Feed URL") },
        placeholder = { Text("https://example.com/rss") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = action.sourceData.isNotBlank() && !action.sourceData.startsWith("http")
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Max items:", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = config.maxItems.toString(),
            onValueChange = { v ->
                val n = v.filter { it.isDigit() }.take(2).toIntOrNull() ?: 5
                updateConfig(config.copy(maxItems = n.coerceIn(1, 20)))
            },
            singleLine = true,
            modifier = Modifier.width(72.dp)
        )
    }

    OutlinedTextField(
        value = config.keywordFilter,
        onValueChange = { updateConfig(config.copy(keywordFilter = it)) },
        label = { Text("Keyword filter (optional, comma-separated)") },
        placeholder = { Text("e.g. AI, machine learning") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ApiGetEditor(action: WorkflowAction, onUpdate: (WorkflowAction) -> Unit) {
    val config = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<ApiGetConfig>(action.extraConfig) }
            catch (_: Exception) { ApiGetConfig() }
        } else ApiGetConfig()
    }

    fun updateConfig(newConfig: ApiGetConfig) {
        onUpdate(action.copy(extraConfig = json.encodeToString(newConfig)))
    }

    OutlinedTextField(
        value = action.sourceData,
        onValueChange = { onUpdate(action.copy(sourceData = it)) },
        label = { Text("API Endpoint URL") },
        placeholder = { Text("https://api.example.com/data") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = action.sourceData.isNotBlank() && !action.sourceData.startsWith("http")
    )

    // Headers as comma-separated key:value pairs (simple V1 approach)
    val headersText = remember(config.headers) {
        config.headers.entries.joinToString(", ") { "${it.key}: ${it.value}" }
    }
    OutlinedTextField(
        value = headersText,
        onValueChange = { text ->
            val map = text.split(",").associate { pair ->
                val parts = pair.split(":", limit = 2)
                (parts.getOrElse(0) { "" }.trim()) to (parts.getOrElse(1) { "" }.trim())
            }.filterKeys { it.isNotBlank() }
            updateConfig(config.copy(headers = map))
        },
        label = { Text("Headers (optional, key: value, comma-separated)") },
        placeholder = { Text("Accept: application/json, X-Api-Key: abc") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = config.extractionHint,
        onValueChange = { updateConfig(config.copy(extractionHint = it)) },
        label = { Text("Extraction hint (optional)") },
        placeholder = { Text("e.g. Extract the 'articles' array") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SavedNoteEditor(
    action: WorkflowAction,
    onUpdate: (WorkflowAction) -> Unit,
    availableNotes: List<SavedNote>
) {
    val ref = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<SavedNoteReference>(action.extraConfig) }
            catch (_: Exception) { SavedNoteReference() }
        } else SavedNoteReference()
    }

    var showNoteSelector by remember { mutableStateOf(false) }

    if (availableNotes.isNotEmpty()) {
        // Select from saved notes
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ref.noteId > 0) "Selected: ${ref.noteTitle}" else "No note selected",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { showNoteSelector = true }) {
                Text("Choose Note")
            }
        }

        if (showNoteSelector) {
            AlertDialog(
                onDismissRequest = { showNoteSelector = false },
                title = { Text("Select Saved Note") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableNotes.forEach { note ->
                            TextButton(
                                onClick = {
                                    val newRef = SavedNoteReference(noteId = note.id, noteTitle = note.title)
                                    onUpdate(action.copy(
                                        extraConfig = json.encodeToString(newRef),
                                        sourceData = note.content.take(200)
                                    ))
                                    showNoteSelector = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(note.title, maxLines = 1)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNoteSelector = false }) { Text("Cancel") }
                }
            )
        }
    }

    // Inline content fallback / override
    OutlinedTextField(
        value = action.sourceData,
        onValueChange = { onUpdate(action.copy(sourceData = it)) },
        label = { Text(if (availableNotes.isEmpty()) "Note content" else "Content override (or paste inline)") },
        placeholder = { Text("Paste or type note content...") },
        minLines = 3, maxLines = 8,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PreviousOutputEditor(
    action: WorkflowAction,
    onUpdate: (WorkflowAction) -> Unit,
    availableWorkflows: List<WorkflowTemplate>
) {
    val config = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<PreviousOutputConfig>(action.extraConfig) }
            catch (_: Exception) { PreviousOutputConfig() }
        } else PreviousOutputConfig()
    }

    fun updateConfig(newConfig: PreviousOutputConfig) {
        onUpdate(action.copy(extraConfig = json.encodeToString(newConfig)))
    }

    var showWorkflowSelector by remember { mutableStateOf(false) }

    // Workflow selector
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (config.sourceWorkflowId > 0) "From: ${config.sourceWorkflowName}" else "No workflow selected",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = { showWorkflowSelector = true }) {
            Text("Choose")
        }
    }

    if (showWorkflowSelector) {
        AlertDialog(
            onDismissRequest = { showWorkflowSelector = false },
            title = { Text("Select Source Workflow") },
            text = {
                if (availableWorkflows.isEmpty()) {
                    Text("No other workflows available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableWorkflows.forEach { wf ->
                            TextButton(
                                onClick = {
                                    updateConfig(config.copy(
                                        sourceWorkflowId = wf.id,
                                        sourceWorkflowName = wf.name
                                    ))
                                    showWorkflowSelector = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(wf.name, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkflowSelector = false }) { Text("Cancel") }
            }
        )
    }

    // Selection mode
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = config.selectionMode == OutputSelectionMode.LATEST_SUCCESSFUL,
            onClick = { updateConfig(config.copy(selectionMode = OutputSelectionMode.LATEST_SUCCESSFUL)) }
        )
        Text("Latest successful run", style = MaterialTheme.typography.bodySmall)
    }

    // Include metadata toggle
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = config.includeMetadata,
            onCheckedChange = { updateConfig(config.copy(includeMetadata = it)) }
        )
        Text("Include run metadata", style = MaterialTheme.typography.bodySmall)
    }
}

// ── Multi-Feed RSS Editor ──

@Composable
private fun MultiFeedRssEditor(action: WorkflowAction, onUpdate: (WorkflowAction) -> Unit) {
    val config = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<MultiFeedRssConfig>(action.extraConfig) }
            catch (_: Exception) { MultiFeedRssConfig() }
        } else MultiFeedRssConfig()
    }

    fun updateConfig(newConfig: MultiFeedRssConfig) {
        onUpdate(action.copy(extraConfig = json.encodeToString(newConfig)))
    }

    Text("Feed URLs", style = MaterialTheme.typography.labelLarge)

    // Dynamic feed URL list
    config.feedUrls.forEachIndexed { i, url ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = url,
                onValueChange = { newUrl ->
                    val updated = config.feedUrls.toMutableList()
                    updated[i] = newUrl
                    updateConfig(config.copy(feedUrls = updated))
                },
                label = { Text("Feed #${i + 1}") },
                placeholder = { Text("https://example.com/rss") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = url.isNotBlank() && !url.startsWith("http")
            )
            IconButton(onClick = {
                val updated = config.feedUrls.toMutableList()
                updated.removeAt(i)
                updateConfig(config.copy(feedUrls = updated))
            }) {
                Icon(Icons.Default.Close, "Remove feed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }

    OutlinedButton(
        onClick = { updateConfig(config.copy(feedUrls = config.feedUrls + "")) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Add Feed URL")
    }

    // Config options
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Max items total:", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = config.maxItems.toString(),
            onValueChange = { v ->
                val n = v.filter { it.isDigit() }.take(2).toIntOrNull() ?: 10
                updateConfig(config.copy(maxItems = n.coerceIn(1, 50)))
            },
            singleLine = true,
            modifier = Modifier.width(72.dp)
        )
    }

    OutlinedTextField(
        value = config.keywordFilter,
        onValueChange = { updateConfig(config.copy(keywordFilter = it)) },
        label = { Text("Keyword filter (optional, comma-separated)") },
        placeholder = { Text("e.g. AI, tech, funding") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = config.deduplicateByTitle,
            onCheckedChange = { updateConfig(config.copy(deduplicateByTitle = it)) }
        )
        Text("Deduplicate by title", style = MaterialTheme.typography.bodySmall)
    }
}

// ── Collapsed summary — delegates to registry ──

private fun actionSummary(action: WorkflowAction): String =
    WorkflowActionRegistry.getSummary(action)

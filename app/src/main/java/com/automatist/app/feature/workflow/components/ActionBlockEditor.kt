package com.automatist.app.feature.workflow.components

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
import com.automatist.app.domain.actions.WorkflowActionRegistry
import com.automatist.app.domain.models.*
import com.automatist.app.domain.models.ProviderProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun ActionBlockEditor(
    action: WorkflowAction,
    index: Int,
    totalCount: Int,
    allActions: List<WorkflowAction> = emptyList(),
    availableNotes: List<SavedNote> = emptyList(),
    availableWorkflows: List<WorkflowTemplate> = emptyList(),
    availableProfiles: List<ProviderProfile> = emptyList(),
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
                    text = "${action.type.displayName} #${index + 1}",
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
                        WorkflowActionType.FETCH_WEATHER -> WeatherEditor(action, onUpdate)
                        WorkflowActionType.FETCH_ROUTE_TIME -> RouteTimeEditor(action, onUpdate)
                        WorkflowActionType.USE_ACTION_OUTPUT -> ActionOutputEditor(action, onUpdate, allActions)
                        WorkflowActionType.AI_PROMPT -> AiPromptEditor(action, onUpdate, availableProfiles)
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

                    // AI profile override — only show when profiles exist and action has instruction context
                    if (availableProfiles.isNotEmpty() && action.instruction.isNotBlank()) {
                        ProfilePicker(
                            label = "AI Profile for this action",
                            hint = "Override the workflow default for this action",
                            selectedProfileId = action.profileId,
                            profiles = availableProfiles,
                            onProfileSelected = { onUpdate(action.copy(profileId = it)) },
                            inheritLabel = "Use workflow default"
                        )
                    }
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

    // RSS examples
    RssExamplesSection(onSelect = { url -> onUpdate(action.copy(sourceData = url)) })

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
        placeholder = { Text("https://api.example.com/v1/data?symbol=AAPL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = action.sourceData.isNotBlank() && !action.sourceData.startsWith("http")
    )

    if (action.sourceData.isBlank()) {
        Text(
            "Enter the full URL including query parameters. The app will make a GET request and pass the response to the AI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }

    // Example APIs section
    ApiExamplesSection(onSelect = { url -> onUpdate(action.copy(sourceData = url)) })

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
        placeholder = { Text("Accept: application/json, X-Api-Key: YOUR_KEY") },
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

// ── API Examples ──

private data class ApiExample(val name: String, val url: String, val category: String, val note: String)

private val API_EXAMPLES = listOf(
    ApiExample(
        "Alpha Vantage — Stock Quote",
        "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=AAPL&apikey=YOUR_KEY",
        "Stocks",
        "Free tier: 25 requests/day. Get key at alphavantage.co"
    ),
    ApiExample(
        "Finnhub — Stock Quote",
        "https://finnhub.io/api/v1/quote?symbol=AAPL&token=YOUR_KEY",
        "Stocks",
        "Free tier: 60 calls/min. Get key at finnhub.io"
    ),
    ApiExample(
        "AviationStack — Flight Status",
        "https://api.aviationstack.com/v1/flights?access_key=YOUR_KEY&flight_iata=LH123",
        "Flights",
        "Free tier: 100 requests/month. Get key at aviationstack.com"
    ),
    ApiExample(
        "Exchange Rates",
        "https://open.er-api.com/v6/latest/USD",
        "Finance",
        "Free, no key required"
    ),
    ApiExample(
        "JSONPlaceholder (Test)",
        "https://jsonplaceholder.typicode.com/posts/1",
        "Testing",
        "Free test API — use to verify your workflow works"
    )
)

@Composable
private fun ApiExamplesSection(onSelect: (String) -> Unit) {
    var showExamples by remember { mutableStateOf(false) }

    Column {
        TextButton(onClick = { showExamples = !showExamples }, contentPadding = PaddingValues(0.dp)) {
            Icon(
                if (showExamples) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (showExamples) "Hide API examples" else "See example APIs you can use",
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (showExamples) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                API_EXAMPLES.forEach { example ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        example.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        example.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { onSelect(example.url) },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Use", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text(
                                example.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    "Replace YOUR_KEY with your actual API key. Most services offer a free tier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
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

    // RSS examples — insert into feed list
    RssExamplesSection(onSelect = { url ->
        updateConfig(config.copy(feedUrls = config.feedUrls + url))
    })

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

// ── Weather Editor ──

@Composable
private fun WeatherEditor(action: WorkflowAction, onUpdate: (WorkflowAction) -> Unit) {
    val config = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<WeatherConfig>(action.extraConfig) }
            catch (_: Exception) { WeatherConfig() }
        } else WeatherConfig()
    }

    fun updateConfig(newConfig: WeatherConfig) {
        onUpdate(action.copy(extraConfig = json.encodeToString(newConfig)))
    }

    OutlinedTextField(
        value = config.location,
        onValueChange = { updateConfig(config.copy(location = it)) },
        label = { Text("Location *") },
        placeholder = { Text("e.g. New York, 10001, or 40.71,-74.00") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Units:", style = MaterialTheme.typography.bodySmall)
        WeatherUnits.entries.forEach { unit ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = config.units == unit,
                    onClick = { updateConfig(config.copy(units = unit)) }
                )
                Text(unit.displayName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Text(
        "Uses OpenWeatherMap (free). Add your API key in Settings \u2192 Service Keys.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

// ── Route/Commute Editor ──

@Composable
private fun RouteTimeEditor(action: WorkflowAction, onUpdate: (WorkflowAction) -> Unit) {
    val config = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<RouteConfig>(action.extraConfig) }
            catch (_: Exception) { RouteConfig() }
        } else RouteConfig()
    }

    fun updateConfig(newConfig: RouteConfig) {
        onUpdate(action.copy(extraConfig = json.encodeToString(newConfig)))
    }

    OutlinedTextField(
        value = config.origin,
        onValueChange = { updateConfig(config.copy(origin = it)) },
        label = { Text("Origin *") },
        placeholder = { Text("e.g. Home address or 40.71,-74.00") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = config.destination,
        onValueChange = { updateConfig(config.copy(destination = it)) },
        label = { Text("Destination *") },
        placeholder = { Text("e.g. Office address or 40.75,-73.99") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Text("Travel mode:", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TravelMode.entries.forEach { mode ->
            FilterChip(
                selected = config.travelMode == mode,
                onClick = { updateConfig(config.copy(travelMode = mode)) },
                label = { Text(mode.displayName, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    Text(
        "Uses OpenRouteService (free). Add your API key in Settings \u2192 Service Keys.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

// ── Use Action Output Editor ──

@Composable
private fun ActionOutputEditor(
    action: WorkflowAction,
    onUpdate: (WorkflowAction) -> Unit,
    allActions: List<WorkflowAction>
) {
    val config = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<ActionOutputConfig>(action.extraConfig) }
            catch (_: Exception) { ActionOutputConfig() }
        } else ActionOutputConfig()
    }

    // Only show actions that appear before this one (by order), excluding self
    val earlierActions = allActions.filter { it.order < action.order && it.id != action.id && it.isEnabled }

    var showSelector by remember { mutableStateOf(false) }

    if (earlierActions.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                "No earlier actions available to reference. Add actions above this one first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (config.sourceActionId.isNotBlank())
                    "Referencing: ${config.sourceActionLabel.ifBlank { "Action" }}"
                else "No action selected",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { showSelector = true }) {
                Text("Choose")
            }
        }

        if (showSelector) {
            AlertDialog(
                onDismissRequest = { showSelector = false },
                title = { Text("Select Source Action") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        earlierActions.forEach { src ->
                            val srcLabel = src.label.ifBlank { "${src.type.displayName} #${src.order + 1}" }
                            TextButton(
                                onClick = {
                                    val newConfig = ActionOutputConfig(
                                        sourceActionId = src.id,
                                        sourceActionLabel = srcLabel
                                    )
                                    onUpdate(action.copy(extraConfig = json.encodeToString(newConfig)))
                                    showSelector = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(srcLabel, fontWeight = FontWeight.Medium)
                                    Text(
                                        src.type.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelector = false }) { Text("Cancel") }
                }
            )
        }
    }

    Text(
        "This action passes the referenced action's output forward as its own result.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

// ── AI Prompt Editor ──

@Composable
private fun AiPromptEditor(
    action: WorkflowAction,
    onUpdate: (WorkflowAction) -> Unit,
    availableProfiles: List<ProviderProfile>
) {
    val config = remember(action.extraConfig) {
        if (action.extraConfig.isNotBlank()) {
            try { json.decodeFromString<AiPromptConfig>(action.extraConfig) }
            catch (_: Exception) { AiPromptConfig() }
        } else AiPromptConfig()
    }

    fun updateConfig(newConfig: AiPromptConfig) {
        onUpdate(action.copy(extraConfig = json.encodeToString(newConfig)))
    }

    // Prompt text
    OutlinedTextField(
        value = config.promptText,
        onValueChange = { updateConfig(config.copy(promptText = it)) },
        label = { Text("AI Prompt *") },
        placeholder = { Text("e.g. Summarize the above content as 5 bullet points...") },
        minLines = 3, maxLines = 8,
        modifier = Modifier.fillMaxWidth()
    )

    // Output format selector
    Text("Output format:", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AiPromptOutputFormat.entries.forEach { fmt ->
            FilterChip(
                selected = config.outputFormat == fmt,
                onClick = { updateConfig(config.copy(outputFormat = fmt)) },
                label = { Text(fmt.displayName, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    // AI profile selector
    if (availableProfiles.isNotEmpty()) {
        ProfilePicker(
            label = "AI Profile",
            hint = "Select which AI model to use for this prompt",
            selectedProfileId = config.profileId,
            profiles = availableProfiles,
            onProfileSelected = { updateConfig(config.copy(profileId = it)) },
            inheritLabel = "Use workflow default"
        )
    }

    Text(
        "This action sends your prompt to an AI provider and returns the response. " +
        "Context from prior actions in this run is automatically included.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

// ── RSS Feed Examples ──

private data class RssExample(val name: String, val url: String, val category: String)

private val RSS_EXAMPLES = listOf(
    RssExample("NY Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "World News"),
    RssExample("CBS News", "https://www.cbsnews.com/latest/rss/main", "World News"),
    RssExample("The Hindu", "https://www.thehindu.com/news/national/?service=rss", "India News"),
    RssExample("TechCrunch", "https://techcrunch.com/feed/", "Technology"),
    RssExample("Hacker News", "https://hnrss.org/frontpage", "Technology"),
    RssExample("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "World News")
)

@Composable
private fun RssExamplesSection(onSelect: (String) -> Unit) {
    var showExamples by remember { mutableStateOf(false) }

    Column {
        TextButton(onClick = { showExamples = !showExamples }, contentPadding = PaddingValues(0.dp)) {
            Icon(
                if (showExamples) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(if (showExamples) "Hide examples" else "Try example RSS feeds", style = MaterialTheme.typography.labelMedium)
        }

        if (showExamples) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RSS_EXAMPLES.forEach { example ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(example.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(example.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onSelect(example.url) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Use", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "To find more feeds, search online for the site name + \"RSS\" (e.g. \"BBC RSS\", \"AI news RSS\").",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── Collapsed summary — delegates to registry ──

private fun actionSummary(action: WorkflowAction): String =
    WorkflowActionRegistry.getSummary(action)

package com.synapse.app.domain.actions

import com.synapse.app.domain.models.*
import kotlinx.serialization.json.Json

/**
 * Central registry for workflow action type metadata, validation, and summaries.
 *
 * Reduces scattered when/switch blocks across the codebase. When adding a new action type:
 * 1. Add enum value to WorkflowActionType
 * 2. Add entry to this registry (metadata, validation, summary)
 * 3. Add executor in WorkflowExecutionEngine
 * 4. Add editor composable in ActionBlockEditor
 */
object WorkflowActionRegistry {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Action Type Metadata ──

    data class ActionTypeInfo(
        val type: WorkflowActionType,
        val displayName: String,
        val description: String,
        val category: ActionCategory,
        val requiresUrl: Boolean = false,
        val requiresSourceData: Boolean = true
    )

    enum class ActionCategory(val label: String) {
        WEB("Web & Feeds"),
        CONTENT("Content"),
        WORKFLOW("Workflow")
    }

    private val registry: Map<WorkflowActionType, ActionTypeInfo> = mapOf(
        WorkflowActionType.FETCH_URL to ActionTypeInfo(
            type = WorkflowActionType.FETCH_URL,
            displayName = "Fetch URL",
            description = "Fetch and extract text from a web page",
            category = ActionCategory.WEB,
            requiresUrl = true
        ),
        WorkflowActionType.PASTE_TEXT to ActionTypeInfo(
            type = WorkflowActionType.PASTE_TEXT,
            displayName = "Paste Text",
            description = "Paste raw text or content",
            category = ActionCategory.CONTENT
        ),
        WorkflowActionType.FETCH_RSS_FEED to ActionTypeInfo(
            type = WorkflowActionType.FETCH_RSS_FEED,
            displayName = "Fetch RSS Feed",
            description = "Pull items from a single RSS/Atom feed",
            category = ActionCategory.WEB,
            requiresUrl = true
        ),
        WorkflowActionType.FETCH_API_GET to ActionTypeInfo(
            type = WorkflowActionType.FETCH_API_GET,
            displayName = "Fetch API (GET)",
            description = "Fetch data from a REST API endpoint",
            category = ActionCategory.WEB,
            requiresUrl = true
        ),
        WorkflowActionType.USE_SAVED_NOTE to ActionTypeInfo(
            type = WorkflowActionType.USE_SAVED_NOTE,
            displayName = "Saved Note",
            description = "Use a reusable saved note as input",
            category = ActionCategory.CONTENT,
            requiresSourceData = false // can use noteId reference instead
        ),
        WorkflowActionType.USE_PREVIOUS_OUTPUT to ActionTypeInfo(
            type = WorkflowActionType.USE_PREVIOUS_OUTPUT,
            displayName = "Previous Output",
            description = "Use output from a previous workflow run",
            category = ActionCategory.WORKFLOW,
            requiresSourceData = false
        ),
        WorkflowActionType.FETCH_RSS_MULTI to ActionTypeInfo(
            type = WorkflowActionType.FETCH_RSS_MULTI,
            displayName = "Multi-Feed RSS",
            description = "Pull and merge items from multiple RSS/Atom feeds",
            category = ActionCategory.WEB,
            requiresSourceData = false // feeds stored in extraConfig
        )
    )

    fun getInfo(type: WorkflowActionType): ActionTypeInfo =
        registry[type] ?: ActionTypeInfo(type, type.displayName, "", ActionCategory.CONTENT)

    fun getAllTypes(): List<ActionTypeInfo> = registry.values.toList()

    fun getTypesByCategory(): Map<ActionCategory, List<ActionTypeInfo>> =
        registry.values.groupBy { it.category }

    fun getDescription(type: WorkflowActionType): String = getInfo(type).description

    // ── Validation ──

    fun validate(action: WorkflowAction, index: Int): List<String> {
        val label = action.label.ifBlank { "Action #${index + 1}" }
        val errors = mutableListOf<String>()

        when (action.type) {
            WorkflowActionType.FETCH_URL -> {
                if (action.sourceData.isBlank()) errors.add("$label: URL is empty.")
                else if (!action.sourceData.startsWith("http")) errors.add("$label: URL must start with http:// or https://")
            }
            WorkflowActionType.PASTE_TEXT -> {
                if (action.sourceData.isBlank()) errors.add("$label: text content is empty.")
            }
            WorkflowActionType.FETCH_RSS_FEED -> {
                if (action.sourceData.isBlank()) errors.add("$label: feed URL is empty.")
                else if (!action.sourceData.startsWith("http")) errors.add("$label: feed URL must start with http:// or https://")
            }
            WorkflowActionType.FETCH_API_GET -> {
                if (action.sourceData.isBlank()) errors.add("$label: API endpoint URL is empty.")
                else if (!action.sourceData.startsWith("http")) errors.add("$label: API URL must start with http:// or https://")
            }
            WorkflowActionType.USE_SAVED_NOTE -> {
                val hasRef = action.extraConfig.isNotBlank() && try {
                    json.decodeFromString<SavedNoteReference>(action.extraConfig).noteId > 0
                } catch (_: Exception) { false }
                if (!hasRef && action.sourceData.isBlank()) {
                    errors.add("$label: select a saved note or provide inline content.")
                }
            }
            WorkflowActionType.USE_PREVIOUS_OUTPUT -> {
                if (action.extraConfig.isBlank()) {
                    errors.add("$label: select a source workflow.")
                } else {
                    try {
                        val cfg = json.decodeFromString<PreviousOutputConfig>(action.extraConfig)
                        if (cfg.sourceWorkflowId <= 0) errors.add("$label: select a source workflow.")
                    } catch (_: Exception) {
                        errors.add("$label: invalid previous output configuration.")
                    }
                }
            }
            WorkflowActionType.FETCH_RSS_MULTI -> {
                if (action.extraConfig.isBlank()) {
                    errors.add("$label: add at least one feed URL.")
                } else {
                    try {
                        val cfg = json.decodeFromString<MultiFeedRssConfig>(action.extraConfig)
                        if (cfg.feedUrls.isEmpty()) errors.add("$label: add at least one feed URL.")
                        cfg.feedUrls.forEachIndexed { i, url ->
                            if (url.isBlank()) errors.add("$label: feed #${i + 1} URL is empty.")
                            else if (!url.startsWith("http")) errors.add("$label: feed #${i + 1} must start with http:// or https://")
                        }
                        if (cfg.maxItems < 1) errors.add("$label: max items must be at least 1.")
                    } catch (_: Exception) {
                        errors.add("$label: invalid multi-feed configuration.")
                    }
                }
            }
        }

        return errors
    }

    // ── Summary (collapsed card preview) ──

    fun getSummary(action: WorkflowAction): String {
        return when (action.type) {
            WorkflowActionType.FETCH_URL -> action.sourceData.take(60).ifBlank { "No URL set" }
            WorkflowActionType.PASTE_TEXT -> action.sourceData.take(60).replace("\n", " ").ifBlank { "No text" }
            WorkflowActionType.FETCH_RSS_FEED -> action.sourceData.take(60).ifBlank { "No feed URL" }
            WorkflowActionType.FETCH_API_GET -> action.sourceData.take(60).ifBlank { "No endpoint" }
            WorkflowActionType.USE_SAVED_NOTE -> {
                if (action.extraConfig.isNotBlank()) {
                    try {
                        val ref = json.decodeFromString<SavedNoteReference>(action.extraConfig)
                        if (ref.noteTitle.isNotBlank()) "Note: ${ref.noteTitle}" else action.sourceData.take(60)
                    } catch (_: Exception) { action.sourceData.take(60) }
                } else action.sourceData.take(60).ifBlank { "No note selected" }
            }
            WorkflowActionType.USE_PREVIOUS_OUTPUT -> {
                if (action.extraConfig.isNotBlank()) {
                    try {
                        val cfg = json.decodeFromString<PreviousOutputConfig>(action.extraConfig)
                        if (cfg.sourceWorkflowName.isNotBlank()) "From: ${cfg.sourceWorkflowName}"
                        else "No workflow selected"
                    } catch (_: Exception) { "Configuration error" }
                } else "No workflow selected"
            }
            WorkflowActionType.FETCH_RSS_MULTI -> {
                if (action.extraConfig.isNotBlank()) {
                    try {
                        val cfg = json.decodeFromString<MultiFeedRssConfig>(action.extraConfig)
                        "${cfg.feedUrls.size} feed(s), max ${cfg.maxItems} items"
                    } catch (_: Exception) { "Configuration error" }
                } else "No feeds configured"
            }
        }
    }
}

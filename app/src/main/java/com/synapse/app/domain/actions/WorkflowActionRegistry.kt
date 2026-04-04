package com.synapse.app.domain.actions

import com.synapse.app.domain.models.*
import kotlinx.serialization.json.Json

/**
 * Central registry for workflow action type metadata, validation, and summaries.
 *
 * When adding a new action type:
 * 1. Add enum value to WorkflowActionType
 * 2. Add entry to this registry (metadata, validation, summary)
 * 3. Add executor in WorkflowExecutionEngine
 * 4. Add editor composable in ActionBlockEditor
 */
object WorkflowActionRegistry {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Metadata Model ──

    data class ActionTypeInfo(
        val type: WorkflowActionType,
        val displayName: String,
        val description: String,
        val longDescription: String,
        val category: ActionCategory,
        val inputSummary: String,         // what the user needs to provide
        val outputSummary: String,        // what the action produces
        val exampleUseCase: String,       // a concrete example
        val setupRequirements: List<SetupRequirement> = emptyList(),
        val requiresUrl: Boolean = false,
        val requiresSourceData: Boolean = true
    )

    data class SetupRequirement(
        val label: String,
        val type: RequirementType,
        val serviceKey: String? = null // for API key requirements
    )

    enum class RequirementType {
        API_KEY,         // needs an API key configured in settings
        SERVICE_KEY,     // needs an external service key (weather, maps)
        PERMISSION,      // needs a runtime permission (future: calendar, location)
        NONE             // ready to use
    }

    enum class ActionCategory(val label: String, val order: Int) {
        DAILY_LIFE("Daily Life", 0),
        NEWS_WEB("News & Web", 1),
        NOTES_TEXT("Notes & Text", 2),
        DATA_API("Data & APIs", 3),
        WORKFLOW_CHAIN("Workflow", 4)
    }

    // ── Registry ──

    private val registry: Map<WorkflowActionType, ActionTypeInfo> = mapOf(

        // ── Daily Life ──

        WorkflowActionType.FETCH_WEATHER to ActionTypeInfo(
            type = WorkflowActionType.FETCH_WEATHER,
            displayName = "Current Weather",
            description = "Get today's weather for any location",
            longDescription = "Fetches current temperature, humidity, wind speed, and conditions from OpenWeatherMap. Returns clean, structured weather data ready for your workflow.",
            category = ActionCategory.DAILY_LIFE,
            inputSummary = "City name, zip code, or coordinates",
            outputSummary = "Temperature, humidity, conditions, wind",
            exampleUseCase = "Add to a morning briefing to know if you need an umbrella",
            setupRequirements = listOf(
                SetupRequirement("OpenWeatherMap API key", RequirementType.SERVICE_KEY, WeatherService.SERVICE_KEY)
            ),
            requiresSourceData = false
        ),
        WorkflowActionType.FETCH_ROUTE_TIME to ActionTypeInfo(
            type = WorkflowActionType.FETCH_ROUTE_TIME,
            displayName = "Commute / Route Time",
            description = "Get travel time and distance between two places",
            longDescription = "Calculates travel time, distance, and route between an origin and destination using OpenRouteService. Supports driving, walking, and cycling.",
            category = ActionCategory.DAILY_LIFE,
            inputSummary = "Origin address, destination address, travel mode",
            outputSummary = "Distance, duration, travel mode",
            exampleUseCase = "Check your morning commute time before leaving",
            setupRequirements = listOf(
                SetupRequirement("OpenRouteService API key", RequirementType.SERVICE_KEY, RouteService.SERVICE_KEY)
            ),
            requiresSourceData = false
        ),

        // ── News & Web ──

        WorkflowActionType.FETCH_URL to ActionTypeInfo(
            type = WorkflowActionType.FETCH_URL,
            displayName = "Read Web Page",
            description = "Fetch and extract text content from a URL",
            longDescription = "Downloads a web page, strips HTML formatting, and extracts the readable text content. Useful for pulling articles, blog posts, or documentation into your workflow.",
            category = ActionCategory.NEWS_WEB,
            inputSummary = "Any web page URL",
            outputSummary = "Extracted text content (up to 4000 chars)",
            exampleUseCase = "Pull an article to summarize or analyze",
            requiresUrl = true
        ),
        WorkflowActionType.FETCH_RSS_FEED to ActionTypeInfo(
            type = WorkflowActionType.FETCH_RSS_FEED,
            displayName = "RSS / News Feed",
            description = "Pull latest items from an RSS or Atom feed",
            longDescription = "Fetches items from a single RSS or Atom feed, extracts titles and summaries, and optionally filters by keywords. Great for monitoring a specific blog, news source, or publication.",
            category = ActionCategory.NEWS_WEB,
            inputSummary = "Feed URL, optional keyword filter",
            outputSummary = "Feed titles and summaries",
            exampleUseCase = "Monitor a tech blog for AI-related updates",
            requiresUrl = true
        ),
        WorkflowActionType.FETCH_RSS_MULTI to ActionTypeInfo(
            type = WorkflowActionType.FETCH_RSS_MULTI,
            displayName = "Multi-Feed News",
            description = "Combine items from multiple RSS feeds into one stream",
            longDescription = "Fetches from multiple RSS/Atom feeds, merges the results, deduplicates similar items, and filters by keywords. Perfect for monitoring an entire topic across several sources.",
            category = ActionCategory.NEWS_WEB,
            inputSummary = "Multiple feed URLs, keyword filter, max items",
            outputSummary = "Merged, deduplicated feed items",
            exampleUseCase = "Track competitor news across industry publications",
            requiresSourceData = false
        ),

        // ── Notes & Text ──

        WorkflowActionType.PASTE_TEXT to ActionTypeInfo(
            type = WorkflowActionType.PASTE_TEXT,
            displayName = "Text Input",
            description = "Paste or type text content directly",
            longDescription = "Provides raw text content as input to the workflow. Use this for meeting notes, article text, instructions, or any content you want to process.",
            category = ActionCategory.NOTES_TEXT,
            inputSummary = "Any text content",
            outputSummary = "The text you provide, as-is",
            exampleUseCase = "Paste meeting notes to generate a brief"
        ),
        WorkflowActionType.USE_SAVED_NOTE to ActionTypeInfo(
            type = WorkflowActionType.USE_SAVED_NOTE,
            displayName = "Saved Note",
            description = "Use a reusable note from your library",
            longDescription = "References a saved note from your Notes library, or lets you type inline content. Saved notes are reusable across workflows — great for company context, style guides, or recurring instructions.",
            category = ActionCategory.NOTES_TEXT,
            inputSummary = "Select a saved note or type inline",
            outputSummary = "The note content",
            exampleUseCase = "Include your company context in every briefing",
            requiresSourceData = false
        ),

        // ── Data & APIs ──

        WorkflowActionType.FETCH_API_GET to ActionTypeInfo(
            type = WorkflowActionType.FETCH_API_GET,
            displayName = "API Request (GET)",
            description = "Fetch data from any REST API endpoint",
            longDescription = "Makes an HTTP GET request to a REST API, with optional custom headers and query parameters. Returns the response as text or JSON for the workflow to process.",
            category = ActionCategory.DATA_API,
            inputSummary = "API endpoint URL, optional headers",
            outputSummary = "API response data (JSON or text)",
            exampleUseCase = "Pull stock prices or weather data from a custom API",
            requiresUrl = true
        ),

        // ── Workflow ──

        WorkflowActionType.USE_PREVIOUS_OUTPUT to ActionTypeInfo(
            type = WorkflowActionType.USE_PREVIOUS_OUTPUT,
            displayName = "Previous Workflow Output",
            description = "Use the result of another workflow as input",
            longDescription = "Pulls the output from the latest successful run (or a specific run) of another workflow. This lets you chain workflows together — for example, turning yesterday's brief into social posts.",
            category = ActionCategory.WORKFLOW_CHAIN,
            inputSummary = "Select source workflow",
            outputSummary = "The selected run's output text",
            exampleUseCase = "Turn yesterday's news digest into social media posts",
            requiresSourceData = false
        )
    )

    fun getInfo(type: WorkflowActionType): ActionTypeInfo =
        registry[type] ?: ActionTypeInfo(
            type, type.displayName, "", "", ActionCategory.NOTES_TEXT,
            "", "", ""
        )

    fun getAllTypes(): List<ActionTypeInfo> = registry.values.toList()

    fun getTypesByCategory(): Map<ActionCategory, List<ActionTypeInfo>> =
        registry.values.groupBy { it.category }.toSortedMap(compareBy { it.order })

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
            WorkflowActionType.FETCH_WEATHER -> {
                if (action.extraConfig.isBlank()) {
                    errors.add("$label: configure a location for weather data.")
                } else {
                    try {
                        val cfg = json.decodeFromString<WeatherConfig>(action.extraConfig)
                        if (cfg.location.isBlank()) errors.add("$label: location is required (city, zip, or lat,lon).")
                    } catch (_: Exception) {
                        errors.add("$label: invalid weather configuration.")
                    }
                }
            }
            WorkflowActionType.FETCH_ROUTE_TIME -> {
                if (action.extraConfig.isBlank()) {
                    errors.add("$label: configure origin and destination.")
                } else {
                    try {
                        val cfg = json.decodeFromString<RouteConfig>(action.extraConfig)
                        if (cfg.origin.isBlank()) errors.add("$label: origin address is required.")
                        if (cfg.destination.isBlank()) errors.add("$label: destination address is required.")
                    } catch (_: Exception) {
                        errors.add("$label: invalid route configuration.")
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
            WorkflowActionType.FETCH_WEATHER -> {
                if (action.extraConfig.isNotBlank()) {
                    try {
                        val cfg = json.decodeFromString<WeatherConfig>(action.extraConfig)
                        if (cfg.location.isNotBlank()) "${cfg.location} (${cfg.units.displayName})"
                        else "No location set"
                    } catch (_: Exception) { "Configuration error" }
                } else "No location set"
            }
            WorkflowActionType.FETCH_ROUTE_TIME -> {
                if (action.extraConfig.isNotBlank()) {
                    try {
                        val cfg = json.decodeFromString<RouteConfig>(action.extraConfig)
                        if (cfg.origin.isNotBlank() && cfg.destination.isNotBlank())
                            "${cfg.origin.take(20)} → ${cfg.destination.take(20)}"
                        else "Configure route"
                    } catch (_: Exception) { "Configuration error" }
                } else "Configure route"
            }
        }
    }
}

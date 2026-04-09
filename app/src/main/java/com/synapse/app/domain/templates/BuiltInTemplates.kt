package com.synapse.app.domain.templates

import com.synapse.app.domain.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Curated built-in workflow templates provided by the app.
 *
 * These are NOT stored in Room — they are code-defined blueprints.
 * When a user taps "Use Template", a WorkflowTemplate instance is created
 * in Room with sourceTemplateId referencing the built-in template ID.
 */
object BuiltInTemplates {

    data class BuiltInTemplate(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val useCases: List<String>,
        val setupNotes: List<String> = emptyList(),
        val blueprint: WorkflowTemplate
    )

    val ALL: List<BuiltInTemplate> = listOf(
        morningBrief(),
        newsToSocial(),
        articleSummarizer(),
        stockTracker(),
        flightTracker()
    )

    fun findById(id: String): BuiltInTemplate? = ALL.find { it.id == id }

    val CATEGORIES = listOf("Daily Routines", "Social Media", "Communication", "Finance", "Travel")

    // ── Template Definitions ──

    private fun morningBrief() = BuiltInTemplate(
        id = "morning_commute",
        name = "Morning Brief",
        description = "Get weather, commute time, and top headlines before you leave. Your personal morning briefing.",
        category = "Daily Routines",
        useCases = listOf("Morning routine", "Commute planning", "Daily weather + news"),
        setupNotes = listOf(
            "Add your OpenWeatherMap API key in Settings (free)",
            "Add your OpenRouteService API key in Settings (free)",
            "Enter your home and office addresses after creating the workflow",
            "Add an RSS feed URL for your preferred news source"
        ),
        blueprint = WorkflowTemplate(
            name = "Morning Brief",
            description = "Weather, commute time, and top headlines for your morning",
            category = "Daily Routines",
            sourceTemplateId = "morning_commute",
            trigger = WorkflowTrigger.Daily(hour = 7, minute = 0),
            actions = listOf(
                WorkflowAction(
                    id = "weather",
                    type = WorkflowActionType.FETCH_WEATHER,
                    label = "Today's Weather",
                    instruction = "Summarize today's weather conditions and whether I need an umbrella or jacket.",
                    order = 0,
                    extraConfig = Json.encodeToString(
                        WeatherConfig(location = "", units = WeatherUnits.METRIC)
                    )
                ),
                WorkflowAction(
                    id = "commute",
                    type = WorkflowActionType.FETCH_ROUTE_TIME,
                    label = "Commute to Office",
                    instruction = "Report travel time and suggest when to leave based on the estimated duration.",
                    order = 1,
                    extraConfig = Json.encodeToString(
                        RouteConfig(origin = "", destination = "", travelMode = TravelMode.DRIVING)
                    )
                ),
                WorkflowAction(
                    id = "news",
                    type = WorkflowActionType.FETCH_RSS_FEED,
                    label = "Morning Headlines",
                    sourceData = "",
                    instruction = "Pick the top 3-5 most relevant headlines.",
                    order = 2
                )
            ),
            globalInstruction = "Create a concise, friendly morning briefing. Start with weather, then commute info, then headlines. Keep it scannable so I can read it in 30 seconds.",
            outputConfig = WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.MARKDOWN
            ),
            notifyOnCompletion = true
        )
    )

    private fun newsToSocial() = BuiltInTemplate(
        id = "news_to_social",
        name = "News to Social",
        description = "Fetch daily news from RSS feeds and generate ready-to-share social media posts for X, LinkedIn, and more. Perfect for content creators and marketers.",
        category = "Social Media",
        useCases = listOf("Daily social content", "Content repurposing", "Multi-platform publishing"),
        setupNotes = listOf(
            "Add one or more RSS feed URLs for your preferred news sources",
            "Select which social platforms to generate content for",
            "Optionally add per-platform style instructions",
            "An AI provider API key is required for content generation"
        ),
        blueprint = WorkflowTemplate(
            name = "News to Social",
            description = "Generate daily social media posts from curated news",
            category = "Social Media",
            sourceTemplateId = "news_to_social",
            trigger = WorkflowTrigger.Daily(hour = 10, minute = 0),
            actions = listOf(
                WorkflowAction(
                    id = "news_feed",
                    type = WorkflowActionType.FETCH_RSS_FEED,
                    label = "News Feed",
                    sourceData = "",
                    instruction = "Select the most interesting and shareable items from today's feed.",
                    order = 0,
                    extraConfig = Json.encodeToString(
                        RssFeedConfig(maxItems = 5, includeTitle = true, includeSummary = true)
                    )
                )
            ),
            globalInstruction = "Focus on the most newsworthy and engaging items. Each platform output should feel native to that platform, not like a copy-paste. Highlight different angles for different audiences.",
            outputConfig = WorkflowOutputConfig(
                outputType = WorkflowOutputType.SOCIAL_POST,
                outputFormat = OutputFormat.MARKDOWN, // overridden internally to JSON for social mode
                socialPlatforms = setOf(SocialPlatform.X, SocialPlatform.LINKEDIN),
                socialGlobalInstruction = "Focus on tech and business trends. Keep tone professional but approachable.",
                platformInstructions = mapOf(
                    "X" to "Short, punchy, under 280 chars. Include 1-2 relevant hashtags.",
                    "LinkedIn" to "Professional insight with a hook. 2-3 short paragraphs. End with a question or CTA."
                )
            ),
            notifyOnCompletion = true,
            customization = TemplateCustomization(
                editableSections = setOf(
                    EditableSection.TRIGGER,
                    EditableSection.ACTIONS,
                    EditableSection.INSTRUCTIONS,
                    EditableSection.OUTPUT,
                    EditableSection.NOTIFICATIONS
                ),
                canAddActions = true,
                canRemoveActions = true
            )
        )
    )

    private fun articleSummarizer() = BuiltInTemplate(
        id = "article_summarizer",
        name = "Article Transformer",
        description = "Transform articles and text into summaries, social threads, or professional posts.",
        category = "Communication",
        useCases = listOf("Article summaries", "Social media threads", "LinkedIn posts"),
        setupNotes = listOf(
            "Paste or share text into the workflow before running",
            "Choose your output style (briefing, social post, etc.) in Output settings"
        ),
        blueprint = WorkflowTemplate(
            name = "Article Transformer",
            description = "Transform pasted text into structured outputs",
            category = "Communication",
            sourceTemplateId = "article_summarizer",
            trigger = WorkflowTrigger.Manual,
            actions = listOf(
                WorkflowAction(
                    id = "article_input",
                    type = WorkflowActionType.PASTE_TEXT,
                    label = "Article Text",
                    sourceData = "",
                    instruction = "",
                    order = 0
                )
            ),
            globalInstruction = "Transform this text into a concise, well-structured output.",
            outputConfig = WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.MARKDOWN
            ),
            customization = TemplateCustomization(
                editableSections = setOf(
                    EditableSection.ACTIONS,
                    EditableSection.INSTRUCTIONS,
                    EditableSection.OUTPUT
                ),
                lockedActionIds = setOf("article_input"),
                canAddActions = true,
                canRemoveActions = false
            )
        )
    )

    private fun stockTracker() = BuiltInTemplate(
        id = "stock_tracker",
        name = "Stock Tracker",
        description = "Monitor stock prices and financial news. Uses RSS feeds for market news and a public API for price data. You provide the API endpoint and an optional personal watchlist.",
        category = "Finance",
        useCases = listOf("Stock price monitoring", "Market news digest", "Portfolio watch"),
        setupNotes = listOf(
            "Stock Prices action: enter a free stock API endpoint (see examples in the editor)",
            "Market News action: add a financial RSS feed URL",
            "My Watchlist: create a Saved Note with your ticker symbols (e.g. AAPL, MSFT, TSLA)",
            "An AI provider API key is required for the final summary"
        ),
        blueprint = WorkflowTemplate(
            name = "Stock Tracker",
            description = "Financial news and stock data brief",
            category = "Finance",
            sourceTemplateId = "stock_tracker",
            trigger = WorkflowTrigger.Interval(intervalMinutes = 240),
            actions = listOf(
                WorkflowAction(
                    id = "market_news",
                    type = WorkflowActionType.FETCH_RSS_FEED,
                    label = "Market News",
                    sourceData = "",
                    instruction = "Extract headlines about major market moves, earnings, and notable stock changes.",
                    order = 0
                ),
                WorkflowAction(
                    id = "stock_data",
                    type = WorkflowActionType.FETCH_API_GET,
                    label = "Stock Prices",
                    sourceData = "",
                    instruction = "Summarize current prices, daily change, and trend direction for each ticker.",
                    order = 1,
                    extraConfig = Json.encodeToString(
                        ApiGetConfig(
                            extractionHint = "Extract ticker symbols, prices, daily change percentage, and volume if available."
                        )
                    )
                ),
                WorkflowAction(
                    id = "watchlist_notes",
                    type = WorkflowActionType.USE_SAVED_NOTE,
                    label = "My Watchlist",
                    sourceData = "",
                    instruction = "Use this watchlist to focus the analysis on tickers and sectors I care about.",
                    order = 2
                )
            ),
            globalInstruction = "Create a concise financial brief. Lead with the biggest movers, then summarize market sentiment, then cover my watchlist items. Flag anything that needs attention.",
            outputConfig = WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.MARKDOWN
            ),
            notifyOnCompletion = true,
            customization = TemplateCustomization(
                editableSections = setOf(
                    EditableSection.TRIGGER,
                    EditableSection.ACTIONS,
                    EditableSection.INSTRUCTIONS,
                    EditableSection.OUTPUT,
                    EditableSection.NOTIFICATIONS
                ),
                canAddActions = true,
                canRemoveActions = true
            )
        )
    )

    private fun flightTracker() = BuiltInTemplate(
        id = "flight_tracker",
        name = "Flight Tracker",
        description = "Track flight status and destination weather before your trip. Uses a public flight API for status data and OpenWeatherMap for destination conditions. You provide the API endpoint and trip details.",
        category = "Travel",
        useCases = listOf("Flight status checks", "Airport weather", "Travel day briefing"),
        setupNotes = listOf(
            "Flight Status action: enter a flight status API endpoint (see examples in the editor)",
            "Destination Weather: enter the destination city and add your OpenWeatherMap key in Settings",
            "Trip Details: create a Saved Note with your booking info, hotel, and transport details",
            "An AI provider API key is required for the final summary"
        ),
        blueprint = WorkflowTemplate(
            name = "Flight Tracker",
            description = "Flight status, weather at destination, and travel updates",
            category = "Travel",
            sourceTemplateId = "flight_tracker",
            trigger = WorkflowTrigger.Interval(intervalMinutes = 60),
            actions = listOf(
                WorkflowAction(
                    id = "flight_status",
                    type = WorkflowActionType.FETCH_API_GET,
                    label = "Flight Status",
                    sourceData = "",
                    instruction = "Report flight number, departure/arrival times, gate, terminal, and any delays or cancellations.",
                    order = 0,
                    extraConfig = Json.encodeToString(
                        ApiGetConfig(
                            extractionHint = "Extract flight number, status, departure time, arrival time, gate, terminal, and delay info."
                        )
                    )
                ),
                WorkflowAction(
                    id = "destination_weather",
                    type = WorkflowActionType.FETCH_WEATHER,
                    label = "Destination Weather",
                    instruction = "Summarize the weather at the destination city. Mention what to pack or expect on arrival.",
                    order = 1,
                    extraConfig = Json.encodeToString(
                        WeatherConfig(location = "", units = WeatherUnits.METRIC)
                    )
                ),
                WorkflowAction(
                    id = "travel_notes",
                    type = WorkflowActionType.USE_SAVED_NOTE,
                    label = "Trip Details",
                    sourceData = "",
                    instruction = "Use these trip details (booking references, hotel, transport) to contextualize the briefing.",
                    order = 2
                )
            ),
            globalInstruction = "Create a travel briefing. Start with flight status and any delays, then destination weather, then any relevant trip details. Keep it actionable — tell me what I need to do next.",
            outputConfig = WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.MARKDOWN
            ),
            notifyOnCompletion = true,
            customization = TemplateCustomization(
                editableSections = setOf(
                    EditableSection.TRIGGER,
                    EditableSection.ACTIONS,
                    EditableSection.INSTRUCTIONS,
                    EditableSection.OUTPUT,
                    EditableSection.NOTIFICATIONS
                ),
                canAddActions = true,
                canRemoveActions = true
            )
        )
    )
}

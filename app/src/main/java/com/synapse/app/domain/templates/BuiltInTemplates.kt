package com.synapse.app.domain.templates

import com.synapse.app.domain.models.*

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
        val blueprint: WorkflowTemplate
    )

    val ALL: List<BuiltInTemplate> = listOf(
        morningBrief(),
        articleSummarizer(),
        meetingPrep(),
        contentRepurposer(),
        competitorMonitor(),
        researchDigest()
    )

    fun findById(id: String): BuiltInTemplate? = ALL.find { it.id == id }

    val CATEGORIES = listOf("News & Content", "Communication", "Research", "Social Media")

    // ── Template Definitions ──

    private fun morningBrief() = BuiltInTemplate(
        id = "morning_brief",
        name = "Morning Brief",
        description = "Monitor RSS feeds and get a daily AI-generated executive summary of what matters.",
        category = "News & Content",
        useCases = listOf("Daily news digest", "Industry monitoring", "Team updates"),
        blueprint = WorkflowTemplate(
            name = "Morning Brief",
            description = "Daily executive brief from your RSS feeds",
            category = "News & Content",
            sourceTemplateId = "morning_brief",
            trigger = WorkflowTrigger.Daily(hour = 8, minute = 0),
            actions = listOf(
                WorkflowAction(
                    id = "rss_main",
                    type = WorkflowActionType.FETCH_RSS_FEED,
                    label = "News Feed",
                    sourceData = "",
                    instruction = "Extract the most relevant headlines and insights",
                    order = 0
                )
            ),
            globalInstruction = "Produce a scannable executive morning brief. Highlight only what truly matters today. Be concise.",
            outputConfig = WorkflowOutputConfig(outputType = WorkflowOutputType.BRIEFING),
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
            outputConfig = WorkflowOutputConfig(outputType = WorkflowOutputType.BRIEFING),
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

    private fun meetingPrep() = BuiltInTemplate(
        id = "meeting_prep",
        name = "Meeting Strategist",
        description = "Prepare briefs and strategic questions from meeting notes.",
        category = "Communication",
        useCases = listOf("Meeting briefs", "Strategic questions", "Action item extraction"),
        blueprint = WorkflowTemplate(
            name = "Meeting Strategist",
            description = "Generate briefs and questions from meeting notes",
            category = "Communication",
            sourceTemplateId = "meeting_prep",
            trigger = WorkflowTrigger.Manual,
            actions = listOf(
                WorkflowAction(
                    id = "meeting_notes",
                    type = WorkflowActionType.PASTE_TEXT,
                    label = "Meeting Notes",
                    sourceData = "",
                    instruction = "These are raw meeting notes. Extract key decisions, action items, and open questions.",
                    order = 0
                )
            ),
            globalInstruction = "Generate an executive meeting brief with clear action items and strategic follow-up questions.",
            outputConfig = WorkflowOutputConfig(outputType = WorkflowOutputType.BRIEFING),
            customization = TemplateCustomization(
                editableSections = setOf(
                    EditableSection.ACTIONS,
                    EditableSection.INSTRUCTIONS,
                    EditableSection.OUTPUT
                ),
                lockedActionIds = setOf("meeting_notes"),
                canAddActions = true,
                canRemoveActions = false
            )
        )
    )

    private fun contentRepurposer() = BuiltInTemplate(
        id = "content_repurposer",
        name = "Content Repurposer",
        description = "Turn existing content into social media posts for multiple platforms.",
        category = "Social Media",
        useCases = listOf("Cross-platform posting", "Content recycling", "Social media management"),
        blueprint = WorkflowTemplate(
            name = "Content Repurposer",
            description = "Repurpose content into platform-specific social posts",
            category = "Social Media",
            sourceTemplateId = "content_repurposer",
            trigger = WorkflowTrigger.Manual,
            actions = listOf(
                WorkflowAction(
                    id = "source_content",
                    type = WorkflowActionType.PASTE_TEXT,
                    label = "Source Content",
                    sourceData = "",
                    instruction = "This is the source content to repurpose.",
                    order = 0
                )
            ),
            globalInstruction = "Create distinct, platform-appropriate social media posts from this content. Each post should feel native to its platform, not just a rewrite.",
            outputConfig = WorkflowOutputConfig(
                outputType = WorkflowOutputType.SOCIAL_POST,
                socialPlatforms = setOf(SocialPlatform.LINKEDIN, SocialPlatform.X, SocialPlatform.THREADS)
            ),
            customization = TemplateCustomization(
                editableSections = setOf(
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

    private fun competitorMonitor() = BuiltInTemplate(
        id = "competitor_monitor",
        name = "Competitor Monitor",
        description = "Track multiple RSS feeds for competitor news and industry movements.",
        category = "Research",
        useCases = listOf("Competitive intelligence", "Market monitoring", "Industry trends"),
        blueprint = WorkflowTemplate(
            name = "Competitor Monitor",
            description = "Multi-feed competitive intelligence brief",
            category = "Research",
            sourceTemplateId = "competitor_monitor",
            trigger = WorkflowTrigger.Daily(hour = 9, minute = 0),
            actions = listOf(
                WorkflowAction(
                    id = "multi_rss",
                    type = WorkflowActionType.FETCH_RSS_MULTI,
                    label = "Industry Feeds",
                    sourceData = "",
                    instruction = "Focus on competitor product launches, partnerships, and strategic moves.",
                    order = 0
                )
            ),
            globalInstruction = "Produce a structured competitive intelligence brief. Group by competitor or theme. Highlight threats and opportunities.",
            outputConfig = WorkflowOutputConfig(outputType = WorkflowOutputType.BRIEFING),
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

    private fun researchDigest() = BuiltInTemplate(
        id = "research_digest",
        name = "Research Digest",
        description = "Combine multiple sources — URLs, APIs, notes — into a research summary.",
        category = "Research",
        useCases = listOf("Market research", "Topic deep-dives", "Due diligence"),
        blueprint = WorkflowTemplate(
            name = "Research Digest",
            description = "Multi-source research compilation and summary",
            category = "Research",
            sourceTemplateId = "research_digest",
            trigger = WorkflowTrigger.Manual,
            actions = listOf(
                WorkflowAction(
                    id = "research_url",
                    type = WorkflowActionType.FETCH_URL,
                    label = "Research Source",
                    sourceData = "",
                    instruction = "Extract key facts, data points, and insights.",
                    order = 0
                )
            ),
            globalInstruction = "Synthesize all research sources into a structured summary with key findings, data points, and recommended next steps.",
            outputConfig = WorkflowOutputConfig(outputType = WorkflowOutputType.BRIEFING),
            customization = TemplateCustomization(
                editableSections = EditableSection.entries.toSet(),
                canAddActions = true,
                canRemoveActions = true
            )
        )
    )
}

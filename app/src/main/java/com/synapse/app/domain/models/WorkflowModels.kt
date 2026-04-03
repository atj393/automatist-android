package com.synapse.app.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Workflow Template (shared model for both built-in blueprints and user workflows) ──

data class WorkflowTemplate(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val trigger: WorkflowTrigger = WorkflowTrigger.Manual,
    val actions: List<WorkflowAction> = emptyList(),
    val globalInstruction: String = "",
    val outputConfig: WorkflowOutputConfig = WorkflowOutputConfig(),
    val notifyOnCompletion: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val lastRunAtMillis: Long? = null,
    val lastRunStatus: WorkflowRunStatus? = null,
    // ── Template-system fields ──
    val sourceTemplateId: String = "",         // built-in template ID this was created from ("" for legacy)
    val category: String = "",                 // UI grouping: "News & Content", "Communication", etc.
    val customization: TemplateCustomization = TemplateCustomization(),
    // ── Profile routing ──
    val defaultProfileId: String = "" // workflow-level default profile ("" = use app default)
)

// ── Template Customization Rules ──

@Serializable
data class TemplateCustomization(
    val editableSections: Set<EditableSection> = EditableSection.entries.toSet(),
    val lockedActionIds: Set<String> = emptySet(), // action IDs that cannot be removed
    val canAddActions: Boolean = true,
    val canRemoveActions: Boolean = true
)

@Serializable
enum class EditableSection {
    BASICS,
    TRIGGER,
    ACTIONS,
    INSTRUCTIONS,
    OUTPUT,
    NOTIFICATIONS
}

// ── Trigger ──

@Serializable
sealed interface WorkflowTrigger {
    @Serializable
    @SerialName("manual")
    data object Manual : WorkflowTrigger

    @Serializable
    @SerialName("daily")
    data class Daily(
        val hour: Int = 8,
        val minute: Int = 0
    ) : WorkflowTrigger

    @Serializable
    @SerialName("weekly")
    data class Weekly(
        val daysOfWeek: Set<Int> = setOf(1),
        val hour: Int = 8,
        val minute: Int = 0
    ) : WorkflowTrigger

    @Serializable
    @SerialName("notification")
    data class NotificationKeyword(
        val keywords: List<String> = emptyList()
    ) : WorkflowTrigger
}

// ── Action Types ──

@Serializable
enum class WorkflowActionType(val displayName: String) {
    FETCH_URL("Fetch URL"),
    PASTE_TEXT("Paste Text"),
    FETCH_RSS_FEED("Fetch RSS Feed"),
    FETCH_API_GET("Fetch API (GET)"),
    USE_SAVED_NOTE("Saved Note"),
    USE_PREVIOUS_OUTPUT("Previous Workflow Output"),
    FETCH_RSS_MULTI("Multi-Feed RSS")
}

// ── Action Model ──

@Serializable
data class WorkflowAction(
    val id: String,
    val type: WorkflowActionType,
    val label: String = "",
    val sourceData: String = "",
    val instruction: String = "",
    val order: Int = 0,
    val isEnabled: Boolean = true,
    val extraConfig: String = ""
)

// ── Per-Action Config Models ──

@Serializable
data class RssFeedConfig(
    val maxItems: Int = 5,
    val includeTitle: Boolean = true,
    val includeSummary: Boolean = true,
    val includeLink: Boolean = false,
    val includePublishedDate: Boolean = false,
    val keywordFilter: String = ""
)

@Serializable
data class ApiGetConfig(
    val queryParams: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val extractionHint: String = ""
)

@Serializable
data class SavedNoteReference(
    val noteId: Long = 0,
    val noteTitle: String = ""
)

@Serializable
enum class OutputSelectionMode {
    LATEST_SUCCESSFUL,
    SPECIFIC_RUN
}

@Serializable
data class PreviousOutputConfig(
    val sourceWorkflowId: Long = 0,
    val sourceWorkflowName: String = "",
    val selectionMode: OutputSelectionMode = OutputSelectionMode.LATEST_SUCCESSFUL,
    val specificRunId: Long? = null,
    val includeMetadata: Boolean = false
)

@Serializable
data class MultiFeedRssConfig(
    val feedUrls: List<String> = emptyList(),
    val maxItems: Int = 10,
    val includeTitle: Boolean = true,
    val includeSummary: Boolean = true,
    val includeLink: Boolean = false,
    val includePublishedDate: Boolean = false,
    val keywordFilter: String = "",
    val deduplicateByTitle: Boolean = true,
    val sortNewestFirst: Boolean = true
)

// ── Saved Note ──

data class SavedNote(
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

// ── Output Config ──

@Serializable
enum class WorkflowOutputType(val displayName: String) {
    BRIEFING("Briefing"),
    SOCIAL_POST("Social Post"),
    BOTH("Both"),
    CUSTOM("Custom")
}

@Serializable
data class WorkflowOutputConfig(
    val outputType: WorkflowOutputType = WorkflowOutputType.BRIEFING,
    val customInstruction: String = "",
    val socialPlatforms: Set<SocialPlatform> = emptySet(),
    val saveToHistory: Boolean = true,
    val outputProfileId: String = "" // profile override for final output generation ("" = inherit workflow default)
)

// ── Run ──

enum class WorkflowRunStatus {
    RUNNING,
    COMPLETED,
    FAILED
}

data class WorkflowRun(
    val id: Long = 0,
    val templateId: Long,
    val templateName: String,
    val triggerType: String = "manual",
    val status: WorkflowRunStatus = WorkflowRunStatus.RUNNING,
    val currentStage: String = "",
    val outputText: String = "",
    val providerType: ProviderType? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val durationMs: Long? = null,
    val errorMessage: String? = null,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null
)

data class TokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val isEstimated: Boolean = false
)

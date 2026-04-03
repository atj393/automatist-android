package com.synapse.app.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Workflow Template ──

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
    val lastRunStatus: WorkflowRunStatus? = null
)

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
        val daysOfWeek: Set<Int> = setOf(1), // 1=Mon..7=Sun (ISO)
        val hour: Int = 8,
        val minute: Int = 0
    ) : WorkflowTrigger

    // Future extension point — not implemented in V1 runtime
    @Serializable
    @SerialName("notification")
    data class NotificationKeyword(
        val keywords: List<String> = emptyList()
    ) : WorkflowTrigger
}

// ── Action ──

@Serializable
enum class WorkflowActionType(val displayName: String) {
    FETCH_URL("Fetch URL"),
    PASTE_TEXT("Paste Text")
    // Future: FETCH_RSS, FETCH_API, USE_FILE, USE_CLIPBOARD, USE_NOTIFICATION
}

@Serializable
data class WorkflowAction(
    val id: String, // UUID string
    val type: WorkflowActionType,
    val label: String = "",
    val sourceData: String = "", // URL or pasted text
    val instruction: String = "", // per-action instruction
    val order: Int = 0,
    val isEnabled: Boolean = true
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
    val saveToHistory: Boolean = true
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
    val triggerType: String = "manual", // "manual", "scheduled"
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

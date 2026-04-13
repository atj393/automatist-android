package com.automatist.app.feature.workflow.run

import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus

/**
 * Builds a complete, pasteable error report from a failed workflow run.
 * Used by both the live run screen (from RunUiState) and the run detail screen (from WorkflowRun).
 */
object FullErrorReportBuilder {

    private val timestampFormat = java.text.SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
    )

    private fun formatTs(millis: Long): String = timestampFormat.format(java.util.Date(millis))

    /**
     * Build from the live run screen's UI state (has execution log stages).
     */
    fun fromRunUiState(state: RunUiState): String = buildString {
        appendLine("=== Automatist Full Error Report ===")
        appendLine()

        // ── Run Info ──
        appendLine("Workflow: ${state.templateName}")
        if (state.runId != null) appendLine("Run ID: ${state.runId}")
        appendLine("Status: ${if (state.isFailed) "Failed" else if (state.isCompleted) "Completed" else "Running"}")
        appendLine("Trigger: Manual")
        val now = formatTs(System.currentTimeMillis())
        appendLine("Report generated: $now")
        if (state.durationMs != null) appendLine("Duration: ${formatDurationText(state.durationMs)}")
        appendLine()

        // ── Provider / Profile ──
        if (state.profileName.isNotBlank() || state.providerType != null || state.modelId.isNotBlank()) {
            appendLine("--- Provider ---")
            if (state.profileName.isNotBlank()) appendLine("Profile: ${state.profileName}")
            if (state.providerType != null) appendLine("Provider: ${state.providerType.displayName}")
            if (state.modelId.isNotBlank()) appendLine("Model: ${state.modelId}")
            appendLine()
        }

        // ── Readable Error ──
        if (state.errorMessage != null) {
            appendLine("--- Error ---")
            appendLine(state.errorMessage)
            appendLine()
        }

        // ── Technical Detail ──
        if (!state.errorDetail.isNullOrBlank()) {
            appendLine("--- Technical Details ---")
            appendLine(state.errorDetail)
            appendLine()
        }

        // ── Execution Log ──
        if (state.stages.isNotEmpty()) {
            appendLine("--- Execution Log ---")
            for (stage in state.stages) {
                val icon = when (stage.status) {
                    StageStatus.COMPLETED -> "[OK]"
                    StageStatus.FAILED -> "[FAIL]"
                    StageStatus.RUNNING -> "[...]"
                    StageStatus.PENDING -> "[ ]"
                }
                appendLine("$icon ${stage.label}")
                if (stage.detail.isNotBlank()) {
                    // Indent detail lines
                    for (line in stage.detail.lines()) {
                        appendLine("    $line")
                    }
                }
            }
            appendLine()
        }

        // ── Token Usage ──
        if (state.tokenUsage != null) {
            val u = state.tokenUsage
            val est = if (u.isEstimated) " (estimated)" else ""
            appendLine("--- Token Usage$est ---")
            if (u.promptTokens != null) appendLine("Prompt: ${u.promptTokens}")
            if (u.completionTokens != null) appendLine("Completion: ${u.completionTokens}")
            if (u.totalTokens != null) appendLine("Total: ${u.totalTokens}")
        }
    }

    /**
     * Build from a persisted WorkflowRun record (no execution log stages available).
     */
    fun fromWorkflowRun(r: WorkflowRun): String = buildString {
        appendLine("=== Automatist Full Error Report ===")
        appendLine()

        // ── Run Info ──
        appendLine("Workflow: ${r.templateName}")
        appendLine("Run ID: ${r.id}")
        appendLine("Status: ${when (r.status) {
            WorkflowRunStatus.FAILED -> "Failed"
            WorkflowRunStatus.COMPLETED -> "Completed"
            WorkflowRunStatus.RUNNING -> "Running"
        }}")
        appendLine("Trigger: ${r.triggerType.replaceFirstChar { it.uppercase() }}")
        appendLine("Stage: ${r.currentStage}")
        appendLine("Started: ${formatTs(r.startedAtMillis)}")
        if (r.completedAtMillis != null) appendLine("Completed: ${formatTs(r.completedAtMillis)}")
        if (r.durationMs != null) appendLine("Duration: ${formatDurationText(r.durationMs)}")
        appendLine()

        // ── Provider / Profile ──
        if (r.profileName.isNotBlank() || r.providerType != null || r.modelId.isNotBlank()) {
            appendLine("--- Provider ---")
            if (r.profileName.isNotBlank()) appendLine("Profile: ${r.profileName}")
            if (r.providerType != null) appendLine("Provider: ${r.providerType.displayName}")
            if (r.modelId.isNotBlank()) appendLine("Model: ${r.modelId}")
            appendLine()
        }

        // ── Readable Error ──
        if (r.errorMessage != null) {
            appendLine("--- Error ---")
            appendLine(r.errorMessage)
            appendLine()
        }

        // ── Technical Detail ──
        if (!r.errorDetail.isNullOrBlank()) {
            appendLine("--- Technical Details ---")
            appendLine(r.errorDetail)
            appendLine()
        }

        // ── Token Usage ──
        if (r.promptTokens != null || r.completionTokens != null || r.totalTokens != null) {
            appendLine("--- Token Usage ---")
            if (r.promptTokens != null) appendLine("Prompt: ${r.promptTokens}")
            if (r.completionTokens != null) appendLine("Completion: ${r.completionTokens}")
            if (r.totalTokens != null) appendLine("Total: ${r.totalTokens}")
        }
    }

    private fun formatDurationText(ms: Long): String {
        val seconds = ms / 1000
        return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
    }
}

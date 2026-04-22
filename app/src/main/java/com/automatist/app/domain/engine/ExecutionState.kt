package com.automatist.app.domain.engine

import com.automatist.app.domain.models.OutputVersion
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TokenUsage

sealed interface ExecutionState {

    data object Preparing : ExecutionState

    data class ValidatingInputs(
        val totalActions: Int
    ) : ExecutionState

    data class ActionStarted(
        val actionIndex: Int,
        val totalActions: Int,
        val actionLabel: String,
        val actionType: String
    ) : ExecutionState

    /**
     * Emitted after the action's raw source has been fetched and **before** the
     * pre-source instruction preprocessing AI pass runs. Only emitted when the
     * action has a non-blank instruction — so the UI can show a distinct
     * "running action prompt" stage for actions that actually have a prompt.
     * Actions without an instruction skip this state entirely.
     */
    data class ActionPromptStarted(
        val actionIndex: Int,
        val totalActions: Int,
        val actionLabel: String,
        val instructionPreview: String = "" // short excerpt of the instruction for display
    ) : ExecutionState

    data class ActionCompleted(
        val actionIndex: Int,
        val totalActions: Int,
        val actionLabel: String,
        val resultPreview: String,
        val fullResultText: String = "" // full action output for inspection
    ) : ExecutionState

    data class ActionFailed(
        val actionIndex: Int,
        val totalActions: Int,
        val actionLabel: String,
        val error: String,
        val rawError: String = ""
    ) : ExecutionState

    data class ProcessingStarted(
        val combinedInputLength: Int,
        val originalInputLength: Int = combinedInputLength,
        val compactionMode: String = ""
    ) : ExecutionState

    data class GeneratingOutput(
        val profileName: String,
        val providerName: String = "",
        val modelId: String = ""
    ) : ExecutionState

    data class Completed(
        val outputText: String,
        val providerType: ProviderType,
        val tokenUsage: TokenUsage,
        val durationMs: Long,
        val profileName: String = "",
        val modelId: String = "",
        val isSocialOutput: Boolean = false,
        val versions: List<OutputVersion> = emptyList(),
        val synthesisInput: String = "" // frozen combined input for regeneration
    ) : ExecutionState

    data class Failed(
        val error: String,
        val stage: String,
        val rawError: String = ""
    ) : ExecutionState
}

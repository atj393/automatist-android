package com.automatist.app.domain.engine

import com.automatist.app.domain.models.OutputVersion
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.ResumeSnapshot
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
     * Emitted after an action's raw source has been successfully fetched,
     * **before** any preprocessing AI pass. Carries the raw source text so
     * the run page can attach a "Show source content" expander to the
     * "Reading source" stage row. Only emitted when the action has a
     * non-blank instruction — for plain-fetch actions the raw source and
     * the final per-action result are the same, and get surfaced through
     * the existing "Show result" expander on ActionCompleted.
     */
    data class ActionSourceFetched(
        val actionIndex: Int,
        val totalActions: Int,
        val actionLabel: String,
        val rawSourceText: String = ""
    ) : ExecutionState

    /**
     * Emitted after the action's raw source has been fetched and **before** the
     * pre-source instruction preprocessing AI pass runs. Only emitted when the
     * action has a non-blank instruction — so the UI can show a distinct
     * "running action prompt" stage for actions that actually have a prompt.
     * Actions without an instruction skip this state entirely.
     *
     * [instructionPreview] is the short header-friendly excerpt; [instructionText]
     * is the full instruction text for the expandable "Show prompt" detail.
     */
    data class ActionPromptStarted(
        val actionIndex: Int,
        val totalActions: Int,
        val actionLabel: String,
        val instructionPreview: String = "",
        val instructionText: String = ""
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

    /**
     * [systemPrompt] and [userContent] together describe the full text the
     * provider sees for the final output pass. Exposed here purely for the
     * run page's "Show final prompt" expander — the provider layer is the
     * one that actually sends them to the model.
     */
    data class GeneratingOutput(
        val profileName: String,
        val providerName: String = "",
        val modelId: String = "",
        val systemPrompt: String = "",
        val userContent: String = ""
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
        val rawError: String = "",
        /**
         * Present when the engine has enough intermediate state for a later
         * retry to resume from the first failed step instead of rerunning
         * everything. Currently populated only for processing-stage failures
         * (final generation failed after all actions succeeded).
         */
        val resumeSnapshot: ResumeSnapshot? = null
    ) : ExecutionState
}

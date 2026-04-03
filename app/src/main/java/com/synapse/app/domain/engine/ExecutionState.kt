package com.synapse.app.domain.engine

import com.synapse.app.domain.models.ProviderType
import com.synapse.app.domain.models.TokenUsage

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

    data class ActionCompleted(
        val actionIndex: Int,
        val totalActions: Int,
        val actionLabel: String,
        val resultPreview: String
    ) : ExecutionState

    data class ActionFailed(
        val actionIndex: Int,
        val totalActions: Int,
        val actionLabel: String,
        val error: String
    ) : ExecutionState

    data class ProcessingStarted(
        val combinedInputLength: Int
    ) : ExecutionState

    data class GeneratingOutput(
        val providerName: String
    ) : ExecutionState

    data class Completed(
        val outputText: String,
        val providerType: ProviderType,
        val tokenUsage: TokenUsage,
        val durationMs: Long
    ) : ExecutionState

    data class Failed(
        val error: String,
        val stage: String
    ) : ExecutionState
}

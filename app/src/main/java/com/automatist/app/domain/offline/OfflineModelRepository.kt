package com.automatist.app.domain.offline

import kotlinx.coroutines.flow.Flow

/**
 * Repository for tracking offline AI model availability state.
 *
 * This interface decouples the domain layer from the underlying availability
 * mechanism. For system-managed models (Gemini Nano via AICore), "availability"
 * means probing the Android system service — there is no binary download.
 * For future file-download models, implementations would manage actual downloads.
 *
 * To add support for a new offline model: register it in [OfflineModelCatalog.ALL_MODELS]
 * and call these methods with the new model's ID — no interface changes required.
 */
interface OfflineModelRepository {

    /**
     * Stream the current [OfflineModelStatus] for [modelId].
     * Emits [OfflineModelStatus.NOT_INSTALLED] for unknown/new model IDs.
     */
    fun getModelStatus(modelId: String): Flow<OfflineModelStatus>

    /**
     * Check model availability. For system-managed models (AICore), this probes the
     * system service. For file-download models, this would initiate a download.
     * Transitions the model to [OfflineModelStatus.DOWNLOADING] during the check.
     */
    suspend fun requestDownload(modelId: String)

    /**
     * Cancel an in-progress check/download and reset status to [OfflineModelStatus.NOT_INSTALLED].
     */
    suspend fun cancelDownload(modelId: String)

    /**
     * Reset model status to [OfflineModelStatus.NOT_INSTALLED].
     * For system-managed models, this allows re-running the availability check.
     * For file-download models, implementations should also delete the model file.
     */
    suspend fun removeModel(modelId: String)

    /**
     * Mark a model as [OfflineModelStatus.INSTALLED].
     * Used by availability-check completion handlers and testing utilities.
     */
    suspend fun markInstalled(modelId: String)

    /**
     * Mark a model as [OfflineModelStatus.FAILED].
     * Used when an availability check or download fails unexpectedly.
     */
    suspend fun markFailed(modelId: String)
}

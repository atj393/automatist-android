package com.automatist.app.domain.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Download progress for a single offline model.
 *
 * @param bytesDownloaded Bytes received so far.
 * @param totalBytes Total expected bytes (-1 if unknown, e.g. server didn't send Content-Length).
 */
data class DownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L
) {
    /** Returns a 0–100 percentage, or -1 if total is unknown. */
    val percent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
}

/**
 * Repository for tracking offline AI model availability state.
 *
 * This interface decouples the domain layer from the underlying availability
 * mechanism. For system-managed models (Gemini Nano via AICore), "availability"
 * means probing the Android system service — there is no binary download.
 * For downloadable models, implementations manage the actual HTTP download.
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
     * Stream download progress for [modelId].
     * Only meaningful while status is [OfflineModelStatus.DOWNLOADING] for downloadable models.
     * System-managed models (AICore) do not report progress.
     */
    fun getDownloadProgress(modelId: String): StateFlow<DownloadProgress>

    /**
     * Check model availability or initiate download.
     * For system-managed models (AICore), this probes the system service.
     * For downloadable models, this initiates an HTTP download.
     * Transitions the model to [OfflineModelStatus.DOWNLOADING] during the operation.
     */
    suspend fun requestDownload(modelId: String)

    /**
     * Cancel an in-progress check/download and reset status to [OfflineModelStatus.NOT_INSTALLED].
     */
    suspend fun cancelDownload(modelId: String)

    /**
     * Reset model status to [OfflineModelStatus.NOT_INSTALLED].
     * For system-managed models, this allows re-running the availability check.
     * For downloadable models, also deletes the model file from storage.
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

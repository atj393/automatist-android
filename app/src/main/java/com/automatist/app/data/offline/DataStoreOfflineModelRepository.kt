package com.automatist.app.data.offline

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.automatist.app.domain.offline.DownloadProgress
import com.automatist.app.domain.offline.OfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelStatus
import com.automatist.app.domain.offline.OfflineRuntimeType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.offlineModelDataStore by preferencesDataStore(name = "offline_model_status")

/**
 * DataStore-backed implementation of [OfflineModelRepository].
 *
 * Persists each model's [OfflineModelStatus] as a string keyed by model ID.
 *
 * Dispatches availability checks and downloads based on [OfflineRuntimeType]:
 * - **AICORE**: Probes the Android AICore system service for Gemini Nano availability.
 * - **DOWNLOADABLE**: Downloads model file via [ModelDownloadManager] to app-internal storage.
 *
 * For system-managed models (AICORE):
 * - [removeModel] resets status to NOT_INSTALLED for re-checking (no file to delete).
 *
 * For app-managed models (DOWNLOADABLE):
 * - [removeModel] deletes the model file from storage and resets status.
 */
@Singleton
class DataStoreOfflineModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val offlineModelRegistry: OfflineModelRegistry
) : OfflineModelRepository {

    /** Per-model download progress. Only meaningful during DOWNLOADING state for DOWNLOADABLE models. */
    private val progressFlows = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()

    private fun progressFlow(modelId: String): MutableStateFlow<DownloadProgress> =
        progressFlows.getOrPut(modelId) { MutableStateFlow(DownloadProgress()) }

    override fun getDownloadProgress(modelId: String): StateFlow<DownloadProgress> =
        progressFlow(modelId)

    private fun statusKey(modelId: String) = stringPreferencesKey("model_status_$modelId")

    override fun getModelStatus(modelId: String): Flow<OfflineModelStatus> =
        context.offlineModelDataStore.data.map { prefs ->
            val raw = prefs[statusKey(modelId)]
            raw?.let { runCatching { OfflineModelStatus.valueOf(it) }.getOrNull() }
                ?: OfflineModelStatus.NOT_INSTALLED
        }

    /**
     * Initiates availability check or download for the specified model.
     *
     * Dispatches based on the model's [OfflineRuntimeType]:
     * - **AICORE**: Probes the AICore system service for Gemini Nano availability.
     * - **DOWNLOADABLE**: Downloads the model file via [ModelDownloadManager].
     *
     * Sets status to [OfflineModelStatus.DOWNLOADING] immediately so the UI shows progress,
     * then updates to the final status when the operation completes.
     */
    override suspend fun requestDownload(modelId: String) {
        val entry = offlineModelRegistry.findById(modelId)
            ?: run {
                setStatus(modelId, OfflineModelStatus.FAILED)
                return
            }
        // Initialize progress with the known size so the UI can show a determinate
        // progress bar immediately (before the first HTTP chunk arrives).
        val knownTotal = if (entry.downloadSizeBytes > 0) entry.downloadSizeBytes else -1L
        progressFlow(modelId).value = DownloadProgress(bytesDownloaded = 0, totalBytes = knownTotal)
        setStatus(modelId, OfflineModelStatus.DOWNLOADING)
        try {
            val result = when (entry.runtimeType) {
                OfflineRuntimeType.DOWNLOADABLE -> {
                    if (entry.downloadUrl == null) {
                        OfflineModelStatus.FAILED
                    } else {
                        modelDownloadManager.downloadModel(entry) { bytesDownloaded, totalBytes ->
                            progressFlow(modelId).value = DownloadProgress(bytesDownloaded, totalBytes)
                        }
                    }
                }
                OfflineRuntimeType.AICORE -> checkAICoreAvailability()
            }
            setStatus(modelId, result)
        } catch (e: CancellationException) {
            // Use NonCancellable so the DataStore write completes even though
            // the coroutine is cancelled (e.g. user tapped Cancel).
            withContext(NonCancellable) {
                progressFlow(modelId).value = DownloadProgress()
                setStatus(modelId, OfflineModelStatus.NOT_INSTALLED)
            }
            throw e
        }
    }

    override suspend fun cancelDownload(modelId: String) {
        // Cancel the active OkHttp Call so network I/O stops immediately.
        val entry = offlineModelRegistry.findById(modelId)
        if (entry?.runtimeType == OfflineRuntimeType.DOWNLOADABLE) {
            modelDownloadManager.cancelActiveDownload(entry)
        }
        progressFlow(modelId).value = DownloadProgress()
        setStatus(modelId, OfflineModelStatus.NOT_INSTALLED)
    }

    /**
     * Removes a model and resets its status to [OfflineModelStatus.NOT_INSTALLED].
     *
     * For system-managed models (AICORE): no file to delete, just resets status.
     * For downloadable models (DOWNLOADABLE): deletes the model file from storage, then resets status.
     */
    override suspend fun removeModel(modelId: String) {
        val entry = offlineModelRegistry.findById(modelId)
        if (entry?.runtimeType == OfflineRuntimeType.DOWNLOADABLE) {
            withContext(Dispatchers.IO) {
                modelDownloadManager.deleteModel(entry)
            }
        }
        setStatus(modelId, OfflineModelStatus.NOT_INSTALLED)
    }

    override suspend fun markInstalled(modelId: String) {
        setStatus(modelId, OfflineModelStatus.INSTALLED)
    }

    override suspend fun markFailed(modelId: String) {
        setStatus(modelId, OfflineModelStatus.FAILED)
    }

    private companion object {
        /** Timeout for the AICore availability check (service binding probe). */
        const val AICORE_CHECK_TIMEOUT_MS = 30_000L
    }

    /**
     * Probes the AICore system service for Gemini Nano availability.
     *
     * Short-circuits to [OfflineModelStatus.UNSUPPORTED] on Android < 14 without loading any
     * AICore classes — the `tools:overrideLibrary` in the manifest is safe precisely because
     * this guard prevents any AICore code from running on unsupported OS versions.
     *
     * Creates a short-lived [GenerativeModel] solely to call [GenerativeModel.prepareInferenceEngine].
     * Closes the model in a `finally` block regardless of outcome.
     *
     * Uses [withTimeoutOrNull] to avoid the `TimeoutCancellationException`-vs-real-cancellation
     * ambiguity that arises with [withTimeout] and `catch (CancellationException)`.
     */
    private suspend fun checkAICoreAvailability(): OfflineModelStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return OfflineModelStatus.UNSUPPORTED
        }
        return withContext(Dispatchers.IO) {
            val config = generationConfig {
                context = this@DataStoreOfflineModelRepository.context
            }
            val model = GenerativeModel(config)
            try {
                val completed = withTimeoutOrNull(AICORE_CHECK_TIMEOUT_MS) {
                    model.prepareInferenceEngine()
                    true
                }
                if (completed == true) OfflineModelStatus.INSTALLED else OfflineModelStatus.FAILED
            } catch (e: GenerativeAIException) {
                OfflineModelStatus.UNSUPPORTED
            } catch (e: CancellationException) {
                throw e // Real coroutine cancellation — propagate
            } catch (e: Exception) {
                OfflineModelStatus.FAILED
            } finally {
                model.close()
            }
        }
    }

    private suspend fun setStatus(modelId: String, status: OfflineModelStatus) {
        context.offlineModelDataStore.edit { prefs ->
            prefs[statusKey(modelId)] = status.name
        }
    }
}

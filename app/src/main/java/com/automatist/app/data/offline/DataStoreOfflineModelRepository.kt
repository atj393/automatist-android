package com.automatist.app.data.offline

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.automatist.app.domain.offline.OfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
 * For Gemini Nano via Android AICore (the current single offline model):
 * - [requestDownload] performs an AICore availability check via [GenerativeModel.prepareInferenceEngine].
 * - Requires Android 14+ (API 34) and a supported device (Pixel 8+, Galaxy S24+).
 * - On unsupported devices or Android < 14, sets [OfflineModelStatus.UNSUPPORTED] without
 *   touching the AICore library at all.
 * - [removeModel] resets status to [OfflineModelStatus.NOT_INSTALLED] for re-checking.
 *   (Gemini Nano is system-managed; there is no app-owned binary to delete.)
 */
@Singleton
class DataStoreOfflineModelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : OfflineModelRepository {

    private fun statusKey(modelId: String) = stringPreferencesKey("model_status_$modelId")

    override fun getModelStatus(modelId: String): Flow<OfflineModelStatus> =
        context.offlineModelDataStore.data.map { prefs ->
            val raw = prefs[statusKey(modelId)]
            raw?.let { runCatching { OfflineModelStatus.valueOf(it) }.getOrNull() }
                ?: OfflineModelStatus.NOT_INSTALLED
        }

    /**
     * Checks whether Gemini Nano is available on this device via Android AICore.
     *
     * Sequence:
     * 1. Sets status to [OfflineModelStatus.DOWNLOADING] ("Checking…" in the UI).
     * 2. Guards against Android < 14 — AICore requires API 34.
     * 3. Calls [GenerativeModel.prepareInferenceEngine] which connects to the AICore system
     *    service. If the service binds and the model is ready, the call returns successfully.
     *    If not, it throws [GenerativeAIException].
     * 4. Maps the result to [OfflineModelStatus] and persists it.
     *
     * Result codes:
     * - [OfflineModelStatus.INSTALLED]    — AICore responded and Gemini Nano is ready.
     * - [OfflineModelStatus.UNSUPPORTED]  — AICore is not available or model is not on device.
     * - [OfflineModelStatus.FAILED]       — Unexpected non-AICore exception during the check.
     */
    override suspend fun requestDownload(modelId: String) {
        // Transition to DOWNLOADING immediately so UI shows "Checking…" spinner
        setStatus(modelId, OfflineModelStatus.DOWNLOADING)
        try {
            val result = checkAICoreAvailability()
            setStatus(modelId, result)
        } catch (e: CancellationException) {
            // Coroutine was cancelled (e.g. ViewModel cleared) while check was in progress.
            // Reset to NOT_INSTALLED so the UI doesn't get stuck on "Checking…" forever.
            setStatus(modelId, OfflineModelStatus.NOT_INSTALLED)
            throw e
        }
    }

    override suspend fun cancelDownload(modelId: String) {
        setStatus(modelId, OfflineModelStatus.NOT_INSTALLED)
    }

    /**
     * Resets the model status to [OfflineModelStatus.NOT_INSTALLED].
     *
     * For system-managed models (Gemini Nano via AICore), there is no app-owned file to delete.
     * Resetting allows the user to re-run the availability check from a clean state.
     */
    override suspend fun removeModel(modelId: String) {
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

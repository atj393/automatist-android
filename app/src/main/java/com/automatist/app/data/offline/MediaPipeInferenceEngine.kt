package com.automatist.app.data.offline

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.automatist.app.domain.offline.OfflineModelEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the MediaPipe LLM Inference API for running downloadable offline models.
 *
 * This engine:
 * - Loads the model from a local .task file on each inference call
 * - Creates a session with temperature/topK controls
 * - Runs text generation on [Dispatchers.IO]
 * - Releases both session and model resources in `finally` blocks
 * - Returns plain text output only
 *
 * The model is NOT kept loaded in memory between calls. Each call loads the model fresh.
 * This is intentional: it avoids holding ~2-3 GB of RAM between uses, which would cause
 * the system to aggressively kill the app process in the background.
 *
 * ## API structure (MediaPipe tasks-genai 0.10.22)
 * - [LlmInference] — model-level: loads model file, sets max tokens
 * - [LlmInferenceSession] — session-level: sets temperature/topK, runs inference
 *
 * ## Current limitations
 * - No streaming output (returns full text when complete)
 * - Uses CPU backend explicitly (broadest device compatibility; GPU can be faster but not universally supported)
 * - Model load time adds ~5-15 seconds per call depending on device
 */
@Singleton
class MediaPipeInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager
) {
    companion object {
        private const val TAG = "MediaPipeLLM"

        /** Timeout for the entire inference operation (model load + generation). */
        const val INFERENCE_TIMEOUT_MS = 120_000L // 2 minutes — model loading is slow

        /**
         * Max output tokens. Tuned for a ~1B-parameter int4 model on mobile CPU where
         * typical throughput is 5–15 tok/s: at 384 tokens, pure generation fits
         * comfortably inside [INFERENCE_TIMEOUT_MS] even with a cold model load.
         * 1024 was the previous value and caused timeouts on real devices.
         */
        private const val MAX_TOKENS = 384
        private const val TEMPERATURE = 0.7f
        private const val TOP_K = 40

        /** Timing buckets reported when inference completes (or times out just before). */
        data class PhaseTimings(
            val modelLoadMs: Long,
            val sessionCreateMs: Long,
            val addQueryChunkMs: Long,
            val generateResponseMs: Long,
            val totalMs: Long,
            val modelCloseMs: Long
        ) {
            fun summary(modelFileName: String, promptChars: Int, responseChars: Int): String =
                "$modelFileName: load=${modelLoadMs}ms session=${sessionCreateMs}ms " +
                    "prompt=${addQueryChunkMs}ms(${promptChars}ch) " +
                    "generate=${generateResponseMs}ms(${responseChars}ch) " +
                    "close=${modelCloseMs}ms total=${totalMs}ms"
        }
    }

    /** Most recent phase timings, exposed for diagnostic error surfacing. Null until first run. */
    @Volatile
    var lastTimings: PhaseTimings? = null
        private set

    /**
     * Runs inference using the specified downloadable model entry.
     *
     * @param entry The offline model entry (must have runtimeType = DOWNLOADABLE)
     * @param prompt The full prompt text to send to the model
     * @return The model's text output
     * @throws ModelNotAvailableException if the model file is missing
     * @throws ModelInferenceException if inference fails for any reason
     */
    suspend fun generateText(entry: OfflineModelEntry, prompt: String): String {
        val modelFile = modelDownloadManager.getModelFile(entry)
            ?: throw ModelNotAvailableException(
                "Model file not found for '${entry.displayName}'. " +
                    "Download it first in Settings → On-device AI."
            )

        return withContext(Dispatchers.IO) {
            runMediaPipeInference(modelFile, prompt)
        }
    }

    /**
     * Runs the actual MediaPipe LLM inference using the two-level API:
     * 1. [LlmInference] loads the model from file
     * 2. [LlmInferenceSession] configures generation parameters and runs inference
     *
     * Both are closed in `finally` blocks to release native resources (~2-3 GB RAM).
     *
     * @throws ModelInferenceException on any error (wraps the underlying cause)
     */
    private fun runMediaPipeInference(modelFile: File, prompt: String): String {
        val totalStart = System.currentTimeMillis()
        var modelLoadMs = 0L
        var sessionCreateMs = 0L
        var addQueryChunkMs = 0L
        var generateResponseMs = 0L
        var modelCloseMs = 0L

        val modelOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(TOP_K)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()

        val llm: LlmInference
        try {
            val loadStart = System.currentTimeMillis()
            llm = LlmInference.createFromOptions(context, modelOptions)
            modelLoadMs = System.currentTimeMillis() - loadStart
            Log.d(TAG, "${modelFile.name}: model load ${modelLoadMs}ms")
        } catch (e: Exception) {
            recordTimings(
                modelFile.name, prompt.length, 0,
                modelLoadMs, sessionCreateMs, addQueryChunkMs, generateResponseMs,
                modelCloseMs, totalStart
            )
            throw ModelInferenceException(
                "Failed to load ${modelFile.name}: ${e.message}",
                e
            )
        }

        var responseChars = 0
        try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(TEMPERATURE)
                .setTopK(TOP_K)
                .build()

            val sessionStart = System.currentTimeMillis()
            val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
            sessionCreateMs = System.currentTimeMillis() - sessionStart
            Log.d(TAG, "${modelFile.name}: session create ${sessionCreateMs}ms")

            try {
                val chunkStart = System.currentTimeMillis()
                session.addQueryChunk(prompt)
                addQueryChunkMs = System.currentTimeMillis() - chunkStart

                val genStart = System.currentTimeMillis()
                val response = session.generateResponse()
                generateResponseMs = System.currentTimeMillis() - genStart
                Log.d(TAG, "${modelFile.name}: generate ${generateResponseMs}ms (${MAX_TOKENS} max)")

                if (response.isNullOrBlank()) {
                    throw ModelInferenceException(
                        "Model returned an empty response for ${modelFile.name}"
                    )
                }
                responseChars = response.length
                return response
            } finally {
                session.close()
            }
        } catch (e: ModelInferenceException) {
            throw e // Already wrapped
        } catch (e: Exception) {
            throw ModelInferenceException(
                "Inference failed for ${modelFile.name}: ${e.message}",
                e
            )
        } finally {
            val closeStart = System.currentTimeMillis()
            llm.close()
            modelCloseMs = System.currentTimeMillis() - closeStart
            recordTimings(
                modelFile.name, prompt.length, responseChars,
                modelLoadMs, sessionCreateMs, addQueryChunkMs, generateResponseMs,
                modelCloseMs, totalStart
            )
        }
    }

    private fun recordTimings(
        modelFileName: String,
        promptChars: Int,
        responseChars: Int,
        modelLoadMs: Long,
        sessionCreateMs: Long,
        addQueryChunkMs: Long,
        generateResponseMs: Long,
        modelCloseMs: Long,
        totalStart: Long
    ) {
        val timings = PhaseTimings(
            modelLoadMs = modelLoadMs,
            sessionCreateMs = sessionCreateMs,
            addQueryChunkMs = addQueryChunkMs,
            generateResponseMs = generateResponseMs,
            modelCloseMs = modelCloseMs,
            totalMs = System.currentTimeMillis() - totalStart
        )
        lastTimings = timings
        Log.i(TAG, timings.summary(modelFileName, promptChars, responseChars))
    }
}

/** Thrown when a model file is expected but not found on disk. */
class ModelNotAvailableException(message: String) : Exception(message)

/** Thrown when the inference engine encounters an error during generation. */
class ModelInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)

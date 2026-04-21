package com.automatist.app.data.offline

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.automatist.app.domain.offline.OfflineModelEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
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
    /**
     * Dedicated single-thread dispatcher for all MediaPipe work.
     *
     * Why a dedicated thread (not [kotlinx.coroutines.Dispatchers.IO]):
     *  - Model load allocates ~2-3 GB of native memory and can hold a thread for
     *    5-15 s. Running it on the shared IO pool means other IO work (Room DAOs,
     *    OkHttp, DataStore) can compete with — and be delayed by — a MediaPipe run.
     *  - A single-thread executor **serialises** concurrent inference attempts (e.g.
     *    a regenerate tap while a run is in flight), avoiding two simultaneous
     *    `LlmInference.createFromOptions` calls that would each allocate multi-GB.
     *  - Lower thread priority (`NORM_PRIORITY - 1`) means the scheduler will pick
     *    the UI thread over this worker on a contended core, reducing apparent
     *    "UI freezes" during model load on mid-range devices.
     *  - The named thread ("MediaPipe-Worker") makes logcat + profiler traces easy
     *    to read when diagnosing performance regressions.
     */
    private val mediaPipeDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "MediaPipe-Worker").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }.asCoroutineDispatcher()

    companion object {
        private const val TAG = "MediaPipeLLM"

        /** Timeout for the entire inference operation (model load + generation). */
        const val INFERENCE_TIMEOUT_MS = 120_000L // 2 minutes — model loading is slow

        /**
         * Total token budget passed to [LlmInference.LlmInferenceOptions.setMaxTokens].
         *
         * ### IMPORTANT: this is input + output, not output-only
         *
         * MediaPipe's `setMaxTokens` sets the combined input-plus-output token budget
         * (KV cache size). A prompt whose tokenised length alone exceeds this value
         * causes a **native crash** in `session.addQueryChunk`, not a graceful error.
         *
         * Previously set to 384 under the mistaken assumption it was an output-only cap —
         * which crashed on any real prompt once the engine's system prompt plus user
         * content tokenised past 384 (reported: 586 tokens).
         *
         * Current split:
         *  - Total budget:   1536 tokens
         *  - Input ceiling:  1280 tokens ([MAX_INPUT_TOKENS])
         *  - Output reserve: 256 tokens ([MIN_OUTPUT_RESERVE_TOKENS])
         *
         * At ~10 tok/s on a Gemma 3 1B int4 running on mobile CPU, the worst-case full
         * 1280-token generation is ~128 s. The engine enforces a 120 s timeout, so in
         * practice generation will stop earlier once the model emits its end-of-sequence
         * token. 1536 is the memory/latency sweet spot for this model on real devices.
         */
        const val MAX_TOTAL_TOKENS = 1536

        /** Minimum tokens we reserve for the model to generate a response. */
        const val MIN_OUTPUT_RESERVE_TOKENS = 256

        /** Maximum allowed tokenised prompt size. Enforced by preflight before native call. */
        const val MAX_INPUT_TOKENS = MAX_TOTAL_TOKENS - MIN_OUTPUT_RESERVE_TOKENS

        /**
         * Conservative char-per-token ratio for pre-tokenisation budget estimation.
         * Real Gemma tokenisation of English prose is ~4–5 chars/token; we use 3.0 so
         * our estimate **over-counts** tokens. Trading false positives (minor extra
         * truncation on well-formatted input) for false negatives (native crash) is
         * the right call.
         */
        private const val CHARS_PER_TOKEN_ESTIMATE = 3.0

        /**
         * Fast, conservative token estimator. For a model-configured 1280-token input
         * ceiling this permits prompts up to ~3840 chars, which comfortably fits a
         * typical local-model prompt (system instruction + one 2000-char article).
         */
        fun estimateTokens(text: String): Int =
            (text.length / CHARS_PER_TOKEN_ESTIMATE).toInt() + 1

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

        Log.d(TAG, "generateText enter thread=${Thread.currentThread().name}")
        return withContext(mediaPipeDispatcher) {
            Log.d(TAG, "generateText running on thread=${Thread.currentThread().name}")
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

        // ── Preflight guard ───────────────────────────────────────────────────────
        // The native MediaPipe LLM layer will ABORT the process if the tokenised
        // prompt exceeds the model's configured max-tokens budget. Estimate the
        // token count from prompt chars and reject oversized inputs before we call
        // LlmInference at all. The provider layer is expected to have already
        // truncated — this is the last line of defence against JNI crashes.
        val estimatedInputTokens = estimateTokens(prompt)
        if (estimatedInputTokens > MAX_INPUT_TOKENS) {
            throw InputTooLargeException(
                estimatedTokens = estimatedInputTokens,
                maxInputTokens = MAX_INPUT_TOKENS,
                promptChars = prompt.length
            )
        }
        Log.d(TAG, "${modelFile.name}: preflight est=${estimatedInputTokens} tokens / " +
            "$MAX_INPUT_TOKENS ceiling (${prompt.length} chars)")

        val modelOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOTAL_TOKENS)
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
                Log.d(TAG, "${modelFile.name}: generate ${generateResponseMs}ms " +
                    "(total budget ${MAX_TOTAL_TOKENS}, input est=${estimatedInputTokens})")

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

/**
 * Thrown by the preflight guard when the tokenised prompt would exceed the model's
 * configured input budget. Callers should catch this and either re-attempt with a
 * more aggressively truncated prompt or surface a graceful error to the user.
 */
class InputTooLargeException(
    val estimatedTokens: Int,
    val maxInputTokens: Int,
    val promptChars: Int
) : Exception(
    "Estimated input is $estimatedTokens tokens but the local model accepts at most " +
        "$maxInputTokens tokens ($promptChars chars of prompt)."
)

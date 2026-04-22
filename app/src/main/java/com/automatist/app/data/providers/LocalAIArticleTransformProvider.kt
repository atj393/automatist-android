package com.automatist.app.data.providers

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.automatist.app.data.offline.InputTooLargeException
import com.automatist.app.data.offline.MediaPipeInferenceEngine
import com.automatist.app.data.offline.ModelInferenceException
import com.automatist.app.data.offline.ModelNotAvailableException
import com.automatist.app.domain.engine.DiagnosticException
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelEntry
import com.automatist.app.domain.offline.OfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelStatus
import com.automatist.app.domain.offline.OfflineRuntimeType
import com.automatist.app.domain.providers.ArticleTransformProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local AI provider that dispatches to the correct inference runtime based on model metadata.
 *
 * Supports two runtime paths under a single [ProviderType.LOCAL_AI]:
 *
 * ## A. AICore / Gemini Nano (system-managed)
 * - Android 14+ (API 34), Pixel 8+ / Galaxy S24+
 * - No app-managed model download; system-managed by Android
 * - Inference via [GenerativeModel.generateContent]
 *
 * ## B. MediaPipe LLM Inference (downloadable)
 * - Android 8+ (API 26), broader device support
 * - App-managed model file downloaded by user from Settings
 * - Inference via [MediaPipeInferenceEngine]
 *
 * ## Dispatch logic
 * The selected model ID (from the profile's `modelId` field) determines which runtime is used.
 * The model's [OfflineModelEntry.runtimeType] controls the dispatch:
 * - [OfflineRuntimeType.AICORE] → [runAICoreInference]
 * - [OfflineRuntimeType.DOWNLOADABLE] → [runDownloadableInference]
 *
 * ## Prompt safety
 * Both paths use the same [buildPrompt] method with delimiter-wrapped untrusted content,
 * per-model character caps, and plain-text-only output.
 *
 * ## Cloud provider isolation
 * This class is only invoked when the active profile's ProviderType is LOCAL_AI.
 * All other provider paths are completely unaffected.
 */
@Singleton
class LocalAIArticleTransformProvider @Inject constructor(
    private val offlineModelRepository: OfflineModelRepository,
    private val mediaPipeInferenceEngine: MediaPipeInferenceEngine,
    @ApplicationContext private val context: Context
) : ArticleTransformProvider {

    /**
     * Local-path stage-boundary reset. Clears the MediaPipe engine's transient
     * diagnostic state so error surfaces for the *current* call never read
     * phase timings from a previous, unrelated run. Does not touch in-flight
     * native resources — model/session are always allocated per-call anyway.
     *
     * The AICore path is stateless (the provider creates a fresh [GenerativeModel]
     * on each [runAICoreInference] call and closes it in `finally`), so there is
     * nothing to reset for that path.
     */
    override suspend fun resetForNewStage(reason: String) {
        Log.i(TAG, "resetForNewStage reason=$reason")
        mediaPipeInferenceEngine.resetTransientState(reason)
    }

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        val modelId = input.modelOverride ?: OfflineModelCatalog.GEMINI_NANO_ID
        val entry = OfflineModelCatalog.findById(modelId)
        val status = offlineModelRepository.getModelStatus(modelId).first()

        return when (status) {
            OfflineModelStatus.NOT_INSTALLED -> {
                val action = if (entry?.isSystemManaged == true) "Check Availability" else "Download"
                Result.failure(
                    DiagnosticException(
                        message = "${entry?.displayName ?: "On-device AI model"} is not yet available. " +
                            "Open Settings → On-device AI and tap \"$action\" first.",
                        rawDetail = "Model '$modelId' status: NOT_INSTALLED"
                    )
                )
            }

            OfflineModelStatus.FAILED -> {
                val action = if (entry?.isSystemManaged == true) "Retry check" else "Retry download"
                Result.failure(
                    DiagnosticException(
                        message = "The ${entry?.displayName ?: "on-device AI"} setup failed. " +
                            "Open Settings → On-device AI and tap \"$action\" to try again.",
                        rawDetail = "Model '$modelId' status: FAILED"
                    )
                )
            }

            OfflineModelStatus.DOWNLOADING -> {
                val action = if (entry?.isSystemManaged == true) "checking availability" else "downloading"
                Result.failure(
                    DiagnosticException(
                        message = "${entry?.displayName ?: "On-device AI"} is still $action. " +
                            "Please wait a moment and try again.",
                        rawDetail = "Model '$modelId' status: DOWNLOADING"
                    )
                )
            }

            OfflineModelStatus.UNSUPPORTED -> {
                val reason = if (entry?.isSystemManaged == true) {
                    "It requires Android 14+ and a compatible Pixel 8+ or Galaxy S24+ device."
                } else {
                    "This device does not meet the minimum requirements."
                }
                Result.failure(
                    DiagnosticException(
                        message = "${entry?.displayName ?: "On-device AI"} is not supported on this device. " +
                            "$reason Cloud-based AI profiles work on all devices.",
                        rawDetail = "Model '$modelId' status: UNSUPPORTED " +
                            "(Android API ${Build.VERSION.SDK_INT})"
                    )
                )
            }

            OfflineModelStatus.INSTALLED -> {
                when (entry?.runtimeType) {
                    OfflineRuntimeType.DOWNLOADABLE -> runDownloadableInference(input, type, modelId, entry)
                    OfflineRuntimeType.AICORE, null -> runAICoreInference(input, type, modelId, entry)
                }
            }
        }
    }

    // companion object is declared at the bottom of the class (Kotlin only allows one
    // per class) — see LOCAL_INSTRUCTION_CHARS et al. below.

    /**
     * Runs inference using a downloadable model via the [MediaPipeInferenceEngine].
     */
    private suspend fun runDownloadableInference(
        input: ArticleInput,
        type: TransformType,
        modelId: String,
        entry: OfflineModelEntry
    ): Result<TransformResult> {
        // Defence-in-depth dispatcher switch. The engine path arrives here already on
        // Dispatchers.IO (via .flowOn(IO)), but the regenerate path enters on Main
        // (viewModelScope.launch → engine.regenerate → transform directly). Inference
        // work must never run on Main, so we force a switch here regardless of caller.
        return withContext(Dispatchers.Default) {
            Log.d(TAG, "runDownloadableInference entry thread=${Thread.currentThread().name} modelId=$modelId")
            runDownloadableInferenceInternal(input, type, modelId, entry)
        }
    }

    private suspend fun runDownloadableInferenceInternal(
        input: ArticleInput,
        type: TransformType,
        modelId: String,
        entry: OfflineModelEntry
    ): Result<TransformResult> {
        return try {
            // Track whether any truncation happened before the native call so we can
            // honestly report it in provider details. Three cases count as "truncated":
            //   (a) raw input text longer than the model's contextWindowChars
            //   (b) systemPromptOverride longer than LocalPromptBuilder.INSTRUCTION_CHARS
            //   (c) the retry path below fired (first build overshot the token ceiling)
            val contentTruncated = input.text.length > entry.contextWindowChars
            val instructionTruncated = (input.systemPromptOverride?.length ?: 0) >
                LocalPromptBuilder.INSTRUCTION_CHARS
            var retriedAfterOvershoot = false

            // Build once with normal sizing. LocalPromptBuilder is input-budget-aware
            // for the downloadable path: it shrinks the system instruction and content
            // caps so the estimated tokenised prompt fits MAX_INPUT_TOKENS on the
            // first attempt for typical inputs.
            var prompt = LocalPromptBuilder.buildForMediaPipe(input, type, entry)

            // Provider-side preflight. If the first attempt overshoots the input
            // budget (e.g. unusually dense non-English text that tokenises tighter
            // than our 3 chars/token heuristic assumes), rebuild once with even
            // tighter content truncation before calling native inference.
            if (MediaPipeInferenceEngine.estimateTokens(prompt) > MediaPipeInferenceEngine.MAX_INPUT_TOKENS) {
                Log.w(TAG, "Prompt over budget on first build (${prompt.length} chars); retruncating")
                prompt = LocalPromptBuilder.buildForMediaPipe(
                    input.copy(text = input.text.take(LocalPromptBuilder.FALLBACK_CONTENT_CHARS)),
                    type,
                    entry,
                    instructionCap = LocalPromptBuilder.FALLBACK_INSTRUCTION_CHARS
                )
                retriedAfterOvershoot = true
            }

            val wasTruncated = contentTruncated || instructionTruncated || retriedAfterOvershoot

            val result = withTimeoutOrNull(MediaPipeInferenceEngine.INFERENCE_TIMEOUT_MS) {
                mediaPipeInferenceEngine.generateText(entry, prompt)
            }
            if (result == null) {
                // Surface phase breakdown from the last (partial) run so the user sees
                // whether time was spent loading the model, prefilling, or generating.
                val timingSummary = mediaPipeInferenceEngine.lastTimings
                    ?.summary(entry.modelFileName ?: "local", prompt.length, 0)
                    ?: "no phase timings recorded"
                return Result.failure(
                    DiagnosticException(
                        message = "${entry.displayName} took too long to respond on this device. " +
                            "Try again with shorter input, or switch to a cloud-based AI profile " +
                            "for faster results.",
                        rawDetail = "MediaPipe inference timed out after " +
                            "${MediaPipeInferenceEngine.INFERENCE_TIMEOUT_MS}ms for '$modelId' " +
                            "(prompt=${prompt.length}ch). Last timings: $timingSummary"
                    )
                )
            }
            val outputText = result.trim()
            if (outputText.isBlank()) {
                Result.failure(
                    DiagnosticException(
                        message = "${entry.displayName} returned an empty response. Please try again.",
                        rawDetail = "MediaPipe inference returned blank output for '$modelId'"
                    )
                )
            } else {
                // Honest usage accounting. MediaPipe doesn't return authoritative
                // token counts; we estimate from char length using the same heuristic
                // the engine's preflight uses, and mark the result as estimated so
                // the UI can surface that clearly.
                val estPromptTokens = MediaPipeInferenceEngine.estimateTokens(prompt)
                val estCompletionTokens = MediaPipeInferenceEngine.estimateTokens(outputText)
                Result.success(
                    TransformResult(
                        outputText = outputText,
                        transformType = type,
                        providerType = ProviderType.LOCAL_AI,
                        promptTokens = estPromptTokens,
                        completionTokens = estCompletionTokens,
                        isUsageEstimated = true,
                        inputChars = prompt.length,
                        outputChars = outputText.length,
                        wasTruncated = wasTruncated,
                        contextCeilingTokens = MediaPipeInferenceEngine.MAX_INPUT_TOKENS
                    )
                )
            }
        } catch (e: ModelNotAvailableException) {
            Result.failure(
                DiagnosticException(
                    message = "${entry.displayName} model file is missing. " +
                        "Open Settings → On-device AI and download it again.",
                    rawDetail = "Model file missing for '$modelId': ${e.message}"
                )
            )
        } catch (e: InputTooLargeException) {
            // Last-line-of-defence — the engine's preflight caught an oversized
            // prompt despite the provider's own preflight. Never allowed to reach
            // native inference. Surface a clean, actionable message to the user.
            Result.failure(
                DiagnosticException(
                    message = "${entry.displayName} can't process inputs this long on this " +
                        "device. Shorten the source material (the on-device model accepts " +
                        "roughly one long article at a time), or switch to a cloud-based " +
                        "AI profile for longer inputs.",
                    rawDetail = "Input too large for local model '$modelId': " +
                        "estimated ${e.estimatedTokens} tokens exceeds ceiling ${e.maxInputTokens} " +
                        "(prompt=${e.promptChars} chars)"
                )
            )
        } catch (e: ModelInferenceException) {
            Result.failure(
                DiagnosticException(
                    message = "${entry.displayName} failed to process your request. " +
                        "Try again or switch to a cloud-based AI profile.",
                    rawDetail = "MediaPipe inference error for '$modelId': ${e.message}"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                DiagnosticException(
                    message = "${entry.displayName} encountered an unexpected error. " +
                        "Try again or switch to a cloud-based AI profile.",
                    rawDetail = "Unexpected error during MediaPipe inference for '$modelId' [${e::class.simpleName}]: ${e.message}"
                )
            )
        }
    }

    /**
     * Runs Gemini Nano inference via the Android AICore system service.
     */
    private suspend fun runAICoreInference(
        input: ArticleInput,
        type: TransformType,
        modelId: String,
        entry: OfflineModelEntry?
    ): Result<TransformResult> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return Result.failure(
                DiagnosticException(
                    message = "On-device AI requires Android 14 or later. " +
                        "Cloud-based AI profiles work on all devices.",
                    rawDetail = "AICore inference blocked: Android API ${Build.VERSION.SDK_INT} < 34"
                )
            )
        }

        return withContext(Dispatchers.IO) {
            val config = generationConfig {
                context = this@LocalAIArticleTransformProvider.context
                temperature = 0.7f
                topK = 40
                maxOutputTokens = 1024
            }
            val model = GenerativeModel(config)
            try {
                val result = withTimeoutOrNull(AICORE_TIMEOUT_MS) {
                    model.prepareInferenceEngine()

                    val prompt = buildPrompt(input, type, entry)
                    val response = model.generateContent(prompt)
                    val outputText = response.text?.trim()
                        ?: return@withTimeoutOrNull Result.failure(
                            DiagnosticException(
                                message = "On-device AI returned an empty response. Please try again.",
                                rawDetail = "AICore GenerateContentResponse.text was null or blank for '$modelId'"
                            )
                        )
                    Result.success(
                        TransformResult(
                            outputText = outputText,
                            transformType = type,
                            providerType = ProviderType.LOCAL_AI
                        )
                    )
                }
                result ?: Result.failure(
                    DiagnosticException(
                        message = "On-device AI took too long to respond. " +
                            "Try again or switch to a cloud-based AI profile.",
                        rawDetail = "AICore inference timed out after ${AICORE_TIMEOUT_MS}ms for '$modelId'"
                    )
                )
            } catch (e: GenerativeAIException) {
                Result.failure(
                    DiagnosticException(
                        message = "On-device AI failed to process your request. " +
                            "Try again or switch to a cloud-based AI profile.",
                        rawDetail = "AICore inference error [${e::class.simpleName}] code=${e.errorCode}: ${e.message}"
                    )
                )
            } catch (e: CancellationException) {
                throw e // Real coroutine cancellation — propagate
            } catch (e: Exception) {
                Result.failure(
                    DiagnosticException(
                        message = "On-device AI encountered an unexpected error. " +
                            "Try again or switch to a cloud-based AI profile.",
                        rawDetail = "Non-AICore error during inference [${e::class.simpleName}]: ${e.message}"
                    )
                )
            } finally {
                model.close()
            }
        }
    }

    /** Prompt for the AICore (Gemini Nano) path. Delegates to the shared builder. */
    private fun buildPrompt(input: ArticleInput, type: TransformType, entry: OfflineModelEntry?): String =
        LocalPromptBuilder.buildForAICore(input, type, entry)

    private companion object {
        private const val TAG = "LocalAIProvider"

        /** Timeout for AICore inference (prepare + generate). */
        const val AICORE_TIMEOUT_MS = 60_000L
    }
}

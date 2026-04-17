package com.automatist.app.data.providers

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
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

    private companion object {
        /** Timeout for AICore inference (prepare + generate). */
        const val AICORE_TIMEOUT_MS = 60_000L
    }

    /**
     * Runs inference using a downloadable model via the [MediaPipeInferenceEngine].
     */
    private suspend fun runDownloadableInference(
        input: ArticleInput,
        type: TransformType,
        modelId: String,
        entry: OfflineModelEntry
    ): Result<TransformResult> {
        return try {
            val prompt = buildPrompt(input, type, entry)
            val result = withTimeoutOrNull(MediaPipeInferenceEngine.INFERENCE_TIMEOUT_MS) {
                mediaPipeInferenceEngine.generateText(entry, prompt)
            }
            if (result == null) {
                return Result.failure(
                    DiagnosticException(
                        message = "${entry.displayName} took too long to respond. " +
                            "Try again or switch to a cloud-based AI profile.",
                        rawDetail = "MediaPipe inference timed out after ${MediaPipeInferenceEngine.INFERENCE_TIMEOUT_MS}ms for '$modelId'"
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
                Result.success(
                    TransformResult(
                        outputText = outputText,
                        transformType = type,
                        providerType = ProviderType.LOCAL_AI
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

    /**
     * Constructs the prompt sent to a local AI model.
     *
     * Safety design:
     * - A per-transform-type instruction header is placed before the content.
     * - User-provided or fetched content is wrapped in explicit `---BEGIN CONTENT---` /
     *   `---END CONTENT---` delimiters to reduce prompt-injection risk from untrusted text.
     * - Input is hard-capped at [OfflineModelEntry.contextWindowChars] to stay within the
     *   model's practical context limit.
     * - Output is plain text; the model is never asked to produce structured data.
     * - For custom workflows, [ArticleInput.systemPromptOverride] is used as the instruction
     *   header directly (still wrapped around content, never mixed into it).
     */
    private fun buildPrompt(input: ArticleInput, type: TransformType, entry: OfflineModelEntry?): String {
        val instruction = input.systemPromptOverride ?: when (type) {
            TransformType.SUMMARY ->
                "Write a concise summary of the following content."
            TransformType.THREAD ->
                "Convert the following content into 5–7 short, tweet-sized bullet points. " +
                    "Each bullet should stand alone and be under 280 characters."
            TransformType.PRO_POST ->
                "Rewrite the following as a polished, professional LinkedIn post. " +
                    "Keep it engaging and under 300 words."
            TransformType.MEETING_BRIEF ->
                "Summarize the following meeting notes into a short, structured brief " +
                    "with key decisions and action items."
            TransformType.STRATEGIC_QUESTIONS ->
                "Generate exactly 5 strategic questions based on the following meeting notes. " +
                    "Each question should provoke deeper thinking about goals or risks."
            TransformType.MORNING_SUMMARY ->
                "Summarize the following news items into a concise morning digest. " +
                    "Group related topics and highlight the most important developments."
            TransformType.CUSTOM_WORKFLOW ->
                "Process the following content as a helpful AI assistant."
        }

        // Safety cap: on-device models have limited context windows.
        // The per-model contextWindowChars sets the upper bound for the input portion.
        val maxChars = entry?.contextWindowChars ?: 3_000
        val safeContent = input.text.take(maxChars)

        return buildString {
            appendLine(instruction)
            appendLine()
            appendLine("---BEGIN CONTENT---")
            appendLine(safeContent)
            append("---END CONTENT---")
        }
    }
}

package com.automatist.app.data.providers

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.automatist.app.domain.engine.DiagnosticException
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelStatus
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
 * On-device AI provider using Gemini Nano via Android AICore.
 *
 * ## Device requirements
 * - Android 14+ (API 34) — AICore requires UPSIDE_DOWN_CAKE or higher.
 * - Supported hardware — currently Pixel 8 / 8a / 9 series, Samsung Galaxy S24+ (select regions).
 * - Gemini Nano must be confirmed available through [OfflineModelRepository.requestDownload]
 *   before this provider can execute inference.
 *
 * ## Behaviour by [OfflineModelStatus]
 * - NOT_INSTALLED / FAILED → clear "not available" diagnostic; direct user to Settings → On-device AI.
 * - DOWNLOADING             → "check in progress" diagnostic; user should wait.
 * - UNSUPPORTED             → "device not compatible" diagnostic; no crash.
 * - INSTALLED               → real AICore inference via [GenerativeModel.generateContent].
 *
 * ## Prompt safety
 * Fetched or pasted text is treated as untrusted input. It is wrapped between explicit
 * `---BEGIN CONTENT---` / `---END CONTENT---` delimiters and hard-capped at 3 000 characters
 * to stay within Gemini Nano's context limit and prevent prompt-overflow from hostile content.
 * Output is returned as plain text only — no structured parsing is applied.
 *
 * ## Cloud provider isolation
 * This class is only invoked when the active profile's ProviderType is LOCAL_AI.
 * All other provider paths (OpenAI, Anthropic, Gemini, OpenAI-compatible) are completely
 * unaffected and continue to work on all devices.
 */
@Singleton
class LocalAIArticleTransformProvider @Inject constructor(
    private val offlineModelRepository: OfflineModelRepository,
    @ApplicationContext private val context: Context
) : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        val modelId = input.modelOverride ?: OfflineModelCatalog.GEMINI_NANO_ID
        val status = offlineModelRepository.getModelStatus(modelId).first()

        return when (status) {
            OfflineModelStatus.NOT_INSTALLED ->
                Result.failure(
                    DiagnosticException(
                        message = "Gemini Nano is not yet confirmed available on this device. " +
                            "Open Settings → On-device AI and tap \"Check Availability\" first.",
                        rawDetail = "Model '$modelId' status: NOT_INSTALLED (availability check not run)"
                    )
                )

            OfflineModelStatus.FAILED ->
                Result.failure(
                    DiagnosticException(
                        message = "The on-device AI availability check failed. " +
                            "Open Settings → On-device AI and tap \"Retry\" to check again.",
                        rawDetail = "Model '$modelId' status: FAILED (AICore check error)"
                    )
                )

            OfflineModelStatus.DOWNLOADING ->
                Result.failure(
                    DiagnosticException(
                        message = "On-device AI availability check is still in progress. " +
                            "Please wait a moment and try again.",
                        rawDetail = "Model '$modelId' status: DOWNLOADING (AICore check in progress)"
                    )
                )

            OfflineModelStatus.UNSUPPORTED ->
                Result.failure(
                    DiagnosticException(
                        message = "On-device AI (Gemini Nano) is not supported on this device. " +
                            "It requires Android 14+ and a compatible Pixel 8+ or Galaxy S24+ device. " +
                            "Cloud-based AI profiles work on all devices.",
                        rawDetail = "Model '$modelId' status: UNSUPPORTED " +
                            "(Android API ${Build.VERSION.SDK_INT}, AICore not available)"
                    )
                )

            OfflineModelStatus.INSTALLED -> runAICoreInference(input, type, modelId)
        }
    }

    private companion object {
        /** Timeout for the entire AICore inference operation (prepare + generate). */
        const val AICORE_TIMEOUT_MS = 60_000L
    }

    /**
     * Runs Gemini Nano inference via the Android AICore system service.
     *
     * A fresh [GenerativeModel] is created per call so concurrent workflow executions
     * do not share state. The model is closed in a `finally` block to release AICore resources.
     *
     * The function guards with an explicit API-level check as a safety net; in practice the
     * INSTALLED status can only be set on API 34+ by [DataStoreOfflineModelRepository].
     *
     * Uses [withTimeoutOrNull] to avoid the `TimeoutCancellationException`-vs-real-cancellation
     * ambiguity that arises with [withTimeout] and `catch (CancellationException)`.
     */
    private suspend fun runAICoreInference(
        input: ArticleInput,
        type: TransformType,
        modelId: String
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

                    val prompt = buildPrompt(input, type)
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
     * Constructs the prompt sent to Gemini Nano.
     *
     * Safety design:
     * - A per-transform-type instruction header is placed before the content.
     * - User-provided or fetched content is wrapped in explicit `---BEGIN CONTENT---` /
     *   `---END CONTENT---` delimiters to reduce prompt-injection risk from untrusted text.
     * - Input is hard-capped at 3 000 characters to stay within Gemini Nano's practical
     *   context limit (≈ 1 000 tokens for the input portion).
     * - Output is plain text; the model is never asked to produce structured data.
     * - For custom workflows, [ArticleInput.systemPromptOverride] is used as the instruction
     *   header directly (still wrapped around content, never mixed into it).
     */
    private fun buildPrompt(input: ArticleInput, type: TransformType): String {
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

        // Safety cap: Gemini Nano has a limited context window. 3 000 chars is a conservative
        // upper bound for the input portion; the model's instruction and reasoning add to that.
        val safeContent = input.text.take(3_000)

        return buildString {
            appendLine(instruction)
            appendLine()
            appendLine("---BEGIN CONTENT---")
            appendLine(safeContent)
            append("---END CONTENT---")
        }
    }
}

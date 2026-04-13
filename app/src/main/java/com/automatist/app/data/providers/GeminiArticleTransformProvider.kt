package com.automatist.app.data.providers

import com.automatist.app.data.providers.gemini.GeminiApi
import com.automatist.app.data.providers.gemini.GeminiContent
import com.automatist.app.data.providers.gemini.GeminiPart
import com.automatist.app.data.providers.gemini.GeminiRequest
import com.automatist.app.data.providers.gemini.GeminiSystemInstruction
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.providers.ArticleTransformProvider
import com.automatist.app.platform.security.SecureStorage
import javax.inject.Inject

class GeminiArticleTransformProvider @Inject constructor(
    private val api: GeminiApi,
    private val secureStorage: SecureStorage
) : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        val model = input.modelOverride ?: "gemini-1.5-flash"
        return try {
            val apiKey = secureStorage.getApiKey(ProviderType.GEMINI)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(Exception("Gemini API key is missing. Add it in Settings → Provider API Keys."))
            }

            val systemPrompt = input.systemPromptOverride ?: when (type) {
                TransformType.SUMMARY -> "You are a professional assistant. Provide a concise bulleted summary of the following text."
                TransformType.THREAD -> "You are a professional assistant. Convert the following text into an engaging, high-quality social media thread (e.g. for Twitter/X). Use 🧵 and numbering."
                TransformType.PRO_POST -> "You are a professional assistant. Write a high-quality, professional LinkedIn post based on the following text."
                TransformType.MEETING_BRIEF -> "You are a professional assistant. Convert these raw meeting notes into a clean, structured Executive Meeting Brief with clear action items."
                TransformType.STRATEGIC_QUESTIONS -> "You are a professional assistant. From these notes, generate 3-5 hard-hitting strategic questions I should ask in the follow-up meeting."
                TransformType.MORNING_SUMMARY -> "You are an executive assistant. Summarize these feeds/articles into a rapid, scannable Morning Executive Brief highlighting only what truly matters today."
                else -> "You are a professional assistant. Process the following text according to the requested template."
            }

            val request = GeminiRequest(
                systemInstruction = GeminiSystemInstruction(
                    parts = listOf(GeminiPart(text = systemPrompt))
                ),
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = input.text))
                    )
                )
            )

            val response = api.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
            val output = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (output != null) {
                Result.success(
                    TransformResult(
                        outputText = output.trim(),
                        transformType = type,
                        providerType = ProviderType.GEMINI,
                        promptTokens = response.usageMetadata?.promptTokenCount,
                        completionTokens = response.usageMetadata?.candidatesTokenCount
                    )
                )
            } else {
                Result.failure(Exception("Gemini (model: $model) returned empty response. Check your API limits or try a different model."))
            }
        } catch (e: retrofit2.HttpException) {
            val rawBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val hint = when (e.code()) {
                401, 403 -> "API key is invalid or lacks access. Check your Gemini key in Settings → Provider API Keys."
                429 -> "Rate limit or quota exceeded. Wait and retry, or check your Google AI billing."
                else -> "HTTP ${e.code()}."
            }
            val rawDetail = buildString {
                appendLine("HTTP ${e.code()} ${e.message()}")
                appendLine("Provider: Gemini")
                appendLine("Model: $model")
                if (!rawBody.isNullOrBlank()) { appendLine("Response: $rawBody") }
            }
            Result.failure(com.automatist.app.domain.engine.DiagnosticException(
                message = "Gemini API failed (model: $model): $hint",
                rawDetail = rawDetail,
                httpStatus = e.code(),
                cause = e
            ))
        } catch (e: Exception) {
            Result.failure(com.automatist.app.domain.engine.DiagnosticException(
                message = "Gemini API error (model: $model): ${e.message}",
                rawDetail = "Exception: ${e.javaClass.simpleName}: ${e.message}\nProvider: Gemini\nModel: $model",
                cause = e
            ))
        }
    }
}

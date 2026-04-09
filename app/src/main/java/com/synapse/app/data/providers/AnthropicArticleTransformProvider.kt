package com.synapse.app.data.providers

import com.synapse.app.data.providers.anthropic.AnthropicApi
import com.synapse.app.data.providers.anthropic.AnthropicMessage
import com.synapse.app.data.providers.anthropic.AnthropicRequest
import com.synapse.app.domain.models.ArticleInput
import com.synapse.app.domain.models.ProviderType
import com.synapse.app.domain.models.TransformResult
import com.synapse.app.domain.models.TransformType
import com.synapse.app.domain.providers.ArticleTransformProvider
import com.synapse.app.platform.security.SecureStorage
import javax.inject.Inject

class AnthropicArticleTransformProvider @Inject constructor(
    private val api: AnthropicApi,
    private val secureStorage: SecureStorage
) : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        val model = input.modelOverride ?: "claude-3-haiku-20240307"
        return try {
            val apiKey = secureStorage.getApiKey(ProviderType.ANTHROPIC)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(Exception("Anthropic API key is missing. Add it in Settings → Provider API Keys."))
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

            val request = AnthropicRequest(
                model = model,
                system = systemPrompt,
                messages = listOf(
                    AnthropicMessage(role = "user", content = input.text)
                )
            )

            val response = api.createMessage(apiKey = apiKey, request = request)
            val output = response.content?.firstOrNull()?.text

            if (output != null) {
                Result.success(
                    TransformResult(
                        outputText = output.trim(),
                        transformType = type,
                        providerType = ProviderType.ANTHROPIC,
                        promptTokens = response.usage?.input_tokens,
                        completionTokens = response.usage?.output_tokens
                    )
                )
            } else {
                Result.failure(Exception("Anthropic (model: $model) returned empty response. Check your API limits or try a different model."))
            }
        } catch (e: retrofit2.HttpException) {
            val rawBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val hint = when (e.code()) {
                401 -> "API key is invalid or expired. Check your Anthropic key in Settings → Provider API Keys."
                403 -> "Access denied. Your Anthropic key may lack permissions for model '$model'."
                429 -> "Rate limit or quota exceeded. Wait and retry, or check your Anthropic billing."
                else -> "HTTP ${e.code()}."
            }
            val rawDetail = buildString {
                appendLine("HTTP ${e.code()} ${e.message()}")
                appendLine("Provider: Anthropic")
                appendLine("Model: $model")
                if (!rawBody.isNullOrBlank()) { appendLine("Response: $rawBody") }
            }
            Result.failure(com.synapse.app.domain.engine.DiagnosticException(
                message = "Anthropic API failed (model: $model): $hint",
                rawDetail = rawDetail,
                httpStatus = e.code(),
                cause = e
            ))
        } catch (e: Exception) {
            Result.failure(com.synapse.app.domain.engine.DiagnosticException(
                message = "Anthropic API error (model: $model): ${e.message}",
                rawDetail = "Exception: ${e.javaClass.simpleName}: ${e.message}\nProvider: Anthropic\nModel: $model",
                cause = e
            ))
        }
    }
}

package com.automatist.app.data.providers

import com.automatist.app.data.providers.openai.ChatMessage
import com.automatist.app.data.providers.openai.ChatRequest
import com.automatist.app.data.providers.openai.OpenAIApi
import com.automatist.app.domain.engine.DiagnosticException
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.providers.ArticleTransformProvider
import com.automatist.app.platform.security.SecureStorage
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

/**
 * Provider adapter for any OpenAI-compatible API endpoint.
 * Uses the same OpenAI chat completions format (POST /v1/chat/completions)
 * but with a user-configured base URL and per-profile API key.
 *
 * Compatible with: Together AI, Groq, Fireworks, Ollama, LM Studio, vLLM,
 * OpenRouter, Anyscale, Perplexity, and many others.
 */
class OpenAICompatibleProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureStorage: SecureStorage
) : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        val model = input.modelOverride ?: return Result.failure(
            Exception("No model specified for OpenAI-compatible provider. Set a model in the profile.")
        )

        // These are passed via the input's extra fields from the router
        val baseUrl = input.customBaseUrl
        val apiKeyId = input.customApiKeyId

        if (baseUrl.isNullOrBlank()) {
            return Result.failure(Exception("No base URL configured for this OpenAI-compatible profile. Edit the profile to add one."))
        }

        val apiKey = if (!apiKeyId.isNullOrBlank()) {
            secureStorage.getProfileKey(apiKeyId)
        } else null

        return try {
            // Create a Retrofit instance with the custom base URL
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val api = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAIApi::class.java)

            val systemPrompt = input.systemPromptOverride ?: when (type) {
                TransformType.SUMMARY -> "You are a professional assistant. Provide a concise bulleted summary of the following text."
                TransformType.THREAD -> "You are a professional assistant. Convert the following text into an engaging, high-quality social media thread. Use numbering."
                TransformType.PRO_POST -> "You are a professional assistant. Write a high-quality, professional LinkedIn post based on the following text."
                TransformType.MEETING_BRIEF -> "You are a professional assistant. Convert these raw meeting notes into a clean, structured Executive Meeting Brief with clear action items."
                TransformType.STRATEGIC_QUESTIONS -> "You are a professional assistant. From these notes, generate 3-5 hard-hitting strategic questions."
                TransformType.MORNING_SUMMARY -> "You are an executive assistant. Summarize these feeds/articles into a rapid, scannable Morning Executive Brief."
                else -> "You are a professional assistant. Process the following text according to the requested template."
            }

            val request = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = input.text)
                )
            )

            val authHeader = if (!apiKey.isNullOrBlank()) "Bearer $apiKey" else ""
            val response = api.createChatCompletion(authHeader, request)
            val output = response.choices?.firstOrNull()?.message?.content

            if (output != null) {
                Result.success(
                    TransformResult(
                        outputText = output.trim(),
                        transformType = type,
                        providerType = ProviderType.OPENAI_COMPATIBLE,
                        promptTokens = response.usage?.prompt_tokens,
                        completionTokens = response.usage?.completion_tokens
                    )
                )
            } else {
                Result.failure(Exception("OpenAI-compatible endpoint ($baseUrl) returned empty response for model '$model'."))
            }
        } catch (e: retrofit2.HttpException) {
            val rawBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val hint = when (e.code()) {
                401 -> "API key is invalid or missing."
                403 -> "Access denied for model '$model'."
                429 -> "Rate limit exceeded."
                else -> "HTTP ${e.code()}."
            }
            Result.failure(DiagnosticException(
                message = "OpenAI-compatible API failed ($baseUrl, model: $model): $hint",
                rawDetail = buildString {
                    appendLine("HTTP ${e.code()} ${e.message()}")
                    appendLine("Base URL: $baseUrl")
                    appendLine("Model: $model")
                    if (!rawBody.isNullOrBlank()) appendLine("Response: $rawBody")
                },
                httpStatus = e.code(),
                cause = e
            ))
        } catch (e: Exception) {
            Result.failure(DiagnosticException(
                message = "OpenAI-compatible API error ($baseUrl, model: $model): ${e.message}",
                rawDetail = "Exception: ${e.javaClass.simpleName}: ${e.message}\nBase URL: $baseUrl\nModel: $model",
                cause = e
            ))
        }
    }
}

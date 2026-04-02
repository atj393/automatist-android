package com.synapse.app.data.providers

import com.synapse.app.data.providers.gemini.GeminiApi
import com.synapse.app.data.providers.gemini.GeminiContent
import com.synapse.app.data.providers.gemini.GeminiPart
import com.synapse.app.data.providers.gemini.GeminiRequest
import com.synapse.app.data.providers.gemini.GeminiSystemInstruction
import com.synapse.app.domain.models.ArticleInput
import com.synapse.app.domain.models.ProviderType
import com.synapse.app.domain.models.TransformResult
import com.synapse.app.domain.models.TransformType
import com.synapse.app.domain.providers.ArticleTransformProvider
import com.synapse.app.platform.security.SecureStorage
import javax.inject.Inject

class GeminiArticleTransformProvider @Inject constructor(
    private val api: GeminiApi,
    private val secureStorage: SecureStorage
) : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        return try {
            val apiKey = secureStorage.getApiKey(ProviderType.GEMINI)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(Exception("Gemini API key is missing. Please add it to the Vault."))
            }

            val systemPrompt = when (type) {
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

            val response = api.generateContent(apiKey = apiKey, request = request)
            val output = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (output != null) {
                Result.success(
                    TransformResult(
                        outputText = output.trim(),
                        transformType = type,
                        providerType = ProviderType.GEMINI
                    )
                )
            } else {
                Result.failure(Exception("Received empty response from Gemini."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

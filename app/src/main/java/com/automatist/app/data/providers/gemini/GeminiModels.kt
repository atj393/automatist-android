package com.automatist.app.data.providers.gemini

data class GeminiRequest(
    val systemInstruction: GeminiSystemInstruction? = null,
    val contents: List<GeminiContent>
)

data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null
)

data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)

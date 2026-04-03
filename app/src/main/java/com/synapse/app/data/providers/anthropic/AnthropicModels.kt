package com.synapse.app.data.providers.anthropic

data class AnthropicRequest(
    val model: String = "claude-3-haiku-20240307",
    val max_tokens: Int = 1000,
    val system: String,
    val messages: List<AnthropicMessage>
)

data class AnthropicMessage(
    val role: String,
    val content: String
)

data class AnthropicResponse(
    val content: List<AnthropicContent>? = null,
    val usage: AnthropicUsage? = null
)

data class AnthropicUsage(
    val input_tokens: Int? = null,
    val output_tokens: Int? = null
)

data class AnthropicContent(
    val type: String?,
    val text: String?
)

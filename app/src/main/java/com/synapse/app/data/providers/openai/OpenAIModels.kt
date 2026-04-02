package com.synapse.app.data.providers.openai

data class ChatRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<ChatChoice>? = null
)

data class ChatChoice(
    val message: ChatMessage? = null
)

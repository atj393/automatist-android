package com.synapse.app.domain.models

enum class WorkflowType {
    ARTICLE_TRANSFORMER,
    MEETING_STRATEGIST,
    MORNING_BRIEF
}

enum class TransformType(val displayName: String) {
    SUMMARY("Concise Summary"),
    THREAD("5-Post Thread Draft"),
    PRO_POST("Professional Post Draft"),
    MEETING_BRIEF("Short Brief"),
    STRATEGIC_QUESTIONS("5 Strategic Questions"),
    MORNING_SUMMARY("Morning Summary")
}

enum class ProviderType(val displayName: String) {
    FAKE("Local Fake Demo"),
    OPENAI("OpenAI API"),
    ANTHROPIC("Anthropic API"),
    GEMINI("Google Gemini API")
}

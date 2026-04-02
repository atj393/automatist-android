package com.synapse.app.domain.models

import kotlinx.serialization.Serializable

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

@Serializable
enum class BriefOutputType(val displayName: String) {
    SUMMARY("Summary"),
    SOCIAL_POST("Social Media Post"),
    BULLET_INSIGHTS("Bullet Point Insights"),
    CUSTOM("Custom Format")
}

@Serializable
enum class SocialPlatform(val displayName: String) {
    LINKEDIN("LinkedIn"),
    X("X"),
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram"),
    THREADS("Threads")
}

@Serializable
enum class ScheduleType {
    EVERY_N_HOURS,
    DAILY_AT_HOUR
}

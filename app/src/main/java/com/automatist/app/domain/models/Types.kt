package com.automatist.app.domain.models

import kotlinx.serialization.Serializable

enum class WorkflowType {
    ARTICLE_TRANSFORMER,
    MEETING_STRATEGIST,
    MORNING_BRIEF,
    CUSTOM_WORKFLOW
}

enum class TransformType(val displayName: String) {
    SUMMARY("Concise Summary"),
    THREAD("5-Post Thread Draft"),
    PRO_POST("Professional Post Draft"),
    MEETING_BRIEF("Short Brief"),
    STRATEGIC_QUESTIONS("5 Strategic Questions"),
    MORNING_SUMMARY("Morning Summary"),
    CUSTOM_WORKFLOW("Custom Workflow")
}

enum class ProviderType(val displayName: String) {
    FAKE("Local Fake Demo"),
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GEMINI("Google Gemini"),
    OPENAI_COMPATIBLE("OpenAI-Compatible")
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

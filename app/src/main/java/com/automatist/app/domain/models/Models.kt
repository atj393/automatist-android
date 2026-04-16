package com.automatist.app.domain.models

import kotlinx.serialization.Serializable

data class ArticleInput(
    val text: String,
    val systemPromptOverride: String? = null,
    val profileId: String? = null,     // provider profile ID (null = use app default)
    val modelOverride: String? = null, // model ID override (null = use profile's default model)
    // ── Custom provider fields (populated by router from profile) ──
    val customBaseUrl: String? = null,
    val customApiKeyId: String? = null
)

@Serializable
data class BriefConfig(
    val rssFeeds: List<String> = emptyList(),
    val scheduleType: ScheduleType = ScheduleType.DAILY_AT_HOUR,
    val intervalHours: Int? = null,
    val dailyHour: Int? = 8,
    val briefOutputType: BriefOutputType = BriefOutputType.SUMMARY,
    val customFormatText: String = "",
    val socialPlatforms: Set<SocialPlatform> = setOf(),
    val isNotificationsEnabled: Boolean = false
)

data class TransformResult(
    val outputText: String,
    val transformType: TransformType,
    val providerType: ProviderType,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null
)

data class HistoryItem(
    val id: Long = 0,
    val workflowType: WorkflowType,
    val inputPreview: String,
    val transformType: TransformType,
    val outputText: String,
    val providerType: ProviderType,
    val createdAtMillis: Long
)

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
    val completionTokens: Int? = null,
    // ── Honest-usage metadata ────────────────────────────────────────────────
    /**
     * True when [promptTokens] / [completionTokens] are best-effort estimates
     * rather than authoritative counts from the provider. Local and Fake
     * providers set this to `true`; cloud providers leave it `false` because
     * their token counts come from the API response.
     */
    val isUsageEstimated: Boolean = false,
    /** Actual prompt character count (after provider-side truncation). */
    val inputChars: Int? = null,
    /** Actual output character count. */
    val outputChars: Int? = null,
    /**
     * True if the provider truncated or compressed the caller's input before
     * running inference (e.g. input exceeded the local model's context window).
     * Null when the provider does not perform such truncation (cloud providers).
     */
    val wasTruncated: Boolean? = null,
    /**
     * Input-token ceiling enforced by the provider. Meaningful for on-device
     * models with tight context budgets; null for cloud providers whose
     * effective ceilings are much larger than any prompt we send.
     */
    val contextCeilingTokens: Int? = null
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

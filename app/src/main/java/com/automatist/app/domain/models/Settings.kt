package com.automatist.app.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class AppSettings(
    val activeProvider: ProviderType = ProviderType.FAKE,
    val defaultProfileId: String = "" // provider profile ID (empty = use activeProvider legacy behavior)
)

// ── Provider Profile ──

data class ProviderProfile(
    val id: String, // UUID string
    val name: String,
    val providerType: ProviderType,
    val modelId: String,
    val isDefault: Boolean = false,
    val isFallback: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    // ── Custom / compatible provider fields ──
    val customBaseUrl: String = "",    // e.g. "https://api.together.xyz"
    val customApiKeyId: String = "",   // SecureStorage key ID for per-profile key
    val providerPresetId: String = ""  // ProviderCatalog entry ID (e.g. "groq", "openrouter", "custom")
) {
    /** User-facing label using the catalog name when available. */
    val displayLabel: String get() {
        val catalogName = if (providerPresetId.isNotBlank()) {
            ProviderCatalog.findById(providerPresetId)?.displayName ?: providerType.displayName
        } else providerType.displayName
        return "$name ($catalogName)"
    }

    /** Whether this profile uses a custom model not in the built-in list. */
    val isCustomModel: Boolean get() = providerType == ProviderType.OPENAI_COMPATIBLE ||
        !ProviderModels.isBuiltIn(providerType, modelId)

    /** Whether this profile stores its key per-profile (vs. centrally by ProviderType). */
    val usesPerProfileKey: Boolean get() = customApiKeyId.isNotBlank()
}

// ── Profile Selection (for workflow/action/output routing) ──

@Serializable
sealed interface ProfileSelection {
    @Serializable
    @SerialName("inherit")
    data object Inherit : ProfileSelection

    @Serializable
    @SerialName("specific")
    data class Specific(val profileId: String) : ProfileSelection
}

// ── Known model IDs per provider ──

object ProviderModels {
    val OPENAI = listOf(
        "gpt-4.1" to "GPT-4.1",
        "gpt-4.1-mini" to "GPT-4.1 Mini",
        "gpt-4.1-nano" to "GPT-4.1 Nano",
        "gpt-4o" to "GPT-4o",
        "gpt-4o-mini" to "GPT-4o Mini",
        "o3-mini" to "o3-mini",
        "gpt-4-turbo" to "GPT-4 Turbo",
        "gpt-3.5-turbo" to "GPT-3.5 Turbo"
    )
    val ANTHROPIC = listOf(
        "claude-opus-4-20250514" to "Claude Opus 4",
        "claude-sonnet-4-20250514" to "Claude Sonnet 4",
        "claude-3-5-sonnet-20241022" to "Claude 3.5 Sonnet",
        "claude-3-5-haiku-20241022" to "Claude 3.5 Haiku",
        "claude-3-haiku-20240307" to "Claude 3 Haiku"
    )
    val GEMINI = listOf(
        "gemini-2.5-flash" to "Gemini 2.5 Flash",
        "gemini-2.5-pro" to "Gemini 2.5 Pro",
        "gemini-2.0-flash" to "Gemini 2.0 Flash",
        "gemini-1.5-flash" to "Gemini 1.5 Flash",
        "gemini-1.5-pro" to "Gemini 1.5 Pro"
    )
    val FAKE = listOf(
        "fake-demo" to "Local Demo"
    )

    /** On-device offline models. IDs match [com.automatist.app.domain.offline.OfflineModelCatalog]. */
    val LOCAL_AI = listOf(
        "gemini-nano" to "Gemini Nano"
    )

    /** Whether the given model ID is in the built-in curated list for its provider. */
    fun isBuiltIn(provider: ProviderType, modelId: String): Boolean =
        modelsFor(provider).any { it.first == modelId }

    fun modelsFor(provider: ProviderType): List<Pair<String, String>> = when (provider) {
        ProviderType.OPENAI -> OPENAI
        ProviderType.ANTHROPIC -> ANTHROPIC
        ProviderType.GEMINI -> GEMINI
        ProviderType.FAKE -> FAKE
        ProviderType.OPENAI_COMPATIBLE -> emptyList() // custom model only
        ProviderType.LOCAL_AI -> LOCAL_AI
    }

    fun defaultModelFor(provider: ProviderType): String = when (provider) {
        ProviderType.OPENAI -> "gpt-4o-mini"
        ProviderType.ANTHROPIC -> "claude-3-5-haiku-20241022"
        ProviderType.GEMINI -> "gemini-2.0-flash"
        ProviderType.FAKE -> "fake-demo"
        ProviderType.OPENAI_COMPATIBLE -> ""
        ProviderType.LOCAL_AI -> "gemini-nano"
    }

    /**
     * Providers that use API keys stored centrally (by ProviderType).
     * LOCAL_AI is intentionally excluded — it requires no API key.
     */
    val BUILT_IN_PROVIDERS = listOf(ProviderType.OPENAI, ProviderType.ANTHROPIC, ProviderType.GEMINI)
}

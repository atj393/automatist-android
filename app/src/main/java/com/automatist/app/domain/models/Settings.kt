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
    val isEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val displayLabel: String get() = "$name (${providerType.displayName})"
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
        "gpt-4o" to "GPT-4o",
        "gpt-4o-mini" to "GPT-4o Mini",
        "gpt-3.5-turbo" to "GPT-3.5 Turbo"
    )
    val ANTHROPIC = listOf(
        "claude-sonnet-4-20250514" to "Claude Sonnet 4",
        "claude-3-5-haiku-20241022" to "Claude 3.5 Haiku",
        "claude-3-haiku-20240307" to "Claude 3 Haiku"
    )
    val GEMINI = listOf(
        "gemini-2.0-flash" to "Gemini 2.0 Flash",
        "gemini-1.5-flash" to "Gemini 1.5 Flash",
        "gemini-1.5-pro" to "Gemini 1.5 Pro"
    )
    val FAKE = listOf(
        "fake-demo" to "Local Demo"
    )

    fun modelsFor(provider: ProviderType): List<Pair<String, String>> = when (provider) {
        ProviderType.OPENAI -> OPENAI
        ProviderType.ANTHROPIC -> ANTHROPIC
        ProviderType.GEMINI -> GEMINI
        ProviderType.FAKE -> FAKE
    }

    fun defaultModelFor(provider: ProviderType): String = when (provider) {
        ProviderType.OPENAI -> "gpt-3.5-turbo"
        ProviderType.ANTHROPIC -> "claude-3-haiku-20240307"
        ProviderType.GEMINI -> "gemini-1.5-flash"
        ProviderType.FAKE -> "fake-demo"
    }
}

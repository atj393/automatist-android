package com.automatist.app.data.providers

import com.automatist.app.data.local.SettingsRepository
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.providers.ArticleTransformProvider
import com.automatist.app.domain.repositories.WorkflowRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class TransformProviderRouter @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val workflowRepository: WorkflowRepository,
    private val fakeProvider: FakeArticleTransformProvider,
    private val openAIProvider: OpenAIArticleTransformProvider,
    private val anthropicProvider: AnthropicArticleTransformProvider,
    private val geminiProvider: GeminiArticleTransformProvider,
    private val openAICompatibleProvider: OpenAICompatibleProvider
) : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        val resolved = resolveProfile(input.profileId)

        val providerType = resolved?.providerType ?: settingsRepository.settings.first().activeProvider
        val modelId = input.modelOverride ?: resolved?.modelId

        // For OPENAI_COMPATIBLE, pass custom fields through the input
        val routedInput = input.copy(
            modelOverride = modelId,
            customBaseUrl = resolved?.customBaseUrl,
            customApiKeyId = resolved?.customApiKeyId
        )

        return when (providerType) {
            ProviderType.FAKE -> fakeProvider.transform(routedInput, type)
            ProviderType.OPENAI -> openAIProvider.transform(routedInput, type)
            ProviderType.ANTHROPIC -> anthropicProvider.transform(routedInput, type)
            ProviderType.GEMINI -> geminiProvider.transform(routedInput, type)
            ProviderType.OPENAI_COMPATIBLE -> openAICompatibleProvider.transform(routedInput, type)
        }
    }

    /**
     * Resolves a provider profile from a profile ID.
     * Resolution order:
     * 1. Explicit profile ID from workflow/action
     * 2. App default profile (isDefault = true)
     * 3. Fallback profile (isFallback = true)
     * 4. null (caller uses legacy activeProvider)
     */
    private suspend fun resolveProfile(profileId: String?): ProviderProfile? {
        if (!profileId.isNullOrBlank()) {
            val profile = workflowRepository.getProfileById(profileId)
            if (profile != null && profile.isEnabled) return profile
        }

        val defaultProfile = workflowRepository.getDefaultProfile()
        if (defaultProfile != null && defaultProfile.isEnabled) return defaultProfile

        val fallbackProfile = workflowRepository.getFallbackProfile()
        if (fallbackProfile != null && fallbackProfile.isEnabled) return fallbackProfile

        return null
    }
}

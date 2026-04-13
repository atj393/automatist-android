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
    private val geminiProvider: GeminiArticleTransformProvider
) : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        // Resolve provider and model from profile if specified, otherwise fall back to app default
        val resolved = resolveProviderAndModel(input.profileId)

        val providerType = resolved.first
        val modelId = input.modelOverride ?: resolved.second

        // Create input with model override applied
        val routedInput = if (modelId != null) input.copy(modelOverride = modelId) else input

        return when (providerType) {
            ProviderType.FAKE -> fakeProvider.transform(routedInput, type)
            ProviderType.OPENAI -> openAIProvider.transform(routedInput, type)
            ProviderType.ANTHROPIC -> anthropicProvider.transform(routedInput, type)
            ProviderType.GEMINI -> geminiProvider.transform(routedInput, type)
        }
    }

    /**
     * Resolves provider type and optional model ID from a profile ID.
     * Falls back to app default provider if profile not found or not specified.
     */
    private suspend fun resolveProviderAndModel(profileId: String?): Pair<ProviderType, String?> {
        // Try explicit profile ID
        if (!profileId.isNullOrBlank()) {
            val profile = workflowRepository.getProfileById(profileId)
            if (profile != null && profile.isEnabled) {
                return profile.providerType to profile.modelId
            }
        }

        // Try app default profile
        val defaultProfile = workflowRepository.getDefaultProfile()
        if (defaultProfile != null && defaultProfile.isEnabled) {
            return defaultProfile.providerType to defaultProfile.modelId
        }

        // Fall back to legacy activeProvider setting
        val activeProvider = settingsRepository.settings.first().activeProvider
        return activeProvider to null
    }
}

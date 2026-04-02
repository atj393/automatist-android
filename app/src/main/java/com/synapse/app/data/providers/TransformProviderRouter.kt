package com.synapse.app.data.providers

import com.synapse.app.data.local.SettingsRepository
import com.synapse.app.domain.models.ArticleInput
import com.synapse.app.domain.models.ProviderType
import com.synapse.app.domain.models.TransformResult
import com.synapse.app.domain.models.TransformType
import com.synapse.app.domain.providers.ArticleTransformProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class TransformProviderRouter @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val fakeProvider: FakeArticleTransformProvider,
    private val openAIProvider: OpenAIArticleTransformProvider,
    private val anthropicProvider: AnthropicArticleTransformProvider,
    private val geminiProvider: GeminiArticleTransformProvider
) : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        val activeProvider = settingsRepository.settings.first().activeProvider
        
        return when (activeProvider) {
            ProviderType.FAKE -> fakeProvider.transform(input, type)
            ProviderType.OPENAI -> openAIProvider.transform(input, type)
            ProviderType.ANTHROPIC -> anthropicProvider.transform(input, type)
            ProviderType.GEMINI -> geminiProvider.transform(input, type)
        }
    }
}

package com.synapse.app.data.providers

import com.synapse.app.domain.models.ArticleInput
import com.synapse.app.domain.models.ProviderType
import com.synapse.app.domain.models.TransformResult
import com.synapse.app.domain.models.TransformType
import com.synapse.app.domain.providers.ArticleTransformProvider
import kotlinx.coroutines.delay
import javax.inject.Inject

class FakeArticleTransformProvider @Inject constructor() : ArticleTransformProvider {

    override suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult> {
        // Simulate network delay to make the UX loading state visible later in UI
        delay(1500)
        
        if (input.text.isBlank()) {
            return Result.failure(IllegalArgumentException("Input text cannot be empty."))
        }

        val output = when (type) {
            TransformType.SUMMARY -> {
                "Here is a concise summary of your article:\n\n" +
                "The primary theme centers around increasing productivity by reducing context switching. " +
                "Key takeaways indicate that professionals lose up to 20% of their daily efficiency " +
                "juggling between different communication apps."
            }
            TransformType.THREAD -> {
                "🧵 1/5 The hidden cost of modern work isn't meetings, it's context switching. " +
                "Here's why you're exhausted by 3 PM.\n\n" +
                "🧵 2/5 Studies show it takes ~23 minutes to refocus after a distraction. " +
                "If you check apps 10 times a day, that's nearly 4 hours lost.\n\n" +
                "🧵 3/5 The solution isn't to work harder. It's to consolidate. " +
                "Batch your notifications and dedicate deep work blocks.\n\n" +
                "🧵 4/5 Tool overload is real. Stick to one hub for asynchronous communication " +
                "and one for synchronous emergencies.\n\n" +
                "🧵 5/5 Protect your attention like it's your most valuable asset—because it is. " +
                "What's your strategy for staying focused?"
            }
            TransformType.PRO_POST -> {
                "Recently read an insightful piece on the hidden costs of context switching in our daily work. " +
                "It’s fascinating that the actual drain isn't the workload itself, but the constant shifting of attention between various apps.\n\n" +
                "A few strategies I'm implementing:\n" +
                "• Batching my check-ins for email/messages to 3 times a day.\n" +
                "• Blocking out 2 hours of 'deep work' every morning with zero notifications.\n" +
                "• Using asynchronous updates rather than ad-hoc syncs.\n\n" +
                "How does your team handle tool overload? Would love to hear your approaches.\n\n" +
                "#Productivity #DeepWork #FutureOfWork"
            }
            else -> "Simulation unavailable for this transform type."
        }

        return Result.success(
            TransformResult(
                outputText = output,
                transformType = type,
                providerType = ProviderType.FAKE
            )
        )
    }
}

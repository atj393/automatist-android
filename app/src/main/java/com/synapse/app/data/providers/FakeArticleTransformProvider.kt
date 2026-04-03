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

        val output = if (input.systemPromptOverride != null) {
            "Simulated response to custom automation configuration:\n\nFormat matching user request:\n${input.systemPromptOverride}"
        } else when (type) {
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
            TransformType.MEETING_BRIEF -> {
                "**Executive Meeting Brief: Q3 Operations Sync**\n\n" +
                "- **Status:** Q3 Product launch is tracking 2 weeks behind schedule due to API integration blockers.\n" +
                "- **Budget:** Marketing spend has been officially approved for the $50k tier.\n" +
                "- **Action Item:** Sarah to finalize the external PR copy by Thursday.\n" +
                "- **Action Item:** DevOps to escalate the API ticket to priority queue."
            }
            TransformType.STRATEGIC_QUESTIONS -> {
                "Based on the notes provided, here are strategic questions to leverage in the follow-up:\n\n" +
                "1. If the Q3 launch is delayed by 2 weeks, how does this directly impact our committed deliverables for early Q4?\n" +
                "2. Does the newly approved $50k marketing budget need adjustment to align with the later launch date?\n" +
                "3. What specific resources are required to unblock the API integration immediately?"
            }
            TransformType.MORNING_SUMMARY -> {
                "🌅 **Your Morning Executive Brief**\n\n" +
                "**Markets & Industry:** The sector is up broadly, led by favorable interest rate signals. Competitor 'Apex' launched their highly anticipated Feature X overnight.\n\n" +
                "**Internal Metrics:** Engagement shows a 15% WoW growth, maintaining the aggressive uptrend.\n\n" +
                "**Top Priority Today:** Focus deeply on finalizing the Series-B investor deck before the 3 PM walkthrough."
            }
            else -> "Simulation unavailable for this transform type."
        }

        return Result.success(
            TransformResult(
                outputText = output,
                transformType = type,
                providerType = ProviderType.FAKE,
                promptTokens = input.text.length / 4,
                completionTokens = output.length / 4
            )
        )
    }
}

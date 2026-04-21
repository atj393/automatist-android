package com.automatist.app.data.providers

import com.automatist.app.data.offline.MediaPipeInferenceEngine
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.offline.OfflineModelCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [LocalPromptBuilder.buildForMediaPipe] caps the engine-supplied system
 * prompt + user content so the combined prompt fits within the MediaPipe input-
 * token ceiling.
 *
 * Regression guard for the "Input size (586 tokens) exceeds maxTokens (384)"
 * native crash: the original bug was that cloud-sized systemPromptOverride
 * (1500–2500 chars in social/custom-workflow mode) on top of 2500 chars of
 * content pushed the combined prompt past the MediaPipe budget. This test
 * exercises exactly that failure shape.
 */
class LocalAIPromptBuilderTest {

    private val entry = OfflineModelCatalog.findById(OfflineModelCatalog.GEMMA_3N_E2B_ID)!!

    @Test
    fun `default transform instruction produces a prompt well inside the input ceiling`() {
        val input = ArticleInput(text = "Lorem ipsum ".repeat(200))
        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.SUMMARY, entry)

        val estimated = MediaPipeInferenceEngine.estimateTokens(prompt)
        assertTrue(
            "default-instruction prompt est=$estimated must fit in MAX_INPUT_TOKENS " +
                "${MediaPipeInferenceEngine.MAX_INPUT_TOKENS}",
            estimated <= MediaPipeInferenceEngine.MAX_INPUT_TOKENS
        )
    }

    @Test
    fun `oversized systemPromptOverride is truncated so the prompt fits`() {
        val cloudSizedInstruction = "You are an AI assistant. ".repeat(100) // ~2500 chars
        val content = "Headline: Tech News Today. ".repeat(100)              // ~2700 chars

        val input = ArticleInput(
            text = content,
            systemPromptOverride = cloudSizedInstruction
        )
        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)

        val estimated = MediaPipeInferenceEngine.estimateTokens(prompt)
        assertTrue(
            "cloud-sized prompt est=$estimated must be truncated to fit " +
                "MAX_INPUT_TOKENS ${MediaPipeInferenceEngine.MAX_INPUT_TOKENS}",
            estimated <= MediaPipeInferenceEngine.MAX_INPUT_TOKENS
        )
        assertTrue(
            "truncation marker should be present when instruction was cut",
            prompt.contains("[instructions truncated]")
        )
    }

    @Test
    fun `short systemPromptOverride is passed through unchanged`() {
        val shortOverride = "Summarise briefly."
        val input = ArticleInput(text = "Some content.", systemPromptOverride = shortOverride)
        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)

        assertTrue(
            "short override should appear verbatim at the top of the prompt",
            prompt.startsWith(shortOverride)
        )
        assertFalse(
            "no truncation marker should appear when instruction was not cut",
            prompt.contains("[instructions truncated]")
        )
    }

    @Test
    fun `content is capped at the catalog contextWindowChars`() {
        val hugeContent = "x".repeat(10_000)
        val input = ArticleInput(text = hugeContent)
        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.SUMMARY, entry)

        val maxExpectedPromptLength =
            LocalPromptBuilder.INSTRUCTION_CHARS +
            entry.contextWindowChars +
            200 // delimiters + newlines
        assertTrue(
            "prompt length ${prompt.length} must be <= $maxExpectedPromptLength — " +
                "content was not capped at contextWindowChars",
            prompt.length <= maxExpectedPromptLength
        )
    }

    @Test
    fun `fallback retry path produces an even smaller prompt`() {
        val cloudSizedInstruction = "Y".repeat(3000)
        val content = "Z".repeat(5000)
        val input = ArticleInput(text = content, systemPromptOverride = cloudSizedInstruction)

        val normal = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)
        val fallback = LocalPromptBuilder.buildForMediaPipe(
            input.copy(text = content.take(LocalPromptBuilder.FALLBACK_CONTENT_CHARS)),
            TransformType.CUSTOM_WORKFLOW,
            entry,
            instructionCap = LocalPromptBuilder.FALLBACK_INSTRUCTION_CHARS
        )

        assertTrue(
            "fallback path (${fallback.length} chars) must be smaller than normal path " +
                "(${normal.length} chars) so the retry is meaningful",
            fallback.length < normal.length
        )

        val fallbackEstimated = MediaPipeInferenceEngine.estimateTokens(fallback)
        assertTrue(
            "fallback prompt est=$fallbackEstimated should comfortably fit MAX_INPUT_TOKENS",
            fallbackEstimated <= MediaPipeInferenceEngine.MAX_INPUT_TOKENS
        )
    }

    @Test
    fun `reproduces the original crash shape but stays under budget after the fix`() {
        // Reconstruct the approximate shape of the prompt that produced the crash
        // report: engine's social-mode system prompt (~1500 chars) + 2500 chars of
        // tokenised-tight content. Under the old MAX_TOKENS=384 budget this hit
        // ~586 tokens and aborted native. Under the new 1280-token ceiling with
        // truncation applied, it must fit.
        val socialPrompt = "You are a professional AI assistant. ".repeat(45) // ~1665 chars
        val feedContent = "news item title. short description. ".repeat(70)   // ~2590 chars
        val input = ArticleInput(text = feedContent, systemPromptOverride = socialPrompt)

        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)
        val estimated = MediaPipeInferenceEngine.estimateTokens(prompt)

        assertTrue(
            "regression guard: prompt est=$estimated must fit the new MAX_INPUT_TOKENS " +
                "${MediaPipeInferenceEngine.MAX_INPUT_TOKENS}. If this fails the native " +
                "crash returns.",
            estimated <= MediaPipeInferenceEngine.MAX_INPUT_TOKENS
        )
    }
}

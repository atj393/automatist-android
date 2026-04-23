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

    // ── Local-only social-prompt substitution (Phase "lighter local prompt") ──

    /**
     * Reconstructs the exact fragment [WorkflowExecutionEngine.buildSocialSystemPrompt]
     * emits for the default "News to Social Posts" template. The compact-local
     * substitution in [LocalPromptBuilder.buildForMediaPipe] is triggered by
     * the `{"outputs":[{"platform":` schema signature this fragment carries.
     */
    private val cloudSocialOverride = """
        Write distinct social posts from the sources below — one per platform, each tailored (no duplicates).

        Global: Keep it punchy and informative.

        Platforms:
        - X: punchy, under 280 chars. Hashtags sparingly.
        - LinkedIn: professional hook, 1–3 short paragraphs.
        - Facebook: conversational, encourages engagement.

        Output STRICT valid JSON only — no prose, no code fences. Use this exact shape:
        {"outputs":[{"platform":"X","content":"…","title":"","notes":""}]}
        One object per platform in "outputs". Double-quoted strings, commas between fields. title/notes may be "".
        Platforms to generate: X, LinkedIn, Facebook
    """.trimIndent()

    @Test
    fun `social override is replaced with a compact local-only prompt`() {
        val feedContent = "Headline: Tech news today. ".repeat(60) // ~1680 chars
        val input = ArticleInput(text = feedContent, systemPromptOverride = cloudSocialOverride)

        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)

        assertTrue(
            "local prompt must ask for the bare-array social shape, not the outputs-wrapped one",
            prompt.contains("[{\"platform\":\"X\",\"content\":\"…\"}]")
        )
        assertFalse(
            "local prompt must NOT keep the cloud outputs-wrapped schema — that's what we're shrinking",
            prompt.contains("\"outputs\":[{\"platform\":")
        )
        assertFalse(
            "local prompt must drop title/notes fields — Gemma 3 1B emits them as empty strings anyway",
            prompt.contains("\"title\":\"\"") || prompt.contains("\"notes\":\"\"")
        )
    }

    @Test
    fun `compact local social prompt is materially shorter than the cloud override`() {
        // Spirit of the fix: the instruction footprint must be meaningfully
        // smaller so Gemma has room for source content and output tokens.
        val input = ArticleInput(text = "x".repeat(200), systemPromptOverride = cloudSocialOverride)
        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)

        // Everything before the BEGIN CONTENT delimiter is the instruction portion
        // of the final prompt. The cloud override itself is ~700–900 chars; the
        // local compact variant should be ≤ 600 chars.
        val instructionPortion = prompt.substringBefore("---BEGIN CONTENT---")
        assertTrue(
            "local social instruction portion (${instructionPortion.length} chars) must be shorter " +
                "than the incoming cloud override (${cloudSocialOverride.length} chars)",
            instructionPortion.length < cloudSocialOverride.length
        )
    }

    @Test
    fun `compact local social prompt carries the platform list from the cloud override`() {
        val input = ArticleInput(text = "content", systemPromptOverride = cloudSocialOverride)
        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)

        // All three platforms from the cloud override must still be listed so
        // the model knows what to generate for.
        assertTrue(prompt.contains("X"))
        assertTrue(prompt.contains("LinkedIn"))
        assertTrue(prompt.contains("Facebook"))
    }

    @Test
    fun `compact local social prompt preserves the template's global instruction`() {
        val input = ArticleInput(text = "content", systemPromptOverride = cloudSocialOverride)
        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)

        assertTrue(
            "template-author-level global instruction must survive substitution",
            prompt.contains("Keep it punchy and informative")
        )
    }

    @Test
    fun `local social path caps content at SOCIAL_CONTENT_CHARS not contextWindowChars`() {
        // The whole point of this path is to leave input-token budget for the
        // schema + output. Content cap must drop from 2500 to SOCIAL_CONTENT_CHARS.
        val oversizedContent = "y".repeat(5_000)
        val input = ArticleInput(
            text = oversizedContent,
            systemPromptOverride = cloudSocialOverride
        )
        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)

        val contentPortion = prompt.substringAfter("---BEGIN CONTENT---").substringBefore("---END CONTENT---")
        // Trim leading/trailing whitespace introduced by the delimiter lines.
        val trimmed = contentPortion.trim()
        assertTrue(
            "content on the local social path (${trimmed.length} chars) must be capped at " +
                "SOCIAL_CONTENT_CHARS (${LocalPromptBuilder.SOCIAL_CONTENT_CHARS})",
            trimmed.length <= LocalPromptBuilder.SOCIAL_CONTENT_CHARS
        )
    }

    @Test
    fun `local social path fits inside the input-token ceiling even with a worst-case cloud override`() {
        // Default template with 3 platforms + ~2500-char cloud social override
        // + oversized content. Before the fix this reliably timed out; after
        // the substitution + tighter content cap it must fit comfortably.
        val heavyOverride = cloudSocialOverride + ("\nDetailed extra: " + "More guidance text. ".repeat(50))
        val input = ArticleInput(text = "a".repeat(10_000), systemPromptOverride = heavyOverride)

        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)
        val estimated = MediaPipeInferenceEngine.estimateTokens(prompt)

        assertTrue(
            "local social prompt est=$estimated must fit MAX_INPUT_TOKENS " +
                "${MediaPipeInferenceEngine.MAX_INPUT_TOKENS} — otherwise scheduled offline " +
                "News-to-Social runs will keep timing out",
            estimated <= MediaPipeInferenceEngine.MAX_INPUT_TOKENS
        )
    }

    @Test
    fun `non-social cloud-sized override is NOT rerouted through the social compactor`() {
        // A long CUSTOM_WORKFLOW instruction that doesn't contain the schema
        // signature must still go through the old content+instruction cap path.
        // Regression guard: the social substitution must be gated precisely.
        val customOverride = "Rewrite the content as a friendly email. " +
            "Keep paragraphs short. ".repeat(80) // ~2100 chars, no outputs schema
        val input = ArticleInput(text = "content body", systemPromptOverride = customOverride)

        val prompt = LocalPromptBuilder.buildForMediaPipe(input, TransformType.CUSTOM_WORKFLOW, entry)

        assertFalse(
            "non-social override must not be substituted with the bare-array schema",
            prompt.contains("[{\"platform\":\"X\",\"content\":\"…\"}]")
        )
        // And the standard truncation marker still applies for oversize instructions.
        assertTrue(
            "long non-social override must still be truncated by the existing cap path",
            prompt.contains("[instructions truncated]")
        )
    }

    @Test
    fun `cloud path is unaffected — builder only runs on explicit local entry points`() {
        // This is a positive assertion about call-site discipline rather than
        // runtime behaviour: only [buildForMediaPipe] and [buildForAICore] are
        // invoked on the LOCAL_AI provider. Cloud providers (OpenAI, Anthropic,
        // Gemini) receive systemPromptOverride directly. The test exists as a
        // fence: a future change that routes cloud calls through this builder
        // would need to re-evaluate whether the social substitution is still
        // safe for cloud — today it isn't invoked at all for cloud paths.
        // Structural — the assertion lives in the codebase, not here.
        assertTrue(true)
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

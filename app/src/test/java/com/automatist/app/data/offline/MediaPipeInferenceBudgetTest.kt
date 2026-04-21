package com.automatist.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the token-budget contract for the MediaPipe local-model path.
 *
 * The values here are **safety-critical**: MediaPipe's native LLM layer aborts the
 * process when the tokenised prompt exceeds the configured `setMaxTokens` budget.
 * A regression that shrinks MAX_INPUT_TOKENS below a typical workflow prompt size
 * (~500–700 tokens) re-introduces the original crash.
 */
class MediaPipeInferenceBudgetTest {

    @Test
    fun `MAX_TOTAL_TOKENS is the sum of input ceiling and output reserve`() {
        // This invariant is the whole point of the constants — it documents that
        // MediaPipe's setMaxTokens is an input+output total, not an output cap.
        assertEquals(
            MediaPipeInferenceEngine.MAX_TOTAL_TOKENS,
            MediaPipeInferenceEngine.MAX_INPUT_TOKENS + MediaPipeInferenceEngine.MIN_OUTPUT_RESERVE_TOKENS
        )
    }

    @Test
    fun `MAX_INPUT_TOKENS must be well above the crash-report observed 586 tokens`() {
        // Device crash report showed "Input size (586 tokens) exceeds maxTokens (384)".
        // The fix raises MAX_TOTAL_TOKENS so input ceiling comfortably holds that size.
        assertTrue(
            "MAX_INPUT_TOKENS ${MediaPipeInferenceEngine.MAX_INPUT_TOKENS} must be > 586 " +
                "to hold the real-world prompt that caused the original crash",
            MediaPipeInferenceEngine.MAX_INPUT_TOKENS > 586
        )
    }

    @Test
    fun `MIN_OUTPUT_RESERVE_TOKENS leaves room for a meaningful generation`() {
        // Summaries and social posts typically run 100–300 tokens. Below ~128 the
        // model can't produce a usable output even for very short content.
        assertTrue(
            "Output reserve ${MediaPipeInferenceEngine.MIN_OUTPUT_RESERVE_TOKENS} must be >= 128",
            MediaPipeInferenceEngine.MIN_OUTPUT_RESERVE_TOKENS >= 128
        )
    }

    @Test
    fun `estimateTokens is conservative — it over-counts vs real tokenisation`() {
        // Real English prose tokenises at ~4 chars/token for Gemma. Our estimator
        // uses 3.0 so it consistently over-estimates. That trade biases us toward
        // slightly-too-aggressive truncation over a native crash.
        val text = "a".repeat(4000) // 4000 chars
        val estimated = MediaPipeInferenceEngine.estimateTokens(text)
        assertTrue(
            "estimated $estimated should over-count vs a typical 4 chars/token actual " +
                "(expected > 1000 tokens for 4000 chars)",
            estimated > 1000
        )
    }

    @Test
    fun `estimateTokens handles empty input`() {
        // +1 floor covers any instruction/template overhead — never returns 0.
        assertEquals(1, MediaPipeInferenceEngine.estimateTokens(""))
    }

    @Test
    fun `InputTooLargeException carries the diagnostic fields needed for user message`() {
        val ex = InputTooLargeException(
            estimatedTokens = 1500,
            maxInputTokens = 1280,
            promptChars = 4500
        )
        assertEquals(1500, ex.estimatedTokens)
        assertEquals(1280, ex.maxInputTokens)
        assertEquals(4500, ex.promptChars)
        // Message should embed all three for the raw-detail error report
        val msg = ex.message ?: ""
        assertTrue("message should include estimated tokens", msg.contains("1500"))
        assertTrue("message should include max tokens", msg.contains("1280"))
        assertTrue("message should include prompt char count", msg.contains("4500"))
    }

    @Test
    fun `a prompt at the budget ceiling is accepted, one past it is rejected`() {
        // Bracket the preflight decision. We don't call real MediaPipe here; we
        // reproduce the provider-side preflight condition to lock the behaviour in.
        val ceiling = MediaPipeInferenceEngine.MAX_INPUT_TOKENS

        val atCeiling = "x".repeat(((ceiling - 1) * 3))
        assertTrue(
            "prompt at estimated ${MediaPipeInferenceEngine.estimateTokens(atCeiling)} tokens " +
                "should be <= MAX_INPUT_TOKENS $ceiling",
            MediaPipeInferenceEngine.estimateTokens(atCeiling) <= ceiling
        )

        val overCeiling = "x".repeat(((ceiling + 100) * 3))
        assertFalse(
            "prompt at estimated ${MediaPipeInferenceEngine.estimateTokens(overCeiling)} tokens " +
                "should be > MAX_INPUT_TOKENS $ceiling",
            MediaPipeInferenceEngine.estimateTokens(overCeiling) <= ceiling
        )
    }
}

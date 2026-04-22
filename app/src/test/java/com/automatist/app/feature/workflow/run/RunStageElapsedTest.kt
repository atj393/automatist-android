package com.automatist.app.feature.workflow.run

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-level coverage for the user-facing elapsed-time formatter on the run
 * page. The live ticker composable ([rememberRunningElapsedLabel]) is Compose
 * runtime code and is exercised manually on-device; this test focuses on the
 * pure formatter so wording regressions ("14s elapsed" → "14000ms elapsed")
 * are caught at build time.
 */
class RunStageElapsedTest {

    @Test
    fun `formats zero elapsed as 0s`() {
        assertEquals("0s elapsed", formatElapsed(0L))
    }

    @Test
    fun `millisecond jitter under one second rounds down to zero`() {
        assertEquals(
            "fractional sub-second elapsed must not show fractional seconds",
            "0s elapsed",
            formatElapsed(999L)
        )
    }

    @Test
    fun `formats sub-minute elapsed as plain seconds`() {
        assertEquals("14s elapsed", formatElapsed(14_000L))
        assertEquals("59s elapsed", formatElapsed(59_999L))
    }

    @Test
    fun `formats minute-plus elapsed with zero-padded seconds`() {
        assertEquals("1m 00s elapsed", formatElapsed(60_000L))
        assertEquals("1m 08s elapsed", formatElapsed(68_000L))
        assertEquals("2m 30s elapsed", formatElapsed(150_000L))
    }

    @Test
    fun `pads single-digit seconds when minutes are present`() {
        // Regression guard — "1m 8s" would be unpadded and look inconsistent
        // next to "1m 12s".
        assertEquals("1m 05s elapsed", formatElapsed(65_000L))
    }

    @Test
    fun `defends against negative and absurdly large inputs`() {
        assertEquals(
            "negative elapsed (clock skew / bad anchor) must not crash the UI",
            "0s elapsed",
            formatElapsed(-500L)
        )
        // 10 hours — we don't format hours, but seconds-per-minute math must stay sane.
        assertEquals(
            "very long elapsed stays formatted as minutes + zero-padded seconds",
            "600m 00s elapsed",
            formatElapsed(36_000_000L)
        )
    }
}

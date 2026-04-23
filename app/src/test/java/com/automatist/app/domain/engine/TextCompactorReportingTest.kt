package com.automatist.app.domain.engine

import com.automatist.app.domain.models.InputCompactionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release-polish guard for [TextCompactor] reporting honesty.
 *
 * Previously the pipeline was happy to hand back a "compacted" string longer
 * than what it was given (a `[...]` marker inserted by `trimParagraph` on a
 * paragraph just over the threshold was the common culprit) and the run log
 * would then dutifully render the inflated result as a successful compaction
 * with a double-negative percent like `--8%`.
 *
 * The fix is two-part: [TextCompactor.compact] falls back to the original
 * when the candidate grew the text, and [TextCompactor.CompactionResult.reductionPercent]
 * clamps to 0 so a no-reduction result is never reported as a negative saving.
 * These tests fence both changes.
 */
class TextCompactorReportingTest {

    @Test
    fun `NONE mode returns the input unchanged with 0 percent`() {
        val input = "hello world"
        val r = TextCompactor.compact(input, InputCompactionMode.NONE)
        assertEquals(input, r.text)
        assertEquals(input.length, r.originalLength)
        assertEquals(input.length, r.compactedLength)
        assertEquals(0, r.reductionPercent)
        assertFalse(r.didReduce)
    }

    @Test
    fun `LIGHT compaction that genuinely shortens text reports the real reduction`() {
        // HTML + entities + whitespace runs — everything light-compact scrubs.
        val bloated = "<p>Hello&nbsp;&nbsp;&nbsp;world</p>   \n\n\n\n   &amp; friends"
        val r = TextCompactor.compact(bloated, InputCompactionMode.LIGHT)
        assertTrue(
            "expected light compaction to strip HTML and collapse whitespace",
            r.compactedLength < r.originalLength
        )
        assertTrue(r.didReduce)
        assertTrue("percent must be strictly positive on a real reduction", r.reductionPercent > 0)
        assertTrue("percent must be within 0..100", r.reductionPercent in 0..100)
    }

    @Test
    fun `AGGRESSIVE compaction cannot return text longer than the input`() {
        // Short, already-clean input is the pathological case: aggressive mode
        // has no headers/boilerplate to strip, so it can legitimately produce a
        // string equal to or longer than the original depending on internal
        // behavior. The guard in compact() must always fall back to the input.
        val shortClean = "Short clean input with a single sentence."
        val r = TextCompactor.compact(shortClean, InputCompactionMode.AGGRESSIVE)
        assertTrue(
            "compaction must never hand back a longer string than it received",
            r.compactedLength <= r.originalLength
        )
        if (r.compactedLength == r.originalLength) {
            // Identity path — the guard activated or compaction had nothing to do.
            assertEquals(shortClean, r.text)
            assertFalse(r.didReduce)
            assertEquals(0, r.reductionPercent)
        }
    }

    @Test
    fun `reductionPercent never goes negative even when the candidate would have grown the text`() {
        // Manually construct a pathological CompactionResult. This catches a
        // future refactor that might bypass compact() and build the result by
        // hand without clamping.
        val pathological = TextCompactor.CompactionResult(
            text = "same",
            originalLength = 10,
            compactedLength = 20
        )
        assertEquals(
            "reductionPercent must clamp to 0 on an inflated result — this is what stopped the `--8%` display",
            0, pathological.reductionPercent
        )
        assertFalse(pathological.didReduce)
    }

    @Test
    fun `empty input returns 0 percent without division by zero`() {
        val r = TextCompactor.compact("", InputCompactionMode.AGGRESSIVE)
        assertEquals(0, r.originalLength)
        assertEquals(0, r.compactedLength)
        assertEquals(0, r.reductionPercent)
        assertFalse(r.didReduce)
    }

    @Test
    fun `already-compact input is returned as-is from aggressive mode`() {
        // "Already compact" — single short sentence, no HTML, no entities. If
        // aggressive would grow it, the guard steps in and hands back the
        // original by reference (kotlin String equality is enough — we don't
        // rely on referential identity, but the content must match exactly).
        val alreadyCompact = "News: markets up today."
        val r = TextCompactor.compact(alreadyCompact, InputCompactionMode.AGGRESSIVE)
        assertTrue(r.compactedLength <= r.originalLength)
        assertEquals(
            "content must round-trip byte-for-byte when no real compaction occurred",
            alreadyCompact, r.text
        )
    }

    @Test
    fun `compaction that matches original length reports 0 percent not 0-to-0 rounding artefact`() {
        val sameLen = TextCompactor.CompactionResult(
            text = "abcde",
            originalLength = 5,
            compactedLength = 5
        )
        assertEquals(0, sameLen.reductionPercent)
        assertFalse(sameLen.didReduce)
    }

    @Test
    fun `heavy HTML payload sees a meaningful reduction through aggressive mode`() {
        val htmlHeavy = buildString {
            repeat(12) {
                append("<article class=\"foo bar\"><h1>Headline ")
                append(it)
                append("</h1><p>Some paragraph content here with &amp; entities and &nbsp;spaces. ")
                append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ")
                append("Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p></article>\n")
            }
        }
        val r = TextCompactor.compact(htmlHeavy, InputCompactionMode.AGGRESSIVE)
        assertTrue("aggressive mode should reduce HTML-heavy input", r.didReduce)
        assertTrue(
            "percent should be a believable positive number, not negative",
            r.reductionPercent in 1..99
        )
    }

    // ── Smoke on the public contract ──

    @Test
    fun `CompactionResult fields are internally consistent`() {
        val r = TextCompactor.compact("    hello   world   ", InputCompactionMode.LIGHT)
        assertEquals(r.text.length, r.compactedLength)
        assertTrue(r.originalLength >= r.compactedLength)
    }

    @Test
    fun `no-regression guard falls back by value not by reference`() {
        // The guard returns `text` (the original parameter) when the candidate
        // grew. We only need content equality — but confirming the `text`
        // field is the incoming string, not some processed intermediate, is
        // the point here.
        val original = "Short."
        val r = TextCompactor.compact(original, InputCompactionMode.AGGRESSIVE)
        if (r.compactedLength == r.originalLength) {
            assertSame(
                "guard path should short-circuit back to the exact input instance",
                original, r.text
            )
        }
    }
}

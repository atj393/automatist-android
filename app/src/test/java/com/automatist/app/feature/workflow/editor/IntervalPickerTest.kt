package com.automatist.app.feature.workflow.editor

import com.automatist.app.domain.models.WorkflowTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the interval-trigger picker's pure conversion + derivation helpers.
 * The UI is a thin shell over these functions, so locking them in here also
 * effectively fences the UI behaviour.
 *
 * Contract:
 *  - storage remains an integer `intervalMinutes`; these helpers don't touch
 *    the scheduler.
 *  - [deriveIntervalUi] is lossless for "new-picker" values (Minutes 1–59,
 *    exact-hour multiples 60..1440) — no snapping, no display changes.
 *  - legacy / imported values outside those ranges are SNAPPED for display
 *    so the picker always renders a valid state, and [IntervalUi.snapped] is
 *    true so the UI can surface a note.
 *  - [intervalUiToMinutes] produces the correct storage integer for any
 *    valid picker selection.
 */
class IntervalPickerTest {

    // ── Lossless path: values that fit the new picker cleanly ──

    @Test
    fun `15 minutes loads as Minutes 15`() {
        val ui = deriveIntervalUi(15)
        assertEquals(15, ui.value)
        assertEquals(IntervalUnit.Minutes, ui.unit)
        assertFalse("exact minutes value must not be flagged as snapped", ui.snapped)
    }

    @Test
    fun `45 minutes loads as Minutes 45`() {
        val ui = deriveIntervalUi(45)
        assertEquals(45, ui.value)
        assertEquals(IntervalUnit.Minutes, ui.unit)
        assertFalse(ui.snapped)
    }

    @Test
    fun `1 minute is the floor and loads as Minutes 1`() {
        val ui = deriveIntervalUi(1)
        assertEquals(1, ui.value)
        assertEquals(IntervalUnit.Minutes, ui.unit)
        assertFalse(ui.snapped)
    }

    @Test
    fun `59 minutes is the minutes ceiling and loads as Minutes 59`() {
        val ui = deriveIntervalUi(59)
        assertEquals(59, ui.value)
        assertEquals(IntervalUnit.Minutes, ui.unit)
        assertFalse(ui.snapped)
    }

    @Test
    fun `60 minutes prefers Hours 1 representation over Minutes`() {
        val ui = deriveIntervalUi(60)
        assertEquals(1, ui.value)
        assertEquals(IntervalUnit.Hours, ui.unit)
        assertFalse("exact-hour value must not be flagged as snapped", ui.snapped)
    }

    @Test
    fun `180 minutes loads as Hours 3`() {
        val ui = deriveIntervalUi(180)
        assertEquals(3, ui.value)
        assertEquals(IntervalUnit.Hours, ui.unit)
        assertFalse(ui.snapped)
    }

    @Test
    fun `720 minutes loads as Hours 12`() {
        val ui = deriveIntervalUi(720)
        assertEquals(12, ui.value)
        assertEquals(IntervalUnit.Hours, ui.unit)
        assertFalse(ui.snapped)
    }

    @Test
    fun `1440 minutes loads as Hours 24`() {
        val ui = deriveIntervalUi(24 * 60)
        assertEquals(24, ui.value)
        assertEquals(IntervalUnit.Hours, ui.unit)
        assertFalse(ui.snapped)
    }

    // ── Snapping path: legacy / imported / unusual values ──

    @Test
    fun `90 minutes (1_5 hours) snaps down to Hours 1 and is marked snapped`() {
        // Not representable in the new picker without losing precision.
        // Picker is Hours 1–24 or Minutes 1–59; closest representable is
        // Hours 1 (rounded down). Flag for UI messaging.
        val ui = deriveIntervalUi(90)
        assertEquals(1, ui.value)
        assertEquals(IntervalUnit.Hours, ui.unit)
        assertTrue("90 minutes must be flagged as snapped for display", ui.snapped)
    }

    @Test
    fun `150 minutes (2h 30m) snaps down to Hours 2 and is marked snapped`() {
        val ui = deriveIntervalUi(150)
        assertEquals(2, ui.value)
        assertEquals(IntervalUnit.Hours, ui.unit)
        assertTrue(ui.snapped)
    }

    @Test
    fun `values beyond 24 hours snap to Hours 24 ceiling`() {
        val ui = deriveIntervalUi(10_000)
        assertEquals(24, ui.value)
        assertEquals(IntervalUnit.Hours, ui.unit)
        assertTrue(ui.snapped)
    }

    @Test
    fun `zero or negative values snap to Minutes 15 default`() {
        assertEquals(
            IntervalUi(15, IntervalUnit.Minutes, snapped = true),
            deriveIntervalUi(0)
        )
        assertEquals(
            IntervalUi(15, IntervalUnit.Minutes, snapped = true),
            deriveIntervalUi(-5)
        )
    }

    // ── Picker → storage conversion ──

    @Test
    fun `converting Minutes picker back to storage returns the same integer`() {
        assertEquals(1, intervalUiToMinutes(1, IntervalUnit.Minutes))
        assertEquals(30, intervalUiToMinutes(30, IntervalUnit.Minutes))
        assertEquals(59, intervalUiToMinutes(59, IntervalUnit.Minutes))
    }

    @Test
    fun `converting Hours picker back to storage multiplies by 60`() {
        assertEquals(60, intervalUiToMinutes(1, IntervalUnit.Hours))
        assertEquals(180, intervalUiToMinutes(3, IntervalUnit.Hours))
        assertEquals(24 * 60, intervalUiToMinutes(24, IntervalUnit.Hours))
    }

    @Test
    fun `converting out-of-range picker values clamps to allowed bounds`() {
        // Defensive: the picker UI caps values at the bounds, but if an
        // out-of-range value ever reaches the converter it must clamp, not
        // silently multiply nonsense through to the scheduler.
        assertEquals(1, intervalUiToMinutes(0, IntervalUnit.Minutes))
        assertEquals(59, intervalUiToMinutes(999, IntervalUnit.Minutes))
        assertEquals(60, intervalUiToMinutes(-5, IntervalUnit.Hours))
        assertEquals(24 * 60, intervalUiToMinutes(999, IntervalUnit.Hours))
    }

    // ── Round-trip stability for new-picker values ──

    @Test
    fun `round-trip of every picker-valid value is lossless`() {
        // Minutes 1..59
        for (m in 1..59) {
            val ui = deriveIntervalUi(m)
            val back = intervalUiToMinutes(ui.value, ui.unit)
            assertEquals("Minutes $m round-trip", m, back)
            assertFalse("Minutes $m should not be flagged snapped", ui.snapped)
        }
        // Hours 1..24 (stored as 60..1440 in 60-minute steps)
        for (h in 1..24) {
            val stored = h * 60
            val ui = deriveIntervalUi(stored)
            val back = intervalUiToMinutes(ui.value, ui.unit)
            assertEquals("Hours $h round-trip", stored, back)
            assertFalse("Hours $h should not be flagged snapped", ui.snapped)
        }
    }

    // ── Display-label wording (plural / singular) ──

    @Test
    fun `displayLabelVerbose uses correct singular and plural`() {
        assertEquals("Every 1 minute", WorkflowTrigger.Interval(1).displayLabelVerbose)
        assertEquals("Every 15 minutes", WorkflowTrigger.Interval(15).displayLabelVerbose)
        assertEquals("Every 1 hour", WorkflowTrigger.Interval(60).displayLabelVerbose)
        assertEquals("Every 3 hours", WorkflowTrigger.Interval(180).displayLabelVerbose)
        assertEquals("Every 24 hours", WorkflowTrigger.Interval(24 * 60).displayLabelVerbose)
    }

    @Test
    fun `displayLabelVerbose preserves meaningful mixed legacy values`() {
        // 90 min → "Every 1 hour 30 minutes" — long-form is honest even though
        // the new picker snaps for editing.
        assertEquals("Every 1 hour 30 minutes", WorkflowTrigger.Interval(90).displayLabelVerbose)
        assertEquals("Every 2 hours 15 minutes", WorkflowTrigger.Interval(135).displayLabelVerbose)
    }

    @Test
    fun `existing compact displayLabel is unchanged for common values`() {
        // Regression guard: list / card / chip sites still read `displayLabel`.
        assertEquals("Every 15m", WorkflowTrigger.Interval(15).displayLabel)
        assertEquals("Every hour", WorkflowTrigger.Interval(60).displayLabel)
        assertEquals("Every 3h", WorkflowTrigger.Interval(180).displayLabel)
        assertEquals("Every 1h 30m", WorkflowTrigger.Interval(90).displayLabel)
    }

    // ── Scheduler-contract fence ──

    @Test
    fun `picker outputs remain valid intervalMinutes for the scheduler`() {
        // The scheduler consumes `intervalMinutes` unchanged. Every value the
        // new picker can produce must be a positive, non-zero integer.
        val allPickerOutputs = buildList {
            (1..59).forEach { add(intervalUiToMinutes(it, IntervalUnit.Minutes)) }
            (1..24).forEach { add(intervalUiToMinutes(it, IntervalUnit.Hours)) }
        }
        assertTrue("every picker output must be >= 1", allPickerOutputs.all { it >= 1 })
        assertTrue("every picker output must be <= 1440 (24h)", allPickerOutputs.all { it <= 24 * 60 })
    }
}

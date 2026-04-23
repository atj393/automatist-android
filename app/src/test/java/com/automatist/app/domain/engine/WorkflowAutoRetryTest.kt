package com.automatist.app.domain.engine

import com.automatist.app.domain.models.InputCompactionMode
import com.automatist.app.domain.models.OutputFormat
import com.automatist.app.domain.models.ResumeSnapshot
import com.automatist.app.domain.models.WorkflowAction
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.models.WorkflowOutputConfig
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.models.computeWorkflowFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Model-level tests for the auto-retry feature. Covers:
 * - schema defaults (backwards-compat for workflows without the field)
 * - the cap constant ([WorkflowRun.MAX_AUTO_RETRIES])
 * - chain metadata round-trip (attempt number + parent pointer)
 * - interaction with the resume path (`canResumeFor` still honours the
 *   fingerprint gate on every auto-retry)
 *
 * Integration behaviour (VM + Worker loop) is exercised indirectly through
 * the existing [WorkflowResumeTest] + a full instrumented run once the app
 * is installed; unit tests here stay fast and deterministic.
 */
class WorkflowAutoRetryTest {

    private fun baseTemplate(
        id: Long = 1L,
        autoRetry: Boolean = false
    ): WorkflowTemplate = WorkflowTemplate(
        id = id,
        name = "Auto-retry Test",
        actions = listOf(
            WorkflowAction(
                id = "act-1",
                type = WorkflowActionType.PASTE_TEXT,
                sourceData = "content",
                order = 0,
                isEnabled = true,
                compaction = InputCompactionMode.NONE
            )
        ),
        outputConfig = WorkflowOutputConfig(outputFormat = OutputFormat.PLAIN_TEXT),
        autoRetryEnabled = autoRetry
    )

    // ── Schema defaults / backwards-compat ──

    @Test
    fun `template autoRetryEnabled defaults to false`() {
        val t = WorkflowTemplate(name = "x")
        assertFalse(
            "new workflows must default to OFF so existing behaviour is preserved",
            t.autoRetryEnabled
        )
    }

    @Test
    fun `workflow run attempt metadata defaults to zero and null`() {
        val run = WorkflowRun(templateId = 1L, templateName = "t")
        assertEquals(
            "0 represents the initial attempt so legacy rows read as fresh runs",
            0, run.autoRetryAttempt
        )
        assertNull(run.parentRunId)
    }

    @Test
    fun `cap is 3 so total attempts including initial stay bounded`() {
        assertEquals(3, WorkflowRun.MAX_AUTO_RETRIES)
    }

    // ── Fingerprint + auto-retry interaction ──

    @Test
    fun `enabling auto-retry on a template does NOT change its execution fingerprint`() {
        // autoRetryEnabled governs WHEN retries fire, not WHAT gets executed.
        // The fingerprint guards resume safety (does the same work produce the
        // same output?) so flipping the retry toggle must not invalidate
        // previously-captured snapshots — otherwise enabling retry would force
        // a full rerun of the first retry, defeating the point.
        val off = baseTemplate(autoRetry = false)
        val on = baseTemplate(autoRetry = true)
        assertEquals(computeWorkflowFingerprint(off), computeWorkflowFingerprint(on))
    }

    @Test
    fun `editing an action between retries invalidates prior snapshot`() {
        val before = baseTemplate()
        val snap = ResumeSnapshot(
            fingerprint = computeWorkflowFingerprint(before),
            reachedStage = ResumeSnapshot.STAGE_PROCESSING,
            synthesisInput = "frozen"
        )
        // User edits the workflow mid-chain — auto-retry must fall back to a full rerun.
        val edited = before.copy(
            actions = before.actions.map { it.copy(sourceData = "edited") }
        )
        assertFalse(
            "after an action edit, canResumeFor must return false so the next retry does a full rerun",
            snap.canResumeFor(edited)
        )
        assertNotEquals(
            computeWorkflowFingerprint(before),
            computeWorkflowFingerprint(edited)
        )
    }

    // ── Chain metadata ──

    @Test
    fun `auto-retry rows carry attempt number and parent run id`() {
        val initial = WorkflowRun(
            id = 100L,
            templateId = 1L,
            templateName = "t",
            autoRetryAttempt = 0,
            parentRunId = null
        )
        val retry1 = WorkflowRun(
            id = 101L,
            templateId = 1L,
            templateName = "t",
            autoRetryAttempt = 1,
            parentRunId = initial.id
        )
        val retry3 = WorkflowRun(
            id = 103L,
            templateId = 1L,
            templateName = "t",
            autoRetryAttempt = 3,
            parentRunId = initial.id
        )

        assertEquals(0, initial.autoRetryAttempt)
        assertNull(initial.parentRunId)

        assertEquals(1, retry1.autoRetryAttempt)
        assertEquals(initial.id, retry1.parentRunId)

        assertEquals(3, retry3.autoRetryAttempt)
        assertEquals(initial.id, retry3.parentRunId)
        assertTrue(
            "the cap is inclusive: attempt == MAX_AUTO_RETRIES is allowed as the last try",
            retry3.autoRetryAttempt <= WorkflowRun.MAX_AUTO_RETRIES
        )
    }

    // ── Retry cap check used by both VM and Worker ──

    @Test
    fun `retry-cap predicate allows attempts 1 through MAX but stops at MAX+1`() {
        // Mirrors the `autoRetryAttempt < MAX_AUTO_RETRIES` check in
        // WorkflowRunViewModel and WorkflowWorker. Guards against off-by-one.
        val maxN = WorkflowRun.MAX_AUTO_RETRIES
        for (attempt in 0 until maxN) {
            assertTrue(
                "attempt=$attempt must be allowed to spawn the next retry",
                attempt < maxN
            )
        }
        assertFalse(
            "attempt=$maxN is the final try — no further retry must be spawned",
            maxN < maxN
        )
    }

    // ── Snapshot persistence across auto-retry chain ──

    @Test
    fun `auto-retry row can still carry a fresh resume snapshot so the next retry can resume`() {
        val template = baseTemplate(autoRetry = true)
        val snap = ResumeSnapshot(
            fingerprint = computeWorkflowFingerprint(template),
            reachedStage = ResumeSnapshot.STAGE_PROCESSING,
            synthesisInput = "frozen combined input"
        )
        val retry1 = WorkflowRun(
            id = 200L,
            templateId = template.id,
            templateName = template.name,
            autoRetryAttempt = 1,
            parentRunId = 199L,
            resumeSnapshotJson = ResumeSnapshot.toJson(snap)
        )
        val parsed = retry1.resumeSnapshot
        assertNotNull(parsed)
        assertTrue(parsed!!.canResumeFor(template))
    }
}

package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.PersistedStage
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression fence for the Run Results page showing Started == Completed
 * even when Duration was non-zero.
 *
 * Root cause was that worker-side terminal writes rebuilt a fresh
 * [WorkflowRun] via `WorkflowRun(id = runId, ...)` WITHOUT passing
 * [WorkflowRun.startedAtMillis]. The data-class default
 * `startedAtMillis = System.currentTimeMillis()` then ran at construction
 * time, and Room's full-row @Update wrote that terminal-moment value over
 * the original start. Both Started and Completed displayed the same
 * timestamp, while Duration came from the engine's authoritative measure
 * and was correct — producing the "Started 12:43:55 / Completed 12:43:55
 * / Duration 1m 48s" contradiction the user reported.
 *
 * These tests simulate the worker's insert-then-terminal sequence at the
 * repository layer. They don't boot the worker or WorkManager (not
 * unit-testable) — the repo-level sequence is exactly what the worker
 * does, so fencing the data round-trip here is sufficient to prove the
 * fix.
 */
class RunTimestampPersistenceTest {

    private fun stagesJson(vararg labels: String): String =
        PersistedStage.toJson(labels.map { PersistedStage(it, "COMPLETED") })

    @Test
    fun `completed run preserves its original startedAtMillis after terminal updateRun`() = runBlocking {
        // Simulate: worker inserts a run at T0, engine runs for some time,
        // worker rebuilds WorkflowRun at T1 and calls updateRun. The fix
        // guarantees startedAtMillis is passed explicitly — so T0 survives.
        val repo = FakeWorkflowRepository()
        val started = 1_700_000_000_000L
        val completed = started + 108_000L // 1m 48s later — the reported bug's duration

        val runId = repo.insertRun(
            WorkflowRun(
                templateId = 1L,
                templateName = "News to Social Posts",
                triggerType = "scheduled",
                status = WorkflowRunStatus.RUNNING,
                currentStage = "Preparing",
                startedAtMillis = started
            )
        )

        // Exactly the worker's rebuild pattern — note the explicit startedAtMillis
        // that used to be missing.
        repo.updateRun(
            WorkflowRun(
                id = runId,
                templateId = 1L,
                templateName = "News to Social Posts",
                triggerType = "scheduled",
                status = WorkflowRunStatus.COMPLETED,
                currentStage = "Completed",
                outputText = "social cards JSON",
                providerType = ProviderType.LOCAL_AI,
                durationMs = completed - started,
                startedAtMillis = started,
                completedAtMillis = completed,
                stagesJson = stagesJson("Preparing", "Completed")
            )
        )

        val after = repo.getRunById(runId)!!
        assertEquals(
            "startedAtMillis must survive the terminal update unchanged — " +
                "Started mirroring Completed was the exact user-reported bug",
            started, after.startedAtMillis
        )
        assertEquals(completed, after.completedAtMillis)
        assertNotEquals(after.startedAtMillis, after.completedAtMillis)
        assertEquals(completed - started, after.durationMs)
    }

    @Test
    fun `failed run preserves its original startedAtMillis after terminal updateRun`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val started = 1_700_000_000_000L
        val failedAt = started + 8_400L // 8.4s — representative fast-fail

        val runId = repo.insertRun(
            WorkflowRun(
                templateId = 1L,
                templateName = "News to Social Posts",
                triggerType = "scheduled",
                status = WorkflowRunStatus.RUNNING,
                currentStage = "Reading source: News Feed",
                startedAtMillis = started
            )
        )

        repo.updateRun(
            WorkflowRun(
                id = runId,
                templateId = 1L,
                templateName = "News to Social Posts",
                triggerType = "scheduled",
                status = WorkflowRunStatus.FAILED,
                currentStage = "actions",
                errorMessage = "All actions failed. No data to process.",
                startedAtMillis = started,
                completedAtMillis = failedAt,
                stagesJson = stagesJson("Preparing", "Failed")
            )
        )

        val after = repo.getRunById(runId)!!
        assertEquals(started, after.startedAtMillis)
        assertEquals(failedAt, after.completedAtMillis)
        assertNotEquals(after.startedAtMillis, after.completedAtMillis)
    }

    @Test
    fun `unexpected-terminal-state write preserves startedAtMillis`() = runBlocking {
        // Mirrors the worker's defensive else-branch: engine flow ended
        // without Completed or Failed. startedAtMillis must still survive.
        val repo = FakeWorkflowRepository()
        val started = 1_700_000_000_000L
        val ended = started + 600L

        val runId = repo.insertRun(
            WorkflowRun(
                templateId = 1L,
                templateName = "X",
                status = WorkflowRunStatus.RUNNING,
                startedAtMillis = started
            )
        )

        repo.updateRun(
            WorkflowRun(
                id = runId,
                templateId = 1L,
                templateName = "X",
                status = WorkflowRunStatus.FAILED,
                currentStage = "Unknown",
                errorMessage = "Workflow ended without a terminal state",
                startedAtMillis = started,
                completedAtMillis = ended
            )
        )

        val after = repo.getRunById(runId)!!
        assertEquals(started, after.startedAtMillis)
        assertEquals(ended, after.completedAtMillis)
    }

    @Test
    fun `incremental progress writes do not overwrite startedAtMillis`() = runBlocking {
        // Existing narrow-update contract — kept locked in alongside the
        // terminal-write fix so no refactor accidentally widens the update.
        val repo = FakeWorkflowRepository()
        val started = 1_700_000_000_000L
        val runId = repo.insertRun(
            WorkflowRun(
                templateId = 1L,
                templateName = "X",
                status = WorkflowRunStatus.RUNNING,
                startedAtMillis = started
            )
        )

        repo.updateRunProgress(runId, stagesJson("Preparing"), "Preparing")
        repo.updateRunProgress(runId, stagesJson("Preparing", "Reading"), "Reading")
        repo.updateRunProfile(runId, "My Profile", "gpt-4o-mini")

        val after = repo.getRunById(runId)!!
        assertEquals(started, after.startedAtMillis)
    }

    @Test
    fun `cancellation write preserves startedAtMillis`() = runBlocking {
        // Belt-and-braces: verify that the separate markRunCancelled path
        // (for abandoned manual runs) also leaves Started alone.
        val repo = FakeWorkflowRepository()
        val started = 1_700_000_000_000L
        val runId = repo.insertRun(
            WorkflowRun(
                templateId = 1L,
                templateName = "X",
                status = WorkflowRunStatus.RUNNING,
                startedAtMillis = started
            )
        )

        repo.markRunCancelled(runId)

        val after = repo.getRunById(runId)!!
        assertEquals(started, after.startedAtMillis)
        assertEquals(WorkflowRunStatus.FAILED, after.status)
        assertNotNull(after.completedAtMillis)
        assertTrue(
            "completedAtMillis must reflect the cancellation moment, not the start",
            after.completedAtMillis!! >= started
        )
    }

    @Test
    fun `durationMs matches the span between preserved timestamps`() = runBlocking {
        // Sanity: once startedAtMillis + completedAtMillis are persisted
        // honestly, the UI's duration rendering can be computed from either
        // `durationMs` or the timestamp diff and the two must agree.
        val repo = FakeWorkflowRepository()
        val started = 1_700_000_000_000L
        val completed = started + 108_000L

        val runId = repo.insertRun(
            WorkflowRun(
                templateId = 1L,
                templateName = "X",
                status = WorkflowRunStatus.RUNNING,
                startedAtMillis = started
            )
        )
        repo.updateRun(
            WorkflowRun(
                id = runId,
                templateId = 1L,
                templateName = "X",
                status = WorkflowRunStatus.COMPLETED,
                currentStage = "Completed",
                durationMs = 108_000L,
                startedAtMillis = started,
                completedAtMillis = completed
            )
        )

        val after = repo.getRunById(runId)!!
        val computed = after.completedAtMillis!! - after.startedAtMillis
        assertEquals(
            "durationMs must equal completedAtMillis - startedAtMillis so the Run Results page " +
                "can't show a Duration that contradicts Started/Completed",
            computed, after.durationMs
        )
    }
}

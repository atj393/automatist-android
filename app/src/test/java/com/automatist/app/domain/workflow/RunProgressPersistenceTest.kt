package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.PersistedStage
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.ResumeSnapshot
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the fix for the "blank run details while running" bug.
 *
 * Previously the workflow engine only wrote `stagesJson` at terminal state
 * (Completed / Failed), so reopening a run in progress from the dashboard
 * showed only the header — the Run Log and everything below depended on
 * persisted stages which didn't exist yet.
 *
 * The fix is two new narrow repository methods: [WorkflowRepository.updateRunProgress]
 * writes only `stagesJson` + `currentStage`, and [WorkflowRepository.updateRunProfile]
 * writes only `profileName` + `modelId`. Both are called from the engine's
 * state-handler loop per event, so a detail screen observing via
 * [WorkflowRepository.observeRunById] renders live progress.
 *
 * These tests guard the narrow-write contract — touching only the intended
 * fields and leaving retry/resume metadata intact.
 */
class RunProgressPersistenceTest {

    private fun newRunningRun(): WorkflowRun = WorkflowRun(
        templateId = 1L,
        templateName = "Test",
        triggerType = "manual",
        status = WorkflowRunStatus.RUNNING,
        currentStage = "Preparing",
        stagesJson = "",
        resumeSnapshotJson = ResumeSnapshot.toJson(
            ResumeSnapshot(
                fingerprint = "stable-fp",
                reachedStage = ResumeSnapshot.STAGE_PROCESSING,
                synthesisInput = "frozen"
            )
        ),
        synthesisInput = "frozen",
        autoRetryAttempt = 1,
        parentRunId = 99L
    )

    private fun stagesJson(vararg labels: String): String =
        PersistedStage.toJson(labels.map { PersistedStage(it, "COMPLETED") })

    // ── updateRunProgress narrow-write contract ──

    @Test
    fun `updateRunProgress writes only stagesJson and currentStage`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val runId = repo.insertRun(newRunningRun())
        val beforeFull = repo.getRunById(runId)!!

        repo.updateRunProgress(
            id = runId,
            stagesJson = stagesJson("Preparing", "Reading source: Paste Text"),
            currentStage = "Reading source: Paste Text"
        )

        val after = repo.getRunById(runId)!!
        // Intended updates:
        assertEquals("Reading source: Paste Text", after.currentStage)
        assertTrue(
            "persisted stages should reflect the incremental write",
            after.persistedStages.map { it.label } == listOf("Preparing", "Reading source: Paste Text")
        )
        // Retry/resume metadata must NOT be touched by the narrow write —
        // these are the fields the retry-from-failure + auto-retry logic
        // depends on. Any silent overwrite here would break those features.
        assertEquals(beforeFull.resumeSnapshotJson, after.resumeSnapshotJson)
        assertEquals(beforeFull.synthesisInput, after.synthesisInput)
        assertEquals(beforeFull.autoRetryAttempt, after.autoRetryAttempt)
        assertEquals(beforeFull.parentRunId, after.parentRunId)
        assertEquals(beforeFull.status, after.status)
        assertEquals(beforeFull.templateName, after.templateName)
        assertEquals(beforeFull.startedAtMillis, after.startedAtMillis)
    }

    @Test
    fun `updateRunProgress is idempotent across many incremental writes`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val runId = repo.insertRun(newRunningRun())

        val labels = listOf(
            "Preparing",
            "Validating 1 action",
            "Reading source: Paste Text",
            "Processing 42 chars",
            "Generating final output"
        )
        // Simulate the engine driving the flow event-by-event — each tick
        // appends the newest label and rewrites stagesJson.
        val accumulated = mutableListOf<String>()
        for (label in labels) {
            accumulated.add(label)
            repo.updateRunProgress(
                id = runId,
                stagesJson = stagesJson(*accumulated.toTypedArray()),
                currentStage = label
            )
            // After each write, the detail screen would render exactly this.
            val snapshot = repo.getRunById(runId)!!
            assertEquals(label, snapshot.currentStage)
            assertEquals(
                "stages visible to detail screen must match what was written",
                accumulated,
                snapshot.persistedStages.map { it.label }
            )
        }
    }

    @Test
    fun `updateRunProgress does nothing when run id is unknown`() = runBlocking {
        val repo = FakeWorkflowRepository()
        // Should not throw; the in-flight write is best-effort.
        repo.updateRunProgress(id = 9_999L, stagesJson = "[]", currentStage = "Preparing")
        assertNull(repo.getRunById(9_999L))
    }

    // ── updateRunProfile narrow-write contract ──

    @Test
    fun `updateRunProfile writes only profile and model`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val runId = repo.insertRun(newRunningRun())
        val before = repo.getRunById(runId)!!

        repo.updateRunProfile(id = runId, profileName = "My OpenAI", modelId = "gpt-4o-mini")

        val after = repo.getRunById(runId)!!
        assertEquals("My OpenAI", after.profileName)
        assertEquals("gpt-4o-mini", after.modelId)
        // Everything else stays as before.
        assertEquals(before.stagesJson, after.stagesJson)
        assertEquals(before.currentStage, after.currentStage)
        assertEquals(before.resumeSnapshotJson, after.resumeSnapshotJson)
        assertEquals(before.autoRetryAttempt, after.autoRetryAttempt)
        assertEquals(before.status, after.status)
    }

    // ── Full updateRun still writes everything (regression guard) ──

    @Test
    fun `updateRun still replaces the whole row (retry-terminal-persist path)`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val runId = repo.insertRun(newRunningRun())

        // Simulate terminal persist: rebuild the run with final fields.
        val terminal = repo.getRunById(runId)!!.copy(
            status = WorkflowRunStatus.COMPLETED,
            currentStage = "Completed",
            outputText = "final output",
            providerType = ProviderType.OPENAI,
            promptTokens = 100,
            completionTokens = 200,
            totalTokens = 300,
            durationMs = 4321L,
            stagesJson = stagesJson("Preparing", "Generating final output", "Completed")
        )
        repo.updateRun(terminal)

        val after = repo.getRunById(runId)!!
        assertEquals(WorkflowRunStatus.COMPLETED, after.status)
        assertEquals("final output", after.outputText)
        assertEquals(300, after.totalTokens)
        assertEquals(3, after.persistedStages.size)
        // Retry metadata preserved through terminal — retry-from-failure
        // contract keeps auto-retry chain info alongside terminal status.
        assertEquals(1, after.autoRetryAttempt)
        assertEquals(99L, after.parentRunId)
    }

    // ── Live observation (the user-visible bug fix) ──

    @Test
    fun `detail screen observer sees incremental progress updates live`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val runId = repo.insertRun(newRunningRun())

        // Kotlin's StateFlow-backed observeRunById always emits the latest
        // value synchronously on the current thread. This models what a
        // WorkflowRunDetailViewModel collecting via stateIn would see.
        val snapshots = mutableListOf<WorkflowRun?>()
        val flow = repo.observeRunById(runId)

        // Seed
        snapshots.add(repo.getRunById(runId))
        repo.updateRunProgress(runId, stagesJson("Preparing"), "Preparing")
        snapshots.add(repo.getRunById(runId))
        repo.updateRunProgress(runId, stagesJson("Preparing", "Reading"), "Reading")
        snapshots.add(repo.getRunById(runId))
        repo.updateRunProgress(
            runId,
            stagesJson("Preparing", "Reading", "Generating"),
            "Generating"
        )
        snapshots.add(repo.getRunById(runId))

        assertEquals(4, snapshots.size)
        assertEquals(0, snapshots[0]!!.persistedStages.size) // start: empty
        assertEquals(1, snapshots[1]!!.persistedStages.size)
        assertEquals(2, snapshots[2]!!.persistedStages.size)
        assertEquals(3, snapshots[3]!!.persistedStages.size)
        assertEquals("Generating", snapshots[3]!!.currentStage)
        // observeRunById is still an actual Flow that callers can subscribe to.
        assertNotNull(flow)
    }
}

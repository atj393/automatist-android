package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.PersistedStage
import com.automatist.app.domain.models.ResumeSnapshot
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the release-critical "manual-run is honest about its lifetime"
 * contract.
 *
 * When a user leaves the run screen, the owning [WorkflowRunViewModel]'s
 * scope is cancelled and the engine coroutine stops mid-stream. Before
 * this fix the DB row stayed `RUNNING` forever (until the 1-hour app-start
 * sweep), so reopening the run showed a phantom spinner on something that
 * was no longer actually executing.
 *
 * The new contract: [WorkflowRepository.markRunCancelled] flips a RUNNING
 * row to FAILED with a cancellation message, atomically gated so a
 * terminal write from the engine that won the race is never clobbered.
 * Incremental progress, retry metadata, and resume snapshot all survive.
 */
class ManualRunCancellationTest {

    private fun runningRun(
        status: WorkflowRunStatus = WorkflowRunStatus.RUNNING
    ): WorkflowRun = WorkflowRun(
        templateId = 1L,
        templateName = "T",
        triggerType = "manual",
        status = status,
        currentStage = "Reading source: Paste Text",
        stagesJson = PersistedStage.toJson(
            listOf(
                PersistedStage("Preparing", "COMPLETED"),
                PersistedStage("Reading source: Paste Text", "RUNNING")
            )
        ),
        synthesisInput = "frozen combined",
        resumeSnapshotJson = ResumeSnapshot.toJson(
            ResumeSnapshot(
                fingerprint = "fp-1",
                reachedStage = ResumeSnapshot.STAGE_PROCESSING,
                synthesisInput = "frozen combined"
            )
        ),
        autoRetryAttempt = 2,
        parentRunId = 77L
    )

    // ── The primary contract ──

    @Test
    fun `markRunCancelled flips a RUNNING row to FAILED with a clear message`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val id = repo.insertRun(runningRun())

        repo.markRunCancelled(id)

        val after = repo.getRunById(id)!!
        assertEquals(WorkflowRunStatus.FAILED, after.status)
        assertNotNull(after.errorMessage)
        assertTrue(
            "error message must clearly signal the cancellation reason to the user",
            after.errorMessage!!.contains("cancel", ignoreCase = true)
        )
        assertEquals("Cancelled", after.currentStage)
        assertNotNull(after.completedAtMillis)
    }

    // ── The "don't clobber a terminal write" atomicity guarantee ──

    @Test
    fun `markRunCancelled leaves a COMPLETED run untouched`() = runBlocking {
        // Race: engine emitted Completed and the terminal write landed before
        // the VM noticed it was being cleared. The cancellation write must
        // not rewrite a successful run as failed.
        val repo = FakeWorkflowRepository()
        val id = repo.insertRun(runningRun(status = WorkflowRunStatus.COMPLETED).copy(
            outputText = "final output",
            completedAtMillis = 1L
        ))
        val before = repo.getRunById(id)!!

        repo.markRunCancelled(id)

        val after = repo.getRunById(id)!!
        assertEquals(before, after)
    }

    @Test
    fun `markRunCancelled leaves a FAILED run untouched`() = runBlocking {
        // Race: engine emitted Failed before the VM cleared. Keep the real
        // error, don't overwrite with the cancellation copy.
        val repo = FakeWorkflowRepository()
        val id = repo.insertRun(runningRun(status = WorkflowRunStatus.FAILED).copy(
            errorMessage = "auth token expired",
            completedAtMillis = 1L
        ))
        val before = repo.getRunById(id)!!

        repo.markRunCancelled(id)

        val after = repo.getRunById(id)!!
        assertEquals(before, after)
    }

    // ── What cancellation preserves ──

    @Test
    fun `markRunCancelled preserves partial stage history`() = runBlocking {
        // The detail screen needs to show what got done before the user
        // backed out — so stagesJson must survive untouched.
        val repo = FakeWorkflowRepository()
        val id = repo.insertRun(runningRun())
        val stagesBefore = repo.getRunById(id)!!.stagesJson

        repo.markRunCancelled(id)

        assertEquals(stagesBefore, repo.getRunById(id)!!.stagesJson)
        // And parsed it still yields the same two stages.
        val parsed = repo.getRunById(id)!!.persistedStages
        assertEquals(2, parsed.size)
        assertEquals("Preparing", parsed[0].label)
    }

    @Test
    fun `markRunCancelled preserves retry chain metadata`() = runBlocking {
        // A cancelled auto-retry must still know which chain it belonged to,
        // so history can continue to group it correctly with its siblings.
        val repo = FakeWorkflowRepository()
        val id = repo.insertRun(runningRun())

        repo.markRunCancelled(id)

        val after = repo.getRunById(id)!!
        assertEquals(2, after.autoRetryAttempt)
        assertEquals(77L, after.parentRunId)
    }

    @Test
    fun `markRunCancelled preserves resume snapshot so Run Again can still resume`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val id = repo.insertRun(runningRun())
        val before = repo.getRunById(id)!!

        repo.markRunCancelled(id)

        val after = repo.getRunById(id)!!
        assertEquals(before.resumeSnapshotJson, after.resumeSnapshotJson)
        assertEquals(before.synthesisInput, after.synthesisInput)
    }

    // ── Defensive / no-op ──

    @Test
    fun `markRunCancelled is a safe no-op on an unknown runId`() = runBlocking {
        val repo = FakeWorkflowRepository()
        // Should not throw.
        repo.markRunCancelled(9_999L)
    }

    @Test
    fun `cancellation fix does NOT affect scheduled (worker-driven) runs`() = runBlocking {
        // Scheduled runs live outside any VM — they persist their own terminal
        // state from [WorkflowWorker]. To stand in for "untouched by the VM
        // cancellation path", this test exercises a representative worker-run
        // row through the same atomic gate. A COMPLETED worker-run must not
        // be reinterpreted as cancelled if somehow markRunCancelled was
        // called with its id.
        val repo = FakeWorkflowRepository()
        val workerRunId = repo.insertRun(
            runningRun(status = WorkflowRunStatus.COMPLETED).copy(
                triggerType = "scheduled",
                outputText = "worker output",
                completedAtMillis = 1L
            )
        )

        repo.markRunCancelled(workerRunId)

        val after = repo.getRunById(workerRunId)!!
        assertEquals(
            "scheduled run in COMPLETED state must remain COMPLETED",
            WorkflowRunStatus.COMPLETED, after.status
        )
        assertEquals("worker output", after.outputText)
        assertFalse(
            "no cancellation message should be inserted onto a COMPLETED row",
            after.errorMessage?.contains("cancel", ignoreCase = true) == true
        )
    }
}

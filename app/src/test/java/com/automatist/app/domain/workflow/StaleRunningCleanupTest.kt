package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the release-hardening contract for stale RUNNING row cleanup.
 *
 * Problem: if the app is killed mid-run (crash, OOM, user force-stop) the
 * VM-driven execution path has no worker-level recovery, so the run row
 * stays status=RUNNING forever. The detail screen would then show an
 * infinite spinner.
 *
 * Fix: [WorkflowRepository.failAllStaleRunningRecordsOlderThan] is called
 * from app start with a 1-hour grace window, and
 * [WorkflowRepository.failStaleRunningRecords] (scoped to one template,
 * called from the worker's pre-run sweep) now honors a 60-second grace
 * so it cannot clobber a concurrently-starting VM run.
 *
 * These tests guard the grace-window contract — they are the fence that
 * keeps a future edit from accidentally going back to "kill all RUNNING".
 */
class StaleRunningCleanupTest {

    private fun runningRun(templateId: Long, startedAtMillis: Long): WorkflowRun =
        WorkflowRun(
            templateId = templateId,
            templateName = "T$templateId",
            status = WorkflowRunStatus.RUNNING,
            startedAtMillis = startedAtMillis
        )

    // ── failAllStaleRunningRecordsOlderThan: global sweep, age-gated ──

    @Test
    fun `global sweep only fails rows older than cutoff`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val now = System.currentTimeMillis()
        val oldId = repo.insertRun(runningRun(1L, now - 2 * 60 * 60 * 1000L)) // 2h old
        val freshId = repo.insertRun(runningRun(1L, now - 5_000L))            // 5s old

        repo.failAllStaleRunningRecordsOlderThan(now - 60 * 60 * 1000L) // 1h cutoff

        assertEquals(WorkflowRunStatus.FAILED, repo.getRunById(oldId)!!.status)
        assertEquals(WorkflowRunStatus.RUNNING, repo.getRunById(freshId)!!.status)
    }

    @Test
    fun `global sweep leaves terminal rows untouched`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val now = System.currentTimeMillis()
        val completed = repo.insertRun(
            runningRun(1L, now - 3 * 60 * 60 * 1000L).copy(
                status = WorkflowRunStatus.COMPLETED,
                completedAtMillis = now - 2 * 60 * 60 * 1000L
            )
        )
        val failed = repo.insertRun(
            runningRun(1L, now - 3 * 60 * 60 * 1000L).copy(
                status = WorkflowRunStatus.FAILED,
                errorMessage = "earlier failure",
                completedAtMillis = now - 2 * 60 * 60 * 1000L
            )
        )

        repo.failAllStaleRunningRecordsOlderThan(now)

        // Prior terminal states must NOT be clobbered — re-writing a prior
        // completed run as failed would corrupt history.
        assertEquals(WorkflowRunStatus.COMPLETED, repo.getRunById(completed)!!.status)
        assertEquals(WorkflowRunStatus.FAILED, repo.getRunById(failed)!!.status)
        assertEquals("earlier failure", repo.getRunById(failed)!!.errorMessage)
    }

    @Test
    fun `global sweep writes an error message on killed rows`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val now = System.currentTimeMillis()
        val id = repo.insertRun(runningRun(1L, now - 2 * 60 * 60 * 1000L))

        repo.failAllStaleRunningRecordsOlderThan(now - 60 * 60 * 1000L)

        val after = repo.getRunById(id)!!
        assertEquals(WorkflowRunStatus.FAILED, after.status)
        assert(!after.errorMessage.isNullOrBlank()) {
            "expected a non-blank errorMessage, got ${after.errorMessage}"
        }
    }

    @Test
    fun `global sweep is a no-op when no stale rows exist`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val now = System.currentTimeMillis()
        val a = repo.insertRun(runningRun(1L, now - 5_000L))
        val b = repo.insertRun(runningRun(2L, now - 1_000L))

        repo.failAllStaleRunningRecordsOlderThan(now - 60 * 60 * 1000L)

        assertEquals(WorkflowRunStatus.RUNNING, repo.getRunById(a)!!.status)
        assertEquals(WorkflowRunStatus.RUNNING, repo.getRunById(b)!!.status)
    }

    // ── failStaleRunningRecords(templateId): per-template sweep, 60s grace ──

    @Test
    fun `per-template sweep leaves fresh rows alone`() = runBlocking {
        // Simulates a worker starting up while a VM run for the same template
        // is already in flight — the 60s grace must not kill the in-flight row.
        val repo = FakeWorkflowRepository()
        val now = System.currentTimeMillis()
        val freshVmRunId = repo.insertRun(runningRun(1L, now - 2_000L))

        repo.failStaleRunningRecords(templateId = 1L)

        assertEquals(
            "VM run less than 60s old must survive the worker's pre-run sweep",
            WorkflowRunStatus.RUNNING,
            repo.getRunById(freshVmRunId)!!.status
        )
    }

    @Test
    fun `per-template sweep kills truly orphaned rows older than grace`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val now = System.currentTimeMillis()
        val orphanId = repo.insertRun(runningRun(1L, now - 10 * 60 * 1000L)) // 10m old

        repo.failStaleRunningRecords(templateId = 1L)

        assertEquals(WorkflowRunStatus.FAILED, repo.getRunById(orphanId)!!.status)
    }

    @Test
    fun `per-template sweep is scoped to the requested template only`() = runBlocking {
        val repo = FakeWorkflowRepository()
        val now = System.currentTimeMillis()
        val template1Orphan = repo.insertRun(runningRun(1L, now - 10 * 60 * 1000L))
        val template2Orphan = repo.insertRun(runningRun(2L, now - 10 * 60 * 1000L))

        repo.failStaleRunningRecords(templateId = 1L)

        assertEquals(WorkflowRunStatus.FAILED, repo.getRunById(template1Orphan)!!.status)
        assertEquals(
            "template 2 row must be untouched by a template 1-scoped sweep",
            WorkflowRunStatus.RUNNING,
            repo.getRunById(template2Orphan)!!.status
        )
    }
}

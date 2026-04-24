package com.automatist.app.data.local

import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
import org.junit.Assert.*
import org.junit.Test

class WorkflowRunTimeFieldsTest {

    @Test
    fun `completed run should have distinct startedAtMillis and completedAtMillis`() {
        val startTime = 1000L
        val completedTime = 5000L // 4 seconds later

        val run = WorkflowRun(
            id = 1L,
            templateId = 1L,
            templateName = "Test Workflow",
            status = WorkflowRunStatus.COMPLETED,
            startedAtMillis = startTime,
            completedAtMillis = completedTime,
            durationMs = completedTime - startTime
        )

        assertEquals("startedAtMillis should be preserved", startTime, run.startedAtMillis)
        assertEquals("completedAtMillis should be set to completion time", completedTime, run.completedAtMillis)
        assertEquals("durationMs should be correct (completedAt - startedAt)", 4000L, run.durationMs)
    }

    @Test
    fun `running run should have no completedAtMillis`() {
        val startTime = 1000L

        val run = WorkflowRun(
            id = 1L,
            templateId = 1L,
            templateName = "Test Workflow",
            status = WorkflowRunStatus.RUNNING,
            startedAtMillis = startTime
        )

        assertEquals(startTime, run.startedAtMillis)
        assertNull("completedAtMillis should be null for running runs", run.completedAtMillis)
    }

    @Test
    fun `failed run should have distinct startedAtMillis and completedAtMillis`() {
        val startTime = 2000L
        val failedTime = 8000L

        val run = WorkflowRun(
            id = 2L,
            templateId = 1L,
            templateName = "Test Workflow",
            status = WorkflowRunStatus.FAILED,
            startedAtMillis = startTime,
            completedAtMillis = failedTime,
            errorMessage = "Network error",
            durationMs = failedTime - startTime
        )

        assertEquals("startedAtMillis", startTime, run.startedAtMillis)
        assertEquals("completedAtMillis", failedTime, run.completedAtMillis)
        assertEquals("durationMs should be correct", 6000L, run.durationMs)
        assertNotNull("errorMessage", run.errorMessage)
    }

    @Test
    fun `entity mapper should preserve startedAtMillis and completedAtMillis`() {
        val startTime = 1000L
        val completedTime = 5000L

        val run = WorkflowRun(
            id = 1L,
            templateId = 1L,
            templateName = "Test Workflow",
            status = WorkflowRunStatus.COMPLETED,
            currentStage = "Completed",
            startedAtMillis = startTime,
            completedAtMillis = completedTime,
            durationMs = completedTime - startTime
        )

        // Convert to entity
        val entity = run.toEntity()
        assertEquals("entity.startedAtMillis should match", startTime, entity.startedAtMillis)
        assertEquals("entity.completedAtMillis should match", completedTime, entity.completedAtMillis)

        // Convert back to domain
        val domainRun = entity.toDomain()
        assertEquals("roundtrip should preserve startedAtMillis", startTime, domainRun.startedAtMillis)
        assertEquals("roundtrip should preserve completedAtMillis", completedTime, domainRun.completedAtMillis)
    }

    @Test
    fun `completion time should be strictly after start time`() {
        val startTime = System.currentTimeMillis()
        Thread.sleep(10) // Ensure time advance
        val completedTime = System.currentTimeMillis()

        val run = WorkflowRun(
            id = 1L,
            templateId = 1L,
            templateName = "Test Workflow",
            status = WorkflowRunStatus.COMPLETED,
            startedAtMillis = startTime,
            completedAtMillis = completedTime
        )

        assertTrue("completedAtMillis must be strictly after startedAtMillis", completedTime > startTime)
    }
}

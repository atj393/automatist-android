package com.automatist.app.domain.workflow

import com.automatist.app.domain.access.PlanState
import com.automatist.app.domain.access.PlanType
import com.automatist.app.domain.models.WorkflowTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies workflow activation behavior after the free/open-source migration.
 *
 * - The editor's new-workflow enable decision (WorkflowEditorViewModel.save())
 *   still routes through PlanState.canActivateWorkflow and is always permissive.
 * - The details enable toggle (WorkflowDetailsViewModel.toggleEnabled()) simply
 *   flips isEnabled on one workflow; it never blocks and never disables another
 *   (the old one-active-workflow gate and its "switch active" workaround are gone).
 *
 * The ViewModels are Android components backed by a WorkManager ScheduleManager and
 * are not directly unit-testable in this JVM suite, so — as with BillingEntitlementTest —
 * we exercise the exact repository/decision logic against a real FakeWorkflowRepository.
 * The scheduling registration/cancellation side effects are device-tested (see the
 * migration doc's smoke-test checklist), not reproduced here.
 */
class WorkflowActivationPolicyTest {

    // Mirror of WorkflowEditorViewModel.save() enable-decision for a NEW workflow.
    private suspend fun shouldEnableNewWorkflow(repo: FakeWorkflowRepository, plan: PlanState): Boolean {
        val activeCount = repo.getAllTemplates().first().count { it.isEnabled }
        return plan.canActivateWorkflow(activeCount)
    }

    // Mirror of WorkflowDetailsViewModel.toggleEnabled()'s repository effect (the
    // ScheduleManager/WorkManager calls are device-tested, not reproduced here).
    private suspend fun toggleEnabledOn(repo: FakeWorkflowRepository, id: Long) {
        val t = repo.getTemplateById(id) ?: return
        repo.updateTemplate(t.copy(isEnabled = !t.isEnabled))
    }

    private fun repoWithActiveWorkflows(count: Int): FakeWorkflowRepository {
        val repo = FakeWorkflowRepository()
        repeat(count) { i ->
            repo.seedTemplate(WorkflowTemplate(name = "Active $i", isEnabled = true))
        }
        return repo
    }

    private suspend fun enabledCount(repo: FakeWorkflowRepository): Int =
        repo.getAllTemplates().first().count { it.isEnabled }

    // ── New-workflow enablement (editor) ──

    @Test
    fun `new workflow is enabled when a first workflow is already active`() = runTest {
        val repo = repoWithActiveWorkflows(1)
        assertTrue(shouldEnableNewWorkflow(repo, PlanState(PlanType.FREE)))
    }

    @Test
    fun `new workflow is not silently paused for a free user with many active`() = runTest {
        val repo = repoWithActiveWorkflows(25)
        assertTrue(shouldEnableNewWorkflow(repo, PlanState(PlanType.FREE)))
    }

    // ── Enable toggle (details) ──

    @Test
    fun `enabling a second workflow leaves both enabled without a dialog`() = runTest {
        val repo = FakeWorkflowRepository()
        repo.seedTemplate(WorkflowTemplate(name = "First", isEnabled = true))
        val second = repo.seedTemplate(WorkflowTemplate(name = "Second", isEnabled = false))

        toggleEnabledOn(repo, second)

        assertEquals(2, enabledCount(repo))
    }

    @Test
    fun `multiple workflows can be enabled independently`() = runTest {
        val repo = FakeWorkflowRepository()
        val a = repo.seedTemplate(WorkflowTemplate(name = "A", isEnabled = false))
        val b = repo.seedTemplate(WorkflowTemplate(name = "B", isEnabled = false))
        val c = repo.seedTemplate(WorkflowTemplate(name = "C", isEnabled = false))

        toggleEnabledOn(repo, a)
        toggleEnabledOn(repo, b)
        toggleEnabledOn(repo, c)

        assertEquals(3, enabledCount(repo))
    }

    @Test
    fun `enabling one workflow does not disable any other workflow`() = runTest {
        val repo = FakeWorkflowRepository()
        val first = repo.seedTemplate(WorkflowTemplate(name = "First", isEnabled = true))
        val second = repo.seedTemplate(WorkflowTemplate(name = "Second", isEnabled = false))

        toggleEnabledOn(repo, second)

        // The previously-active workflow must remain active (no switch-active workaround).
        assertTrue(repo.getTemplateById(first)!!.isEnabled)
        assertTrue(repo.getTemplateById(second)!!.isEnabled)
    }

    @Test
    fun `disabling a workflow only disables that workflow`() = runTest {
        val repo = FakeWorkflowRepository()
        val keep = repo.seedTemplate(WorkflowTemplate(name = "Keep", isEnabled = true))
        val turnOff = repo.seedTemplate(WorkflowTemplate(name = "TurnOff", isEnabled = true))

        toggleEnabledOn(repo, turnOff)

        assertFalse(repo.getTemplateById(turnOff)!!.isEnabled)
        assertTrue(repo.getTemplateById(keep)!!.isEnabled)
    }
}

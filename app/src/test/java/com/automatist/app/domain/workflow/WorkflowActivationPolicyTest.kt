package com.automatist.app.domain.workflow

import com.automatist.app.domain.access.PlanState
import com.automatist.app.domain.access.PlanType
import com.automatist.app.domain.models.WorkflowTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies the workflow-activation decisions made by
 * WorkflowEditorViewModel.save() (new-workflow enablement) and
 * WorkflowDetailsViewModel.checkActivationBlocked() (enable toggle).
 *
 * Those methods live in Android ViewModels (viewModelScope + a WorkManager-backed
 * ScheduleManager) and are not directly unit-testable in this JVM suite, so — as
 * with BillingEntitlementTest — we exercise the exact decision expressions against
 * a real FakeWorkflowRepository and the real PlanState policy. Scheduling
 * registration/cancellation is unchanged by the migration and is not re-tested here.
 */
class WorkflowActivationPolicyTest {

    // Mirror of WorkflowEditorViewModel.save() enable-decision for a NEW workflow.
    private suspend fun shouldEnableNewWorkflow(repo: FakeWorkflowRepository, plan: PlanState): Boolean {
        val activeCount = repo.getAllTemplates().first().count { it.isEnabled }
        return plan.canActivateWorkflow(activeCount)
    }

    // Mirror of WorkflowDetailsViewModel.checkActivationBlocked() for enabling `thisId`.
    private suspend fun activationBlockedName(
        repo: FakeWorkflowRepository,
        plan: PlanState,
        thisId: Long
    ): String? {
        if (plan.isProUnlocked) return null
        val activeOthers = repo.getAllTemplates().first().filter { it.isEnabled && it.id != thisId }
        return if (plan.canActivateWorkflow(activeOthers.size)) null
        else activeOthers.firstOrNull()?.name ?: "another workflow"
    }

    private fun repoWithActiveWorkflows(count: Int): FakeWorkflowRepository {
        val repo = FakeWorkflowRepository()
        repeat(count) { i ->
            repo.seedTemplate(WorkflowTemplate(name = "Active $i", isEnabled = true))
        }
        return repo
    }

    @Test
    fun `new workflow is enabled when a first workflow is already active`() = runTest {
        val repo = repoWithActiveWorkflows(1)
        assertTrue(shouldEnableNewWorkflow(repo, PlanState(PlanType.FREE)))
    }

    @Test
    fun `new workflow is enabled when two workflows are already active`() = runTest {
        val repo = repoWithActiveWorkflows(2)
        assertTrue(shouldEnableNewWorkflow(repo, PlanState(PlanType.FREE)))
    }

    @Test
    fun `new workflow is not silently paused for a free user with many active`() = runTest {
        val repo = repoWithActiveWorkflows(25)
        assertTrue(shouldEnableNewWorkflow(repo, PlanState(PlanType.FREE)))
    }

    @Test
    fun `enabling an additional workflow does not emit an activation-limit request`() = runTest {
        val repo = repoWithActiveWorkflows(3)
        val target = repo.seedTemplate(WorkflowTemplate(name = "Target", isEnabled = false))
        assertNull(activationBlockedName(repo, PlanState(PlanType.FREE), target))
    }

    @Test
    fun `pro user is never activation-blocked`() = runTest {
        val repo = repoWithActiveWorkflows(10)
        val target = repo.seedTemplate(WorkflowTemplate(name = "Target", isEnabled = false))
        assertNull(activationBlockedName(repo, PlanState(PlanType.PRO), target))
    }
}

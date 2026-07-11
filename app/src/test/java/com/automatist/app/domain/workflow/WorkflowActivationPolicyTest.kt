package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.WorkflowTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies workflow enable/disable behavior after billing + entitlement removal.
 * Feature availability is ordinary app behavior now — there is no plan, entitlement,
 * purchase, or active-workflow count involved.
 *
 * The editor/details ViewModels are Android components backed by a WorkManager
 * ScheduleManager and aren't directly unit-testable in this JVM suite, so — as with
 * the other workflow tests — we exercise the exact create/edit/toggle decision logic
 * against a real FakeWorkflowRepository. Scheduling registration/cancellation is
 * device-tested (see the migration doc's smoke-test checklist), not reproduced here.
 */
class WorkflowActivationPolicyTest {

    // Mirror of WorkflowEditorViewModel.save()'s enabled-state decision:
    // editing preserves the stored state; a new workflow uses the default (enabled).
    private suspend fun shouldEnableOnSave(repo: FakeWorkflowRepository, templateId: Long?): Boolean =
        if (templateId != null) repo.getTemplateById(templateId)?.isEnabled ?: true
        else true

    // Mirror of WorkflowDetailsViewModel.toggleEnabled()'s repository effect.
    private suspend fun toggleEnabledOn(repo: FakeWorkflowRepository, id: Long) {
        val t = repo.getTemplateById(id) ?: return
        repo.updateTemplate(t.copy(isEnabled = !t.isEnabled))
    }

    private suspend fun enabledCount(repo: FakeWorkflowRepository): Int =
        repo.getAllTemplates().first().count { it.isEnabled }

    // ── Create ──

    @Test
    fun `new workflow is created enabled by default`() = runTest {
        val repo = FakeWorkflowRepository()
        assertTrue(shouldEnableOnSave(repo, templateId = null))
        // The decision uses WorkflowTemplate's own default, not a plan/entitlement.
        assertTrue(WorkflowTemplate(name = "X").isEnabled)
    }

    @Test
    fun `creating a workflow does not pause existing workflows`() = runTest {
        val repo = FakeWorkflowRepository()
        val existing = repo.seedTemplate(WorkflowTemplate(name = "Existing", isEnabled = true))
        repo.saveTemplate(WorkflowTemplate(name = "New", isEnabled = shouldEnableOnSave(repo, null)))
        assertTrue(repo.getTemplateById(existing)!!.isEnabled)
        assertEquals(2, enabledCount(repo))
    }

    // ── Edit preserves state ──

    @Test
    fun `editing a disabled workflow preserves disabled state`() = runTest {
        val repo = FakeWorkflowRepository()
        val id = repo.seedTemplate(WorkflowTemplate(name = "Paused", isEnabled = false))
        assertFalse(shouldEnableOnSave(repo, id))
    }

    @Test
    fun `editing an enabled workflow preserves enabled state`() = runTest {
        val repo = FakeWorkflowRepository()
        val id = repo.seedTemplate(WorkflowTemplate(name = "Active", isEnabled = true))
        assertTrue(shouldEnableOnSave(repo, id))
    }

    // ── Independent enable/disable ──

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
    fun `enabling one workflow does not disable another`() = runTest {
        val repo = FakeWorkflowRepository()
        val first = repo.seedTemplate(WorkflowTemplate(name = "First", isEnabled = true))
        val second = repo.seedTemplate(WorkflowTemplate(name = "Second", isEnabled = false))

        toggleEnabledOn(repo, second)

        assertTrue(repo.getTemplateById(first)!!.isEnabled)
        assertTrue(repo.getTemplateById(second)!!.isEnabled)
    }

    @Test
    fun `disabling one workflow does not disable another`() = runTest {
        val repo = FakeWorkflowRepository()
        val keep = repo.seedTemplate(WorkflowTemplate(name = "Keep", isEnabled = true))
        val off = repo.seedTemplate(WorkflowTemplate(name = "Off", isEnabled = true))

        toggleEnabledOn(repo, off)

        assertFalse(repo.getTemplateById(off)!!.isEnabled)
        assertTrue(repo.getTemplateById(keep)!!.isEnabled)
    }
}

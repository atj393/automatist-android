package com.automatist.app.domain.access

import org.junit.Assert.*
import org.junit.Test

class PlanStateTest {

    // ── Active-workflow gating (new model) ──

    @Test
    fun `free plan allows activating first workflow`() {
        val state = PlanState(PlanType.FREE, PlanState.FREE_ACTIVE_WORKFLOW_LIMIT)
        assertTrue(state.canActivateWorkflow(0))
    }

    @Test
    fun `free plan blocks activating second workflow`() {
        val state = PlanState(PlanType.FREE, PlanState.FREE_ACTIVE_WORKFLOW_LIMIT)
        assertFalse(state.canActivateWorkflow(1))
    }

    @Test
    fun `free plan blocks activating when many are active`() {
        val state = PlanState(PlanType.FREE, PlanState.FREE_ACTIVE_WORKFLOW_LIMIT)
        assertFalse(state.canActivateWorkflow(5))
    }

    @Test
    fun `pro plan allows activating at any count`() {
        val state = PlanState(PlanType.PRO, Int.MAX_VALUE)
        assertTrue(state.canActivateWorkflow(0))
        assertTrue(state.canActivateWorkflow(1))
        assertTrue(state.canActivateWorkflow(100))
    }

    @Test
    fun `free active workflow limit is 1`() {
        assertEquals(1, PlanState.FREE_ACTIVE_WORKFLOW_LIMIT)
    }

    @Test
    fun `free plan is not pro unlocked`() {
        val state = PlanState(PlanType.FREE)
        assertFalse(state.isProUnlocked)
    }

    @Test
    fun `pro plan is pro unlocked`() {
        val state = PlanState(PlanType.PRO)
        assertTrue(state.isProUnlocked)
    }

    @Test
    fun `free user can create unlimited workflows - gating is on activation not creation`() {
        // The free tier no longer limits workflow creation count.
        // Only activation (isEnabled) is limited to 1 at a time.
        val state = PlanState(PlanType.FREE, PlanState.FREE_ACTIVE_WORKFLOW_LIMIT)
        // With 0 active, can activate one
        assertTrue(state.canActivateWorkflow(0))
        // With 1 active, cannot activate another
        assertFalse(state.canActivateWorkflow(1))
        // But creating (saving) workflows is unrestricted - no method to block it
    }

    @Test
    fun `seeded workflow counts as active if enabled`() {
        // The seeded "Article Briefing" workflow is isEnabled=true.
        // A free user with this active should not be able to activate another.
        val state = PlanState(PlanType.FREE, PlanState.FREE_ACTIVE_WORKFLOW_LIMIT)
        assertFalse(state.canActivateWorkflow(1)) // 1 active = at limit
    }
}

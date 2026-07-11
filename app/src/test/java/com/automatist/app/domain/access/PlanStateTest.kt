package com.automatist.app.domain.access

import org.junit.Assert.*
import org.junit.Test

/**
 * Free/open-source access policy: every plan grants unlimited active workflows.
 * There is no paid tier that unlocks additional active workflows.
 */
class PlanStateTest {

    // ── Unlimited active-workflow access ──

    @Test
    fun `free plan can activate first workflow`() {
        assertTrue(PlanState(PlanType.FREE).canActivateWorkflow(0))
    }

    @Test
    fun `free plan can activate second workflow`() {
        assertTrue(PlanState(PlanType.FREE).canActivateWorkflow(1))
    }

    @Test
    fun `free plan can activate third workflow`() {
        assertTrue(PlanState(PlanType.FREE).canActivateWorkflow(2))
    }

    @Test
    fun `free plan can activate when a hundred are already active`() {
        assertTrue(PlanState(PlanType.FREE).canActivateWorkflow(100))
    }

    @Test
    fun `pro plan can activate at any count`() {
        val state = PlanState(PlanType.PRO)
        assertTrue(state.canActivateWorkflow(0))
        assertTrue(state.canActivateWorkflow(1))
        assertTrue(state.canActivateWorkflow(100))
    }

    // ── Both plans expose the unlimited ceiling ──

    @Test
    fun `both free and pro grant unlimited active workflows`() {
        assertEquals(Int.MAX_VALUE, PlanState(PlanType.FREE).maxActiveWorkflows)
        assertEquals(Int.MAX_VALUE, PlanState(PlanType.PRO).maxActiveWorkflows)
        assertEquals(Int.MAX_VALUE, PlanState.UNLIMITED_ACTIVE_WORKFLOWS)
    }

    @Test
    fun `default plan is truthfully free with unlimited access`() {
        val state = PlanState()
        assertEquals(PlanType.FREE, state.plan)
        assertFalse(state.isProUnlocked)
        assertEquals(Int.MAX_VALUE, state.maxActiveWorkflows)
        assertTrue(state.canActivateWorkflow(5))
    }

    // ── Identity remains distinct even though access is equal ──

    @Test
    fun `free plan is not pro unlocked`() {
        assertFalse(PlanState(PlanType.FREE).isProUnlocked)
    }

    @Test
    fun `pro plan is pro unlocked`() {
        assertTrue(PlanState(PlanType.PRO).isProUnlocked)
    }
}

package com.automatist.app.domain.access

import org.junit.Assert.*
import org.junit.Test

class PlanStateTest {

    @Test
    fun `free plan allows creation when no workflows exist`() {
        val state = PlanState(PlanType.FREE, PlanState.FREE_WORKFLOW_LIMIT)
        assertTrue(state.canCreateWorkflow(0))
    }

    @Test
    fun `free plan blocks creation at limit`() {
        val state = PlanState(PlanType.FREE, PlanState.FREE_WORKFLOW_LIMIT)
        assertFalse(state.canCreateWorkflow(1))
    }

    @Test
    fun `free plan blocks creation above limit`() {
        val state = PlanState(PlanType.FREE, PlanState.FREE_WORKFLOW_LIMIT)
        assertFalse(state.canCreateWorkflow(5))
    }

    @Test
    fun `pro plan allows creation at any count`() {
        val state = PlanState(PlanType.PRO, Int.MAX_VALUE)
        assertTrue(state.canCreateWorkflow(0))
        assertTrue(state.canCreateWorkflow(1))
        assertTrue(state.canCreateWorkflow(100))
    }

    @Test
    fun `free workflow limit is 1`() {
        assertEquals(1, PlanState.FREE_WORKFLOW_LIMIT)
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
    fun `seeded workflow counts toward free limit`() {
        // The seeded "Article Briefing" workflow is a normal workflow.
        // With 1 seeded workflow, a free user should NOT be able to create another.
        val state = PlanState(PlanType.FREE, PlanState.FREE_WORKFLOW_LIMIT)
        assertFalse(state.canCreateWorkflow(1)) // 1 seeded workflow = at limit
    }
}

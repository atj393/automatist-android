package com.automatist.app.domain.access

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the entitlement mapping from purchase ownership to plan state.
 * These test the domain logic that BillingProductAccessRepository depends on.
 */
class BillingEntitlementTest {

    private fun planFromOwnership(billingOwned: Boolean, localOverride: Boolean): PlanState {
        val unlocked = billingOwned || localOverride
        return if (unlocked) PlanState(PlanType.PRO, Int.MAX_VALUE)
        else PlanState(PlanType.FREE, PlanState.FREE_WORKFLOW_LIMIT)
    }

    @Test
    fun `billing owned purchase unlocks Pro`() {
        val state = planFromOwnership(billingOwned = true, localOverride = false)
        assertTrue(state.isProUnlocked)
        assertEquals(PlanType.PRO, state.plan)
    }

    @Test
    fun `no ownership stays Free`() {
        val state = planFromOwnership(billingOwned = false, localOverride = false)
        assertFalse(state.isProUnlocked)
        assertEquals(PlanType.FREE, state.plan)
        assertEquals(PlanState.FREE_WORKFLOW_LIMIT, state.maxWorkflows)
    }

    @Test
    fun `local override alone unlocks Pro`() {
        val state = planFromOwnership(billingOwned = false, localOverride = true)
        assertTrue(state.isProUnlocked)
    }

    @Test
    fun `both billing and local override still Pro`() {
        val state = planFromOwnership(billingOwned = true, localOverride = true)
        assertTrue(state.isProUnlocked)
    }

    @Test
    fun `Pro state allows unlimited workflows`() {
        val state = planFromOwnership(billingOwned = true, localOverride = false)
        assertTrue(state.canCreateWorkflow(0))
        assertTrue(state.canCreateWorkflow(1))
        assertTrue(state.canCreateWorkflow(100))
    }

    @Test
    fun `Free state blocks at limit`() {
        val state = planFromOwnership(billingOwned = false, localOverride = false)
        assertTrue(state.canCreateWorkflow(0))
        assertFalse(state.canCreateWorkflow(1))
    }

    @Test
    fun `canceled purchase does not unlock`() {
        // Simulates: user purchased then got refunded, billing reports not owned
        val state = planFromOwnership(billingOwned = false, localOverride = false)
        assertFalse(state.isProUnlocked)
    }

    @Test
    fun `restore finds owned purchase unlocks Pro`() {
        // Simulates: user reinstalled, restore finds owned purchase
        val state = planFromOwnership(billingOwned = true, localOverride = false)
        assertTrue(state.isProUnlocked)
        assertTrue(state.canCreateWorkflow(50))
    }
}

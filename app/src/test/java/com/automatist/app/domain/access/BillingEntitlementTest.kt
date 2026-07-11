package com.automatist.app.domain.access

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the entitlement mapping from purchase ownership to plan state.
 * Mirrors the logic in BillingProductAccessRepository, which needs an Android
 * Context + BillingManager and so is not directly unit-testable in this JVM suite.
 *
 * As of the free/open-source migration, ownership only sets the FREE/PRO identity
 * label — it no longer restricts feature access (both plans have full access).
 */
class BillingEntitlementTest {

    // Reproduction of BillingProductAccessRepository.planState mapping.
    private fun planFromOwnership(billingOwned: Boolean, localOverride: Boolean): PlanState {
        val unlocked = billingOwned || localOverride
        return if (unlocked) PlanState(PlanType.PRO) else PlanState(PlanType.FREE)
    }

    // ── Identity mapping (retained for compatibility) ──

    @Test
    fun `billing owned purchase still resolves to Pro identity`() {
        val state = planFromOwnership(billingOwned = true, localOverride = false)
        assertTrue(state.isProUnlocked)
        assertEquals(PlanType.PRO, state.plan)
    }

    @Test
    fun `local override alone resolves to Pro identity`() {
        assertTrue(planFromOwnership(billingOwned = false, localOverride = true).isProUnlocked)
    }

    @Test
    fun `both billing and local override still Pro`() {
        assertTrue(planFromOwnership(billingOwned = true, localOverride = true).isProUnlocked)
    }

    @Test
    fun `no ownership stays truthfully Free`() {
        val state = planFromOwnership(billingOwned = false, localOverride = false)
        assertFalse(state.isProUnlocked)
        assertEquals(PlanType.FREE, state.plan)
    }

    @Test
    fun `missing entitlement defaults to a truthful Free identity`() {
        // DataStore returns false when the pro_unlocked key is absent or corrupt,
        // and proOwned starts false — so a missing entitlement resolves to Free.
        val state = planFromOwnership(billingOwned = false, localOverride = false)
        assertEquals(PlanType.FREE, state.plan)
        assertFalse(state.isProUnlocked)
    }

    // ── Access is unrestricted regardless of identity ──

    @Test
    fun `free users get unlimited active workflows`() {
        val state = planFromOwnership(billingOwned = false, localOverride = false)
        assertTrue(state.canActivateWorkflow(0))
        assertTrue(state.canActivateWorkflow(1))
        assertTrue(state.canActivateWorkflow(2))
        assertTrue(state.canActivateWorkflow(100))
    }

    @Test
    fun `pro users get unlimited active workflows`() {
        val state = planFromOwnership(billingOwned = true, localOverride = false)
        assertTrue(state.canActivateWorkflow(0))
        assertTrue(state.canActivateWorkflow(1))
        assertTrue(state.canActivateWorkflow(100))
    }

    @Test
    fun `both free and pro expose the unlimited active-workflow ceiling`() {
        assertEquals(Int.MAX_VALUE, planFromOwnership(false, false).maxActiveWorkflows)
        assertEquals(Int.MAX_VALUE, planFromOwnership(true, false).maxActiveWorkflows)
    }
}

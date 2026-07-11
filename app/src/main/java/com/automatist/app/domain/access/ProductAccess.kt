package com.automatist.app.domain.access

import kotlinx.coroutines.flow.Flow

enum class PlanType(val displayName: String) {
    FREE("Free"),
    PRO("Pro")
}

data class PlanState(
    val plan: PlanType = PlanType.FREE,
    val maxActiveWorkflows: Int = UNLIMITED_ACTIVE_WORKFLOWS
) {
    val isProUnlocked: Boolean get() = plan == PlanType.PRO

    /**
     * Whether another workflow may be activated (enabled).
     *
     * Automatist is free for everyone: every plan is granted
     * [UNLIMITED_ACTIVE_WORKFLOWS], so this returns true for any realistic
     * active-workflow count. "Active" means isEnabled == true on a WorkflowTemplate.
     * Kept as a policy hook so activation decisions stay centralized here.
     */
    fun canActivateWorkflow(currentActiveCount: Int): Boolean =
        isProUnlocked || currentActiveCount < maxActiveWorkflows

    companion object {
        /**
         * No cap on simultaneously-active workflows. There is no paid tier that
         * unlocks additional active workflows — all features are free.
         */
        const val UNLIMITED_ACTIVE_WORKFLOWS = Int.MAX_VALUE
    }
}

/**
 * Abstraction over product-access / entitlement state.
 *
 * As of the free/open-source migration (Phase 1), entitlement no longer controls
 * feature access — every plan grants full access (see [PlanState]). Existing Play
 * Billing ownership is still surfaced as [PlanType.PRO] for identity/compatibility
 * only. The rest of the app reads only through this interface.
 */
interface ProductAccessRepository {
    val planState: Flow<PlanState>
    suspend fun currentPlanState(): PlanState
    suspend fun setProUnlocked(unlocked: Boolean)
}

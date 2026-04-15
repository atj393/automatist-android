package com.automatist.app.domain.access

import kotlinx.coroutines.flow.Flow

enum class PlanType(val displayName: String) {
    FREE("Free"),
    PRO("Pro")
}

data class PlanState(
    val plan: PlanType = PlanType.FREE,
    val maxActiveWorkflows: Int = FREE_ACTIVE_WORKFLOW_LIMIT
) {
    val isProUnlocked: Boolean get() = plan == PlanType.PRO

    /**
     * Free users can create unlimited workflows but only activate one at a time.
     * "Active" means isEnabled == true on a WorkflowTemplate.
     */
    fun canActivateWorkflow(currentActiveCount: Int): Boolean =
        isProUnlocked || currentActiveCount < maxActiveWorkflows

    companion object {
        const val FREE_ACTIVE_WORKFLOW_LIMIT = 1
    }
}

/**
 * Abstraction over product-access / entitlement state.
 *
 * In Phase 5 this is backed by a local DataStore flag.
 * In a later phase, Play Billing replaces the backing implementation.
 * The rest of the app reads only through this interface.
 */
interface ProductAccessRepository {
    val planState: Flow<PlanState>
    suspend fun currentPlanState(): PlanState
    suspend fun setProUnlocked(unlocked: Boolean)
}

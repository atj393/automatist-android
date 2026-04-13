package com.synapse.app.domain.access

import kotlinx.coroutines.flow.Flow

enum class PlanType(val displayName: String) {
    FREE("Free"),
    PRO("Pro")
}

data class PlanState(
    val plan: PlanType = PlanType.FREE,
    val maxWorkflows: Int = FREE_WORKFLOW_LIMIT
) {
    val isProUnlocked: Boolean get() = plan == PlanType.PRO

    fun canCreateWorkflow(currentCount: Int): Boolean =
        isProUnlocked || currentCount < maxWorkflows

    companion object {
        const val FREE_WORKFLOW_LIMIT = 1
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

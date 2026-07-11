package com.automatist.app.data.access

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.automatist.app.data.billing.BillingManager
import com.automatist.app.domain.access.PlanState
import com.automatist.app.domain.access.PlanType
import com.automatist.app.domain.access.ProductAccessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.accessDataStore by preferencesDataStore(name = "product_access")

/**
 * Production entitlement implementation.
 *
 * Effective plan identity = billing ownership OR local override (debug).
 * NOTE: as of the free/open-source migration this only determines the FREE/PRO
 * *label*; it no longer restricts feature access — every plan has full access.
 */
@Singleton
class BillingProductAccessRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingManager: BillingManager
) : ProductAccessRepository {

    private val PRO_UNLOCKED_KEY = booleanPreferencesKey("pro_unlocked")

    private val localOverride: Flow<Boolean> = context.accessDataStore.data.map { prefs ->
        prefs[PRO_UNLOCKED_KEY] ?: false
    }

    override val planState: Flow<PlanState> =
        combine(billingManager.proOwned, localOverride) { billingOwned, localFlag ->
            // Feature access is unrestricted for everyone (free/open-source migration).
            // Ownership is mapped to PRO only for identity/compatibility; both plans
            // grant UNLIMITED_ACTIVE_WORKFLOWS via the PlanState defaults.
            val unlocked = billingOwned || localFlag
            if (unlocked) PlanState(PlanType.PRO) else PlanState(PlanType.FREE)
        }

    override suspend fun currentPlanState(): PlanState = planState.first()

    /**
     * Local override for development / debug.
     * In production, Pro state comes from [billingManager.proOwned].
     */
    override suspend fun setProUnlocked(unlocked: Boolean) {
        context.accessDataStore.edit { prefs ->
            prefs[PRO_UNLOCKED_KEY] = unlocked
        }
    }
}

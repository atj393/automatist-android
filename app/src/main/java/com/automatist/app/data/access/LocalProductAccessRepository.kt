package com.automatist.app.data.access

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.automatist.app.domain.access.PlanState
import com.automatist.app.domain.access.PlanType
import com.automatist.app.domain.access.ProductAccessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.accessDataStore by preferencesDataStore(name = "product_access")

/**
 * Local-only entitlement backed by DataStore.
 * Play Billing will replace this in a later phase by swapping the Hilt binding.
 */
@Singleton
class LocalProductAccessRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ProductAccessRepository {

    private val PRO_UNLOCKED_KEY = booleanPreferencesKey("pro_unlocked")

    override val planState: Flow<PlanState> = context.accessDataStore.data.map { prefs ->
        val unlocked = prefs[PRO_UNLOCKED_KEY] ?: false
        if (unlocked) PlanState(PlanType.PRO, Int.MAX_VALUE)
        else PlanState(PlanType.FREE, PlanState.FREE_ACTIVE_WORKFLOW_LIMIT)
    }

    override suspend fun currentPlanState(): PlanState = planState.first()

    override suspend fun setProUnlocked(unlocked: Boolean) {
        context.accessDataStore.edit { prefs ->
            prefs[PRO_UNLOCKED_KEY] = unlocked
        }
    }
}

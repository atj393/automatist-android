package com.automatist.app.domain.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake [OfflineModelResolver] for tests.
 *
 * Defaults to the built-in [OfflineModelCatalog.ALL_MODELS]; custom entries can be
 * seeded to simulate user-added models, and removed to simulate a forgotten source.
 */
class FakeOfflineModelResolver(
    initial: List<OfflineModelEntry> = OfflineModelCatalog.ALL_MODELS
) : OfflineModelResolver {

    private val state = MutableStateFlow(initial)

    override val models: Flow<List<OfflineModelEntry>> = state

    override suspend fun findById(modelId: String): OfflineModelEntry? =
        state.value.firstOrNull { it.id == modelId }

    /** Seed a custom (user-added) entry so it resolves like the registry would. */
    fun seedCustom(entry: OfflineModelEntry) {
        state.value = state.value + entry
    }

    /** Forget a previously seeded custom entry. */
    fun forget(modelId: String) {
        state.value = state.value.filterNot { it.id == modelId }
    }
}

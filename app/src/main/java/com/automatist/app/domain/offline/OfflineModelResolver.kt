package com.automatist.app.domain.offline

import kotlinx.coroutines.flow.Flow

/**
 * Read-only resolution of the offline models available on this device.
 *
 * Unlike the static [OfflineModelCatalog] (built-in entries only), a resolver also
 * surfaces user-added custom model sources. Domain consumers (e.g. the readiness
 * system) depend on this interface so they can resolve *any* offline model — built-in
 * or custom — without reaching into the data layer.
 *
 * The data-layer implementation merges [OfflineModelCatalog.ALL_MODELS] with the
 * persisted custom-source registry.
 */
interface OfflineModelResolver {

    /** All offline models available to the user: built-in catalog entries plus custom sources. */
    val models: Flow<List<OfflineModelEntry>>

    /**
     * Resolve a model entry by its stable ID across built-in and custom sources.
     * Returns null if no entry with [modelId] is known — callers must fail safely
     * (never silently substitute a different model).
     */
    suspend fun findById(modelId: String): OfflineModelEntry?
}

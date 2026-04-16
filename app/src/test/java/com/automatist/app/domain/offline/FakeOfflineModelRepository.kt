package com.automatist.app.domain.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake for testing offline model readiness.
 */
class FakeOfflineModelRepository : OfflineModelRepository {

    private val statuses = mutableMapOf<String, MutableStateFlow<OfflineModelStatus>>()

    private fun stateFor(modelId: String): MutableStateFlow<OfflineModelStatus> =
        statuses.getOrPut(modelId) { MutableStateFlow(OfflineModelStatus.NOT_INSTALLED) }

    // ── Test helpers ──

    fun setStatus(modelId: String, status: OfflineModelStatus) {
        stateFor(modelId).value = status
    }

    // ── OfflineModelRepository impl ──

    override fun getModelStatus(modelId: String): Flow<OfflineModelStatus> =
        stateFor(modelId)

    override suspend fun requestDownload(modelId: String) {
        stateFor(modelId).value = OfflineModelStatus.DOWNLOADING
    }

    override suspend fun cancelDownload(modelId: String) {
        stateFor(modelId).value = OfflineModelStatus.NOT_INSTALLED
    }

    override suspend fun removeModel(modelId: String) {
        stateFor(modelId).value = OfflineModelStatus.NOT_INSTALLED
    }

    override suspend fun markInstalled(modelId: String) {
        stateFor(modelId).value = OfflineModelStatus.INSTALLED
    }

    override suspend fun markFailed(modelId: String) {
        stateFor(modelId).value = OfflineModelStatus.FAILED
    }
}

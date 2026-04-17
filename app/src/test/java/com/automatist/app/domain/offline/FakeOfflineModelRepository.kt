package com.automatist.app.domain.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory fake for testing offline model readiness.
 */
class FakeOfflineModelRepository : OfflineModelRepository {

    private val statuses = mutableMapOf<String, MutableStateFlow<OfflineModelStatus>>()
    private val progressFlows = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()

    private fun stateFor(modelId: String): MutableStateFlow<OfflineModelStatus> =
        statuses.getOrPut(modelId) { MutableStateFlow(OfflineModelStatus.NOT_INSTALLED) }

    private fun progressFor(modelId: String): MutableStateFlow<DownloadProgress> =
        progressFlows.getOrPut(modelId) { MutableStateFlow(DownloadProgress()) }

    // ── Test helpers ──

    fun setStatus(modelId: String, status: OfflineModelStatus) {
        stateFor(modelId).value = status
    }

    fun setProgress(modelId: String, progress: DownloadProgress) {
        progressFor(modelId).value = progress
    }

    // ── OfflineModelRepository impl ──

    override fun getModelStatus(modelId: String): Flow<OfflineModelStatus> =
        stateFor(modelId)

    override fun getDownloadProgress(modelId: String): StateFlow<DownloadProgress> =
        progressFor(modelId)

    override suspend fun requestDownload(modelId: String) {
        stateFor(modelId).value = OfflineModelStatus.DOWNLOADING
    }

    override suspend fun cancelDownload(modelId: String) {
        progressFor(modelId).value = DownloadProgress()
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

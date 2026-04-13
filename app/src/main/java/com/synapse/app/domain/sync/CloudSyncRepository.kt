package com.synapse.app.domain.sync

import kotlinx.coroutines.flow.Flow

data class SyncStatus(
    val isConnected: Boolean = false,
    val accountEmail: String = "",
    val lastBackupAt: String = "",
    val lastBackupWorkflowCount: Int = 0
)

sealed interface SyncResult {
    data class Success(val message: String) : SyncResult
    data class Error(val message: String) : SyncResult
}

/**
 * Abstraction for Cloud Sync operations.
 * Backed by Google Drive appDataFolder in this implementation.
 */
interface CloudSyncRepository {
    val syncStatus: Flow<SyncStatus>
    suspend fun currentStatus(): SyncStatus
    suspend fun setSyncStatus(status: SyncStatus)
    suspend fun clearSyncStatus()
}

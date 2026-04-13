package com.synapse.app.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.synapse.app.domain.sync.CloudSyncRepository
import com.synapse.app.domain.sync.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "cloud_sync")

@Singleton
class LocalCloudSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : CloudSyncRepository {

    private val CONNECTED_KEY = booleanPreferencesKey("connected")
    private val EMAIL_KEY = stringPreferencesKey("account_email")
    private val LAST_BACKUP_KEY = stringPreferencesKey("last_backup_at")
    private val LAST_COUNT_KEY = intPreferencesKey("last_backup_count")

    override val syncStatus: Flow<SyncStatus> = context.syncDataStore.data.map { prefs ->
        SyncStatus(
            isConnected = prefs[CONNECTED_KEY] ?: false,
            accountEmail = prefs[EMAIL_KEY] ?: "",
            lastBackupAt = prefs[LAST_BACKUP_KEY] ?: "",
            lastBackupWorkflowCount = prefs[LAST_COUNT_KEY] ?: 0
        )
    }

    override suspend fun currentStatus(): SyncStatus = syncStatus.first()

    override suspend fun setSyncStatus(status: SyncStatus) {
        context.syncDataStore.edit { prefs ->
            prefs[CONNECTED_KEY] = status.isConnected
            prefs[EMAIL_KEY] = status.accountEmail
            prefs[LAST_BACKUP_KEY] = status.lastBackupAt
            prefs[LAST_COUNT_KEY] = status.lastBackupWorkflowCount
        }
    }

    override suspend fun clearSyncStatus() {
        context.syncDataStore.edit { it.clear() }
    }
}

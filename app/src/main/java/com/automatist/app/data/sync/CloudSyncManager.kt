package com.automatist.app.data.sync

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.automatist.app.domain.models.WorkflowExportEnvelope
import com.automatist.app.domain.sync.CloudBackupEnvelope
import com.automatist.app.domain.sync.CloudSyncRepository
import com.automatist.app.domain.sync.SyncResult
import com.automatist.app.domain.sync.SyncStatus
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.domain.workflow.WorkflowPortabilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Google Drive appDataFolder operations for manual cloud backup/restore.
 *
 * Uses [WorkflowPortabilityManager] to produce safe portable workflow DTOs — the same
 * format used by export/import — so no secrets are ever uploaded.
 */
@Singleton
class CloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workflowRepository: WorkflowRepository,
    private val portabilityManager: WorkflowPortabilityManager,
    private val syncRepository: CloudSyncRepository
) {

    companion object {
        private const val TAG = "CloudSyncManager"
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ═══════════════════════════════════════════════════════════
    //  DRIVE SERVICE
    // ═══════════════════════════════════════════════════════════

    private fun buildDriveService(accountEmail: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccountName = accountEmail

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Automatist").build()
    }

    // ═══════════════════════════════════════════════════════════
    //  BACKUP
    // ═══════════════════════════════════════════════════════════

    suspend fun backupWorkflows(): SyncResult = withContext(Dispatchers.IO) {
        val status = syncRepository.currentStatus()
        if (!status.isConnected || status.accountEmail.isBlank()) {
            return@withContext SyncResult.Error("No Google account connected.")
        }

        try {
            val drive = buildDriveService(status.accountEmail)

            // Export all workflows using the portable export pipeline (sanitized, no secrets)
            val allTemplates = workflowRepository.getAllTemplates().first()
            val envelopes = allTemplates.map { template ->
                val exportResult = portabilityManager.exportWorkflow(template.id)
                json.decodeFromString<WorkflowExportEnvelope>(exportResult.json)
            }

            val backup = CloudBackupEnvelope(
                syncedAt = java.time.Instant.now().toString(),
                workflows = envelopes
            )

            val backupJson = json.encodeToString(backup)
            val content = ByteArrayContent.fromString("application/json", backupJson)

            // Find existing backup file or create new
            val existingFileId = findBackupFileId(drive)

            if (existingFileId != null) {
                drive.files().update(existingFileId, null, content).execute()
                Log.d(TAG, "Updated existing backup (${envelopes.size} workflows)")
            } else {
                val metadata = com.google.api.services.drive.model.File().apply {
                    name = CloudBackupEnvelope.DRIVE_FILENAME
                    parents = listOf("appDataFolder")
                }
                drive.files().create(metadata, content)
                    .setFields("id")
                    .execute()
                Log.d(TAG, "Created new backup (${envelopes.size} workflows)")
            }

            val now = java.time.Instant.now().toString()
            syncRepository.setSyncStatus(
                status.copy(lastBackupAt = now, lastBackupWorkflowCount = envelopes.size)
            )

            SyncResult.Success("Backed up ${envelopes.size} workflow(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            SyncResult.Error("Backup failed: ${e.message?.take(100) ?: "unknown error"}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  RESTORE
    // ═══════════════════════════════════════════════════════════

    suspend fun restoreWorkflows(): SyncResult = withContext(Dispatchers.IO) {
        val status = syncRepository.currentStatus()
        if (!status.isConnected || status.accountEmail.isBlank()) {
            return@withContext SyncResult.Error("No Google account connected.")
        }

        try {
            val drive = buildDriveService(status.accountEmail)

            val fileId = findBackupFileId(drive)
                ?: return@withContext SyncResult.Error("No cloud backup found.")

            // Download backup JSON
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            val backupJson = outputStream.toString("UTF-8")

            // Parse backup envelope
            val backup = try {
                json.decodeFromString<CloudBackupEnvelope>(backupJson)
            } catch (e: Exception) {
                return@withContext SyncResult.Error("Invalid backup file: ${e.message?.take(100)}")
            }

            if (backup.type != CloudBackupEnvelope.BACKUP_TYPE) {
                return@withContext SyncResult.Error("Not an Automatist backup file.")
            }
            if (backup.schemaVersion > 1) {
                return@withContext SyncResult.Error("Backup version not supported. Update the app.")
            }

            if (backup.workflows.isEmpty()) {
                return@withContext SyncResult.Success("Backup is empty. No workflows to restore.")
            }

            // Import each workflow through the existing portable import pipeline
            var imported = 0
            val warnings = mutableListOf<String>()
            for (envelope in backup.workflows) {
                try {
                    val envelopeJson = json.encodeToString(envelope)
                    val result = portabilityManager.importWorkflow(envelopeJson)
                    imported++
                    warnings.addAll(result.warnings)
                } catch (e: Exception) {
                    val name = envelope.workflow.name
                    warnings.add("Could not restore \"$name\": ${e.message?.take(80)}")
                    Log.w(TAG, "Failed to import workflow: $name", e)
                }
            }

            val message = buildString {
                append("Restored $imported of ${backup.workflows.size} workflow(s)")
                if (warnings.isNotEmpty()) {
                    append(". ${warnings.take(3).joinToString(". ")}")
                    if (warnings.size > 3) append(". +${warnings.size - 3} more warning(s)")
                }
            }
            SyncResult.Success(message)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            SyncResult.Error("Restore failed: ${e.message?.take(100) ?: "unknown error"}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private fun findBackupFileId(drive: Drive): String? {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '${CloudBackupEnvelope.DRIVE_FILENAME}'")
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute()
        return result.files?.firstOrNull()?.id
    }
}

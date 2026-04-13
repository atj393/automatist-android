package com.automatist.app.domain.sync

import com.automatist.app.domain.models.ExportAppInfo
import com.automatist.app.domain.models.WorkflowExportEnvelope
import kotlinx.serialization.Serializable

/**
 * Top-level collection envelope for cloud backup.
 * Contains multiple portable workflow envelopes — same format used by export/import.
 */
@Serializable
data class CloudBackupEnvelope(
    val schemaVersion: Int = 1,
    val type: String = BACKUP_TYPE,
    val syncedAt: String = "",
    val app: ExportAppInfo = ExportAppInfo(),
    val workflows: List<WorkflowExportEnvelope> = emptyList()
) {
    companion object {
        const val BACKUP_TYPE = "automatist-workflow-backup"
        const val DRIVE_FILENAME = "automatist_workflows.json"
    }
}

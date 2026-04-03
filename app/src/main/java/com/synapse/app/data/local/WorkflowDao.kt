package com.synapse.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao {

    // ── Templates ──

    @Query("SELECT * FROM workflow_templates ORDER BY updatedAtMillis DESC")
    fun getAllTemplates(): Flow<List<WorkflowTemplateEntity>>

    @Query("SELECT * FROM workflow_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): WorkflowTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(entity: WorkflowTemplateEntity): Long

    @Update
    suspend fun updateTemplate(entity: WorkflowTemplateEntity)

    @Query("DELETE FROM workflow_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    // ── Runs ──

    @Query("SELECT * FROM workflow_runs WHERE templateId = :templateId ORDER BY startedAtMillis DESC")
    fun getRunsForTemplate(templateId: Long): Flow<List<WorkflowRunEntity>>

    @Query("SELECT * FROM workflow_runs ORDER BY startedAtMillis DESC LIMIT :limit")
    fun getRecentRuns(limit: Int): Flow<List<WorkflowRunEntity>>

    @Query("SELECT * FROM workflow_runs WHERE id = :id")
    suspend fun getRunById(id: Long): WorkflowRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(entity: WorkflowRunEntity): Long

    @Update
    suspend fun updateRun(entity: WorkflowRunEntity)

    // ── Template metadata updates (for lastRun tracking) ──

    @Query("UPDATE workflow_templates SET lastRunAtMillis = :atMillis, lastRunStatus = :status, updatedAtMillis = :atMillis WHERE id = :templateId")
    suspend fun updateTemplateLastRun(templateId: Long, status: String, atMillis: Long)
}

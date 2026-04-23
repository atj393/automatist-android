package com.automatist.app.data.local

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

    @Query("SELECT * FROM workflow_runs WHERE id = :id")
    fun observeRunById(id: Long): Flow<WorkflowRunEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(entity: WorkflowRunEntity): Long

    @Update
    suspend fun updateRun(entity: WorkflowRunEntity)

    // ── Template metadata updates (for lastRun tracking) ──

    @Query("UPDATE workflow_templates SET lastRunAtMillis = :atMillis, lastRunStatus = :status, updatedAtMillis = :atMillis WHERE id = :templateId")
    suspend fun updateTemplateLastRun(templateId: Long, status: String, atMillis: Long)

    // ── Saved Notes ──

    @Query("SELECT * FROM saved_notes ORDER BY updatedAtMillis DESC")
    fun getAllNotes(): Flow<List<SavedNoteEntity>>

    @Query("SELECT * FROM saved_notes WHERE id = :id")
    suspend fun getNoteById(id: Long): SavedNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(entity: SavedNoteEntity): Long

    @Update
    suspend fun updateNote(entity: SavedNoteEntity)

    @Query("DELETE FROM saved_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    // ── Cross-workflow queries (for Previous Output action) ──

    @Query("SELECT * FROM workflow_runs WHERE templateId = :templateId AND status = 'COMPLETED' ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getLatestSuccessfulRun(templateId: Long): WorkflowRunEntity?

    @Query("SELECT * FROM workflow_runs WHERE templateId = :templateId ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getLatestRun(templateId: Long): WorkflowRunEntity?

    @Query("UPDATE workflow_runs SET status = 'FAILED', errorMessage = :message, completedAtMillis = :atMillis WHERE templateId = :templateId AND status = 'RUNNING'")
    suspend fun failStaleRunningRecords(templateId: Long, message: String, atMillis: Long)

    /**
     * Narrow update for in-flight progress. Writes only the stage log + current
     * stage label, leaving every other run field intact. Called repeatedly
     * during execution — once per [ExecutionState] event — so a user who opens
     * the run detail screen mid-run sees live, accurate progress.
     */
    @Query("UPDATE workflow_runs SET stagesJson = :stagesJson, currentStage = :currentStage WHERE id = :id")
    suspend fun updateRunProgress(id: Long, stagesJson: String, currentStage: String)

    /**
     * Persist the resolved profile/model once the engine has picked them. Runs
     * at [GeneratingOutput] time so the detail header shows the right provider
     * even while final generation is still in flight.
     */
    @Query("UPDATE workflow_runs SET profileName = :profileName, modelId = :modelId WHERE id = :id")
    suspend fun updateRunProfile(id: Long, profileName: String, modelId: String)

    // ── Provider Profiles ──

    @Query("SELECT * FROM provider_profiles WHERE isEnabled = 1 ORDER BY isDefault DESC, name ASC")
    fun getAllProfiles(): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ProviderProfileEntity?

    @Query("SELECT * FROM provider_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ProviderProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(entity: ProviderProfileEntity)

    @Update
    suspend fun updateProfile(entity: ProviderProfileEntity)

    @Query("DELETE FROM provider_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("UPDATE provider_profiles SET isDefault = 0")
    suspend fun clearDefaultProfiles()

    @Query("SELECT * FROM provider_profiles WHERE isFallback = 1 LIMIT 1")
    suspend fun getFallbackProfile(): ProviderProfileEntity?

    @Query("UPDATE provider_profiles SET isFallback = 0")
    suspend fun clearFallbackProfiles()
}

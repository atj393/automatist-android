package com.automatist.app.domain.repositories

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.SavedNote
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowTemplate
import kotlinx.coroutines.flow.Flow

interface WorkflowRepository {

    // Templates
    fun getAllTemplates(): Flow<List<WorkflowTemplate>>
    suspend fun getTemplateById(id: Long): WorkflowTemplate?
    suspend fun saveTemplate(template: WorkflowTemplate): Long
    suspend fun updateTemplate(template: WorkflowTemplate)
    suspend fun deleteTemplate(id: Long)

    // Runs
    fun getRunsForTemplate(templateId: Long): Flow<List<WorkflowRun>>
    fun getRecentRuns(limit: Int = 20): Flow<List<WorkflowRun>>
    suspend fun getRunById(id: Long): WorkflowRun?
    fun observeRunById(id: Long): Flow<WorkflowRun?>
    suspend fun insertRun(run: WorkflowRun): Long
    suspend fun updateRun(run: WorkflowRun)
    /**
     * Narrow incremental update used while a run is in flight. Writes only
     * the stage log + current stage label; the rest of the row stays as-is.
     * Called per [ExecutionState] event so the run detail screen renders
     * live progress even when the user navigates away and back.
     */
    suspend fun updateRunProgress(id: Long, stagesJson: String, currentStage: String)
    /** Persist the resolved profile/model once final generation starts. */
    suspend fun updateRunProfile(id: Long, profileName: String, modelId: String)
    /**
     * Mark an in-flight manual run as cancelled because the user left the run
     * screen. Atomic — only flips if the row is still RUNNING, so a terminal
     * write that won the race is not clobbered. Stages/profile/retry metadata
     * are preserved so the detail screen shows what got done before the user
     * backed out.
     */
    suspend fun markRunCancelled(id: Long)
    suspend fun getLatestSuccessfulRun(templateId: Long): WorkflowRun?
    suspend fun getLatestRun(templateId: Long): WorkflowRun?
    suspend fun failStaleRunningRecords(templateId: Long)
    /**
     * Global sweep that marks every RUNNING row older than [cutoffMillis] as
     * FAILED. Call on app start to reconcile orphaned in-flight rows left
     * over from a prior process death — the VM side has no worker-level
     * crash recovery, so absent this sweep a killed run stays RUNNING
     * forever. The age gate prevents clobbering a row from a concurrently
     * starting worker.
     */
    suspend fun failAllStaleRunningRecordsOlderThan(cutoffMillis: Long)

    // Provider Profiles
    fun getAllProfiles(): Flow<List<ProviderProfile>>
    suspend fun getProfileById(id: String): ProviderProfile?
    suspend fun getDefaultProfile(): ProviderProfile?
    suspend fun getFallbackProfile(): ProviderProfile?
    suspend fun saveProfile(profile: ProviderProfile)
    suspend fun deleteProfile(id: String)
    suspend fun setDefaultProfile(id: String)
    suspend fun setFallbackProfile(id: String)

    // Saved Notes
    fun getAllNotes(): Flow<List<SavedNote>>
    suspend fun getNoteById(id: Long): SavedNote?
    suspend fun saveNote(note: SavedNote): Long
    suspend fun updateNote(note: SavedNote)
    suspend fun deleteNote(id: Long)
}

package com.synapse.app.domain.repositories

import com.synapse.app.domain.models.ProviderProfile
import com.synapse.app.domain.models.SavedNote
import com.synapse.app.domain.models.WorkflowRun
import com.synapse.app.domain.models.WorkflowTemplate
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
    suspend fun insertRun(run: WorkflowRun): Long
    suspend fun updateRun(run: WorkflowRun)
    suspend fun getLatestSuccessfulRun(templateId: Long): WorkflowRun?

    // Provider Profiles
    fun getAllProfiles(): Flow<List<ProviderProfile>>
    suspend fun getProfileById(id: String): ProviderProfile?
    suspend fun getDefaultProfile(): ProviderProfile?
    suspend fun saveProfile(profile: ProviderProfile)
    suspend fun deleteProfile(id: String)
    suspend fun setDefaultProfile(id: String)

    // Saved Notes
    fun getAllNotes(): Flow<List<SavedNote>>
    suspend fun getNoteById(id: Long): SavedNote?
    suspend fun saveNote(note: SavedNote): Long
    suspend fun updateNote(note: SavedNote)
    suspend fun deleteNote(id: Long)
}

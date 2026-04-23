package com.automatist.app.data.repositories

import com.automatist.app.data.local.WorkflowDao
import com.automatist.app.data.local.toDomain
import com.automatist.app.data.local.toEntity
import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.SavedNote
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.repositories.WorkflowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomWorkflowRepository @Inject constructor(
    private val dao: WorkflowDao
) : WorkflowRepository {

    // ── Templates ──

    override fun getAllTemplates(): Flow<List<WorkflowTemplate>> =
        dao.getAllTemplates().map { list -> list.map { it.toDomain() } }

    override suspend fun getTemplateById(id: Long): WorkflowTemplate? =
        dao.getTemplateById(id)?.toDomain()

    override suspend fun saveTemplate(template: WorkflowTemplate): Long =
        dao.insertTemplate(template.toEntity())

    override suspend fun updateTemplate(template: WorkflowTemplate) =
        dao.updateTemplate(template.toEntity())

    override suspend fun deleteTemplate(id: Long) =
        dao.deleteTemplate(id)

    // ── Runs ──

    override fun getRunsForTemplate(templateId: Long): Flow<List<WorkflowRun>> =
        dao.getRunsForTemplate(templateId).map { list -> list.map { it.toDomain() } }

    override fun getRecentRuns(limit: Int): Flow<List<WorkflowRun>> =
        dao.getRecentRuns(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getRunById(id: Long): WorkflowRun? =
        dao.getRunById(id)?.toDomain()

    override fun observeRunById(id: Long): Flow<WorkflowRun?> =
        dao.observeRunById(id).map { it?.toDomain() }

    override suspend fun insertRun(run: WorkflowRun): Long =
        dao.insertRun(run.toEntity())

    override suspend fun updateRun(run: WorkflowRun) =
        dao.updateRun(run.toEntity())

    override suspend fun updateRunProgress(id: Long, stagesJson: String, currentStage: String) =
        dao.updateRunProgress(id, stagesJson, currentStage)

    override suspend fun updateRunProfile(id: Long, profileName: String, modelId: String) =
        dao.updateRunProfile(id, profileName, modelId)

    override suspend fun getLatestSuccessfulRun(templateId: Long): WorkflowRun? =
        dao.getLatestSuccessfulRun(templateId)?.toDomain()

    override suspend fun getLatestRun(templateId: Long): WorkflowRun? =
        dao.getLatestRun(templateId)?.toDomain()

    override suspend fun failStaleRunningRecords(templateId: Long) =
        dao.failStaleRunningRecords(templateId, "Worker terminated unexpectedly", System.currentTimeMillis())

    // ── Provider Profiles ──

    override fun getAllProfiles(): Flow<List<ProviderProfile>> =
        dao.getAllProfiles().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfileById(id: String): ProviderProfile? =
        dao.getProfileById(id)?.toDomain()

    override suspend fun getDefaultProfile(): ProviderProfile? =
        dao.getDefaultProfile()?.toDomain()

    override suspend fun saveProfile(profile: ProviderProfile) =
        dao.insertProfile(profile.toEntity())

    override suspend fun deleteProfile(id: String) =
        dao.deleteProfile(id)

    override suspend fun setDefaultProfile(id: String) {
        dao.clearDefaultProfiles()
        val profile = dao.getProfileById(id) ?: return
        dao.updateProfile(profile.copy(isDefault = true))
    }

    override suspend fun getFallbackProfile(): ProviderProfile? =
        dao.getFallbackProfile()?.toDomain()

    override suspend fun setFallbackProfile(id: String) {
        dao.clearFallbackProfiles()
        val profile = dao.getProfileById(id) ?: return
        dao.updateProfile(profile.copy(isFallback = true))
    }

    // ── Saved Notes ──

    override fun getAllNotes(): Flow<List<SavedNote>> =
        dao.getAllNotes().map { list -> list.map { it.toDomain() } }

    override suspend fun getNoteById(id: Long): SavedNote? =
        dao.getNoteById(id)?.toDomain()

    override suspend fun saveNote(note: SavedNote): Long =
        dao.insertNote(note.toEntity())

    override suspend fun updateNote(note: SavedNote) =
        dao.updateNote(note.toEntity())

    override suspend fun deleteNote(id: Long) =
        dao.deleteNote(id)
}

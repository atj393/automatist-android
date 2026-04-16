package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.SavedNote
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.repositories.WorkflowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Minimal in-memory fake for testing portability logic.
 * Only the methods used by WorkflowPortabilityManager are implemented.
 */
class FakeWorkflowRepository : WorkflowRepository {

    private var nextTemplateId = 1L
    private var nextNoteId = 1L

    private val templates = MutableStateFlow<List<WorkflowTemplate>>(emptyList())
    private val profiles = MutableStateFlow<List<ProviderProfile>>(emptyList())
    private val notes = MutableStateFlow<List<SavedNote>>(emptyList())

    // ── Helpers for test setup ──

    fun seedTemplate(template: WorkflowTemplate): Long {
        val id = if (template.id == 0L) nextTemplateId++ else template.id
        val stored = template.copy(id = id)
        templates.value = templates.value + stored
        return id
    }

    fun seedProfile(profile: ProviderProfile) {
        profiles.value = profiles.value + profile
    }

    fun seedNote(note: SavedNote): Long {
        val id = if (note.id == 0L) nextNoteId++ else note.id
        val stored = note.copy(id = id)
        notes.value = notes.value + stored
        return id
    }

    // ── WorkflowRepository implementation ──

    override fun getAllTemplates(): Flow<List<WorkflowTemplate>> = templates

    override suspend fun getTemplateById(id: Long): WorkflowTemplate? =
        templates.value.find { it.id == id }

    override suspend fun saveTemplate(template: WorkflowTemplate): Long {
        val id = nextTemplateId++
        val stored = template.copy(id = id)
        templates.value = templates.value + stored
        return id
    }

    override suspend fun updateTemplate(template: WorkflowTemplate) {
        templates.value = templates.value.map { if (it.id == template.id) template else it }
    }

    override suspend fun deleteTemplate(id: Long) {
        templates.value = templates.value.filter { it.id != id }
    }

    // ── Profiles ──

    override fun getAllProfiles(): Flow<List<ProviderProfile>> = profiles

    override suspend fun getProfileById(id: String): ProviderProfile? =
        profiles.value.find { it.id == id }

    override suspend fun getDefaultProfile(): ProviderProfile? =
        profiles.value.find { it.isDefault }

    override suspend fun saveProfile(profile: ProviderProfile) {
        profiles.value = profiles.value.filter { it.id != profile.id } + profile
    }

    override suspend fun deleteProfile(id: String) {
        profiles.value = profiles.value.filter { it.id != id }
    }

    override suspend fun setDefaultProfile(id: String) {
        profiles.value = profiles.value.map { it.copy(isDefault = it.id == id) }
    }

    override suspend fun getFallbackProfile(): ProviderProfile? =
        profiles.value.find { it.isFallback }

    override suspend fun setFallbackProfile(id: String) {
        profiles.value = profiles.value.map { it.copy(isFallback = it.id == id) }
    }

    // ── Notes ──

    override fun getAllNotes(): Flow<List<SavedNote>> = notes

    override suspend fun getNoteById(id: Long): SavedNote? =
        notes.value.find { it.id == id }

    override suspend fun saveNote(note: SavedNote): Long {
        val id = nextNoteId++
        notes.value = notes.value + note.copy(id = id)
        return id
    }

    override suspend fun updateNote(note: SavedNote) {
        notes.value = notes.value.map { if (it.id == note.id) note else it }
    }

    override suspend fun deleteNote(id: Long) {
        notes.value = notes.value.filter { it.id != id }
    }

    // ── Runs (not used by portability manager, stub only) ──

    override fun getRunsForTemplate(templateId: Long): Flow<List<WorkflowRun>> =
        MutableStateFlow(emptyList())

    override fun getRecentRuns(limit: Int): Flow<List<WorkflowRun>> =
        MutableStateFlow(emptyList())

    override suspend fun getRunById(id: Long): WorkflowRun? = null
    override fun observeRunById(id: Long): Flow<WorkflowRun?> = MutableStateFlow(null)
    override suspend fun insertRun(run: WorkflowRun): Long = 0
    override suspend fun updateRun(run: WorkflowRun) {}
    override suspend fun getLatestSuccessfulRun(templateId: Long): WorkflowRun? = null
    override suspend fun getLatestRun(templateId: Long): WorkflowRun? = null
    override suspend fun failStaleRunningRecords(templateId: Long) {}
}

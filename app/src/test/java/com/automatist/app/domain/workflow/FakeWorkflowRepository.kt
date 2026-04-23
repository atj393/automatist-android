package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.SavedNote
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
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

    // Back the Run-related methods with a real in-memory map so tests that
    // exercise incremental persistence can verify writes actually land.
    private val runs = MutableStateFlow<Map<Long, WorkflowRun>>(emptyMap())
    private var nextRunId = 1L

    override suspend fun getRunById(id: Long): WorkflowRun? = runs.value[id]

    override fun observeRunById(id: Long): Flow<WorkflowRun?> =
        runs.map { it[id] }

    override suspend fun insertRun(run: WorkflowRun): Long {
        val id = if (run.id == 0L) nextRunId++ else run.id
        runs.value = runs.value + (id to run.copy(id = id))
        return id
    }

    override suspend fun updateRun(run: WorkflowRun) {
        runs.value = runs.value + (run.id to run)
    }

    // Instrumentation counters so tests can assert that coalescing actually
    // skips redundant writes at the VM/Worker layer.
    var updateRunProgressCallCount: Int = 0
        private set
    var updateRunProfileCallCount: Int = 0
        private set

    override suspend fun updateRunProgress(id: Long, stagesJson: String, currentStage: String) {
        val existing = runs.value[id] ?: return
        updateRunProgressCallCount++
        runs.value = runs.value + (id to existing.copy(
            stagesJson = stagesJson,
            currentStage = currentStage
        ))
    }

    override suspend fun updateRunProfile(id: Long, profileName: String, modelId: String) {
        val existing = runs.value[id] ?: return
        updateRunProfileCallCount++
        runs.value = runs.value + (id to existing.copy(
            profileName = profileName,
            modelId = modelId
        ))
    }

    override suspend fun markRunCancelled(id: Long) {
        val existing = runs.value[id] ?: return
        // Atomic semantics: only flip if still RUNNING.
        if (existing.status != WorkflowRunStatus.RUNNING) return
        runs.value = runs.value + (id to existing.copy(
            status = WorkflowRunStatus.FAILED,
            errorMessage = "Run cancelled — the run screen was closed before it finished.",
            currentStage = "Cancelled",
            completedAtMillis = System.currentTimeMillis()
        ))
    }

    override suspend fun getLatestSuccessfulRun(templateId: Long): WorkflowRun? = null
    override suspend fun getLatestRun(templateId: Long): WorkflowRun? = null

    override suspend fun failStaleRunningRecords(templateId: Long) {
        // Mirror the Room impl's 60s grace window so tests see realistic behavior.
        val now = System.currentTimeMillis()
        val cutoff = now - 60_000L
        runs.value = runs.value.mapValues { (_, r) ->
            if (r.templateId == templateId &&
                r.status == WorkflowRunStatus.RUNNING &&
                r.startedAtMillis < cutoff
            ) {
                r.copy(
                    status = WorkflowRunStatus.FAILED,
                    errorMessage = "Worker terminated unexpectedly",
                    completedAtMillis = now
                )
            } else r
        }
    }

    override suspend fun failAllStaleRunningRecordsOlderThan(cutoffMillis: Long) {
        val now = System.currentTimeMillis()
        runs.value = runs.value.mapValues { (_, r) ->
            if (r.status == WorkflowRunStatus.RUNNING && r.startedAtMillis < cutoffMillis) {
                r.copy(
                    status = WorkflowRunStatus.FAILED,
                    errorMessage = "Previous run was interrupted",
                    completedAtMillis = now
                )
            } else r
        }
    }
}

package com.automatist.app.feature.workflow.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.domain.access.PlanState
import com.automatist.app.domain.access.ProductAccessRepository
import com.automatist.app.domain.models.*
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.domain.templates.BuiltInTemplates
import com.automatist.app.domain.workflow.WorkflowPortabilityManager
import com.automatist.app.platform.scheduling.ScheduleInfo
import com.automatist.app.platform.scheduling.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ExportData(
    val json: String,
    val suggestedFilename: String,
    val warnings: List<String>
)

data class WorkflowDetailsUiState(
    val isLoading: Boolean = true,
    val template: WorkflowTemplate? = null,
    val recentRuns: List<WorkflowRun> = emptyList(),
    val scheduleInfo: ScheduleInfo? = null,
    val sourceTemplateName: String = "",
    val defaultProfileName: String = "",
    val outputProfileName: String = "",
    val showDeleteDialog: Boolean = false,
    val duplicatedWorkflowId: Long? = null,
    val exportData: ExportData? = null,
    val snackbarMessage: String? = null,
    val isProcessing: Boolean = false
)

@HiltViewModel
class WorkflowDetailsViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    private val scheduleManager: ScheduleManager,
    private val portabilityManager: WorkflowPortabilityManager,
    private val accessRepository: ProductAccessRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val templateId: Long = savedStateHandle.get<Long>("templateId") ?: 0L

    private val _state = MutableStateFlow(WorkflowDetailsUiState())
    val state = _state.asStateFlow()

    val planState = accessRepository.planState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanState())

    /**
     * Check whether the user can activate (enable) this workflow.
     * Access is unrestricted (all features are free), so activation is always
     * allowed and this returns null. Routed through the centralized [PlanState]
     * policy so a future policy could re-introduce a cap without scattering checks;
     * when blocked it returns the name of the active workflow that blocks it.
     */
    suspend fun checkActivationBlocked(): String? {
        val plan = accessRepository.currentPlanState()
        if (plan.isProUnlocked) return null

        val thisId = templateId
        val allTemplates = repository.getAllTemplates().first()
        val activeOthers = allTemplates.filter { it.isEnabled && it.id != thisId }
        return if (plan.canActivateWorkflow(activeOthers.size)) null
        else activeOthers.firstOrNull()?.name ?: "another workflow"
    }

    /**
     * Deactivate all other active workflows, then enable this one.
     * Used by the "Switch Active" flow for free users.
     */
    fun switchActiveToThis() {
        viewModelScope.launch {
            val template = _state.value.template ?: return@launch
            val allTemplates = repository.getAllTemplates().first()

            // Disable all other enabled workflows and cancel their schedules
            allTemplates.filter { it.isEnabled && it.id != template.id }.forEach { other ->
                repository.updateTemplate(
                    other.copy(isEnabled = false, updatedAtMillis = System.currentTimeMillis())
                )
                scheduleManager.cancelSchedule(other.id)
            }

            // Enable this one
            val updated = template.copy(isEnabled = true, updatedAtMillis = System.currentTimeMillis())
            repository.updateTemplate(updated)
            _state.update { it.copy(template = updated) }

            if (updated.trigger !is WorkflowTrigger.Manual) {
                scheduleManager.scheduleWorkflow(templateId, updated.trigger)
            }

            val info = withContext(Dispatchers.IO) {
                scheduleManager.getScheduleStatusSync(templateId)
            }
            _state.update { it.copy(scheduleInfo = info) }
        }
    }

    init {
        loadWorkflow()
    }

    private fun loadWorkflow() {
        viewModelScope.launch {
            val template = repository.getTemplateById(templateId)
            if (template == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            val sourceName = if (template.sourceTemplateId.isNotBlank()) {
                BuiltInTemplates.findById(template.sourceTemplateId)?.name ?: ""
            } else ""

            // Resolve profile names
            val defaultProfileName = if (template.defaultProfileId.isNotBlank()) {
                repository.getProfileById(template.defaultProfileId)?.displayLabel ?: "Unknown profile"
            } else ""

            val outputProfileName = if (template.outputConfig.outputProfileId.isNotBlank()) {
                repository.getProfileById(template.outputConfig.outputProfileId)?.displayLabel ?: "Unknown profile"
            } else ""

            _state.update {
                it.copy(
                    isLoading = false,
                    template = template,
                    sourceTemplateName = sourceName,
                    defaultProfileName = defaultProfileName,
                    outputProfileName = outputProfileName
                )
            }

            // Load schedule info
            launch {
                val info = withContext(Dispatchers.IO) {
                    scheduleManager.getScheduleStatusSync(templateId)
                }
                _state.update { it.copy(scheduleInfo = info) }
            }

            // Load recent runs
            repository.getRunsForTemplate(templateId).collect { runs ->
                _state.update { it.copy(recentRuns = runs.take(10)) }
            }
        }
    }

    /**
     * Toggle enabled state and sync scheduling. Enabling routes through the
     * centralized access policy (currently unrestricted, so it never blocks) and
     * returns null on success; a non-null result would name the blocking workflow.
     */
    suspend fun toggleEnabled(): String? {
        val template = _state.value.template ?: return null
        val newEnabled = !template.isEnabled

        // If enabling, check activation limit
        if (newEnabled) {
            val blockingName = checkActivationBlocked()
            if (blockingName != null) return blockingName
        }

        val updated = template.copy(
            isEnabled = newEnabled,
            updatedAtMillis = System.currentTimeMillis()
        )
        repository.updateTemplate(updated)
        _state.update { it.copy(template = updated) }

        // Sync scheduling
        if (newEnabled) {
            if (updated.trigger !is WorkflowTrigger.Manual) {
                scheduleManager.scheduleWorkflow(templateId, updated.trigger)
            }
        } else {
            scheduleManager.cancelSchedule(templateId)
        }

        val info = withContext(Dispatchers.IO) {
            scheduleManager.getScheduleStatusSync(templateId)
        }
        _state.update { it.copy(scheduleInfo = info) }
        return null
    }

    fun showDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteWorkflow(onDeleted: () -> Unit) {
        viewModelScope.launch {
            scheduleManager.cancelSchedule(templateId)
            repository.deleteTemplate(templateId)
            onDeleted()
        }
    }

    // ── Duplicate ──

    fun duplicateWorkflow() {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            try {
                val result = portabilityManager.duplicateWorkflow(templateId)
                _state.update {
                    it.copy(
                        isProcessing = false,
                        duplicatedWorkflowId = result.newWorkflowId,
                        snackbarMessage = "Workflow duplicated as \"${result.newName}\" (paused)"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        snackbarMessage = "Duplicate failed: ${e.message?.take(100) ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun clearDuplicatedWorkflowId() {
        _state.update { it.copy(duplicatedWorkflowId = null) }
    }

    // ── Export ──

    fun prepareExport() {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            try {
                val result = portabilityManager.exportWorkflow(templateId)
                _state.update {
                    it.copy(
                        isProcessing = false,
                        exportData = ExportData(result.json, result.suggestedFilename, result.warnings)
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        snackbarMessage = "Export failed: ${e.message?.take(100) ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun clearExportData() {
        _state.update { it.copy(exportData = null) }
    }

    fun clearSnackbarMessage() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}

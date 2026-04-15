package com.automatist.app.feature.workflow.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.domain.workflow.WorkflowPortabilityManager
import com.automatist.app.platform.scheduling.ScheduleInfo
import com.automatist.app.platform.scheduling.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportUiState(
    val isImporting: Boolean = false,
    val importedWorkflowId: Long? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class WorkflowListViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    private val scheduleManager: ScheduleManager,
    private val portabilityManager: WorkflowPortabilityManager
) : ViewModel() {

    val workflows = repository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scheduleStatuses = MutableStateFlow<Map<Long, ScheduleInfo>>(emptyMap())
    val scheduleStatuses = _scheduleStatuses.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState = _importState.asStateFlow()

    fun refreshScheduleStatuses(templates: List<WorkflowTemplate>) {
        viewModelScope.launch {
            val statuses = withContext(Dispatchers.IO) {
                val map = mutableMapOf<Long, ScheduleInfo>()
                for (template in templates) {
                    map[template.id] = scheduleManager.getScheduleStatusSync(template.id)
                }
                map
            }
            _scheduleStatuses.value = statuses
        }
    }

    fun deleteWorkflow(id: Long) {
        viewModelScope.launch {
            scheduleManager.cancelSchedule(id)
            repository.deleteTemplate(id)
        }
    }

    fun importWorkflow(jsonString: String) {
        if (_importState.value.isImporting) return
        viewModelScope.launch {
            _importState.update { it.copy(isImporting = true) }
            try {
                val result = portabilityManager.importWorkflow(jsonString)
                val message = buildString {
                    append("Workflow imported as \"${result.workflowName}\" (paused)")
                    if (result.warnings.isNotEmpty()) {
                        append(". ${result.warnings.joinToString(". ")}")
                    }
                }
                _importState.update {
                    it.copy(
                        isImporting = false,
                        importedWorkflowId = result.workflowId,
                        snackbarMessage = message
                    )
                }
            } catch (e: Exception) {
                _importState.update {
                    it.copy(
                        isImporting = false,
                        snackbarMessage = "Import failed: ${e.message?.take(120) ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun clearImportedWorkflowId() {
        _importState.update { it.copy(importedWorkflowId = null) }
    }

    fun clearImportSnackbar() {
        _importState.update { it.copy(snackbarMessage = null) }
    }
}

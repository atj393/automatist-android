package com.synapse.app.feature.workflow.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.domain.models.*
import com.synapse.app.domain.repositories.WorkflowRepository
import com.synapse.app.domain.templates.BuiltInTemplates
import com.synapse.app.platform.scheduling.ScheduleInfo
import com.synapse.app.platform.scheduling.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class WorkflowDetailsUiState(
    val isLoading: Boolean = true,
    val template: WorkflowTemplate? = null,
    val recentRuns: List<WorkflowRun> = emptyList(),
    val scheduleInfo: ScheduleInfo? = null,
    val sourceTemplateName: String = "",
    val defaultProfileName: String = "",
    val outputProfileName: String = "",
    val showDeleteDialog: Boolean = false
)

@HiltViewModel
class WorkflowDetailsViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    private val scheduleManager: ScheduleManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val templateId: Long = savedStateHandle.get<Long>("templateId") ?: 0L

    private val _state = MutableStateFlow(WorkflowDetailsUiState())
    val state = _state.asStateFlow()

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
}

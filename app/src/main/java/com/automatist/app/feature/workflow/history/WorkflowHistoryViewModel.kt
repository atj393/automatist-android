package com.automatist.app.feature.workflow.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.repositories.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkflowHistoryUiState(
    val isLoading: Boolean = true,
    val workflowName: String = "",
    val runs: List<WorkflowRun> = emptyList()
)

@HiltViewModel
class WorkflowHistoryViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val templateId: Long = savedStateHandle.get<Long>("templateId") ?: 0L

    private val _state = MutableStateFlow(WorkflowHistoryUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val template = repository.getTemplateById(templateId)
            _state.update { it.copy(workflowName = template?.name ?: "Workflow") }

            repository.getRunsForTemplate(templateId).collect { runs ->
                _state.update { it.copy(isLoading = false, runs = runs) }
            }
        }
    }
}

package com.synapse.app.feature.workflow.run

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.domain.models.WorkflowRun
import com.synapse.app.domain.repositories.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkflowRunDetailViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val runId: Long = savedStateHandle.get<Long>("runId") ?: -1L

    private val _run = MutableStateFlow<WorkflowRun?>(null)
    val run = _run.asStateFlow()

    init {
        viewModelScope.launch {
            _run.value = repository.getRunById(runId)
        }
    }
}

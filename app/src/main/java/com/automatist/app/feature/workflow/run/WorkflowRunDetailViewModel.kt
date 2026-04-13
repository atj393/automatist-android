package com.automatist.app.feature.workflow.run

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.repositories.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class WorkflowRunDetailViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val runId: Long = savedStateHandle.get<Long>("runId") ?: -1L

    // Observe live updates so the detail screen refreshes when a RUNNING record
    // gets updated to COMPLETED/FAILED by the worker.
    val run: StateFlow<WorkflowRun?> = repository.observeRunById(runId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

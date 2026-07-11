package com.automatist.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.domain.models.*
import com.automatist.app.domain.repositories.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    workflowRepository: WorkflowRepository
) : ViewModel() {

    // First-run seeding is handled centrally by FirstRunSeeder in AutomatistApp.onCreate,
    // not here — this ViewModel is purely observational now.

    val recentRuns: StateFlow<List<WorkflowRun>> = workflowRepository.getRecentRuns(10)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val customWorkflows: StateFlow<List<WorkflowTemplate>> = workflowRepository.getAllTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

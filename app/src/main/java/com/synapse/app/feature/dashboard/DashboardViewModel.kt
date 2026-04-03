package com.synapse.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.domain.models.HistoryItem
import com.synapse.app.domain.models.WorkflowTemplate
import com.synapse.app.domain.repositories.HistoryRepository
import com.synapse.app.domain.repositories.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    workflowRepository: WorkflowRepository
) : ViewModel() {

    val recentHistory: StateFlow<List<HistoryItem>> = historyRepository.getHistory()
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

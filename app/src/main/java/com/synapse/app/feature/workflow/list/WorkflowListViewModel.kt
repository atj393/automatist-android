package com.synapse.app.feature.workflow.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.domain.models.WorkflowTemplate
import com.synapse.app.domain.repositories.WorkflowRepository
import com.synapse.app.domain.workflow.WorkflowPortabilityManager
import com.synapse.app.platform.scheduling.ScheduleInfo
import com.synapse.app.platform.scheduling.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WorkflowListViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    private val scheduleManager: ScheduleManager
) : ViewModel() {

    val workflows = repository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scheduleStatuses = MutableStateFlow<Map<Long, ScheduleInfo>>(emptyMap())
    val scheduleStatuses = _scheduleStatuses.asStateFlow()

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
}

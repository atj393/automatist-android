package com.synapse.app.feature.workflow.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.synapse.app.domain.models.WorkflowTemplate
import com.synapse.app.domain.repositories.WorkflowRepository
import com.synapse.app.platform.automation.WorkflowWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkflowListViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val workflows = repository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteWorkflow(id: Long) {
        viewModelScope.launch {
            // Cancel any scheduled work
            WorkManager.getInstance(context).cancelUniqueWork("WorkflowWorker_$id")
            repository.deleteTemplate(id)
        }
    }

    fun runNow(template: WorkflowTemplate) {
        val request = OneTimeWorkRequestBuilder<WorkflowWorker>()
            .setInputData(
                workDataOf(
                    WorkflowWorker.KEY_TEMPLATE_ID to template.id,
                    WorkflowWorker.KEY_TRIGGER_TYPE to "manual"
                )
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("WorkflowWorker_${template.id}_OneTime", ExistingWorkPolicy.REPLACE, request)
    }
}

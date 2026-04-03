package com.synapse.app.feature.workflow.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.synapse.app.domain.models.*
import com.synapse.app.domain.repositories.WorkflowRepository
import com.synapse.app.platform.automation.WorkflowWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class EditorUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false, // true when editing an existing template
    val name: String = "",
    val description: String = "",
    val trigger: WorkflowTrigger = WorkflowTrigger.Manual,
    val actions: List<WorkflowAction> = emptyList(),
    val globalInstruction: String = "",
    val outputConfig: WorkflowOutputConfig = WorkflowOutputConfig(),
    val notifyOnCompletion: Boolean = false,
    val isSaving: Boolean = false,
    val savedTemplateId: Long? = null, // set after save
    val validationErrors: List<String> = emptyList()
)

@HiltViewModel
class WorkflowEditorViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val templateId: Long? = savedStateHandle.get<Long>("templateId")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(EditorUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (templateId != null) {
                val template = repository.getTemplateById(templateId)
                if (template != null) {
                    _state.value = EditorUiState(
                        isLoading = false,
                        isEditing = true,
                        name = template.name,
                        description = template.description,
                        trigger = template.trigger,
                        actions = template.actions,
                        globalInstruction = template.globalInstruction,
                        outputConfig = template.outputConfig,
                        notifyOnCompletion = template.notifyOnCompletion
                    )
                } else {
                    _state.value = EditorUiState(isLoading = false)
                }
            } else {
                _state.value = EditorUiState(isLoading = false)
            }
        }
    }

    fun updateName(name: String) = _state.update { it.copy(name = name) }
    fun updateDescription(desc: String) = _state.update { it.copy(description = desc) }
    fun updateTrigger(trigger: WorkflowTrigger) = _state.update { it.copy(trigger = trigger) }
    fun updateActions(actions: List<WorkflowAction>) = _state.update { it.copy(actions = actions) }
    fun updateGlobalInstruction(text: String) = _state.update { it.copy(globalInstruction = text) }
    fun updateNotifyOnCompletion(enabled: Boolean) = _state.update { it.copy(notifyOnCompletion = enabled) }

    fun updateOutputConfig(config: WorkflowOutputConfig) = _state.update { it.copy(outputConfig = config) }

    fun save() {
        val current = _state.value
        val errors = validate(current)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(validationErrors = errors) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, validationErrors = emptyList()) }

            val now = System.currentTimeMillis()
            val template = WorkflowTemplate(
                id = templateId ?: 0,
                name = current.name.trim(),
                description = current.description.trim(),
                isEnabled = true,
                trigger = current.trigger,
                actions = current.actions.mapIndexed { i, a -> a.copy(order = i) },
                globalInstruction = current.globalInstruction.trim(),
                outputConfig = current.outputConfig,
                notifyOnCompletion = current.notifyOnCompletion,
                createdAtMillis = if (templateId != null) {
                    repository.getTemplateById(templateId)?.createdAtMillis ?: now
                } else now,
                updatedAtMillis = now
            )

            val savedId = if (templateId != null) {
                repository.updateTemplate(template)
                templateId
            } else {
                repository.saveTemplate(template)
            }

            // Schedule if trigger is not manual
            scheduleWorkflow(savedId, current.trigger)

            _state.update { it.copy(isSaving = false, savedTemplateId = savedId) }
        }
    }

    private fun scheduleWorkflow(templateId: Long, trigger: WorkflowTrigger) {
        val workManager = WorkManager.getInstance(context)
        val uniqueName = "WorkflowWorker_$templateId"

        when (trigger) {
            is WorkflowTrigger.Manual -> {
                workManager.cancelUniqueWork(uniqueName)
            }
            is WorkflowTrigger.Daily -> {
                val request = PeriodicWorkRequestBuilder<WorkflowWorker>(
                    24, TimeUnit.HOURS
                ).setInputData(
                    workDataOf(
                        WorkflowWorker.KEY_TEMPLATE_ID to templateId,
                        WorkflowWorker.KEY_TRIGGER_TYPE to "scheduled"
                    )
                ).build()
                workManager.enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
            is WorkflowTrigger.Weekly -> {
                // WorkManager doesn't support weekly directly — use 7-day period
                val request = PeriodicWorkRequestBuilder<WorkflowWorker>(
                    7, TimeUnit.DAYS
                ).setInputData(
                    workDataOf(
                        WorkflowWorker.KEY_TEMPLATE_ID to templateId,
                        WorkflowWorker.KEY_TRIGGER_TYPE to "scheduled"
                    )
                ).build()
                workManager.enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
            is WorkflowTrigger.NotificationKeyword -> {
                // V1: Not implemented at runtime — future extension
            }
        }
    }

    private fun validate(state: EditorUiState): List<String> {
        val errors = mutableListOf<String>()
        if (state.name.isBlank()) errors.add("Workflow name is required.")
        if (state.actions.isEmpty()) errors.add("Add at least one action.")
        state.actions.forEachIndexed { i, action ->
            if (action.sourceData.isBlank()) {
                val label = action.label.ifBlank { "Action #${i + 1}" }
                errors.add("$label: source data is empty.")
            }
            if (action.type == WorkflowActionType.FETCH_URL && !action.sourceData.startsWith("http")) {
                val label = action.label.ifBlank { "Action #${i + 1}" }
                errors.add("$label: URL must start with http:// or https://")
            }
        }
        if (state.trigger is WorkflowTrigger.Daily) {
            val daily = state.trigger
            if (daily.hour !in 0..23) errors.add("Daily hour must be 0-23.")
        }
        if (state.trigger is WorkflowTrigger.Weekly) {
            val weekly = state.trigger
            if (weekly.daysOfWeek.isEmpty()) errors.add("Select at least one day of the week.")
        }
        return errors
    }
}

package com.synapse.app.feature.workflow.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.synapse.app.domain.actions.WorkflowActionRegistry
import com.synapse.app.domain.models.*
import com.synapse.app.domain.readiness.ReadinessEvaluator
import com.synapse.app.domain.readiness.WorkflowReadiness
import com.synapse.app.domain.repositories.WorkflowRepository
import com.synapse.app.domain.templates.BuiltInTemplates
import com.synapse.app.platform.automation.WorkflowWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class EditorUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val name: String = "",
    val description: String = "",
    val trigger: WorkflowTrigger = WorkflowTrigger.Manual,
    val actions: List<WorkflowAction> = emptyList(),
    val globalInstruction: String = "",
    val outputConfig: WorkflowOutputConfig = WorkflowOutputConfig(),
    val notifyOnCompletion: Boolean = false,
    val isSaving: Boolean = false,
    val savedTemplateId: Long? = null,
    val validationErrors: List<String> = emptyList(),
    // Template origin — informational only, not restrictive
    val sourceTemplateId: String = "",
    val sourceTemplateName: String = "",
    val category: String = "",
    val defaultProfileId: String = "",
    val workflowReadiness: WorkflowReadiness? = null
)

@HiltViewModel
class WorkflowEditorViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    val readinessEvaluator: ReadinessEvaluator,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val templateId: Long? = savedStateHandle.get<Long>("templateId")?.takeIf { it > 0 }
    private val sourceTemplateId: String? = savedStateHandle.get<String>("sourceTemplateId")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(EditorUiState())
    val state = _state.asStateFlow()

    val availableNotes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val availableWorkflows = repository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            when {
                // Editing an existing user workflow
                templateId != null -> {
                    val template = repository.getTemplateById(templateId)
                    if (template != null) {
                        val sourceName = if (template.sourceTemplateId.isNotBlank()) {
                            BuiltInTemplates.findById(template.sourceTemplateId)?.name ?: ""
                        } else ""
                        _state.value = EditorUiState(
                            isLoading = false,
                            isEditing = true,
                            name = template.name,
                            description = template.description,
                            trigger = template.trigger,
                            actions = template.actions,
                            globalInstruction = template.globalInstruction,
                            outputConfig = template.outputConfig,
                            notifyOnCompletion = template.notifyOnCompletion,
                            sourceTemplateId = template.sourceTemplateId,
                            sourceTemplateName = sourceName,
                            category = template.category,
                            defaultProfileId = template.defaultProfileId
                        )
                    } else {
                        _state.value = EditorUiState(isLoading = false)
                    }
                }

                // Creating a new workflow from a built-in template (starter blueprint)
                sourceTemplateId != null -> {
                    val builtIn = BuiltInTemplates.findById(sourceTemplateId)
                    if (builtIn != null) {
                        val bp = builtIn.blueprint
                        _state.value = EditorUiState(
                            isLoading = false,
                            isEditing = false,
                            name = bp.name,
                            description = bp.description,
                            trigger = bp.trigger,
                            actions = bp.actions,
                            globalInstruction = bp.globalInstruction,
                            outputConfig = bp.outputConfig,
                            notifyOnCompletion = bp.notifyOnCompletion,
                            sourceTemplateId = builtIn.id,
                            sourceTemplateName = builtIn.name,
                            category = builtIn.category
                            // No customization restrictions — user workflow is fully editable
                        )
                    } else {
                        _state.value = EditorUiState(isLoading = false)
                    }
                }

                // Creating a blank workflow from scratch
                else -> {
                    _state.value = EditorUiState(
                        isLoading = false,
                        isEditing = false,
                        name = "",
                        description = "",
                        trigger = WorkflowTrigger.Manual,
                        actions = emptyList(),
                        globalInstruction = "",
                        outputConfig = WorkflowOutputConfig(),
                        notifyOnCompletion = false
                    )
                }
            }
            refreshReadiness()
        }
    }

    // All update functions are unrestricted — user workflows are always fully editable
    fun updateName(name: String) = _state.update { it.copy(name = name) }
    fun updateDescription(desc: String) = _state.update { it.copy(description = desc) }
    fun updateTrigger(trigger: WorkflowTrigger) = _state.update { it.copy(trigger = trigger) }
    fun updateActions(actions: List<WorkflowAction>) {
        _state.update { it.copy(actions = actions) }
        refreshReadiness()
    }
    fun updateGlobalInstruction(text: String) = _state.update { it.copy(globalInstruction = text) }
    fun updateNotifyOnCompletion(enabled: Boolean) = _state.update { it.copy(notifyOnCompletion = enabled) }
    fun updateOutputConfig(config: WorkflowOutputConfig) = _state.update { it.copy(outputConfig = config) }

    fun refreshReadiness() {
        viewModelScope.launch {
            val current = _state.value
            val template = WorkflowTemplate(
                name = current.name,
                actions = current.actions,
                outputConfig = current.outputConfig
            )
            val readiness = readinessEvaluator.evaluateWorkflow(template)
            _state.update { it.copy(workflowReadiness = readiness) }
        }
    }

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
                updatedAtMillis = now,
                sourceTemplateId = current.sourceTemplateId,
                category = current.category,
                defaultProfileId = current.defaultProfileId
            )

            val savedId = if (templateId != null) {
                repository.updateTemplate(template)
                templateId
            } else {
                repository.saveTemplate(template)
            }

            scheduleWorkflow(savedId, current.trigger)
            _state.update { it.copy(isSaving = false, savedTemplateId = savedId) }
        }
    }

    private fun scheduleWorkflow(templateId: Long, trigger: WorkflowTrigger) {
        val workManager = WorkManager.getInstance(context)
        val uniqueName = "WorkflowWorker_$templateId"

        when (trigger) {
            is WorkflowTrigger.Manual -> workManager.cancelUniqueWork(uniqueName)
            is WorkflowTrigger.Daily -> {
                val request = PeriodicWorkRequestBuilder<WorkflowWorker>(
                    24, TimeUnit.HOURS
                ).setInputData(workDataOf(
                    WorkflowWorker.KEY_TEMPLATE_ID to templateId,
                    WorkflowWorker.KEY_TRIGGER_TYPE to "scheduled"
                )).build()
                workManager.enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
            is WorkflowTrigger.Weekly -> {
                val request = PeriodicWorkRequestBuilder<WorkflowWorker>(
                    7, TimeUnit.DAYS
                ).setInputData(workDataOf(
                    WorkflowWorker.KEY_TEMPLATE_ID to templateId,
                    WorkflowWorker.KEY_TRIGGER_TYPE to "scheduled"
                )).build()
                workManager.enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
            is WorkflowTrigger.NotificationKeyword -> { /* Future */ }
        }
    }

    private fun validate(state: EditorUiState): List<String> {
        val errors = mutableListOf<String>()
        if (state.name.isBlank()) errors.add("Workflow name is required.")
        if (state.actions.isEmpty()) errors.add("Add at least one action.")

        state.actions.forEachIndexed { i, action ->
            errors.addAll(WorkflowActionRegistry.validate(action, i))
        }

        if (state.trigger is WorkflowTrigger.Daily) {
            if (state.trigger.hour !in 0..23) errors.add("Daily hour must be 0-23.")
        }
        if (state.trigger is WorkflowTrigger.Weekly) {
            if (state.trigger.daysOfWeek.isEmpty()) errors.add("Select at least one day of the week.")
        }
        return errors
    }
}

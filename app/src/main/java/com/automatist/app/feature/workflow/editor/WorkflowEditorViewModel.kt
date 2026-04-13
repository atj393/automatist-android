package com.automatist.app.feature.workflow.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.domain.actions.WorkflowActionRegistry
import com.automatist.app.domain.models.*
import com.automatist.app.domain.readiness.ReadinessEvaluator
import com.automatist.app.domain.readiness.WorkflowReadiness
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.domain.templates.BuiltInTemplates
import com.automatist.app.platform.notifications.NotificationHelper
import com.automatist.app.platform.scheduling.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val notifyOnStart: Boolean = false,
    val isSaving: Boolean = false,
    val savedTemplateId: Long? = null,
    val validationErrors: List<String> = emptyList(),
    // Template origin — informational only, not restrictive
    val sourceTemplateId: String = "",
    val sourceTemplateName: String = "",
    val category: String = "",
    val defaultProfileId: String = "",
    val workflowReadiness: WorkflowReadiness? = null,
    // Schedule confirmation — shown as dialog before navigating away
    val saveConfirmation: SaveConfirmation? = null,
    val needsNotificationPermission: Boolean = false
)

data class SaveConfirmation(
    val savedId: Long,
    val workflowName: String,
    val isScheduled: Boolean,
    val scheduleMessage: String,
    val nextRunLabel: String,
    val notifyOnStart: Boolean,
    val notifyOnCompletion: Boolean
)

@HiltViewModel
class WorkflowEditorViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    val readinessEvaluator: ReadinessEvaluator,
    private val scheduleManager: ScheduleManager,
    private val notificationHelper: NotificationHelper,
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

    val availableProfiles = repository.getAllProfiles()
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
                            notifyOnStart = template.notifyOnStart,
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
                            notifyOnStart = bp.notifyOnStart,
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
                        notifyOnCompletion = false,
                        notifyOnStart = false
                    )
                }
            }
            checkNotificationPermission()
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
    fun updateNotifyOnStart(enabled: Boolean) = _state.update { it.copy(notifyOnStart = enabled) }
    fun updateOutputConfig(config: WorkflowOutputConfig) = _state.update { it.copy(outputConfig = config) }
    fun updateDefaultProfileId(profileId: String) = _state.update { it.copy(defaultProfileId = profileId) }

    fun checkNotificationPermission() {
        _state.update { it.copy(needsNotificationPermission = notificationHelper.needsNotificationPermissionRequest()) }
    }

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
                notifyOnStart = current.notifyOnStart,
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

            // Schedule via centralized ScheduleManager
            val scheduleMessage = scheduleManager.scheduleWorkflow(savedId, current.trigger)
            val isScheduled = current.trigger !is WorkflowTrigger.Manual

            // Send schedule confirmation notification for non-manual triggers
            if (isScheduled) {
                notificationHelper.notifyScheduleCreated(current.name.trim(), scheduleMessage)
            }

            val nextRun = if (isScheduled) computeNextRunPreview(current.trigger) else ""

            _state.update {
                it.copy(
                    isSaving = false,
                    saveConfirmation = SaveConfirmation(
                        savedId = savedId,
                        workflowName = current.name.trim(),
                        isScheduled = isScheduled,
                        scheduleMessage = scheduleMessage,
                        nextRunLabel = nextRun,
                        notifyOnStart = current.notifyOnStart,
                        notifyOnCompletion = current.notifyOnCompletion
                    )
                )
            }
        }
    }

    fun dismissConfirmation() {
        val conf = _state.value.saveConfirmation ?: return
        _state.update { it.copy(saveConfirmation = null, savedTemplateId = conf.savedId) }
    }

    private fun computeNextRunPreview(trigger: WorkflowTrigger): String {
        val dateFormat = java.text.SimpleDateFormat("EEE, MMM d 'at' HH:mm", java.util.Locale.getDefault())
        val now = java.util.Calendar.getInstance()
        return when (trigger) {
            is WorkflowTrigger.Daily -> {
                val target = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, trigger.hour)
                    set(java.util.Calendar.MINUTE, trigger.minute)
                    set(java.util.Calendar.SECOND, 0)
                }
                if (target.timeInMillis <= now.timeInMillis) target.add(java.util.Calendar.DAY_OF_YEAR, 1)
                dateFormat.format(target.time)
            }
            is WorkflowTrigger.Weekly -> {
                val todayIso = when (now.get(java.util.Calendar.DAY_OF_WEEK)) {
                    java.util.Calendar.MONDAY -> 1; java.util.Calendar.TUESDAY -> 2
                    java.util.Calendar.WEDNESDAY -> 3; java.util.Calendar.THURSDAY -> 4
                    java.util.Calendar.FRIDAY -> 5; java.util.Calendar.SATURDAY -> 6
                    java.util.Calendar.SUNDAY -> 7; else -> 1
                }
                var bestTarget: java.util.Calendar? = null
                for (day in trigger.daysOfWeek) {
                    var daysAhead = day - todayIso
                    if (daysAhead < 0) daysAhead += 7
                    val t = java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.DAY_OF_YEAR, daysAhead)
                        set(java.util.Calendar.HOUR_OF_DAY, trigger.hour)
                        set(java.util.Calendar.MINUTE, trigger.minute)
                        set(java.util.Calendar.SECOND, 0)
                    }
                    if (t.timeInMillis <= now.timeInMillis) t.add(java.util.Calendar.DAY_OF_YEAR, 7)
                    if (bestTarget == null || t.timeInMillis < bestTarget.timeInMillis) bestTarget = t
                }
                bestTarget?.let { dateFormat.format(it.time) } ?: "Unknown"
            }
            is WorkflowTrigger.Interval -> {
                val target = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MINUTE, trigger.intervalMinutes)
                }
                dateFormat.format(target.time)
            }
            else -> ""
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
        if (state.trigger is WorkflowTrigger.Interval) {
            if (state.trigger.intervalMinutes < 15) errors.add("Interval must be at least 15 minutes.")
        }
        return errors
    }
}

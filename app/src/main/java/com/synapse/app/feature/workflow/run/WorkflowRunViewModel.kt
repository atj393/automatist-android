package com.synapse.app.feature.workflow.run

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.domain.engine.ExecutionState
import com.synapse.app.domain.engine.WorkflowExecutionEngine
import com.synapse.app.domain.models.*
import com.synapse.app.domain.repositories.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunUiState(
    val isLoading: Boolean = true,
    val templateName: String = "",
    val isRunning: Boolean = false,
    val stages: List<StageInfo> = emptyList(),
    val currentStageLabel: String = "",
    val outputText: String = "",
    val providerType: ProviderType? = null,
    val tokenUsage: TokenUsage? = null,
    val durationMs: Long? = null,
    val errorMessage: String? = null,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val runId: Long? = null
)

data class StageInfo(
    val label: String,
    val status: StageStatus,
    val detail: String = ""
)

enum class StageStatus { PENDING, RUNNING, COMPLETED, FAILED }

@HiltViewModel
class WorkflowRunViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    private val engine: WorkflowExecutionEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val templateId: Long = savedStateHandle.get<Long>("templateId") ?: -1L

    private val _state = MutableStateFlow(RunUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val template = repository.getTemplateById(templateId)
            if (template == null) {
                _state.value = RunUiState(
                    isLoading = false,
                    errorMessage = "Workflow not found"
                )
                return@launch
            }

            _state.value = RunUiState(
                isLoading = false,
                templateName = template.name
            )

            startRun(template)
        }
    }

    private fun startRun(template: WorkflowTemplate) {
        viewModelScope.launch {
            _state.update { it.copy(isRunning = true) }

            // Create run record
            val runId = repository.insertRun(
                WorkflowRun(
                    templateId = template.id,
                    templateName = template.name,
                    triggerType = "manual",
                    status = WorkflowRunStatus.RUNNING,
                    currentStage = "Preparing"
                )
            )
            _state.update { it.copy(runId = runId) }

            engine.execute(template).collect { executionState ->
                handleState(executionState, runId, template)
            }
        }
    }

    private suspend fun handleState(executionState: ExecutionState, runId: Long, template: WorkflowTemplate) {
        when (executionState) {
            is ExecutionState.Preparing -> {
                _state.update {
                    it.copy(
                        currentStageLabel = "Preparing workflow...",
                        stages = listOf(StageInfo("Preparing", StageStatus.RUNNING))
                    )
                }
            }

            is ExecutionState.ValidatingInputs -> {
                _state.update {
                    val stages = it.stages.markLastCompleted() + StageInfo(
                        "Validating ${executionState.totalActions} action(s)", StageStatus.RUNNING
                    )
                    it.copy(currentStageLabel = "Validating inputs...", stages = stages)
                }
            }

            is ExecutionState.ActionStarted -> {
                _state.update {
                    val stages = it.stages.markLastCompleted() + StageInfo(
                        "Processing: ${executionState.actionLabel}", StageStatus.RUNNING,
                        "${executionState.actionIndex + 1}/${executionState.totalActions}"
                    )
                    it.copy(currentStageLabel = "Processing: ${executionState.actionLabel}", stages = stages)
                }
            }

            is ExecutionState.ActionCompleted -> {
                _state.update {
                    val stages = it.stages.markLastCompleted(executionState.resultPreview)
                    it.copy(stages = stages)
                }
            }

            is ExecutionState.ActionFailed -> {
                _state.update {
                    val stages = it.stages.toMutableList()
                    if (stages.isNotEmpty()) {
                        stages[stages.lastIndex] = stages.last().copy(
                            status = StageStatus.FAILED,
                            detail = executionState.error
                        )
                    }
                    it.copy(stages = stages)
                }
            }

            is ExecutionState.ProcessingStarted -> {
                _state.update {
                    val stages = it.stages.markLastCompleted() + StageInfo(
                        "Combining ${executionState.combinedInputLength} chars", StageStatus.COMPLETED
                    ) + StageInfo("Generating AI output...", StageStatus.RUNNING)
                    it.copy(currentStageLabel = "Generating output via AI...", stages = stages)
                }
            }

            is ExecutionState.GeneratingOutput -> {
                _state.update {
                    it.copy(currentStageLabel = "Generating output via ${executionState.providerName}...")
                }
            }

            is ExecutionState.Completed -> {
                _state.update {
                    val stages = it.stages.markLastCompleted("Output generated") + StageInfo(
                        "Completed", StageStatus.COMPLETED
                    )
                    it.copy(
                        isRunning = false,
                        isCompleted = true,
                        currentStageLabel = "Completed",
                        stages = stages,
                        outputText = executionState.outputText,
                        providerType = executionState.providerType,
                        tokenUsage = executionState.tokenUsage,
                        durationMs = executionState.durationMs
                    )
                }

                // Update run record
                repository.updateRun(
                    WorkflowRun(
                        id = runId,
                        templateId = template.id,
                        templateName = template.name,
                        triggerType = "manual",
                        status = WorkflowRunStatus.COMPLETED,
                        currentStage = "Completed",
                        outputText = executionState.outputText,
                        providerType = executionState.providerType,
                        promptTokens = executionState.tokenUsage.promptTokens,
                        completionTokens = executionState.tokenUsage.completionTokens,
                        totalTokens = executionState.tokenUsage.totalTokens,
                        durationMs = executionState.durationMs,
                        completedAtMillis = System.currentTimeMillis()
                    )
                )

                // Update template last run
                repository.updateTemplate(
                    template.copy(
                        lastRunAtMillis = System.currentTimeMillis(),
                        lastRunStatus = WorkflowRunStatus.COMPLETED,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }

            is ExecutionState.Failed -> {
                _state.update {
                    val stages = it.stages.toMutableList()
                    if (stages.isNotEmpty()) {
                        stages[stages.lastIndex] = stages.last().copy(
                            status = StageStatus.FAILED,
                            detail = executionState.error
                        )
                    }
                    stages.add(StageInfo("Failed", StageStatus.FAILED, executionState.error))
                    it.copy(
                        isRunning = false,
                        isFailed = true,
                        currentStageLabel = "Failed: ${executionState.error}",
                        stages = stages,
                        errorMessage = executionState.error
                    )
                }

                // Update run record
                repository.updateRun(
                    WorkflowRun(
                        id = runId,
                        templateId = template.id,
                        templateName = template.name,
                        triggerType = "manual",
                        status = WorkflowRunStatus.FAILED,
                        currentStage = executionState.stage,
                        errorMessage = executionState.error,
                        completedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun List<StageInfo>.markLastCompleted(detail: String = ""): List<StageInfo> {
        if (isEmpty()) return this
        val list = toMutableList()
        val last = list.last()
        if (last.status == StageStatus.RUNNING) {
            list[list.lastIndex] = last.copy(status = StageStatus.COMPLETED, detail = detail.ifBlank { last.detail })
        }
        return list
    }
}

package com.automatist.app.feature.workflow.run

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.domain.actions.WorkflowActionRegistry
import com.automatist.app.domain.engine.ErrorRedactor
import com.automatist.app.domain.engine.ExecutionState
import com.automatist.app.domain.engine.WorkflowExecutionEngine
import com.automatist.app.domain.models.*
import com.automatist.app.domain.readiness.ReadinessEvaluator
import com.automatist.app.domain.readiness.WorkflowReadiness
import com.automatist.app.domain.repositories.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RunUiState(
    val isLoading: Boolean = true,
    val templateName: String = "",
    val isRunning: Boolean = false,
    val stages: List<StageInfo> = emptyList(),
    val currentStageLabel: String = "",
    val outputText: String = "",
    val outputFormat: OutputFormat = OutputFormat.MARKDOWN,
    val providerType: ProviderType? = null,
    val tokenUsage: TokenUsage? = null,
    val durationMs: Long? = null,
    val errorMessage: String? = null,
    val errorDetail: String? = null,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val runId: Long? = null,
    // Preflight
    val isBlockedBySetup: Boolean = false,
    val setupIssues: List<String> = emptyList(),
    // Profile diagnostics
    val profileName: String = "",
    val modelId: String = "",
    // Social output
    val isSocialOutput: Boolean = false,
    // Versions
    val versions: List<OutputVersion> = emptyList(),
    val synthesisInput: String = "",
    val isRegenerating: Boolean = false
)

data class StageInfo(
    val label: String,
    val status: StageStatus,
    val detail: String = "",
    val actionData: String = "", // full action result text for inspection
    /**
     * Wall-clock start time for this stage, stamped only when the stage is
     * created in [StageStatus.RUNNING]. `0L` means "no timestamp available"
     * (e.g. for purely decorative completed rows like compaction info that
     * are inserted already-COMPLETED). The run page uses this to show a
     * live "Ns elapsed" hint for the currently running row.
     */
    val startedAtMillis: Long = 0L,
    /**
     * Raw source content fetched for this action (Reading source stage).
     * Attached only when meaningfully different from [actionData] — i.e. only
     * for actions that have a preprocessing instruction. Empty string means
     * "don't render a source-content expander for this row".
     */
    val sourceText: String = "",
    /**
     * Prompt text associated with the stage — the action instruction for
     * action-prompt rows, the combined final prompt for the final-generation
     * row. Empty string means "don't render a prompt expander for this row".
     */
    val promptText: String = ""
)

enum class StageStatus { PENDING, RUNNING, COMPLETED, FAILED }

@HiltViewModel
class WorkflowRunViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    private val engine: WorkflowExecutionEngine,
    private val readinessEvaluator: ReadinessEvaluator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        private const val TAG = "WorkflowRunVM"
    }

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

            // Preflight readiness check
            val readiness = readinessEvaluator.evaluateWorkflow(template)
            if (!readiness.isFullyReady) {
                val issues = mutableListOf<String>()

                // Action-level issues (missing service keys, etc.)
                readiness.needsSetupActions.forEach { ar ->
                    val info = WorkflowActionRegistry.getInfo(ar.type)
                    val missing = ar.requirements
                        .filter { it.status == com.automatist.app.domain.readiness.ReadinessStatus.NEEDS_SETUP }
                        .joinToString(", ") { it.requirement.label }
                    issues.add("${info.displayName}: $missing")
                }

                // Profile-level issues (missing key, disabled profile, deleted profile)
                issues.addAll(readiness.profileIssues)

                _state.value = RunUiState(
                    isLoading = false,
                    templateName = template.name,
                    isBlockedBySetup = true,
                    setupIssues = issues
                )
                return@launch
            }

            startRun(template)
        }
    }

    /**
     * Retry the workflow after a failed run. Resets the UI state and starts a fresh run
     * using the current workflow definition. Creates a new run record — the failed run
     * remains intact in history.
     *
     * Safe to call multiple times: guards against duplicate in-progress runs.
     */
    fun retryRun() {
        if (_state.value.isRunning) return // prevent duplicate retry
        Log.i(TAG, "retryRun requested templateId=$templateId — engine will reset local runtime state at run-start")
        viewModelScope.launch {
            val template = repository.getTemplateById(templateId)
            if (template == null) {
                _state.update { it.copy(errorMessage = "Workflow no longer exists. It may have been deleted.") }
                return@launch
            }
            // Reset UI to initial running state
            _state.value = RunUiState(
                isLoading = false,
                templateName = template.name,
                isRunning = false
            )
            startRun(template)
        }
    }

    private fun startRun(template: WorkflowTemplate) {
        viewModelScope.launch {
            Log.d(TAG, "startRun() launched thread=${Thread.currentThread().name}")
            _state.update { it.copy(isRunning = true, outputFormat = template.outputConfig.outputFormat) }

            // Create run record
            val runId = repository.insertRun(
                WorkflowRun(
                    templateId = template.id,
                    templateName = template.name,
                    triggerType = "manual",
                    status = WorkflowRunStatus.RUNNING,
                    currentStage = "Preparing",
                    outputFormat = template.outputConfig.outputFormat
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
                        stages = listOf(runningStage("Preparing"))
                    )
                }
            }

            is ExecutionState.ValidatingInputs -> {
                val n = executionState.totalActions
                val label = "Validating $n ${if (n == 1) "action" else "actions"}"
                _state.update {
                    val stages = it.stages.markLastCompleted() + runningStage(label)
                    it.copy(currentStageLabel = "Validating inputs...", stages = stages)
                }
            }

            is ExecutionState.ActionStarted -> {
                val header = "Reading source: ${executionState.actionLabel}"
                _state.update {
                    val stages = it.stages.markLastCompleted() + runningStage(
                        label = header,
                        detail = "${executionState.actionIndex + 1}/${executionState.totalActions}"
                    )
                    it.copy(currentStageLabel = header, stages = stages)
                }
            }

            is ExecutionState.ActionSourceFetched -> {
                // Attach the raw source to the still-running "Reading source"
                // row so the user can expand and inspect exactly what was read.
                _state.update { s ->
                    val stages = s.stages.toMutableList()
                    val lastIdx = stages.lastIndex
                    if (lastIdx >= 0 && stages[lastIdx].status == StageStatus.RUNNING) {
                        stages[lastIdx] = stages[lastIdx].copy(
                            sourceText = executionState.rawSourceText
                        )
                    }
                    s.copy(stages = stages)
                }
            }

            is ExecutionState.ActionPromptStarted -> {
                // Action has its own instruction — mark the source-reading row complete
                // and push a distinct "Applying instruction" row so the user sees
                // that an AI pass is actively running for this action.
                val header = "Applying instruction: ${executionState.actionLabel}"
                _state.update {
                    val stages = it.stages.markLastCompleted() + runningStage(
                        label = header,
                        detail = executionState.instructionPreview
                    ).copy(
                        // Carry the full instruction text for the "Show prompt"
                        // expander. The short preview stays in `detail` for the
                        // header / first line of the row.
                        promptText = executionState.instructionText
                    )
                    it.copy(currentStageLabel = header, stages = stages)
                }
            }

            is ExecutionState.ActionCompleted -> {
                _state.update {
                    val stages = it.stages.markLastCompleted(
                        detail = executionState.resultPreview,
                        actionData = executionState.fullResultText
                    )
                    it.copy(stages = stages)
                }
            }

            is ExecutionState.ActionFailed -> {
                _state.update {
                    val stages = it.stages.toMutableList()
                    if (stages.isNotEmpty()) {
                        val detail = if (executionState.rawError.isNotBlank()) {
                            "${executionState.error}\n---\n${executionState.rawError}"
                        } else {
                            executionState.error
                        }
                        stages[stages.lastIndex] = stages.last().copy(
                            status = StageStatus.FAILED,
                            detail = detail
                        )
                    }
                    it.copy(stages = stages)
                }
            }

            is ExecutionState.ProcessingStarted -> {
                val compactionDetail = if (executionState.compactionMode.isNotBlank() &&
                    executionState.originalInputLength != executionState.combinedInputLength
                ) {
                    val pct = ((executionState.originalInputLength - executionState.combinedInputLength) * 100) /
                            executionState.originalInputLength.coerceAtLeast(1)
                    "Input compaction: ${executionState.compactionMode} " +
                            "(${executionState.originalInputLength} → ${executionState.combinedInputLength} chars, -${pct}%)"
                } else {
                    "Combined ${executionState.combinedInputLength} chars"
                }
                _state.update {
                    val stages = it.stages.markLastCompleted() + StageInfo(
                        compactionDetail, StageStatus.COMPLETED
                    ) + runningStage("Generating final output…")
                    it.copy(currentStageLabel = "Generating final output…", stages = stages)
                }
            }

            is ExecutionState.GeneratingOutput -> {
                // Refine the running final-generation stage with profile/model context
                // so the user sees exactly which AI is producing the result, without
                // losing the "final output" framing.
                val headerDetail = buildString {
                    if (executionState.providerName.isNotBlank()) append(executionState.providerName)
                    if (executionState.modelId.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(executionState.modelId)
                    }
                }
                val header = if (headerDetail.isNotBlank())
                    "Generating final output · $headerDetail"
                else
                    "Generating final output…"
                // Combine system prompt + user content into a single inspectable
                // block for the "Show final prompt" expander. Read-only — the
                // provider layer is what actually sends text to the model.
                val finalPrompt = buildString {
                    if (executionState.systemPrompt.isNotBlank()) {
                        append(executionState.systemPrompt.trim())
                    }
                    if (executionState.userContent.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(executionState.userContent)
                    }
                }
                _state.update {
                    // Attach provider/model info + final prompt to the existing
                    // running stage so the stage row itself shows which AI is
                    // working and lets the user inspect the prompt.
                    val stages = it.stages.toMutableList()
                    if (stages.isNotEmpty() && stages.last().status == StageStatus.RUNNING) {
                        stages[stages.lastIndex] = stages.last().copy(
                            label = "Generating final output",
                            detail = headerDetail.ifBlank { stages.last().detail },
                            promptText = finalPrompt.ifBlank { stages.last().promptText }
                        )
                    }
                    it.copy(
                        currentStageLabel = header,
                        stages = stages,
                        profileName = executionState.profileName,
                        modelId = executionState.modelId
                    )
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
                        durationMs = executionState.durationMs,
                        profileName = executionState.profileName,
                        modelId = executionState.modelId,
                        isSocialOutput = executionState.isSocialOutput,
                        versions = executionState.versions,
                        synthesisInput = executionState.synthesisInput
                    )
                }

                // Determine output format: social mode uses JSON internally
                val effectiveFormat = if (executionState.isSocialOutput) OutputFormat.JSON
                    else template.outputConfig.outputFormat

                // Snapshot stages before the async block so we serialise a stable value,
                // not whatever the state happens to be when Default picks up the work.
                val stagesSnapshot = _state.value.stages

                // Move JSON serialisation + redaction + Room writes OFF Main. For a
                // typical run with ~5 stages and a few KB of actionData each, stagesToJson
                // + ErrorRedactor.redact can take 10–50 ms of CPU work; doing it on
                // Main.immediate (viewModelScope default) adds avoidable jank.
                withContext(Dispatchers.Default) {
                    val persistedStagesJson = stagesToJson(stagesSnapshot)
                    val versionsJson = OutputVersion.toJson(executionState.versions)

                    repository.updateRun(
                        WorkflowRun(
                            id = runId,
                            templateId = template.id,
                            templateName = template.name,
                            triggerType = "manual",
                            status = WorkflowRunStatus.COMPLETED,
                            currentStage = "Completed",
                            outputText = executionState.outputText,
                            outputFormat = effectiveFormat,
                            providerType = executionState.providerType,
                            promptTokens = executionState.tokenUsage.promptTokens,
                            completionTokens = executionState.tokenUsage.completionTokens,
                            totalTokens = executionState.tokenUsage.totalTokens,
                            durationMs = executionState.durationMs,
                            completedAtMillis = System.currentTimeMillis(),
                            profileName = executionState.profileName,
                            modelId = executionState.modelId,
                            isSocialOutput = executionState.isSocialOutput,
                            stagesJson = persistedStagesJson,
                            synthesisInput = executionState.synthesisInput,
                            versionsJson = versionsJson
                        )
                    )

                    repository.updateTemplate(
                        template.copy(
                            lastRunAtMillis = System.currentTimeMillis(),
                            lastRunStatus = WorkflowRunStatus.COMPLETED,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
            }

            is ExecutionState.Failed -> {
                val rawError = executionState.rawError.ifBlank { null }
                val rawForStorage = ErrorRedactor.redactForStorage(rawError)
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
                        errorMessage = executionState.error,
                        errorDetail = rawError
                    )
                }

                // Snapshot + persist off Main (same reasoning as Completed path)
                val stagesSnapshot = _state.value.stages
                withContext(Dispatchers.Default) {
                    val persistedStagesJson = stagesToJson(stagesSnapshot)
                    repository.updateRun(
                        WorkflowRun(
                            id = runId,
                            templateId = template.id,
                            templateName = template.name,
                            triggerType = "manual",
                            status = WorkflowRunStatus.FAILED,
                            currentStage = executionState.stage,
                            errorMessage = executionState.error,
                            errorDetail = rawForStorage,
                            completedAtMillis = System.currentTimeMillis(),
                            stagesJson = persistedStagesJson
                        )
                    )
                }
            }
        }
    }

    /**
     * Regenerate: append one new version using the frozen synthesis input.
     * Does NOT refetch actions — only reruns final AI generation.
     */
    fun regenerate() {
        val currentState = _state.value
        if (currentState.synthesisInput.isBlank() || currentState.isRegenerating) return
        val runId = currentState.runId ?: return
        val currentVersions = currentState.versions
        if (currentVersions.size >= 10) return // max versions cap

        viewModelScope.launch {
            _state.update { it.copy(isRegenerating = true) }

            val template = repository.getTemplateById(templateId)
            if (template == null) {
                _state.update { it.copy(isRegenerating = false) }
                return@launch
            }

            val nextVersion = currentVersions.maxOfOrNull { it.version }?.plus(1) ?: 2

            val result = engine.regenerate(
                frozenInput = currentState.synthesisInput,
                template = template,
                nextVersion = nextVersion
            )

            result.onSuccess { newVersion ->
                val updatedVersions = currentVersions + newVersion
                _state.update {
                    it.copy(
                        isRegenerating = false,
                        versions = updatedVersions,
                        outputText = newVersion.outputText // show latest version
                    )
                }

                // Persist updated versions to DB
                val run = repository.getRunById(runId)
                if (run != null) {
                    repository.updateRun(run.copy(
                        versionsJson = OutputVersion.toJson(updatedVersions)
                    ))
                }
            }.onFailure {
                _state.update { it.copy(isRegenerating = false) }
            }
        }
    }

    /** Convert in-memory StageInfo list to persisted JSON with redaction/truncation. */
    private fun stagesToJson(stages: List<StageInfo>): String {
        val persisted = stages.map { stage ->
            PersistedStage(
                label = stage.label,
                status = stage.status.name,
                detail = stage.detail,
                actionData = truncateAndRedact(stage.actionData)
            )
        }
        return PersistedStage.toJson(persisted)
    }

    /** Redact secrets and truncate to safe storage size per action. */
    private fun truncateAndRedact(data: String): String {
        if (data.isBlank()) return ""
        val redacted = ErrorRedactor.redact(data)
        val maxPerAction = 6000
        return if (redacted.length <= maxPerAction) redacted
        else redacted.take(maxPerAction - 40) + "\n\n[truncated — ${redacted.length} chars total]"
    }

    /** Factory for a freshly-started running stage that stamps its start time. */
    private fun runningStage(label: String, detail: String = ""): StageInfo =
        StageInfo(
            label = label,
            status = StageStatus.RUNNING,
            detail = detail,
            startedAtMillis = System.currentTimeMillis()
        )

    private fun List<StageInfo>.markLastCompleted(detail: String = "", actionData: String = ""): List<StageInfo> {
        if (isEmpty()) return this
        val list = toMutableList()
        val last = list.last()
        if (last.status == StageStatus.RUNNING) {
            list[list.lastIndex] = last.copy(
                status = StageStatus.COMPLETED,
                detail = detail.ifBlank { last.detail },
                actionData = actionData.ifBlank { last.actionData }
            )
        }
        return list
    }
}

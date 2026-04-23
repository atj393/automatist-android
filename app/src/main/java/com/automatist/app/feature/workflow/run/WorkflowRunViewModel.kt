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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    val isRegenerating: Boolean = false,
    // Auto-retry — 0 for initial attempt / manual retry, 1..MAX_AUTO_RETRIES
    // when an automatic retry is active. Surfaced to UI so the run page can
    // show an "Auto-retry N/3" badge.
    val autoRetryAttempt: Int = 0,
    val isAutoRetrying: Boolean = false
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
    // Passed by "Run Again" from a failed run detail screen. 0L or missing means
    // "no prior run to resume from — fresh run". The VM treats 0L as null.
    private val initialResumeFromRunId: Long? =
        savedStateHandle.get<Long>("resumeFromRunId")?.takeIf { it > 0L }

    private val _state = MutableStateFlow(RunUiState())
    val state = _state.asStateFlow()

    // Coalescing guard for persistLiveProgress. The engine emits several
    // benign non-terminal transitions (e.g. ActionSourceFetched right after
    // ActionStarted) that don't change the UI state enough to warrant a new
    // incremental write. Skipping identical writes spares Room + observers
    // a wasted flow emission on every such tick. Scoped per runId so an
    // auto-retry chain starts fresh.
    private var persistedStagesJson: String = ""
    private var persistedStageLabel: String = ""
    private var persistedProfileName: String = ""
    private var persistedModelId: String = ""
    private var persistedForRunId: Long = -1L

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

            startRun(template, resumeFromRunId = initialResumeFromRunId)
        }
    }

    /**
     * Retry the workflow after a failed run. Creates a NEW run record — the
     * prior failed run remains intact in history. When the prior failed run
     * captured a valid resume snapshot AND the workflow definition hasn't
     * changed, this skips already-successful earlier work and restarts from
     * the first failed step. Otherwise falls back to a full rerun.
     *
     * Safe to call multiple times: guards against duplicate in-progress runs.
     */
    fun retryRun() {
        if (_state.value.isRunning) return // prevent duplicate retry
        val priorRunId = _state.value.runId
        Log.i(TAG, "retryRun requested templateId=$templateId priorRunId=$priorRunId")
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
            startRun(template, resumeFromRunId = priorRunId)
        }
    }

    /**
     * Starts a new run. If [resumeFromRunId] is provided and the prior run
     * captured a valid resume snapshot that still matches the current
     * workflow definition, execution uses the resume path (skip actions,
     * reuse frozen combined input). Otherwise this is a full rerun.
     *
     * Either way, a fresh [WorkflowRun] row is inserted — prior run history
     * is never touched.
     *
     * [autoRetryAttempt] / [parentRunId] carry auto-retry context: 0 means
     * "initial attempt or manual retry — no auto-retry chain"; 1..3 means
     * "this is the Nth automatic retry after a failure". The auto-retry
     * loop itself lives in [handleState]'s Failed branch.
     */
    private fun startRun(
        template: WorkflowTemplate,
        resumeFromRunId: Long? = null,
        autoRetryAttempt: Int = 0,
        parentRunId: Long? = null
    ) {
        viewModelScope.launch {
            Log.d(TAG, "startRun() launched thread=${Thread.currentThread().name} resumeFromRunId=$resumeFromRunId autoRetryAttempt=$autoRetryAttempt")
            _state.update {
                it.copy(
                    isRunning = true,
                    outputFormat = template.outputConfig.outputFormat,
                    autoRetryAttempt = autoRetryAttempt,
                    isAutoRetrying = autoRetryAttempt > 0
                )
            }

            // Decide resume vs fresh BEFORE inserting the new row so currentStage
            // reflects what's actually about to happen.
            val resumeSnapshot = if (resumeFromRunId != null) {
                val prior = repository.getRunById(resumeFromRunId)
                val snap = prior?.resumeSnapshot
                if (snap != null && snap.canResumeFor(template)) {
                    Log.i(TAG, "retry path=resume priorRunId=$resumeFromRunId reachedStage=${snap.reachedStage}")
                    snap
                } else {
                    if (snap != null) {
                        Log.i(TAG, "retry path=full-rerun reason=snapshot-stale-or-mismatched priorRunId=$resumeFromRunId")
                    } else {
                        Log.i(TAG, "retry path=full-rerun reason=no-snapshot priorRunId=$resumeFromRunId")
                    }
                    null
                }
            } else null

            // Create run record
            val runId = repository.insertRun(
                WorkflowRun(
                    templateId = template.id,
                    templateName = template.name,
                    triggerType = when {
                        autoRetryAttempt > 0 -> "auto-retry"
                        resumeSnapshot != null -> "manual-resume"
                        else -> "manual"
                    },
                    status = WorkflowRunStatus.RUNNING,
                    currentStage = when {
                        autoRetryAttempt > 0 -> "Auto-retry $autoRetryAttempt/${WorkflowRun.MAX_AUTO_RETRIES}"
                        resumeSnapshot != null -> "Resuming"
                        else -> "Preparing"
                    },
                    outputFormat = template.outputConfig.outputFormat,
                    autoRetryAttempt = autoRetryAttempt,
                    parentRunId = parentRunId
                )
            )
            _state.update { it.copy(runId = runId) }

            val flow = if (resumeSnapshot != null) {
                engine.resume(template, resumeSnapshot)
            } else {
                engine.execute(template)
            }
            flow.collect { executionState ->
                handleState(executionState, runId, template, autoRetryAttempt, parentRunId)
                // Incrementally persist progress so the run detail screen
                // re-hydrates correctly if the user navigates away and back
                // mid-run. Terminal states (Completed/Failed) do their own
                // full persist via handleState, so we skip them here.
                if (executionState !is ExecutionState.Completed &&
                    executionState !is ExecutionState.Failed
                ) {
                    persistLiveProgress(runId, executionState)
                }
            }
        }
    }

    /**
     * Snapshot the current in-memory stages + current-stage label and push
     * them to the database. Called once per non-terminal [ExecutionState]
     * event so a detail screen opening mid-run sees live progress.
     *
     * Also, on [ExecutionState.GeneratingOutput], writes the resolved
     * provider profile + model once so the detail header isn't stuck on
     * blank values while final generation is in flight.
     */
    private suspend fun persistLiveProgress(runId: Long, executionState: ExecutionState) {
        val snapshot = _state.value
        val stages = snapshot.stages
        val label = snapshot.currentStageLabel
        if (stages.isEmpty() && label.isBlank()) return

        // Reset the coalescing cache on runId boundaries so a chained auto-retry
        // can't be falsely skipped by state left over from the previous attempt.
        if (persistedForRunId != runId) {
            persistedStagesJson = ""
            persistedStageLabel = ""
            persistedProfileName = ""
            persistedModelId = ""
            persistedForRunId = runId
        }

        withContext(Dispatchers.Default) {
            val nextStagesJson = stagesToJson(stages)
            val nextLabel = label
            if (nextStagesJson != persistedStagesJson || nextLabel != persistedStageLabel) {
                repository.updateRunProgress(
                    id = runId,
                    stagesJson = nextStagesJson,
                    currentStage = nextLabel
                )
                persistedStagesJson = nextStagesJson
                persistedStageLabel = nextLabel
            }
            if (executionState is ExecutionState.GeneratingOutput) {
                val profileName = executionState.profileName
                val modelId = executionState.modelId
                if ((profileName.isNotBlank() || modelId.isNotBlank()) &&
                    (profileName != persistedProfileName || modelId != persistedModelId)
                ) {
                    repository.updateRunProfile(
                        id = runId,
                        profileName = profileName,
                        modelId = modelId
                    )
                    persistedProfileName = profileName
                    persistedModelId = modelId
                }
            }
        }
    }

    private suspend fun handleState(
        executionState: ExecutionState,
        runId: Long,
        template: WorkflowTemplate,
        autoRetryAttempt: Int = 0,
        parentRunId: Long? = null
    ) {
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
                val orig = executionState.originalInputLength
                val out = executionState.combinedInputLength
                val mode = executionState.compactionMode
                val compactionDetail = when {
                    mode.isBlank() -> "Combined $out chars"
                    out < orig -> {
                        // Honest reduction: compute once, format with a single
                        // leading minus sign so we never render `--8%` when the
                        // integer itself is already negative.
                        val pct = ((orig - out) * 100) / orig.coerceAtLeast(1)
                        "Input compaction: $mode ($orig → $out chars, -$pct%)"
                    }
                    else -> {
                        // Compaction was requested but didn't reduce (typically
                        // short, already-clean inputs). Say so plainly instead
                        // of inventing a fake percentage.
                        "Input compaction: $mode ($out chars, no reduction)"
                    }
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

                // Always persist the failed run first so history + resume snapshot
                // are durable regardless of whether auto-retry fires next.
                val stagesSnapshotForPersist = run {
                    // Snapshot the would-be failed stages list WITHOUT mutating UI state yet —
                    // UI state is decided below based on whether auto-retry will fire.
                    val temp = _state.value.stages.toMutableList()
                    if (temp.isNotEmpty()) {
                        temp[temp.lastIndex] = temp.last().copy(
                            status = StageStatus.FAILED,
                            detail = executionState.error
                        )
                    }
                    temp.add(StageInfo("Failed", StageStatus.FAILED, executionState.error))
                    temp.toList()
                }
                val resumeSnapshot = executionState.resumeSnapshot
                withContext(Dispatchers.Default) {
                    val persistedStagesJson = stagesToJson(stagesSnapshotForPersist)
                    val resumeSnapshotJson = resumeSnapshot?.let {
                        ResumeSnapshot.toJson(it)
                    } ?: ""
                    repository.updateRun(
                        WorkflowRun(
                            id = runId,
                            templateId = template.id,
                            templateName = template.name,
                            triggerType = if (autoRetryAttempt > 0) "auto-retry" else "manual",
                            status = WorkflowRunStatus.FAILED,
                            currentStage = executionState.stage,
                            errorMessage = executionState.error,
                            errorDetail = rawForStorage,
                            completedAtMillis = System.currentTimeMillis(),
                            stagesJson = persistedStagesJson,
                            resumeSnapshotJson = resumeSnapshotJson,
                            autoRetryAttempt = autoRetryAttempt,
                            parentRunId = parentRunId
                        )
                    )
                }

                // Check if an automatic retry should chain off this failure.
                // Re-read the template so a user edit mid-chain is honoured.
                val currentTemplate = repository.getTemplateById(template.id)
                val canAutoRetry = currentTemplate != null
                    && currentTemplate.autoRetryEnabled
                    && autoRetryAttempt < WorkflowRun.MAX_AUTO_RETRIES

                if (canAutoRetry && currentTemplate != null) {
                    val nextAttempt = autoRetryAttempt + 1
                    val chainParent = parentRunId ?: runId
                    Log.i(
                        TAG,
                        "auto-retry firing attempt=$nextAttempt/${WorkflowRun.MAX_AUTO_RETRIES} " +
                            "parentRunId=$chainParent priorFailedRunId=$runId"
                    )
                    // UI stays in "running" state so the user doesn't flash between
                    // a failed screen and a re-running one. The failed stage row is
                    // preserved but we append a clear "Auto-retry N/3" row to
                    // signal what's happening next.
                    _state.update { ui ->
                        val stages = stagesSnapshotForPersist.toMutableList()
                        // Replace the trailing synthetic "Failed" row with the
                        // retry announcement so the stage list doesn't keep
                        // stacking Failed/Retry/Failed/Retry rows.
                        if (stages.isNotEmpty() && stages.last().label == "Failed") {
                            stages.removeAt(stages.lastIndex)
                        }
                        stages.add(
                            StageInfo(
                                label = "Auto-retry $nextAttempt/${WorkflowRun.MAX_AUTO_RETRIES}",
                                status = StageStatus.RUNNING,
                                detail = "Previous attempt failed: ${executionState.error}",
                                startedAtMillis = System.currentTimeMillis()
                            )
                        )
                        ui.copy(
                            isRunning = true,
                            isFailed = false,
                            isAutoRetrying = true,
                            autoRetryAttempt = nextAttempt,
                            currentStageLabel = "Auto-retry $nextAttempt/${WorkflowRun.MAX_AUTO_RETRIES}",
                            stages = stages,
                            errorMessage = null,
                            errorDetail = null
                        )
                    }
                    startRun(
                        template = currentTemplate,
                        resumeFromRunId = runId,
                        autoRetryAttempt = nextAttempt,
                        parentRunId = chainParent
                    )
                } else {
                    // Final failure — flip UI to the terminal failed state.
                    if (autoRetryAttempt > 0) {
                        Log.i(TAG, "auto-retry exhausted after $autoRetryAttempt attempt(s); run finally failed")
                    }
                    _state.update {
                        it.copy(
                            isRunning = false,
                            isFailed = true,
                            isAutoRetrying = false,
                            currentStageLabel = "Failed: ${executionState.error}",
                            stages = stagesSnapshotForPersist,
                            errorMessage = executionState.error,
                            errorDetail = rawError
                        )
                    }
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

    /**
     * Honest manual-run lifecycle contract.
     *
     * A manual run is owned by [viewModelScope]; when this VM clears (Back
     * navigation pops the NavBackStackEntry, or the Activity is finished),
     * that scope cancels and the engine coroutine stops mid-stream. Without
     * this hook, the DB row left behind stayed status=RUNNING until the
     * app-start stale sweep — the user would reopen the run and see a
     * phantom spinner on a run that was no longer actually executing.
     *
     * Here we flip the abandoned run to FAILED with a clear cancellation
     * message. The DAO query is gated `AND status = 'RUNNING'` so if a
     * terminal write (Completed/Failed from the engine) won the race, it
     * is preserved and we no-op. The write itself runs on a local IO scope,
     * not [viewModelScope] (which is already cancelling), and is fire-and-
     * forget — good enough for the normal case; the app-start sweep still
     * catches the edge where the process dies before the write lands.
     */
    override fun onCleared() {
        super.onCleared()
        val snapshot = _state.value
        val rid = snapshot.runId
        // Only act when the run was genuinely in-flight. Completed / Failed
        // / not-yet-started / setup-blocked all bail out.
        if (rid == null || rid <= 0L) return
        if (!snapshot.isRunning) return
        if (snapshot.isCompleted || snapshot.isFailed) return
        if (snapshot.isBlockedBySetup) return

        val rescueScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        rescueScope.launch {
            try {
                repository.markRunCancelled(rid)
                Log.i(TAG, "manual-run cancellation persisted for runId=$rid (VM cleared while running)")
            } catch (e: Exception) {
                Log.w(TAG, "markRunCancelled failed for runId=$rid — stale sweep will reconcile", e)
            } finally {
                rescueScope.cancel()
            }
        }
    }
}

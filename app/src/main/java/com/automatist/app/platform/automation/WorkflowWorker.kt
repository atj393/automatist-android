package com.automatist.app.platform.automation

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.automatist.app.domain.engine.ExecutionState
import com.automatist.app.domain.engine.WorkflowExecutionEngine
import com.automatist.app.domain.models.ResumeSnapshot
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.platform.notifications.NotificationHelper
import com.automatist.app.platform.scheduling.ScheduleManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class WorkflowWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun workflowRepository(): WorkflowRepository
        fun executionEngine(): WorkflowExecutionEngine
        fun notificationHelper(): NotificationHelper
        fun scheduleManager(): ScheduleManager
    }

    companion object {
        const val KEY_TEMPLATE_ID = "workflow_template_id"
        const val KEY_TRIGGER_TYPE = "trigger_type"
        private const val TAG = "WorkflowWorker"
        private const val PROGRESS_NOTIFICATION_ID = 50_001
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
        Log.i(TAG, "=== WorkflowWorker.doWork() ENTRY ===")
        Log.i(TAG, "  workerId=$id, attempt=$runAttemptCount")
        Log.i(TAG, "  timestamp=${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}")

        val entryPoint = EntryPointAccessors.fromApplication(appContext, WorkerEntryPoint::class.java)
        val repo = entryPoint.workflowRepository()
        val engine = entryPoint.executionEngine()
        val notifications = entryPoint.notificationHelper()
        val scheduleManager = entryPoint.scheduleManager()

        // Ensure notification channels exist (worker may start before any UI)
        notifications.ensureChannelsExist()

        val templateId = inputData.getLong(KEY_TEMPLATE_ID, -1)
        val triggerType = inputData.getString(KEY_TRIGGER_TYPE) ?: "scheduled"
        Log.i(TAG, "  templateId=$templateId, triggerType=$triggerType")

        if (templateId == -1L) {
            Log.e(TAG, "ABORT: No template ID in input data")
            return Result.failure(workDataOf("error" to "No workflow template ID provided."))
        }

        val template = repo.getTemplateById(templateId)
        if (template == null) {
            Log.e(TAG, "ABORT: Template $templateId not found in database")
            return Result.failure(workDataOf("error" to "Workflow template not found."))
        }

        Log.i(TAG, "  template='${template.name}', actions=${template.actions.size}, notifyStart=${template.notifyOnStart}, notifyComplete=${template.notifyOnCompletion}")

        // ── Promote to foreground service ──
        // This requires: manifest declares foregroundServiceType="dataSync" on
        // androidx.work.impl.foreground.SystemForegroundService, plus
        // FOREGROUND_SERVICE and FOREGROUND_SERVICE_DATA_SYNC permissions.
        try {
            setForeground(createForegroundInfo(template.name, "Preparing..."))
            Log.i(TAG, "  Foreground promotion: SUCCESS")
        } catch (e: Exception) {
            // On Android 12+ this can fail if app is not in a state that allows
            // foreground services. Log the FULL exception — this is the #1 debug signal.
            Log.e(TAG, "  Foreground promotion: FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
            // Continue as background worker. May be killed if execution is long.
        }

        // ── Reconcile stale RUNNING records from previous crashed workers ──
        try {
            repo.failStaleRunningRecords(templateId)
        } catch (e: Exception) {
            Log.w(TAG, "  Stale record cleanup failed (non-fatal): ${e.message}")
        }

        // ── Auto-retry loop ──
        // Each iteration is one attempt. The first attempt uses engine.execute();
        // subsequent attempts use engine.resume() when the prior failed run has
        // a usable snapshot (matching fingerprint + non-empty synthesisInput).
        // We cap at 1 initial + MAX_AUTO_RETRIES automatic attempts; the loop
        // exits via `break` on any COMPLETED or on FAILED when auto-retry is
        // disabled or exhausted.
        var currentTemplate: WorkflowTemplate = template
        var autoRetryAttempt = 0
        var parentRunId: Long? = null
        var priorFailedRunId: Long? = null
        lateinit var result: Result

        while (true) {
            // Resolve resume vs full execute for this attempt.
            val resumeSnapshot: ResumeSnapshot? = priorFailedRunId?.let { id ->
                val prior = repo.getRunById(id)
                prior?.resumeSnapshot?.takeIf { it.canResumeFor(currentTemplate) }
            }
            val attemptTrigger = when {
                autoRetryAttempt > 0 -> "auto-retry"
                else -> triggerType
            }
            val attemptStage = when {
                autoRetryAttempt > 0 -> "Auto-retry $autoRetryAttempt/${WorkflowRun.MAX_AUTO_RETRIES}"
                resumeSnapshot != null -> "Resuming"
                else -> "Preparing"
            }

            // Authoritative start time for this attempt. Captured once here so
            // every terminal write below (Completed / Failed / Unexpected)
            // reuses the exact same value. Prior code relied on the
            // [WorkflowRun] data-class default `startedAtMillis =
            // System.currentTimeMillis()` being taken at insert time, but
            // then rebuilt a fresh [WorkflowRun] at terminal time without
            // passing startedAtMillis — Room's @Update wrote the terminal-
            // moment default over the original, making Started and Completed
            // visually identical in the Run Results page.
            val runStartedAtMillis = System.currentTimeMillis()
            val runId = repo.insertRun(
                WorkflowRun(
                    templateId = templateId,
                    templateName = currentTemplate.name,
                    triggerType = attemptTrigger,
                    status = WorkflowRunStatus.RUNNING,
                    currentStage = attemptStage,
                    startedAtMillis = runStartedAtMillis,
                    autoRetryAttempt = autoRetryAttempt,
                    parentRunId = parentRunId
                )
            )
            Log.i(
                TAG,
                "  Created run record: runId=$runId attempt=$autoRetryAttempt/${WorkflowRun.MAX_AUTO_RETRIES} " +
                    "parent=$parentRunId resuming=${resumeSnapshot != null}"
            )

            // Start notification only once per chain (on the first attempt).
            if (autoRetryAttempt == 0 && currentTemplate.notifyOnStart) {
                notifications.notifyRunStarted(templateId, currentTemplate.name, runId)
            }

            setProgress(workDataOf("status" to "Executing workflow: ${currentTemplate.name}", "runId" to runId))

            // Execute this attempt.
            var finalState: ExecutionState? = null
            val stageLog = mutableListOf<com.automatist.app.domain.models.PersistedStage>()
            // Coalescing cache for incremental writes — skip redundant Room updates
            // when the stage snapshot + label + profile fields are all unchanged
            // from the previous tick. Scoped to this single attempt.
            var lastWrittenStagesJson = ""
            var lastWrittenStageLabel = ""
            var lastWrittenProfileName = ""
            var lastWrittenModelId = ""

            val attemptFlow = if (resumeSnapshot != null) {
                engine.resume(currentTemplate, resumeSnapshot)
            } else {
                engine.execute(currentTemplate)
            }

            try {
                attemptFlow.collect { state ->
                    finalState = state

                    val stageLabel = when (state) {
                        is ExecutionState.Preparing -> if (autoRetryAttempt > 0)
                            "Auto-retry $autoRetryAttempt/${WorkflowRun.MAX_AUTO_RETRIES}: preparing..."
                            else "Preparing workflow..."
                        is ExecutionState.ValidatingInputs -> { val n = state.totalActions; "Validating $n ${if (n == 1) "action" else "actions"}..." }
                        is ExecutionState.ActionStarted -> "Reading source: ${state.actionLabel}"
                        is ExecutionState.ActionSourceFetched -> "Source ready: ${state.actionLabel}"
                        is ExecutionState.ActionPromptStarted -> "Applying instruction: ${state.actionLabel}"
                        is ExecutionState.ActionCompleted -> "Completed: ${state.actionLabel}"
                        is ExecutionState.ActionFailed -> "Failed: ${state.actionLabel}"
                        is ExecutionState.ProcessingStarted -> {
                            val orig = state.originalInputLength
                            val out = state.combinedInputLength
                            val mode = state.compactionMode
                            when {
                                mode.isBlank() -> "Processing $out chars..."
                                out < orig -> "Compacted: $mode ($orig → $out chars)"
                                // Compaction was requested but did not shorten
                                // the input. Report truthfully rather than
                                // implying a reduction that didn't happen.
                                else -> "Compaction: $mode (no reduction, $out chars)"
                            }
                        }
                        is ExecutionState.GeneratingOutput -> "Generating final output via ${state.providerName.ifBlank { "AI" }}..."
                        is ExecutionState.Completed -> "Completed"
                        is ExecutionState.Failed -> "Failed: ${state.error}"
                    }

                    val stageStatus = when (state) {
                        is ExecutionState.ActionFailed -> "FAILED"
                        is ExecutionState.Failed -> "FAILED"
                        is ExecutionState.Completed -> "COMPLETED"
                        else -> "COMPLETED"
                    }
                    val actionData = if (state is ExecutionState.ActionCompleted) {
                        val raw = state.fullResultText
                        val redacted = com.automatist.app.domain.engine.ErrorRedactor.redact(raw)
                        if (redacted.length <= 6000) redacted
                        else redacted.take(5960) + "\n\n[truncated — ${redacted.length} chars total]"
                    } else ""
                    stageLog.add(com.automatist.app.domain.models.PersistedStage(stageLabel, stageStatus, actionData = actionData))

                    // Incrementally persist the evolving stage log so a user
                    // who opens the run detail screen while a scheduled run
                    // is in flight sees live progress, not a blank body.
                    // Skip terminal states — they do a full repo.updateRun()
                    // below with the complete payload.
                    if (state !is ExecutionState.Completed && state !is ExecutionState.Failed) {
                        try {
                            val nextStagesJson = com.automatist.app.domain.models.PersistedStage.toJson(stageLog)
                            if (nextStagesJson != lastWrittenStagesJson || stageLabel != lastWrittenStageLabel) {
                                repo.updateRunProgress(
                                    id = runId,
                                    stagesJson = nextStagesJson,
                                    currentStage = stageLabel
                                )
                                lastWrittenStagesJson = nextStagesJson
                                lastWrittenStageLabel = stageLabel
                            }
                            if (state is ExecutionState.GeneratingOutput) {
                                val profileName = state.profileName
                                val modelId = state.modelId
                                if ((profileName.isNotBlank() || modelId.isNotBlank()) &&
                                    (profileName != lastWrittenProfileName || modelId != lastWrittenModelId)
                                ) {
                                    repo.updateRunProfile(
                                        id = runId,
                                        profileName = profileName,
                                        modelId = modelId
                                    )
                                    lastWrittenProfileName = profileName
                                    lastWrittenModelId = modelId
                                }
                            }
                        } catch (e: Exception) {
                            // Persistence is best-effort during the run; terminal
                            // state will still record the final truth.
                            Log.w(TAG, "  Incremental progress write failed (non-fatal): ${e.message}")
                        }
                    }

                    Log.d(TAG, "  Stage: $stageLabel")
                    setProgress(workDataOf("status" to stageLabel, "runId" to runId))

                    try {
                        setForeground(createForegroundInfo(currentTemplate.name, stageLabel))
                    } catch (_: Exception) { /* foreground update not critical */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "  Engine threw exception: ${e.javaClass.simpleName}: ${e.message}", e)
                finalState = ExecutionState.Failed(e.message ?: "Unexpected error", "execution")
                stageLog.add(com.automatist.app.domain.models.PersistedStage("Exception: ${e.message}", "FAILED"))
            }

            val stagesJson = com.automatist.app.domain.models.PersistedStage.toJson(stageLog)
            val terminalName = finalState?.let { it::class.simpleName } ?: "null"
            Log.i(TAG, "  Attempt $autoRetryAttempt engine flow completed. Terminal state: $terminalName")

            // Handle terminal state for THIS attempt.
            when (val terminal = finalState) {
                is ExecutionState.Completed -> {
                    Log.i(TAG, "=== COMPLETED === attempt=$autoRetryAttempt duration=${terminal.durationMs}ms, tokens=${terminal.tokenUsage.totalTokens}")

                    val effectiveFormat = if (terminal.isSocialOutput) com.automatist.app.domain.models.OutputFormat.JSON
                        else currentTemplate.outputConfig.outputFormat

                    repo.updateRun(
                        WorkflowRun(
                            id = runId,
                            templateId = templateId,
                            templateName = currentTemplate.name,
                            triggerType = attemptTrigger,
                            status = WorkflowRunStatus.COMPLETED,
                            currentStage = "Completed",
                            outputText = terminal.outputText,
                            outputFormat = effectiveFormat,
                            providerType = terminal.providerType,
                            promptTokens = terminal.tokenUsage.promptTokens,
                            completionTokens = terminal.tokenUsage.completionTokens,
                            totalTokens = terminal.tokenUsage.totalTokens,
                            durationMs = terminal.durationMs,
                            startedAtMillis = runStartedAtMillis,
                            completedAtMillis = System.currentTimeMillis(),
                            profileName = terminal.profileName,
                            modelId = terminal.modelId,
                            isSocialOutput = terminal.isSocialOutput,
                            stagesJson = stagesJson,
                            synthesisInput = terminal.synthesisInput,
                            versionsJson = com.automatist.app.domain.models.OutputVersion.toJson(terminal.versions),
                            autoRetryAttempt = autoRetryAttempt,
                            parentRunId = parentRunId
                        )
                    )
                    repo.updateTemplateLastRun(templateId, WorkflowRunStatus.COMPLETED)

                    if (currentTemplate.notifyOnCompletion) {
                        notifications.notifyRunCompleted(templateId, currentTemplate.name, runId)
                    }

                    result = Result.success(workDataOf("status" to "completed", "runId" to runId))
                    break
                }

                is ExecutionState.Failed -> {
                    Log.e(TAG, "=== FAILED === attempt=$autoRetryAttempt stage=${terminal.stage}, error=${terminal.error}")
                    val rawForStorage = com.automatist.app.domain.engine.ErrorRedactor
                        .redactForStorage(terminal.rawError.ifBlank { null })
                    val resumeSnapshotJson = terminal.resumeSnapshot?.let { ResumeSnapshot.toJson(it) } ?: ""

                    repo.updateRun(
                        WorkflowRun(
                            id = runId,
                            templateId = templateId,
                            templateName = currentTemplate.name,
                            triggerType = attemptTrigger,
                            status = WorkflowRunStatus.FAILED,
                            currentStage = terminal.stage,
                            errorMessage = terminal.error,
                            errorDetail = rawForStorage,
                            startedAtMillis = runStartedAtMillis,
                            completedAtMillis = System.currentTimeMillis(),
                            stagesJson = stagesJson,
                            resumeSnapshotJson = resumeSnapshotJson,
                            autoRetryAttempt = autoRetryAttempt,
                            parentRunId = parentRunId
                        )
                    )

                    // Re-read template so a user edit mid-chain is honoured.
                    val refreshed = repo.getTemplateById(templateId) ?: currentTemplate
                    currentTemplate = refreshed
                    val canAutoRetry = refreshed.autoRetryEnabled && autoRetryAttempt < WorkflowRun.MAX_AUTO_RETRIES
                    if (canAutoRetry) {
                        parentRunId = parentRunId ?: runId
                        priorFailedRunId = runId
                        autoRetryAttempt++
                        Log.i(TAG, "  Auto-retry queued: next attempt=$autoRetryAttempt/${WorkflowRun.MAX_AUTO_RETRIES}")
                        // Continue the loop — next iteration creates a new run row.
                        continue
                    } else {
                        if (autoRetryAttempt > 0) {
                            Log.i(TAG, "  Auto-retry exhausted after $autoRetryAttempt attempt(s) — chain finally failed")
                        }
                        repo.updateTemplateLastRun(templateId, WorkflowRunStatus.FAILED)
                        notifications.notifyRunFailed(templateId, currentTemplate.name, runId, terminal.error)
                        result = Result.failure(workDataOf("error" to terminal.error, "runId" to runId))
                        break
                    }
                }

                else -> {
                    Log.w(TAG, "=== UNEXPECTED STATE === finalState=$finalState")
                    repo.updateRun(
                        WorkflowRun(
                            id = runId,
                            templateId = templateId,
                            templateName = currentTemplate.name,
                            triggerType = attemptTrigger,
                            status = WorkflowRunStatus.FAILED,
                            currentStage = "Unknown",
                            errorMessage = "Workflow ended without a terminal state",
                            startedAtMillis = runStartedAtMillis,
                            completedAtMillis = System.currentTimeMillis(),
                            stagesJson = stagesJson,
                            autoRetryAttempt = autoRetryAttempt,
                            parentRunId = parentRunId
                        )
                    )
                    repo.updateTemplateLastRun(templateId, WorkflowRunStatus.FAILED)
                    notifications.notifyRunFailed(templateId, currentTemplate.name, runId, "Workflow ended unexpectedly")
                    result = Result.failure(workDataOf("error" to "Unexpected terminal state", "runId" to runId))
                    break
                }
            }
        }

        // ── Self-reschedule for recurring triggers ──
        // One-shot model: after each run, enqueue the next occurrence.
        // Only for "scheduled" trigger type — manual runs do not auto-reschedule.
        if (triggerType == "scheduled") {
            try {
                // Re-read template to get the current trigger (user may have edited it)
                val currentTemplate = repo.getTemplateById(templateId)
                if (currentTemplate != null) {
                    scheduleManager.rescheduleNext(templateId, currentTemplate.trigger)
                    Log.i(TAG, "  Rescheduled next occurrence for workflow $templateId")
                } else {
                    Log.w(TAG, "  Template $templateId no longer exists, skipping reschedule")
                }
            } catch (e: Exception) {
                Log.e(TAG, "  Failed to reschedule next: ${e.message}", e)
            }
        }

        Log.i(TAG, "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
        return result
    }

    private suspend fun WorkflowRepository.updateTemplateLastRun(
        templateId: Long,
        status: WorkflowRunStatus
    ) {
        val template = getTemplateById(templateId) ?: return
        updateTemplate(
            template.copy(
                lastRunAtMillis = System.currentTimeMillis(),
                lastRunStatus = status,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private fun createForegroundInfo(workflowName: String, stage: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, NotificationHelper.CHANNEL_WORKFLOW_RUNS)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Running: $workflowName")
            .setContentText(stage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }
}

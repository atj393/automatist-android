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
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
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

        // ── Create run record FIRST so we have a runId for notifications ──
        val runId = repo.insertRun(
            WorkflowRun(
                templateId = templateId,
                templateName = template.name,
                triggerType = triggerType,
                status = WorkflowRunStatus.RUNNING,
                currentStage = "Preparing"
            )
        )
        Log.i(TAG, "  Created run record: runId=$runId")

        // ── Fire start notification if enabled ──
        if (template.notifyOnStart) {
            notifications.notifyRunStarted(templateId, template.name, runId)
        }

        setProgress(workDataOf("status" to "Executing workflow: ${template.name}", "runId" to runId))

        // ── Execute workflow via shared engine ──
        Log.i(TAG, "  Calling WorkflowExecutionEngine.execute()...")
        var finalState: ExecutionState? = null
        val stageLog = mutableListOf<com.automatist.app.domain.models.PersistedStage>()

        try {
            engine.execute(template)
                .collect { state ->
                    finalState = state

                    val stageLabel = when (state) {
                        is ExecutionState.Preparing -> "Preparing workflow..."
                        is ExecutionState.ValidatingInputs -> "Validating ${state.totalActions} action(s)..."
                        is ExecutionState.ActionStarted -> "Reading source: ${state.actionLabel}"
                        is ExecutionState.ActionSourceFetched -> "Source ready: ${state.actionLabel}"
                        is ExecutionState.ActionPromptStarted -> "Running action prompt: ${state.actionLabel}"
                        is ExecutionState.ActionCompleted -> "Completed: ${state.actionLabel}"
                        is ExecutionState.ActionFailed -> "Failed: ${state.actionLabel}"
                        is ExecutionState.ProcessingStarted -> {
                            if (state.compactionMode.isNotBlank() && state.originalInputLength != state.combinedInputLength) {
                                "Compacted: ${state.compactionMode} (${state.originalInputLength} → ${state.combinedInputLength} chars)"
                            } else {
                                "Processing ${state.combinedInputLength} chars..."
                            }
                        }
                        is ExecutionState.GeneratingOutput -> "Generating final output via ${state.providerName.ifBlank { "AI" }}..."
                        is ExecutionState.Completed -> "Completed"
                        is ExecutionState.Failed -> "Failed: ${state.error}"
                    }

                    // Accumulate stage log for persistence
                    val stageStatus = when (state) {
                        is ExecutionState.ActionFailed -> "FAILED"
                        is ExecutionState.Failed -> "FAILED"
                        is ExecutionState.Completed -> "COMPLETED"
                        else -> "COMPLETED"
                    }
                    // Capture action result data for completed actions
                    val actionData = if (state is ExecutionState.ActionCompleted) {
                        val raw = state.fullResultText
                        val redacted = com.automatist.app.domain.engine.ErrorRedactor.redact(raw)
                        if (redacted.length <= 6000) redacted
                        else redacted.take(5960) + "\n\n[truncated — ${redacted.length} chars total]"
                    } else ""
                    stageLog.add(com.automatist.app.domain.models.PersistedStage(stageLabel, stageStatus, actionData = actionData))

                    Log.d(TAG, "  Stage: $stageLabel")
                    setProgress(workDataOf("status" to stageLabel, "runId" to runId))

                    try {
                        setForeground(createForegroundInfo(template.name, stageLabel))
                    } catch (_: Exception) { /* foreground update not critical */ }
                }
        } catch (e: Exception) {
            Log.e(TAG, "  Engine threw exception: ${e.javaClass.simpleName}: ${e.message}", e)
            finalState = ExecutionState.Failed(e.message ?: "Unexpected error", "execution")
            stageLog.add(com.automatist.app.domain.models.PersistedStage("Exception: ${e.message}", "FAILED"))
        }

        val stagesJson = com.automatist.app.domain.models.PersistedStage.toJson(stageLog)

        val terminalName = finalState?.let { it::class.simpleName } ?: "null"
        Log.i(TAG, "  Engine flow completed. Terminal state: $terminalName")

        // ── Handle terminal state ──
        val result = when (val terminal = finalState) {
            is ExecutionState.Completed -> {
                Log.i(TAG, "=== COMPLETED === duration=${terminal.durationMs}ms, tokens=${terminal.tokenUsage.totalTokens}, profile=${terminal.profileName}, model=${terminal.modelId}, social=${terminal.isSocialOutput}")

                val effectiveFormat = if (terminal.isSocialOutput) com.automatist.app.domain.models.OutputFormat.JSON
                    else template.outputConfig.outputFormat

                repo.updateRun(
                    WorkflowRun(
                        id = runId,
                        templateId = templateId,
                        templateName = template.name,
                        triggerType = triggerType,
                        status = WorkflowRunStatus.COMPLETED,
                        currentStage = "Completed",
                        outputText = terminal.outputText,
                        outputFormat = effectiveFormat,
                        providerType = terminal.providerType,
                        promptTokens = terminal.tokenUsage.promptTokens,
                        completionTokens = terminal.tokenUsage.completionTokens,
                        totalTokens = terminal.tokenUsage.totalTokens,
                        durationMs = terminal.durationMs,
                        completedAtMillis = System.currentTimeMillis(),
                        profileName = terminal.profileName,
                        modelId = terminal.modelId,
                        isSocialOutput = terminal.isSocialOutput,
                        stagesJson = stagesJson,
                        synthesisInput = terminal.synthesisInput,
                        versionsJson = com.automatist.app.domain.models.OutputVersion.toJson(terminal.versions)
                    )
                )

                repo.updateTemplateLastRun(templateId, WorkflowRunStatus.COMPLETED)

                if (template.notifyOnCompletion) {
                    notifications.notifyRunCompleted(templateId, template.name, runId)
                }

                Result.success(workDataOf("status" to "completed", "runId" to runId))
            }

            is ExecutionState.Failed -> {
                Log.e(TAG, "=== FAILED === stage=${terminal.stage}, error=${terminal.error}")
                if (terminal.rawError.isNotBlank()) {
                    Log.e(TAG, "  Raw error detail: ${terminal.rawError}")
                }

                val rawForStorage = com.automatist.app.domain.engine.ErrorRedactor
                    .redactForStorage(terminal.rawError.ifBlank { null })

                repo.updateRun(
                    WorkflowRun(
                        id = runId,
                        templateId = templateId,
                        templateName = template.name,
                        triggerType = triggerType,
                        status = WorkflowRunStatus.FAILED,
                        currentStage = terminal.stage,
                        errorMessage = terminal.error,
                        errorDetail = rawForStorage,
                        completedAtMillis = System.currentTimeMillis(),
                        stagesJson = stagesJson
                    )
                )

                repo.updateTemplateLastRun(templateId, WorkflowRunStatus.FAILED)
                notifications.notifyRunFailed(templateId, template.name, runId, terminal.error)

                Result.failure(workDataOf("error" to terminal.error, "runId" to runId))
            }

            else -> {
                Log.w(TAG, "=== UNEXPECTED STATE === finalState=$finalState")

                repo.updateRun(
                    WorkflowRun(
                        id = runId,
                        templateId = templateId,
                        templateName = template.name,
                        triggerType = triggerType,
                        status = WorkflowRunStatus.FAILED,
                        currentStage = "Unknown",
                        errorMessage = "Workflow ended without a terminal state",
                        completedAtMillis = System.currentTimeMillis(),
                        stagesJson = stagesJson
                    )
                )

                repo.updateTemplateLastRun(templateId, WorkflowRunStatus.FAILED)
                notifications.notifyRunFailed(templateId, template.name, runId, "Workflow ended unexpectedly")

                Result.failure(workDataOf("error" to "Unexpected terminal state", "runId" to runId))
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

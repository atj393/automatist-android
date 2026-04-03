package com.synapse.app.platform.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.synapse.app.MainActivity
import com.synapse.app.domain.engine.ExecutionState
import com.synapse.app.domain.engine.WorkflowExecutionEngine
import com.synapse.app.domain.models.WorkflowRun
import com.synapse.app.domain.models.WorkflowRunStatus
import com.synapse.app.domain.repositories.WorkflowRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onEach

class WorkflowWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun workflowRepository(): WorkflowRepository
        fun executionEngine(): WorkflowExecutionEngine
    }

    companion object {
        const val KEY_TEMPLATE_ID = "workflow_template_id"
        const val KEY_TRIGGER_TYPE = "trigger_type"
        private const val NOTIFICATION_CHANNEL = "synapse_workflows"
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WorkerEntryPoint::class.java)
        val repo = entryPoint.workflowRepository()
        val engine = entryPoint.executionEngine()

        val templateId = inputData.getLong(KEY_TEMPLATE_ID, -1)
        if (templateId == -1L) {
            setProgress(workDataOf("error" to "No workflow template ID provided."))
            return Result.failure()
        }

        val template = repo.getTemplateById(templateId)
        if (template == null) {
            setProgress(workDataOf("error" to "Workflow template not found."))
            return Result.failure()
        }

        val triggerType = inputData.getString(KEY_TRIGGER_TYPE) ?: "scheduled"

        // Create a run record
        val runId = repo.insertRun(
            WorkflowRun(
                templateId = templateId,
                templateName = template.name,
                triggerType = triggerType,
                status = WorkflowRunStatus.RUNNING,
                currentStage = "Preparing"
            )
        )

        setProgress(workDataOf("status" to "Executing workflow: ${template.name}"))

        var finalState: ExecutionState? = null

        engine.execute(template)
            .onEach { state ->
                val stageLabel = when (state) {
                    is ExecutionState.Preparing -> "Preparing workflow..."
                    is ExecutionState.ValidatingInputs -> "Validating ${state.totalActions} action(s)..."
                    is ExecutionState.ActionStarted -> "Processing: ${state.actionLabel}"
                    is ExecutionState.ActionCompleted -> "Completed: ${state.actionLabel}"
                    is ExecutionState.ActionFailed -> "Failed: ${state.actionLabel}"
                    is ExecutionState.ProcessingStarted -> "Processing combined data..."
                    is ExecutionState.GeneratingOutput -> "Generating output via AI..."
                    is ExecutionState.Completed -> "Completed"
                    is ExecutionState.Failed -> "Failed: ${state.error}"
                }
                setProgress(workDataOf("status" to stageLabel))
            }
            .collect { state -> finalState = state }

        // Update run record based on terminal state
        when (val terminal = finalState) {
            is ExecutionState.Completed -> {
                repo.updateRun(
                    WorkflowRun(
                        id = runId,
                        templateId = templateId,
                        templateName = template.name,
                        triggerType = triggerType,
                        status = WorkflowRunStatus.COMPLETED,
                        currentStage = "Completed",
                        outputText = terminal.outputText,
                        providerType = terminal.providerType,
                        promptTokens = terminal.tokenUsage.promptTokens,
                        completionTokens = terminal.tokenUsage.completionTokens,
                        totalTokens = terminal.tokenUsage.totalTokens,
                        durationMs = terminal.durationMs,
                        completedAtMillis = System.currentTimeMillis()
                    )
                )

                repo.updateTemplateLastRun(templateId, WorkflowRunStatus.COMPLETED)

                if (template.notifyOnCompletion) {
                    fireNotification(template.name)
                }

                return Result.success()
            }

            is ExecutionState.Failed -> {
                repo.updateRun(
                    WorkflowRun(
                        id = runId,
                        templateId = templateId,
                        templateName = template.name,
                        triggerType = triggerType,
                        status = WorkflowRunStatus.FAILED,
                        currentStage = terminal.stage,
                        errorMessage = terminal.error,
                        completedAtMillis = System.currentTimeMillis()
                    )
                )

                repo.updateTemplateLastRun(templateId, WorkflowRunStatus.FAILED)
                setProgress(workDataOf("error" to terminal.error))
                return Result.failure()
            }

            else -> {
                return Result.failure()
            }
        }
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

    private fun fireNotification(workflowName: String) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Workflow Runs",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Synapse")
            .setContentText("Workflow \"$workflowName\" completed.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(workflowName.hashCode(), notification)
    }
}

package com.automatist.app.platform.scheduling

import android.content.Context
import android.util.Log
import androidx.work.*
import com.automatist.app.domain.models.WorkflowTrigger
import com.automatist.app.platform.automation.WorkflowWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedule state derived from WorkManager's WorkInfo.State.
 */
enum class ScheduleState(val displayLabel: String) {
    NOT_SCHEDULED("Not scheduled"),
    ENQUEUED("Scheduled"),
    RUNNING("Running"),
    SUCCEEDED("Last run succeeded"),
    FAILED("Last run failed"),
    CANCELLED("Cancelled"),
    BLOCKED("Blocked")
}

data class ScheduleInfo(
    val state: ScheduleState,
    val workManagerState: WorkInfo.State? = null,
    val workerId: String? = null,
    val tags: Set<String> = emptySet(),
    val runAttemptCount: Int = 0
)

@Singleton
class ScheduleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ScheduleManager"

        fun scheduledWorkName(templateId: Long) = "WorkflowWorker_$templateId"
        fun oneTimeWorkName(templateId: Long) = "WorkflowWorker_${templateId}_OneTime"
    }

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    // ── Schedule Creation ──

    /**
     * Schedules or cancels work for a workflow based on its trigger.
     *
     * Uses ONE-SHOT work requests with computed initial delays.
     * After each successful run, the worker calls rescheduleNext() to enqueue the next one.
     * This avoids PeriodicWorkRequest's timing drift (which re-enqueues from completion time).
     *
     * Returns a human-readable status message.
     */
    fun scheduleWorkflow(templateId: Long, trigger: WorkflowTrigger): String {
        val uniqueName = scheduledWorkName(templateId)

        return when (trigger) {
            is WorkflowTrigger.Manual -> {
                workManager.cancelUniqueWork(uniqueName)
                Log.d(TAG, "Cancelled schedule for workflow $templateId (set to Manual)")
                "Schedule removed (trigger set to Manual)"
            }

            is WorkflowTrigger.Daily -> {
                val delayMs = computeDelayToNextTime(trigger.hour, trigger.minute)
                enqueueScheduledOneShot(templateId, uniqueName, delayMs)

                val delayMinutes = delayMs / 60_000
                val fireAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date(System.currentTimeMillis() + delayMs))
                val timeStr = "${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"

                Log.i(TAG, "Scheduled DAILY workflow $templateId: target=$timeStr, delayMs=$delayMs (${delayMinutes}m), fireAt=$fireAt, uniqueName=$uniqueName")
                "Scheduled daily at $timeStr (first run in ~${delayMinutes}m)"
            }

            is WorkflowTrigger.Weekly -> {
                val delayMs = computeDelayToNextWeeklyTime(trigger.daysOfWeek, trigger.hour, trigger.minute)
                enqueueScheduledOneShot(templateId, uniqueName, delayMs)

                val delayHours = delayMs / 3_600_000
                val fireAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date(System.currentTimeMillis() + delayMs))
                val timeStr = "${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"

                Log.i(TAG, "Scheduled WEEKLY workflow $templateId: target=$timeStr, days=${trigger.daysOfWeek}, delayMs=$delayMs (${delayHours}h), fireAt=$fireAt, uniqueName=$uniqueName")
                "Scheduled weekly at $timeStr (first run in ~${delayHours}h)"
            }

            is WorkflowTrigger.Interval -> {
                val delayMs = trigger.intervalMinutes.toLong() * 60_000L
                enqueueScheduledOneShot(templateId, uniqueName, delayMs)

                Log.i(TAG, "Scheduled INTERVAL workflow $templateId: every ${trigger.intervalMinutes}m, delayMs=$delayMs, uniqueName=$uniqueName")
                "Scheduled ${trigger.displayLabel} (next run in ~${trigger.intervalMinutes}m)"
            }

            is WorkflowTrigger.NotificationKeyword -> {
                Log.d(TAG, "NotificationKeyword trigger not yet supported for workflow $templateId")
                "Notification triggers coming soon"
            }
        }
    }

    /**
     * Called by WorkflowWorker after a scheduled run completes to enqueue the next occurrence.
     */
    fun rescheduleNext(templateId: Long, trigger: WorkflowTrigger) {
        val uniqueName = scheduledWorkName(templateId)

        val delayMs = when (trigger) {
            is WorkflowTrigger.Daily -> computeDelayToNextTime(trigger.hour, trigger.minute)
            is WorkflowTrigger.Weekly -> computeDelayToNextWeeklyTime(trigger.daysOfWeek, trigger.hour, trigger.minute)
            is WorkflowTrigger.Interval -> trigger.intervalMinutes.toLong() * 60_000L
            else -> {
                Log.d(TAG, "rescheduleNext: trigger is ${trigger::class.simpleName}, not rescheduling")
                return
            }
        }

        enqueueScheduledOneShot(templateId, uniqueName, delayMs)

        val fireAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis() + delayMs))
        Log.i(TAG, "Rescheduled workflow $templateId: delayMs=$delayMs, nextFireAt=$fireAt")
    }

    private fun enqueueScheduledOneShot(templateId: Long, uniqueName: String, delayMs: Long) {
        val inputData = workDataOf(
            WorkflowWorker.KEY_TEMPLATE_ID to templateId,
            WorkflowWorker.KEY_TRIGGER_TYPE to "scheduled"
        )

        val request = OneTimeWorkRequestBuilder<WorkflowWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag("workflow_schedule")
            .addTag("template_$templateId")
            .build()

        // KEEP: replaces any existing pending work for this workflow
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)

        Log.d(TAG, "  enqueued: uniqueName=$uniqueName, workerId=${request.id}, delayMs=$delayMs")
    }

    /**
     * Fires a one-time immediate execution (manual "test run" from background).
     */
    fun runNow(templateId: Long) {
        val request = OneTimeWorkRequestBuilder<WorkflowWorker>()
            .setInputData(
                workDataOf(
                    WorkflowWorker.KEY_TEMPLATE_ID to templateId,
                    WorkflowWorker.KEY_TRIGGER_TYPE to "manual"
                )
            )
            .addTag("workflow_manual")
            .addTag("template_$templateId")
            .build()

        workManager.enqueueUniqueWork(
            oneTimeWorkName(templateId), ExistingWorkPolicy.REPLACE, request
        )

        Log.i(TAG, "Enqueued ONE-TIME run: templateId=$templateId, workerId=${request.id}")
    }

    /**
     * Reconcile scheduled jobs after app start or reboot.
     * For each non-manual template, check if WorkManager has a pending job.
     * If not (because the one-shot completed and the reschedule was lost to a crash/kill),
     * re-enqueue the next occurrence.
     */
    suspend fun reconcileSchedules(
        getTemplates: suspend () -> List<com.automatist.app.domain.models.WorkflowTemplate>
    ) {
        try {
            val templates = getTemplates()
            var reconciled = 0
            for (template in templates) {
                if (template.trigger is WorkflowTrigger.Manual) continue
                if (!template.isEnabled) continue

                val info = getScheduleStatusSync(template.id)
                // SUCCEEDED, FAILED, CANCELLED, NOT_SCHEDULED all mean no pending work
                if (info.state != ScheduleState.ENQUEUED && info.state != ScheduleState.RUNNING) {
                    val delayMs = when (val trigger = template.trigger) {
                        is WorkflowTrigger.Daily -> computeDelayToNextTime(trigger.hour, trigger.minute)
                        is WorkflowTrigger.Weekly -> computeDelayToNextWeeklyTime(trigger.daysOfWeek, trigger.hour, trigger.minute)
                        is WorkflowTrigger.Interval -> trigger.intervalMinutes.toLong() * 60_000L
                        else -> continue
                    }
                    enqueueScheduledOneShot(template.id, scheduledWorkName(template.id), delayMs)
                    reconciled++
                    Log.i(TAG, "Reconciled schedule for '${template.name}' (id=${template.id}), was ${info.state.displayLabel}")
                }
            }
            if (reconciled > 0) {
                Log.i(TAG, "Schedule reconciliation complete: $reconciled workflow(s) re-enqueued")
            } else {
                Log.d(TAG, "Schedule reconciliation: all schedules intact")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schedule reconciliation failed: ${e.message}", e)
        }
    }

    /**
     * Cancels all scheduled work for a workflow.
     */
    fun cancelSchedule(templateId: Long) {
        workManager.cancelUniqueWork(scheduledWorkName(templateId))
        workManager.cancelUniqueWork(oneTimeWorkName(templateId))
        Log.i(TAG, "Cancelled all work for workflow $templateId")
    }

    // ── Status Queries ──

    fun getScheduleStatusFlow(templateId: Long): Flow<ScheduleInfo> {
        return workManager.getWorkInfosForUniqueWorkFlow(scheduledWorkName(templateId))
            .map { workInfos -> workInfosToScheduleInfo(workInfos) }
    }

    fun getScheduleStatusSync(templateId: Long): ScheduleInfo {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(scheduledWorkName(templateId)).get()
            workInfosToScheduleInfo(workInfos)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying schedule status for workflow $templateId", e)
            ScheduleInfo(state = ScheduleState.NOT_SCHEDULED)
        }
    }

    fun getAllScheduledWorkFlow(): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosByTagFlow("workflow_schedule")
    }

    private fun workInfosToScheduleInfo(workInfos: List<WorkInfo>): ScheduleInfo {
        if (workInfos.isEmpty()) {
            return ScheduleInfo(state = ScheduleState.NOT_SCHEDULED)
        }

        val info = workInfos.first()
        val state = when (info.state) {
            WorkInfo.State.ENQUEUED -> ScheduleState.ENQUEUED
            WorkInfo.State.RUNNING -> ScheduleState.RUNNING
            WorkInfo.State.SUCCEEDED -> ScheduleState.SUCCEEDED
            WorkInfo.State.FAILED -> ScheduleState.FAILED
            WorkInfo.State.BLOCKED -> ScheduleState.BLOCKED
            WorkInfo.State.CANCELLED -> ScheduleState.CANCELLED
        }

        return ScheduleInfo(
            state = state,
            workManagerState = info.state,
            workerId = info.id.toString(),
            tags = info.tags,
            runAttemptCount = info.runAttemptCount
        )
    }

    // ── Delay Computations ──

    private fun computeDelayToNextTime(hour: Int, minute: Int): Long {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    private fun computeDelayToNextWeeklyTime(daysOfWeek: Set<Int>, hour: Int, minute: Int): Long {
        val now = java.util.Calendar.getInstance()
        val todayIso = when (now.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            java.util.Calendar.SUNDAY -> 7
            else -> 1
        }

        var bestDelay = Long.MAX_VALUE
        for (targetDay in daysOfWeek) {
            var daysAhead = targetDay - todayIso
            if (daysAhead < 0) daysAhead += 7
            val target = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, daysAhead)
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 7)
            }
            val delay = target.timeInMillis - now.timeInMillis
            if (delay < bestDelay) bestDelay = delay
        }
        return bestDelay
    }
}

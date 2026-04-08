package com.synapse.app.platform.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.synapse.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "NotificationHelper"

        // Channels
        const val CHANNEL_WORKFLOW_RUNS = "synapse_workflows"
        const val CHANNEL_SCHEDULE_STATUS = "synapse_schedule_status"

        // Notification IDs
        private const val ID_OFFSET_START = 10_000
        private const val ID_OFFSET_COMPLETE = 20_000
        private const val ID_OFFSET_FAILED = 30_000
        private const val ID_OFFSET_SCHEDULE = 40_000
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            val runsChannel = NotificationChannel(
                CHANNEL_WORKFLOW_RUNS,
                "Workflow Runs",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for workflow execution start, completion, and failure"
            }

            val scheduleChannel = NotificationChannel(
                CHANNEL_SCHEDULE_STATUS,
                "Schedule Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications about schedule registration and status"
            }

            notificationManager.createNotificationChannel(runsChannel)
            notificationManager.createNotificationChannel(scheduleChannel)
            Log.d(TAG, "Notification channels created")
        }
    }

    fun ensureChannelsExist() {
        createChannels()
    }

    // ── Permission Checks ──

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun needsNotificationPermissionRequest(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()
    }

    // ── Workflow Run Notifications ──

    /**
     * Notify that a workflow run has started.
     * At start time we only have templateId. Deep link goes to workflow_run/{templateId}
     * which shows the live execution screen or the latest run context.
     */
    fun notifyRunStarted(templateId: Long, workflowName: String, runId: Long) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Cannot send start notification - permission not granted")
            return
        }

        Log.i(TAG, "Posting START notification: workflow='$workflowName', templateId=$templateId, runId=$runId")

        val notification = NotificationCompat.Builder(context, CHANNEL_WORKFLOW_RUNS)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Workflow Started")
            .setContentText("\"$workflowName\" is running...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createRunDetailIntent(runId))
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        safeNotify(ID_OFFSET_START + templateId.toInt(), notification, "start")
    }

    /**
     * Notify that a workflow run completed successfully.
     * Deep link goes directly to the run detail screen.
     */
    fun notifyRunCompleted(templateId: Long, workflowName: String, runId: Long) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Cannot send completion notification - permission not granted")
            return
        }

        Log.i(TAG, "Posting COMPLETED notification: workflow='$workflowName', templateId=$templateId, runId=$runId")

        // Cancel any start notification
        cancelSafe(ID_OFFSET_START + templateId.toInt())

        val notification = NotificationCompat.Builder(context, CHANNEL_WORKFLOW_RUNS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Workflow Completed")
            .setContentText("\"$workflowName\" finished successfully.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createRunDetailIntent(runId))
            .setAutoCancel(true)
            .build()

        safeNotify(ID_OFFSET_COMPLETE + templateId.toInt(), notification, "completed")
    }

    /**
     * Notify that a workflow run failed.
     * Deep link goes to the run detail screen which shows the error.
     */
    fun notifyRunFailed(templateId: Long, workflowName: String, runId: Long, errorMessage: String?) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Cannot send failure notification - permission not granted")
            return
        }

        Log.i(TAG, "Posting FAILED notification: workflow='$workflowName', templateId=$templateId, runId=$runId, error=$errorMessage")

        // Cancel any start notification
        cancelSafe(ID_OFFSET_START + templateId.toInt())

        val notification = NotificationCompat.Builder(context, CHANNEL_WORKFLOW_RUNS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Workflow Failed")
            .setContentText("\"$workflowName\" failed${errorMessage?.let { ": $it" } ?: "."}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createRunDetailIntent(runId))
            .setAutoCancel(true)
            .build()

        safeNotify(ID_OFFSET_FAILED + templateId.toInt(), notification, "failed")
    }

    // ── Schedule Notifications ──

    fun notifyScheduleCreated(workflowName: String, nextRunDescription: String) {
        if (!hasNotificationPermission()) return

        Log.d(TAG, "Posting schedule-created notification for '$workflowName': $nextRunDescription")

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Schedule Active")
            .setContentText("\"$workflowName\" scheduled — $nextRunDescription")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createMainIntent())
            .setAutoCancel(true)
            .build()

        safeNotify(ID_OFFSET_SCHEDULE + workflowName.hashCode(), notification, "schedule")
    }

    // ── Safe Notify ──

    private fun safeNotify(id: Int, notification: android.app.Notification, label: String) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            Log.d(TAG, "  -> $label notification posted (id=$id)")
        } catch (e: SecurityException) {
            Log.e(TAG, "  -> SecurityException posting $label notification (id=$id): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "  -> Exception posting $label notification (id=$id): ${e.message}", e)
        }
    }

    private fun cancelSafe(id: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(id)
        } catch (_: Exception) { /* ignore */ }
    }

    // ── Intents ──

    /**
     * Deep link to the run detail screen showing output, tokens, errors, etc.
     * Route: workflow_run_detail/{runId}
     */
    private fun createRunDetailIntent(runId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "workflow_run_detail/$runId")
        }
        // Use runId as requestCode so each run gets a unique PendingIntent
        return PendingIntent.getActivity(
            context, runId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createMainIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
    }
}

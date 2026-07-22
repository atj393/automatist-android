package com.automatist.app.platform.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.automatist.app.MainActivity
import com.automatist.app.data.local.SettingsRepository
import com.automatist.app.data.network.RssParser
import com.automatist.app.data.providers.TransformProviderRouter
import com.automatist.app.domain.models.*
import com.automatist.app.domain.repositories.HistoryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import androidx.work.ListenableWorker.Result as WorkResult

class SynthesizerWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun rssParser(): RssParser
        fun transformProviderRouter(): TransformProviderRouter
        fun historyRepository(): HistoryRepository
    }

    companion object {
        private const val TAG = "SynthesizerWorker"
        private const val CHANNEL_ID = "automatist_automation"
        private const val PROGRESS_NOTIFICATION_ID = 50_002
    }

    override suspend fun doWork(): WorkResult {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(appContext, WorkerEntryPoint::class.java)
            val settingsRepo = entryPoint.settingsRepository()
            val router = entryPoint.transformProviderRouter()
            val historyRepo = entryPoint.historyRepository()
            val rssParser = entryPoint.rssParser()

            // Ensure the channel exists before posting to it (required for setForeground on API 26+).
            ensureChannelExists()

            // ── Promote to foreground service ──
            // This worker does an RSS fetch followed by an AI transform (which may run
            // on-device via MediaPipe for up to ~120s) — same class of long-running,
            // network-bound work as WorkflowWorker, which already does this. Without
            // foreground promotion this worker has no protection from Android 15/16's
            // stricter background-execution limits.
            try {
                setForeground(createForegroundInfo("Fetching your Morning Brief..."))
                Log.i(TAG, "Foreground promotion: SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "Foreground promotion: FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
                // Continue as background worker. May be killed if execution is long.
            }

            val config = settingsRepo.briefConfig.first()

            setProgress(workDataOf("status" to "Validating RSS Configuration..."))

            // 1. Fetch RSS feeds
            if (config.rssFeeds.isEmpty()) {
                setProgress(workDataOf("error" to "No RSS feeds configured."))
                return WorkResult.failure()
            }

            setProgress(workDataOf("status" to "Fetching & Parsing RSS..."))
            val rawXmlDigest = rssParser.fetchAndParse(config.rssFeeds)
            if (rawXmlDigest.isBlank()) {
                setProgress(workDataOf("error" to "RSS parsing failed or returned empty content. Verify URLs."))
                return WorkResult.failure()
            }

            // 2. Compute prompt Override based on output type and social target
            val promptOverride = buildString {
                append("You are an executive assistant. Here is your configuration: ")
                append("Target Output Format: ${config.briefOutputType.displayName}. ")
                if (config.briefOutputType == BriefOutputType.CUSTOM) {
                    append("Custom Instructions: ${config.customFormatText}. ")
                }
                if (config.briefOutputType == BriefOutputType.SOCIAL_POST && config.socialPlatforms.isNotEmpty()) {
                    append("Target Social Platforms: ${config.socialPlatforms.joinToString { it.displayName }}. Match the format/tone for these specific networks. ")
                }
                append("\n\nProcess the following parsed RSS snippets strictly into that format. Ignore irrelevant ads.\n")
            }

            // 3. Transform via existing Router
            // TransformType is required by the interface but we override the prompt itself securely.
            setProgress(workDataOf("status" to "Generating Summary via AI..."))
            val input = ArticleInput(text = rawXmlDigest, systemPromptOverride = promptOverride)
            val transformResult = router.transform(input, TransformType.MORNING_SUMMARY)

            // 4. Save to History explicitly as Morning Brief.
            transformResult.onSuccess { result ->
                setProgress(workDataOf("status" to "Saving to Database..."))
                val preview = rawXmlDigest.take(50).replace("\n", " ") + "..."
                historyRepo.saveHistoryItem(
                    HistoryItem(
                        workflowType = WorkflowType.MORNING_BRIEF,
                        inputPreview = preview,
                        transformType = TransformType.MORNING_SUMMARY, 
                        outputText = result.outputText,
                        providerType = result.providerType,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )

                // 5. Notify if enabled
                if (config.isNotificationsEnabled) {
                    fireNotification()
                }
                return WorkResult.success()
            }.onFailure {
                // Fail gracefully if API is down
                setProgress(workDataOf("error" to "AI Transformation failed: ${it.message}"))
                return WorkResult.failure() 
            }

            return WorkResult.success()
        } catch (e: Exception) {
            setProgress(workDataOf("error" to "Fatal Error: ${e.message ?: e.toString()}"))
            return WorkResult.failure()
        }
    }

    private fun fireNotification() {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Automatist")
            .setContentText("Your Morning Brief is ready for review.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun ensureChannelExists() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Automatist Automation",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(status: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Automatist")
            .setContentText(status)
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

package com.synapse.app.feature.workflow.schedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.domain.models.WorkflowTemplate
import com.synapse.app.domain.models.WorkflowTrigger
import com.synapse.app.domain.repositories.WorkflowRepository
import com.synapse.app.platform.notifications.NotificationHelper
import com.synapse.app.platform.scheduling.ScheduleInfo
import com.synapse.app.platform.scheduling.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ScheduledWorkflowInfo(
    val template: WorkflowTemplate,
    val scheduleInfo: ScheduleInfo,
    val nextRunLabel: String,
    val lastRunLabel: String?,
    val lastCompletedLabel: String?,
    val lastErrorMessage: String?,
    val triggerLabel: String
)

data class ScheduleStatusUiState(
    val isLoading: Boolean = true,
    val scheduledWorkflows: List<ScheduledWorkflowInfo> = emptyList(),
    val manualWorkflows: List<WorkflowTemplate> = emptyList(),
    val hasNotificationPermission: Boolean = true,
    val isBatteryOptimized: Boolean = false,
    val batterySettingsMessage: String? = null
)

@HiltViewModel
class ScheduleStatusViewModel @Inject constructor(
    private val repository: WorkflowRepository,
    private val scheduleManager: ScheduleManager,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleStatusUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val hasPermission = notificationHelper.hasNotificationPermission()
            val batteryOptimized = isBatteryOptimized()

            // All blocking WorkManager queries run on IO
            val (scheduled, manual) = withContext(Dispatchers.IO) {
                val templates = try {
                    repository.getAllTemplates().first()
                } catch (_: Exception) {
                    emptyList()
                }

                val scheduledList = mutableListOf<ScheduledWorkflowInfo>()
                val manualList = mutableListOf<WorkflowTemplate>()

                for (template in templates) {
                    if (template.trigger is WorkflowTrigger.Manual) {
                        manualList.add(template)
                    } else {
                        val info = scheduleManager.getScheduleStatusSync(template.id)
                        val latestRun = try {
                            repository.getLatestRun(template.id)
                        } catch (_: Exception) { null }

                        scheduledList.add(
                            ScheduledWorkflowInfo(
                                template = template,
                                scheduleInfo = info,
                                nextRunLabel = computeNextRunLabel(template.trigger),
                                lastRunLabel = template.lastRunAtMillis?.let { formatTimestamp(it) },
                                lastCompletedLabel = latestRun?.completedAtMillis?.let { formatTimestamp(it) },
                                lastErrorMessage = latestRun?.errorMessage,
                                triggerLabel = triggerLabel(template.trigger)
                            )
                        )
                    }
                }

                scheduledList to manualList
            }

            _state.update {
                ScheduleStatusUiState(
                    isLoading = false,
                    scheduledWorkflows = scheduled,
                    manualWorkflows = manual,
                    hasNotificationPermission = hasPermission,
                    isBatteryOptimized = batteryOptimized
                )
            }
        }
    }

    fun runNow(templateId: Long) {
        scheduleManager.runNow(templateId)
    }

    fun cancelSchedule(templateId: Long) {
        scheduleManager.cancelSchedule(templateId)
        refresh()
    }

    fun openBatterySettings() {
        // Try direct exemption dialog first (shows "Allow" / "Deny" for this app)
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            _state.update { it.copy(batterySettingsMessage = null) }
            return
        } catch (_: Exception) { /* Intent not available on this device */ }

        // Fallback: open the system battery optimization list (user finds Synapse manually)
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            _state.update { it.copy(batterySettingsMessage = "Find Synapse in the list and select \"Don't optimize.\"") }
            return
        } catch (_: Exception) { /* Intent not available on this device */ }

        // Last resort: open general app settings
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            _state.update { it.copy(batterySettingsMessage = "Look for Battery in the app settings and disable optimization.") }
            return
        } catch (_: Exception) {
            _state.update { it.copy(batterySettingsMessage = "Could not open settings. Go to Settings > Apps > Synapse > Battery and disable optimization manually.") }
        }
    }

    fun clearBatteryMessage() {
        _state.update { it.copy(batterySettingsMessage = null) }
    }

    private fun isBatteryOptimized(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun triggerLabel(trigger: WorkflowTrigger): String = when (trigger) {
        is WorkflowTrigger.Manual -> "Manual"
        is WorkflowTrigger.Daily -> "Daily at ${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"
        is WorkflowTrigger.Weekly -> {
            val dayNames = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
            val days = trigger.daysOfWeek.sorted().mapNotNull { dayNames[it] }.joinToString(", ")
            "Weekly ($days) at ${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"
        }
        is WorkflowTrigger.Interval -> trigger.displayLabel
        is WorkflowTrigger.NotificationKeyword -> "Notification"
    }

    private fun computeNextRunLabel(trigger: WorkflowTrigger): String {
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
            else -> "Not scheduled"
        }
    }

    private fun formatTimestamp(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }
}

package com.automatist.app.feature.workflow.schedule

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.automatist.app.domain.models.WorkflowRunStatus
import com.automatist.app.platform.scheduling.ScheduleState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleStatusScreen(
    onBack: () -> Unit,
    onEditWorkflow: (Long) -> Unit,
    onViewRun: (Long) -> Unit,
    onCreateWorkflow: () -> Unit = {},
    viewModel: ScheduleStatusViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Refresh when returning from edit or run screens
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refresh()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show battery settings fallback message
    LaunchedEffect(state.batterySettingsMessage) {
        state.batterySettingsMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.clearBatteryMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled Workflows") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ── System Status Section ──
            item {
                Text(
                    "System Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Notification permission
            if (!state.hasNotificationPermission) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Notifications disabled",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "You won't receive alerts when scheduled workflows start, complete, or fail.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }) {
                                Text("Enable")
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Notifications, null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Notifications enabled",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Battery optimization warning
            if (state.isBatteryOptimized) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.BatteryAlert, null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Battery optimization active",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "Your device may delay scheduled runs to save battery. Disabling battery optimization for Automatist improves reliability.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            TextButton(onClick = { viewModel.openBatterySettings() }) {
                                Text("Fix")
                            }
                        }
                    }
                }
            }

            // ── Device limitation info (always shown) ──
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Android may adjust run times by a few minutes to save battery. Some phone brands (Samsung, Xiaomi, Huawei) may restrict background tasks more aggressively. If runs are delayed, check your battery optimization settings above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Scheduled Workflows Section ──
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Scheduled (${state.scheduledWorkflows.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.scheduledWorkflows.isEmpty() && state.manualWorkflows.isEmpty()) {
                // No workflows at all
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.EventBusy, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No scheduled workflows yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create a workflow and set a Daily or Weekly trigger to get started.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onCreateWorkflow) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create Workflow")
                            }
                        }
                    }
                }
            } else if (state.scheduledWorkflows.isEmpty()) {
                // Has workflows but none scheduled
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No scheduled workflows",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Edit a workflow and change the trigger from Manual to Daily or Weekly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                items(state.scheduledWorkflows, key = { it.template.id }) { info ->
                    ScheduledWorkflowCard(
                        info = info,
                        onEdit = { onEditWorkflow(info.template.id) },
                        onRun = { onViewRun(info.template.id) },
                        onRunNow = { viewModel.runNow(info.template.id) },
                        onCancel = { viewModel.cancelSchedule(info.template.id) }
                    )
                }
            }

            // ── Manual Workflows Section ──
            if (state.manualWorkflows.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Manual Only (${state.manualWorkflows.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "These workflows run on-demand only. Change their trigger to Daily or Weekly to schedule them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                items(state.manualWorkflows, key = { it.id }) { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    template.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Manual trigger",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onEditWorkflow(template.id) }) {
                                Text("Edit")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ScheduledWorkflowCard(
    info: ScheduledWorkflowInfo,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onRunNow: () -> Unit,
    onCancel: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    val statusColor = when (info.scheduleInfo.state) {
        ScheduleState.ENQUEUED -> MaterialTheme.colorScheme.primary
        ScheduleState.RUNNING -> MaterialTheme.colorScheme.tertiary
        ScheduleState.SUCCEEDED -> MaterialTheme.colorScheme.primary
        ScheduleState.FAILED -> MaterialTheme.colorScheme.error
        ScheduleState.CANCELLED -> MaterialTheme.colorScheme.outline
        ScheduleState.BLOCKED -> MaterialTheme.colorScheme.error
        ScheduleState.NOT_SCHEDULED -> MaterialTheme.colorScheme.error
    }

    val statusIcon = when (info.scheduleInfo.state) {
        ScheduleState.ENQUEUED -> Icons.Default.Schedule
        ScheduleState.RUNNING -> Icons.Default.Sync
        ScheduleState.SUCCEEDED -> Icons.Default.CheckCircle
        ScheduleState.FAILED -> Icons.Default.Error
        ScheduleState.CANCELLED -> Icons.Default.Cancel
        ScheduleState.BLOCKED -> Icons.Default.Block
        ScheduleState.NOT_SCHEDULED -> Icons.Default.EventBusy
    }

    // Friendly status label
    val statusLabel = when (info.scheduleInfo.state) {
        ScheduleState.ENQUEUED -> "Scheduled and waiting"
        ScheduleState.RUNNING -> "Running now"
        ScheduleState.SUCCEEDED -> "Completed, next run scheduled"
        ScheduleState.FAILED -> "Last run failed"
        ScheduleState.CANCELLED -> "Schedule cancelled"
        ScheduleState.BLOCKED -> "Blocked (waiting for conditions)"
        ScheduleState.NOT_SCHEDULED -> "Not registered with system"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        info.template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Details
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                DetailRow("Trigger", info.triggerLabel)
                DetailRow("Next run", info.nextRunLabel)
                if (info.lastRunLabel != null) {
                    DetailRow("Last started", info.lastRunLabel)
                }
                if (info.lastCompletedLabel != null) {
                    DetailRow("Last finished", info.lastCompletedLabel)
                }
                DetailRow("Last result", when (info.template.lastRunStatus) {
                    WorkflowRunStatus.COMPLETED -> "Completed successfully"
                    WorkflowRunStatus.FAILED -> "Failed"
                    WorkflowRunStatus.RUNNING -> "In progress"
                    null -> "No runs yet"
                })

                // Show last error in friendly language
                if (info.lastErrorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                            Text(
                                "Last failure reason",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                info.lastErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                DetailRow("Start alert", if (info.template.notifyOnStart) "On" else "Off")
                DetailRow("Completion alert", if (info.template.notifyOnCompletion) "On" else "Off")

                // Worker details (compact)
                if (info.scheduleInfo.workerId != null) {
                    DetailRow("Worker", info.scheduleInfo.workerId.take(8) + "...")
                }
                if (info.scheduleInfo.runAttemptCount > 0) {
                    DetailRow("Attempts", info.scheduleInfo.runAttemptCount.toString())
                }
            }

            Spacer(Modifier.height(10.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run Now")
                }
                if (info.scheduleInfo.state != ScheduleState.NOT_SCHEDULED &&
                    info.scheduleInfo.state != ScheduleState.CANCELLED) {
                    TextButton(onClick = { showCancelDialog = true }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Schedule") },
            text = { Text("Cancel the schedule for \"${info.template.name}\"? The workflow will still be available for manual runs.") },
            confirmButton = {
                TextButton(onClick = { onCancel(); showCancelDialog = false }) {
                    Text("Cancel Schedule", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

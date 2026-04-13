package com.synapse.app.feature.workflow.details

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synapse.app.domain.actions.WorkflowActionRegistry
import com.synapse.app.domain.models.*
import com.synapse.app.feature.upgrade.UpgradePromptDialog
import com.synapse.app.platform.scheduling.ScheduleInfo
import com.synapse.app.platform.scheduling.ScheduleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowDetailsScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRun: (Long) -> Unit,
    onViewHistory: (Long) -> Unit,
    onViewRunDetail: (Long) -> Unit,
    onNavigateToDuplicated: (Long) -> Unit = {},
    onNavigateToUpgrade: () -> Unit = {},
    viewModel: WorkflowDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showUpgradePrompt by remember { mutableStateOf(false) }

    if (showUpgradePrompt) {
        UpgradePromptDialog(
            onUpgrade = { showUpgradePrompt = false; onNavigateToUpgrade() },
            onDismiss = { showUpgradePrompt = false }
        )
    }

    // ── SAF launcher for export ──
    // Holds the JSON + warnings to surface once the user picks a destination.
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingExportWarnings by remember { mutableStateOf<List<String>>(emptyList()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        val warnings = pendingExportWarnings
        pendingExportJson = null
        pendingExportWarnings = emptyList()
        viewModel.clearExportData()
        if (uri != null && json != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    val message = if (warnings.isEmpty()) {
                        "Workflow exported"
                    } else {
                        "Exported with warnings: ${warnings.joinToString(". ")}"
                    }
                    snackbarHostState.showSnackbar(message)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Export failed: ${e.message?.take(80) ?: "unknown error"}")
                }
            }
        }
    }

    // ── React to exportData becoming available ──
    val exportData = state.exportData
    LaunchedEffect(exportData) {
        if (exportData != null) {
            pendingExportJson = exportData.json
            pendingExportWarnings = exportData.warnings
            exportLauncher.launch(exportData.suggestedFilename)
        }
    }

    // ── React to duplicate success ──
    val duplicatedId = state.duplicatedWorkflowId
    LaunchedEffect(duplicatedId) {
        if (duplicatedId != null) {
            viewModel.clearDuplicatedWorkflowId()
            onNavigateToDuplicated(duplicatedId)
        }
    }

    // ── Snackbar messages from ViewModel ──
    val snackbarMessage = state.snackbarMessage
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            viewModel.clearSnackbarMessage()
        }
    }

    // Delete confirmation dialog
    if (state.showDeleteDialog) {
        val name = state.template?.name ?: ""
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete Workflow") },
            text = { Text("Delete \"$name\"? This will also remove all run history and cancel any scheduled runs.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteWorkflow { onBack() } }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Workflow Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    state.template?.let { t ->
                        IconButton(onClick = { onEdit(t.id) }) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Default.MoreVert, "More actions")
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        overflowExpanded = false
                                        if (!state.isProcessing) {
                                            scope.launch {
                                                if (viewModel.canCreateWorkflow()) {
                                                    viewModel.duplicateWorkflow()
                                                } else {
                                                    showUpgradePrompt = true
                                                }
                                            }
                                        }
                                    },
                                    enabled = !state.isProcessing
                                )
                                DropdownMenuItem(
                                    text = { Text("Export JSON") },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        overflowExpanded = false
                                        if (!state.isProcessing) viewModel.prepareExport()
                                    },
                                    enabled = !state.isProcessing
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val template = state.template
        if (template == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Workflow not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Text(
                template.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (template.description.isNotBlank()) {
                Text(
                    template.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Template origin
            if (state.sourceTemplateName.isNotBlank()) {
                AssistChip(
                    onClick = {},
                    label = { Text("From template: ${state.sourceTemplateName}") },
                    leadingIcon = { Icon(Icons.Default.Dashboard, null, modifier = Modifier.size(16.dp)) }
                )
            }

            // ── Enable / Disable ──
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (template.isEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (template.isEnabled) Icons.Default.CheckCircle else Icons.Default.PauseCircle,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = if (template.isEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (template.isEnabled) "Active" else "Paused",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (template.isEnabled) "Scheduled runs are active"
                            else "Scheduled runs are paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = template.isEnabled,
                        onCheckedChange = { viewModel.toggleEnabled() }
                    )
                }
            }

            // ── Action Buttons ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onRun(template.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run Now")
                }
                OutlinedButton(
                    onClick = { onEdit(template.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            }

            // ── Schedule & Trigger ──
            DetailsSectionCard(
                icon = Icons.Default.Schedule,
                title = "Trigger & Schedule"
            ) {
                DetailRow("Trigger", triggerLabel(template.trigger))

                if (template.trigger !is WorkflowTrigger.Manual) {
                    val nextRun = computeNextRunLabel(template.trigger)
                    DetailRow("Next run", nextRun)

                    template.lastRunAtMillis?.let {
                        DetailRow("Last run", formatTimestamp(it))
                    }

                    // Schedule status from WorkManager
                    state.scheduleInfo?.let { info ->
                        Spacer(Modifier.height(4.dp))
                        ScheduleStatusRow(info)
                    }
                }

                template.lastRunStatus?.let { status ->
                    DetailRow(
                        "Last status",
                        when (status) {
                            WorkflowRunStatus.COMPLETED -> "Completed"
                            WorkflowRunStatus.FAILED -> "Failed"
                            WorkflowRunStatus.RUNNING -> "Running"
                        }
                    )
                }
            }

            // ── AI Profile ──
            DetailsSectionCard(
                icon = Icons.Default.SmartToy,
                title = "AI Profile"
            ) {
                DetailRow(
                    "Workflow default",
                    state.defaultProfileName.ifBlank { "App default" }
                )
                if (state.outputProfileName.isNotBlank()) {
                    DetailRow("Output override", state.outputProfileName)
                }
            }

            // ── Actions Summary ──
            DetailsSectionCard(
                icon = Icons.Default.ListAlt,
                title = "Actions (${template.actions.size})"
            ) {
                template.actions.forEachIndexed { i, action ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp)
                        )
                        Column {
                            Text(
                                action.label.ifBlank { action.type.displayName },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            val summary = WorkflowActionRegistry.getSummary(action)
                            if (summary.isNotBlank()) {
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (template.globalInstruction.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Processing instruction",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        template.globalInstruction,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Output ──
            DetailsSectionCard(
                icon = Icons.Default.Description,
                title = "Output"
            ) {
                DetailRow("Type", template.outputConfig.outputType.displayName)
                if (template.notifyOnStart || template.notifyOnCompletion) {
                    DetailRow(
                        "Notifications",
                        listOfNotNull(
                            if (template.notifyOnStart) "On start" else null,
                            if (template.notifyOnCompletion) "On completion" else null
                        ).joinToString(", ")
                    )
                }
            }

            // ── Recent Runs ──
            DetailsSectionCard(
                icon = Icons.Default.History,
                title = "Recent Runs"
            ) {
                if (state.recentRuns.isEmpty()) {
                    Text(
                        "No runs yet. Tap \"Run Now\" to execute this workflow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.recentRuns.take(5).forEach { run ->
                        RunRow(
                            run = run,
                            onClick = { onViewRunDetail(run.id) }
                        )
                    }

                    if (state.recentRuns.size > 5) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { onViewHistory(template.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View all ${state.recentRuns.size} runs")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── View Full History button ──
            if (state.recentRuns.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onViewHistory(template.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View Full History")
                }
            }

            // ── Delete ──
            TextButton(
                onClick = { viewModel.showDeleteDialog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Delete, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(6.dp))
                Text("Delete Workflow", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Reusable section card ──

@Composable
private fun DetailsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// ── Detail row ──

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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

// ── Run row ──

@Composable
private fun RunRow(run: WorkflowRun, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (run.status) {
                    WorkflowRunStatus.COMPLETED -> Icons.Default.CheckCircle
                    WorkflowRunStatus.FAILED -> Icons.Default.Error
                    WorkflowRunStatus.RUNNING -> Icons.Default.Sync
                },
                null,
                modifier = Modifier.size(16.dp),
                tint = when (run.status) {
                    WorkflowRunStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    WorkflowRunStatus.FAILED -> MaterialTheme.colorScheme.error
                    WorkflowRunStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                }
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatTimestamp(run.startedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        run.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    run.durationMs?.let {
                        Text(
                            formatDuration(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight, null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Schedule status row ──

@Composable
private fun ScheduleStatusRow(info: ScheduleInfo) {
    val (color, icon) = when (info.state) {
        ScheduleState.ENQUEUED -> MaterialTheme.colorScheme.primary to Icons.Default.Schedule
        ScheduleState.RUNNING -> MaterialTheme.colorScheme.tertiary to Icons.Default.Sync
        ScheduleState.SUCCEEDED -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
        ScheduleState.FAILED -> MaterialTheme.colorScheme.error to Icons.Default.Error
        ScheduleState.CANCELLED -> MaterialTheme.colorScheme.outline to Icons.Default.Cancel
        ScheduleState.BLOCKED -> MaterialTheme.colorScheme.error to Icons.Default.Block
        ScheduleState.NOT_SCHEDULED -> MaterialTheme.colorScheme.outline to Icons.Default.EventBusy
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(
            "Schedule: ${info.state.displayLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Helpers ──

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

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

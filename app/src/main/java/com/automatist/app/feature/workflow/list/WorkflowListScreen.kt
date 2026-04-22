package com.automatist.app.feature.workflow.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.automatist.app.domain.models.*
import com.automatist.app.platform.scheduling.ScheduleInfo
import com.automatist.app.platform.scheduling.ScheduleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(
    onBack: () -> Unit,
    onBrowseTemplates: () -> Unit,
    onCreateBlank: () -> Unit,
    onEdit: (Long) -> Unit,
    onRun: (Long) -> Unit,
    onViewDetails: (Long) -> Unit,
    onViewRunDetail: (Long) -> Unit,
    onViewHistory: (Long) -> Unit,
    onViewSchedules: () -> Unit = {},
    onNavigateToImported: (Long) -> Unit = {},
    onNavigateToUpgrade: () -> Unit = {},
    viewModel: WorkflowListViewModel = hiltViewModel()
) {
    val workflows by viewModel.workflows.collectAsState()
    val scheduleStatuses by viewModel.scheduleStatuses.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowExpanded by remember { mutableStateOf(false) }

    // Creation is no longer gated — free users can create workflows freely.
    // The free-tier limit is enforced on activation (enabling) instead.

    // ── SAF launcher for import ──
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                    if (jsonString.isNullOrBlank()) {
                        snackbarHostState.showSnackbar("Import failed: file is empty")
                    } else {
                        viewModel.importWorkflow(jsonString)
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Import failed: could not read file")
                }
            }
        }
    }

    // ── React to import success → navigate ──
    val importedId = importState.importedWorkflowId
    LaunchedEffect(importedId) {
        if (importedId != null) {
            viewModel.clearImportedWorkflowId()
            onNavigateToImported(importedId)
        }
    }

    // ── Snackbar messages from import ──
    val snackbarMessage = importState.snackbarMessage
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            viewModel.clearImportSnackbar()
        }
    }

    // Refresh schedule statuses when workflows change
    LaunchedEffect(workflows) {
        if (workflows.isNotEmpty()) {
            viewModel.refreshScheduleStatuses(workflows)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Workflows") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onViewSchedules) {
                        Icon(Icons.Default.Schedule, "Schedules")
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
                                text = { Text("Import Workflow") },
                                leadingIcon = { Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    overflowExpanded = false
                                    if (!importState.isImporting) {
                                        importLauncher.launch(arrayOf("application/json", "*/*"))
                                    }
                                },
                                enabled = !importState.isImporting
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = onCreateBlank) {
                    Icon(Icons.Default.Add, "Start Empty")
                }
                ExtendedFloatingActionButton(
                    onClick = onBrowseTemplates,
                    icon = { Icon(Icons.Default.Dashboard, "Templates") },
                    text = { Text("From Template") }
                )
            }
        }
    ) { padding ->
        if (workflows.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No workflows yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Create a workflow from scratch or choose a template",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBrowseTemplates) {
                        Icon(Icons.Default.Dashboard, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse Templates")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCreateBlank) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start Empty")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(workflows, key = { it.id }) { template ->
                    MyWorkflowCard(
                        template = template,
                        scheduleInfo = scheduleStatuses[template.id],
                        onClick = { onViewDetails(template.id) },
                        onEdit = { onEdit(template.id) },
                        onRun = { onRun(template.id) },
                        onViewHistory = { onViewHistory(template.id) },
                        onDelete = { viewModel.deleteWorkflow(template.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MyWorkflowCard(
    template: WorkflowTemplate,
    scheduleInfo: ScheduleInfo?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onViewHistory: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isScheduled = template.trigger !is WorkflowTrigger.Manual

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (template.description.isNotBlank()) {
                        Text(
                            template.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight, "View details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Metadata chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!template.isEnabled) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Paused", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) },
                        leadingIcon = { Icon(Icons.Default.PauseCircle, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline) },
                        modifier = Modifier.height(28.dp)
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(triggerLabel(template.trigger), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${template.actions.size} ${if (template.actions.size == 1) "action" else "actions"}", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                if (template.lastRunStatus != null) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when (template.lastRunStatus) {
                                    WorkflowRunStatus.COMPLETED -> "Last: OK"
                                    WorkflowRunStatus.FAILED -> "Last: Failed"
                                    WorkflowRunStatus.RUNNING -> "Running"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // Schedule info with real WorkManager status
            if (isScheduled) {
                val nextRunText = computeNextRunLabel(template.trigger)
                val lastRunText = template.lastRunAtMillis?.let { formatTimestamp(it) }

                Column(modifier = Modifier.padding(top = 6.dp)) {
                    // Real schedule status from WorkManager
                    if (scheduleInfo != null) {
                        ScheduleStatusChip(scheduleInfo)
                    }

                    Text(
                        "Next run: $nextRunText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (lastRunText != null) {
                        Text(
                            "Last run: $lastRunText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
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
                IconButton(onClick = onViewHistory) {
                    Icon(Icons.Default.History, "History", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Workflow") },
            text = { Text("Delete \"${template.name}\"? This will also remove all run history and cancel any scheduled runs.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ScheduleStatusChip(info: ScheduleInfo) {
    val (color, icon) = when (info.state) {
        ScheduleState.ENQUEUED -> MaterialTheme.colorScheme.primary to Icons.Default.Schedule
        ScheduleState.RUNNING -> MaterialTheme.colorScheme.tertiary to Icons.Default.Sync
        ScheduleState.SUCCEEDED -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
        ScheduleState.FAILED -> MaterialTheme.colorScheme.error to Icons.Default.Error
        ScheduleState.CANCELLED -> MaterialTheme.colorScheme.outline to Icons.Default.Cancel
        ScheduleState.BLOCKED -> MaterialTheme.colorScheme.error to Icons.Default.Block
        ScheduleState.NOT_SCHEDULED -> MaterialTheme.colorScheme.outline to Icons.Default.EventBusy
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(
            info.state.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
        if (info.runAttemptCount > 0) {
            Text(
                " (attempt ${info.runAttemptCount})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun triggerLabel(trigger: WorkflowTrigger): String = when (trigger) {
    is WorkflowTrigger.Manual -> "Manual"
    is WorkflowTrigger.Daily -> "Daily at ${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"
    is WorkflowTrigger.Weekly -> "Weekly"
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
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
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

package com.automatist.app.feature.dashboard

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatist.app.domain.models.*
import com.automatist.app.feature.upgrade.UpgradePromptDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToArticle: () -> Unit = {},
    onNavigateToMeeting: () -> Unit = {},
    onNavigateToBrief: () -> Unit = {},
    onNavigateToHistory: () -> Unit,
    onNavigateToHistoryDetail: (Long) -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToWorkflowList: () -> Unit = {},
    onNavigateToWorkflowDetails: (Long) -> Unit = {},
    onNavigateToWorkflowRun: (Long) -> Unit = {},
    onNavigateToRunDetail: (Long) -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToTemplates: () -> Unit = {},
    onCreateBlankWorkflow: () -> Unit = {},
    onNavigateToUpgrade: () -> Unit = {},
    onNavigateToCloudSync: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val recentRuns by viewModel.recentRuns.collectAsState()
    val myWorkflows by viewModel.customWorkflows.collectAsState()
    var showUpgradePrompt by remember { mutableStateOf(false) }

    fun gatedCreate(action: () -> Unit) {
        if (viewModel.canCreateWorkflow()) action() else showUpgradePrompt = true
    }

    if (showUpgradePrompt) {
        UpgradePromptDialog(
            onUpgrade = { showUpgradePrompt = false; onNavigateToUpgrade() },
            onDismiss = { showUpgradePrompt = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automatist", fontWeight = FontWeight.Bold) },
                actions = {
                    val plan by viewModel.planState.collectAsState()
                    AssistChip(
                        onClick = onNavigateToUpgrade,
                        label = {
                            Text(
                                if (plan.isProUnlocked) "Pro" else "Free",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.height(28.dp)
                    )
                    IconButton(onClick = onNavigateToCloudSync) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Cloud Sync")
                    }
                    IconButton(onClick = onNavigateToVault) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Create Workflow Section ──
            item {
                Text(
                    text = "Create a Workflow",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // Browse Templates
                    Card(
                        modifier = Modifier.weight(1f).clickable(onClick = { gatedCreate(onNavigateToTemplates) }),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Dashboard, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("From Template", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    // Start Empty
                    OutlinedCard(
                        modifier = Modifier.weight(1f).clickable(onClick = { gatedCreate(onCreateBlankWorkflow) })
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Start Empty", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── My Workflows ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("My Workflows", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onNavigateToWorkflowList) { Text("See All") }
                }
            }

            if (myWorkflows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToTemplates),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No workflows yet. Create one from a template or start empty.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(myWorkflows.take(5)) { wf ->
                    DashboardWorkflowCard(
                        template = wf,
                        onClick = { onNavigateToWorkflowDetails(wf.id) },
                        onRun = { onNavigateToWorkflowRun(wf.id) }
                    )
                }
                if (myWorkflows.size > 5) {
                    item {
                        TextButton(onClick = onNavigateToWorkflowList, modifier = Modifier.fillMaxWidth()) {
                            Text("View all ${myWorkflows.size} workflows")
                        }
                    }
                }
            }

            // ── Recent Runs ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Runs", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onNavigateToHistory) { Text("See All") }
                }
            }

            if (recentRuns.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No runs yet. Run a workflow to see results here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(recentRuns) { run ->
                    RecentRunCard(run = run, onClick = { onNavigateToRunDetail(run.id) })
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DashboardWorkflowCard(
    template: WorkflowTemplate,
    onClick: () -> Unit,
    onRun: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome, null,
                tint = if (template.isEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (template.isEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        triggerLabel(template.trigger),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Active/Inactive indicator
                    if (!template.isEnabled) {
                        Text(
                            "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    template.lastRunStatus?.let { status ->
                        Text(
                            when (status) {
                                WorkflowRunStatus.COMPLETED -> "Last: OK"
                                WorkflowRunStatus.FAILED -> "Last: Failed"
                                WorkflowRunStatus.RUNNING -> "Running"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (status) {
                                WorkflowRunStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                WorkflowRunStatus.FAILED -> MaterialTheme.colorScheme.error
                                WorkflowRunStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                }
            }
            // Play button
            IconButton(onClick = onRun) {
                Icon(
                    Icons.Default.PlayArrow, "Run Now",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RecentRunCard(run: WorkflowRun, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (run.status) {
                    WorkflowRunStatus.COMPLETED -> Icons.Default.CheckCircle
                    WorkflowRunStatus.FAILED -> Icons.Default.Error
                    WorkflowRunStatus.RUNNING -> Icons.Default.Sync
                },
                null,
                modifier = Modifier.size(20.dp),
                tint = when (run.status) {
                    WorkflowRunStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    WorkflowRunStatus.FAILED -> MaterialTheme.colorScheme.error
                    WorkflowRunStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                }
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    run.templateName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        run.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatTimestamp(run.startedAtMillis),
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

private fun triggerLabel(trigger: WorkflowTrigger): String = when (trigger) {
    is WorkflowTrigger.Manual -> "Manual"
    is WorkflowTrigger.Daily -> "Daily ${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"
    is WorkflowTrigger.Weekly -> "Weekly"
    is WorkflowTrigger.Interval -> trigger.displayLabel
    is WorkflowTrigger.NotificationKeyword -> "Notification"
}

private fun formatTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

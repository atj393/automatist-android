package com.synapse.app.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synapse.app.domain.models.*

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
    onNavigateToNotes: () -> Unit = {},
    onNavigateToTemplates: () -> Unit = {},
    onCreateBlankWorkflow: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val recentHistory by viewModel.recentHistory.collectAsState()
    val myWorkflows by viewModel.customWorkflows.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synapse", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToNotes) {
                        Icon(Icons.Default.StickyNote2, contentDescription = "Notes")
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
                        modifier = Modifier.weight(1f).clickable(onClick = onNavigateToTemplates),
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
                        modifier = Modifier.weight(1f).clickable(onClick = onCreateBlankWorkflow)
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
            }

            // ── Recent History ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent History", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onNavigateToHistory) { Text("See All") }
                }
            }

            if (recentHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No history yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(recentHistory.take(3)) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToHistoryDetail(item.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                            Text(
                                "${item.workflowType.name.replace("_", " ")} - ${item.transformType.displayName}",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(item.inputPreview, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                // Show trigger and status info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        triggerLabel(template.trigger),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            // Play button for quick run
            IconButton(onClick = onRun) {
                Icon(
                    Icons.Default.PlayArrow, "Run Once Now",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun triggerLabel(trigger: WorkflowTrigger): String = when (trigger) {
    is WorkflowTrigger.Manual -> "Manual"
    is WorkflowTrigger.Daily -> "Daily ${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"
    is WorkflowTrigger.Weekly -> "Weekly"
    is WorkflowTrigger.NotificationKeyword -> "Notification"
}

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToArticle: () -> Unit,
    onNavigateToMeeting: () -> Unit,
    onNavigateToBrief: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToHistoryDetail: (Long) -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToWorkflowList: () -> Unit = {},
    onNavigateToWorkflowRun: (Long) -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToTemplates: () -> Unit = {},
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
            // ── Workflow Templates (primary CTA) ──
            item {
                Text(
                    text = "Workflow Templates",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToTemplates),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Dashboard, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Browse Templates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Create a workflow from curated templates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            Icons.Default.ArrowForward, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ── Quick Access (legacy built-in screens) ──
            item {
                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickAccessCard(
                        title = "Article",
                        icon = Icons.Default.Article,
                        onClick = onNavigateToArticle,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAccessCard(
                        title = "Meeting",
                        icon = Icons.Default.EventNote,
                        onClick = onNavigateToMeeting,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAccessCard(
                        title = "Brief",
                        icon = Icons.Default.WbSunny,
                        onClick = onNavigateToBrief,
                        modifier = Modifier.weight(1f)
                    )
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
                                "No workflows yet. Browse templates to get started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(myWorkflows.take(3)) { wf ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToWorkflowRun(wf.id) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(wf.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    wf.description.ifBlank { "${wf.actions.size} action(s)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.PlayArrow, "Run", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
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
private fun QuickAccessCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

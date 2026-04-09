package com.synapse.app.feature.workflow.history

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
import com.synapse.app.domain.models.WorkflowRun
import com.synapse.app.domain.models.WorkflowRunStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowHistoryScreen(
    onBack: () -> Unit,
    onViewRunDetail: (Long) -> Unit,
    viewModel: WorkflowHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Run History", fontWeight = FontWeight.Bold)
                        if (state.workflowName.isNotBlank()) {
                            Text(
                                state.workflowName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
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

        if (state.runs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No runs yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Run this workflow to see results here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Summary header
            item {
                val completedCount = state.runs.count { it.status == WorkflowRunStatus.COMPLETED }
                val failedCount = state.runs.count { it.status == WorkflowRunStatus.FAILED }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryChip("${state.runs.size} total")
                    if (completedCount > 0) SummaryChip("$completedCount completed")
                    if (failedCount > 0) SummaryChip("$failedCount failed")
                }
            }

            items(state.runs, key = { it.id }) { run ->
                HistoryRunCard(
                    run = run,
                    onClick = { onViewRunDetail(run.id) }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.height(28.dp)
    )
}

@Composable
private fun HistoryRunCard(run: WorkflowRun, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Icon(
                when (run.status) {
                    WorkflowRunStatus.COMPLETED -> Icons.Default.CheckCircle
                    WorkflowRunStatus.FAILED -> Icons.Default.Error
                    WorkflowRunStatus.RUNNING -> Icons.Default.Sync
                },
                null,
                modifier = Modifier.size(24.dp),
                tint = when (run.status) {
                    WorkflowRunStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    WorkflowRunStatus.FAILED -> MaterialTheme.colorScheme.error
                    WorkflowRunStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                }
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Status + trigger
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        run.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                run.triggerType.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(22.dp)
                    )
                }

                // Timestamps
                Spacer(Modifier.height(4.dp))
                Text(
                    "Started: ${formatTimestamp(run.startedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                run.completedAtMillis?.let {
                    Text(
                        "Completed: ${formatTimestamp(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Metadata row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    run.durationMs?.let {
                        Text(
                            formatDuration(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    run.providerType?.let {
                        Text(
                            it.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    run.totalTokens?.let {
                        Text(
                            "$it tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Output preview or error
                if (run.status == WorkflowRunStatus.FAILED && run.errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        run.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (run.outputText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        run.outputText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight, null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

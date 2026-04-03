package com.synapse.app.feature.workflow.list

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
import com.synapse.app.domain.models.*
import com.synapse.app.domain.templates.BuiltInTemplates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(
    onBack: () -> Unit,
    onBrowseTemplates: () -> Unit,
    onCreateBlank: () -> Unit,
    onEdit: (Long) -> Unit,
    onRun: (Long) -> Unit,
    onViewRunDetail: (Long) -> Unit,
    viewModel: WorkflowListViewModel = hiltViewModel()
) {
    val workflows by viewModel.workflows.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Workflows") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                        onEdit = { onEdit(template.id) },
                        onRun = { onRun(template.id) },
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
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Look up source template name
    val sourceTemplateName = remember(template.sourceTemplateId) {
        if (template.sourceTemplateId.isNotBlank()) {
            BuiltInTemplates.findById(template.sourceTemplateId)?.name ?: "Custom"
        } else "Legacy"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            }

            Spacer(Modifier.height(8.dp))

            // Metadata chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Source template badge
                AssistChip(
                    onClick = {},
                    label = { Text(sourceTemplateName, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Dashboard, null, modifier = Modifier.size(14.dp))
                    }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(triggerLabel(template.trigger), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${template.actions.size} action(s)", style = MaterialTheme.typography.labelSmall) },
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

            Spacer(Modifier.height(8.dp))

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
                    Text("Run")
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
            text = { Text("Delete \"${template.name}\"? This will also remove all run history.") },
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

private fun triggerLabel(trigger: WorkflowTrigger): String = when (trigger) {
    is WorkflowTrigger.Manual -> "Manual"
    is WorkflowTrigger.Daily -> "Daily at ${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"
    is WorkflowTrigger.Weekly -> "Weekly"
    is WorkflowTrigger.NotificationKeyword -> "Notification"
}

package com.synapse.app.feature.workflow.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synapse.app.domain.models.*
import com.synapse.app.feature.workflow.components.ActionBlockList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onTestRun: (Long) -> Unit,
    viewModel: WorkflowEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val availableNotes by viewModel.availableNotes.collectAsState()
    val availableWorkflows by viewModel.availableWorkflows.collectAsState()

    // Navigate on save
    LaunchedEffect(state.savedTemplateId) {
        state.savedTemplateId?.let { onSaved(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) "Edit Workflow"
                        else if (state.sourceTemplateName.isNotBlank()) "New from ${state.sourceTemplateName}"
                        else "New Workflow"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Template origin badge ──
            if (state.sourceTemplateName.isNotBlank()) {
                AssistChip(
                    onClick = {},
                    label = { Text("Based on: ${state.sourceTemplateName}") },
                    leadingIcon = {
                        Icon(Icons.Default.Dashboard, null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            // ── Section 1: Basic Info ──
            SectionHeader("1", "Basic Info", locked = !state.isSectionEditable(EditableSection.BASICS))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Workflow Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = state.validationErrors.any { "name" in it.lowercase() },
                enabled = state.isSectionEditable(EditableSection.BASICS)
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Description (optional)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isSectionEditable(EditableSection.BASICS)
            )

            // ── Section 2: Trigger ──
            SectionHeader("2", "Trigger", locked = !state.isSectionEditable(EditableSection.TRIGGER))
            if (state.isSectionEditable(EditableSection.TRIGGER)) {
                TriggerSection(
                    trigger = state.trigger,
                    onTriggerChanged = viewModel::updateTrigger
                )
            } else {
                Text(
                    triggerSummary(state.trigger),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Section 3: Input Actions ──
            SectionHeader("3", "Input Actions")
            Text(
                "Add data sources for your workflow to process.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionBlockList(
                actions = state.actions,
                onActionsChanged = viewModel::updateActions,
                availableNotes = availableNotes,
                availableWorkflows = availableWorkflows
            )

            // ── Section 4: Processing Instructions ──
            SectionHeader("4", "Processing Instructions")

            OutlinedTextField(
                value = state.globalInstruction,
                onValueChange = viewModel::updateGlobalInstruction,
                label = { Text("Global Instruction") },
                placeholder = { Text("e.g. Combine all sources into a concise morning briefing") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Section 5: Output ──
            SectionHeader("5", "Output")
            OutputSection(
                config = state.outputConfig,
                onConfigChanged = viewModel::updateOutputConfig
            )

            // Notification toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notify on completion", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.notifyOnCompletion,
                    onCheckedChange = viewModel::updateNotifyOnCompletion
                )
            }

            // ── Validation Errors ──
            if (state.validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        state.validationErrors.forEach { error ->
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ── Save Button ──
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isEditing) "Update Workflow" else "Save Workflow")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(number: String, title: String, locked: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (locked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    number,
                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (locked) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Lock, "Locked", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun triggerSummary(trigger: WorkflowTrigger): String = when (trigger) {
    is WorkflowTrigger.Manual -> "Manual (run on demand)"
    is WorkflowTrigger.Daily -> "Daily at ${trigger.hour.toString().padStart(2, '0')}:${trigger.minute.toString().padStart(2, '0')}"
    is WorkflowTrigger.Weekly -> "Weekly"
    is WorkflowTrigger.NotificationKeyword -> "Notification-based (coming soon)"
}

@Composable
private fun TriggerSection(
    trigger: WorkflowTrigger,
    onTriggerChanged: (WorkflowTrigger) -> Unit
) {
    val triggerOptions = listOf("Manual", "Daily", "Weekly", "Notification (Coming Soon)")
    val selectedIndex = when (trigger) {
        is WorkflowTrigger.Manual -> 0
        is WorkflowTrigger.Daily -> 1
        is WorkflowTrigger.Weekly -> 2
        is WorkflowTrigger.NotificationKeyword -> 3
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        triggerOptions.forEachIndexed { index, label ->
            val isNotification = index == 3
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = selectedIndex == index,
                    onClick = {
                        if (!isNotification) {
                            when (index) {
                                0 -> onTriggerChanged(WorkflowTrigger.Manual)
                                1 -> onTriggerChanged(WorkflowTrigger.Daily())
                                2 -> onTriggerChanged(WorkflowTrigger.Weekly())
                            }
                        }
                    },
                    enabled = !isNotification
                )
                Text(
                    label,
                    color = if (isNotification) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Daily config
        if (trigger is WorkflowTrigger.Daily) {
            Row(
                modifier = Modifier.padding(start = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Run at")
                OutlinedTextField(
                    value = trigger.hour.toString().padStart(2, '0'),
                    onValueChange = { v ->
                        val h = v.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                        onTriggerChanged(trigger.copy(hour = h.coerceIn(0, 23)))
                    },
                    label = { Text("Hour") },
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
                Text(":")
                OutlinedTextField(
                    value = trigger.minute.toString().padStart(2, '0'),
                    onValueChange = { v ->
                        val m = v.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                        onTriggerChanged(trigger.copy(minute = m.coerceIn(0, 59)))
                    },
                    label = { Text("Min") },
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
            }
        }

        // Weekly config
        if (trigger is WorkflowTrigger.Weekly) {
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            Column(modifier = Modifier.padding(start = 48.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames.forEachIndexed { i, name ->
                        val dayNum = i + 1
                        FilterChip(
                            selected = dayNum in trigger.daysOfWeek,
                            onClick = {
                                val newDays = if (dayNum in trigger.daysOfWeek)
                                    trigger.daysOfWeek - dayNum else trigger.daysOfWeek + dayNum
                                onTriggerChanged(trigger.copy(daysOfWeek = newDays))
                            },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("at")
                    OutlinedTextField(
                        value = trigger.hour.toString().padStart(2, '0'),
                        onValueChange = { v ->
                            val h = v.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                            onTriggerChanged(trigger.copy(hour = h.coerceIn(0, 23)))
                        },
                        label = { Text("Hour") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(":")
                    OutlinedTextField(
                        value = trigger.minute.toString().padStart(2, '0'),
                        onValueChange = { v ->
                            val m = v.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                            onTriggerChanged(trigger.copy(minute = m.coerceIn(0, 59)))
                        },
                        label = { Text("Min") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputSection(
    config: WorkflowOutputConfig,
    onConfigChanged: (WorkflowOutputConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkflowOutputType.entries.forEach { type ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = config.outputType == type,
                    onClick = { onConfigChanged(config.copy(outputType = type)) }
                )
                Text(type.displayName)
            }
        }
    }
}

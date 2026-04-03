package com.synapse.app.feature.workflow.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.synapse.app.domain.models.WorkflowAction
import com.synapse.app.domain.models.WorkflowActionType

@Composable
fun ActionBlockEditor(
    action: WorkflowAction,
    index: Int,
    totalCount: Int,
    onUpdate: (WorkflowAction) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge
                AssistChip(
                    onClick = {},
                    label = { Text(action.type.displayName, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = action.label.ifBlank { "${action.type.displayName} #${index + 1}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                // Reorder buttons
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move up", modifier = Modifier.size(18.dp))
                    }
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move down", modifier = Modifier.size(18.dp))
                    }
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Toggle details",
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close, "Remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(4.dp))

                    // Label
                    OutlinedTextField(
                        value = action.label,
                        onValueChange = { onUpdate(action.copy(label = it)) },
                        label = { Text("Label (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Source data
                    when (action.type) {
                        WorkflowActionType.FETCH_URL -> {
                            OutlinedTextField(
                                value = action.sourceData,
                                onValueChange = { onUpdate(action.copy(sourceData = it)) },
                                label = { Text("URL") },
                                placeholder = { Text("https://...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                isError = action.sourceData.isNotBlank() && !action.sourceData.startsWith("http")
                            )
                            if (action.sourceData.isNotBlank() && !action.sourceData.startsWith("http")) {
                                Text(
                                    "URL must start with http:// or https://",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        WorkflowActionType.PASTE_TEXT -> {
                            OutlinedTextField(
                                value = action.sourceData,
                                onValueChange = { onUpdate(action.copy(sourceData = it)) },
                                label = { Text("Content") },
                                placeholder = { Text("Paste text here...") },
                                minLines = 3,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Per-action instruction
                    OutlinedTextField(
                        value = action.instruction,
                        onValueChange = { onUpdate(action.copy(instruction = it)) },
                        label = { Text("Per-source instruction (optional)") },
                        placeholder = { Text("e.g. Summarize as a market update") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

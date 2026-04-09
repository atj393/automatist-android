package com.synapse.app.feature.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synapse.app.domain.models.ProviderProfile
import com.synapse.app.domain.models.SavedNote
import com.synapse.app.domain.models.WorkflowAction
import com.synapse.app.domain.models.WorkflowActionType
import com.synapse.app.domain.models.WorkflowTemplate
import com.synapse.app.domain.readiness.ReadinessEvaluator
import java.util.UUID

@Composable
fun ActionBlockList(
    actions: List<WorkflowAction>,
    onActionsChanged: (List<WorkflowAction>) -> Unit,
    availableNotes: List<SavedNote> = emptyList(),
    availableWorkflows: List<WorkflowTemplate> = emptyList(),
    availableProfiles: List<ProviderProfile> = emptyList(),
    readinessEvaluator: ReadinessEvaluator? = null,
    onNavigateToSettings: () -> Unit = {}
) {
    var showCatalog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEachIndexed { index, action ->
            ActionBlockEditor(
                action = action,
                index = index,
                totalCount = actions.size,
                availableNotes = availableNotes,
                availableWorkflows = availableWorkflows,
                availableProfiles = availableProfiles,
                onUpdate = { updated ->
                    onActionsChanged(actions.toMutableList().also { it[index] = updated })
                },
                onRemove = {
                    onActionsChanged(actions.toMutableList().also { it.removeAt(index) })
                },
                onMoveUp = if (index > 0) {
                    {
                        val list = actions.toMutableList()
                        val item = list.removeAt(index)
                        list.add(index - 1, item)
                        onActionsChanged(list.mapIndexed { i, a -> a.copy(order = i) })
                    }
                } else null,
                onMoveDown = if (index < actions.size - 1) {
                    {
                        val list = actions.toMutableList()
                        val item = list.removeAt(index)
                        list.add(index + 1, item)
                        onActionsChanged(list.mapIndexed { i, a -> a.copy(order = i) })
                    }
                } else null
            )
        }

        // Add action button — opens the full-screen Action Catalog
        OutlinedButton(
            onClick = { showCatalog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Action")
        }
    }

    // Action Catalog (full-screen bottom sheet)
    if (showCatalog) {
        ActionCatalog(
            readinessEvaluator = readinessEvaluator,
            onNavigateToSettings = onNavigateToSettings,
            onSelectAction = { type ->
                val newAction = WorkflowAction(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    order = actions.size
                )
                onActionsChanged(actions + newAction)
            },
            onDismiss = { showCatalog = false }
        )
    }
}

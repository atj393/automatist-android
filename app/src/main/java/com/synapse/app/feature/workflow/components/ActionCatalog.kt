package com.synapse.app.feature.workflow.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import com.synapse.app.domain.actions.WorkflowActionRegistry
import com.synapse.app.domain.actions.WorkflowActionRegistry.ActionTypeInfo
import com.synapse.app.domain.actions.WorkflowActionRegistry.RequirementType
import com.synapse.app.domain.models.WorkflowActionType
import com.synapse.app.domain.readiness.ActionReadiness
import com.synapse.app.domain.readiness.ReadinessEvaluator
import com.synapse.app.domain.readiness.ReadinessStatus
import kotlinx.coroutines.launch

/**
 * Full-screen Action Catalog with dynamic readiness checks.
 * Uses ReadinessEvaluator to show real "Ready" / "Needs Setup" state per action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCatalog(
    readinessEvaluator: ReadinessEvaluator? = null,
    onSelectAction: (WorkflowActionType) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val categorizedActions = remember { WorkflowActionRegistry.getTypesByCategory() }
    var expandedType by remember { mutableStateOf<WorkflowActionType?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Dynamic readiness state — evaluated on open
    val scope = rememberCoroutineScope()
    var readinessMap by remember { mutableStateOf<Map<WorkflowActionType, ActionReadiness>>(emptyMap()) }

    LaunchedEffect(readinessEvaluator) {
        if (readinessEvaluator != null) {
            scope.launch {
                val map = mutableMapOf<WorkflowActionType, ActionReadiness>()
                WorkflowActionType.entries.forEach { type ->
                    map[type] = readinessEvaluator.evaluateAction(type)
                }
                readinessMap = map
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Add Action",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Choose a capability to add to your workflow",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search actions...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(4.dp))

            // Action list
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categorizedActions.forEach { (category, actions) ->
                    val filtered = if (searchQuery.isBlank()) actions
                    else actions.filter { info ->
                        info.displayName.contains(searchQuery, ignoreCase = true) ||
                        info.description.contains(searchQuery, ignoreCase = true) ||
                        info.longDescription.contains(searchQuery, ignoreCase = true)
                    }

                    if (filtered.isNotEmpty()) {
                        item(key = "cat_${category.name}") {
                            Text(
                                category.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(filtered, key = { "action_${it.type.name}" }) { info ->
                            ActionCatalogCard(
                                info = info,
                                readiness = readinessMap[info.type],
                                isExpanded = expandedType == info.type,
                                onToggleExpand = {
                                    expandedType = if (expandedType == info.type) null else info.type
                                },
                                onAdd = {
                                    onSelectAction(info.type)
                                    onDismiss()
                                },
                                onSetup = {
                                    onDismiss()
                                    onNavigateToSettings()
                                }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ActionCatalogCard(
    info: ActionTypeInfo,
    readiness: ActionReadiness?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAdd: () -> Unit,
    onSetup: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top row: icon, name, dynamic badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    actionIcon(info.type),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        info.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        info.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Dynamic readiness badge
                DynamicReadinessBadge(info = info, readiness = readiness)
            }

            // Expanded detail
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(modifier = Modifier.padding(bottom = 10.dp))

                    Text(
                        info.longDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    DetailRow("You provide", info.inputSummary)
                    DetailRow("You get", info.outputSummary)
                    DetailRow("Example", info.exampleUseCase)

                    // Dynamic setup status
                    if (readiness != null && readiness.requirements.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Setup status:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        readiness.requirements.forEach { reqStatus ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(
                                    when (reqStatus.status) {
                                        ReadinessStatus.READY -> Icons.Default.CheckCircle
                                        ReadinessStatus.NEEDS_SETUP -> Icons.Default.Warning
                                        ReadinessStatus.NOT_APPLICABLE -> Icons.Default.Remove
                                    },
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = when (reqStatus.status) {
                                        ReadinessStatus.READY -> MaterialTheme.colorScheme.primary
                                        ReadinessStatus.NEEDS_SETUP -> MaterialTheme.colorScheme.error
                                        ReadinessStatus.NOT_APPLICABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    reqStatus.requirement.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (reqStatus.status == ReadinessStatus.READY)
                                        MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (reqStatus.status == ReadinessStatus.NEEDS_SETUP && reqStatus.actionHint.isNotBlank()) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "- ${reqStatus.actionHint}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    } else if (info.setupRequirements.isNotEmpty()) {
                        // Fallback: static metadata when evaluator not available
                        Spacer(Modifier.height(6.dp))
                        Text("Setup needed:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        info.setupRequirements.forEach { req ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text(req.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val needsSetup = readiness != null && !readiness.isReady

                    if (needsSetup) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onSetup, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Set Up")
                            }
                            Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Anyway")
                            }
                        }
                    } else {
                        Button(
                            onClick = onAdd,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add to Workflow")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicReadinessBadge(
    info: ActionTypeInfo,
    readiness: ActionReadiness?
) {
    if (readiness != null) {
        // Dynamic badge based on actual evaluation
        if (readiness.isReady) {
            AssistChip(
                onClick = {},
                label = { Text("Ready", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.height(26.dp),
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        } else {
            AssistChip(
                onClick = {},
                label = { Text("Needs Setup", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Default.Warning, null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.height(26.dp),
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.error,
                    leadingIconContentColor = MaterialTheme.colorScheme.error
                )
            )
        }
    } else if (info.setupRequirements.isEmpty()) {
        // Static fallback: no requirements
        AssistChip(
            onClick = {},
            label = { Text("Ready", style = MaterialTheme.typography.labelSmall) },
            leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp)) },
            modifier = Modifier.height(26.dp)
        )
    } else {
        // Static fallback: has requirements but no evaluator
        AssistChip(
            onClick = {},
            label = { Text("Setup Info", style = MaterialTheme.typography.labelSmall) },
            leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp)) },
            modifier = Modifier.height(26.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun actionIcon(type: WorkflowActionType) = when (type) {
    WorkflowActionType.FETCH_WEATHER -> Icons.Default.WbSunny
    WorkflowActionType.FETCH_ROUTE_TIME -> Icons.Default.DirectionsCar
    WorkflowActionType.FETCH_URL -> Icons.Default.Language
    WorkflowActionType.FETCH_RSS_FEED -> Icons.Default.RssFeed
    WorkflowActionType.FETCH_RSS_MULTI -> Icons.Default.DynamicFeed
    WorkflowActionType.PASTE_TEXT -> Icons.Default.TextFields
    WorkflowActionType.USE_SAVED_NOTE -> Icons.Default.StickyNote2
    WorkflowActionType.FETCH_API_GET -> Icons.Default.Api
    WorkflowActionType.USE_PREVIOUS_OUTPUT -> Icons.Default.History
}

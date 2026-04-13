package com.automatist.app.feature.workflow.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatist.app.domain.readiness.ReadinessEvaluator
import com.automatist.app.domain.readiness.WorkflowReadiness
import com.automatist.app.domain.templates.BuiltInTemplates
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowTemplatesScreen(
    onBack: () -> Unit,
    onUseTemplate: (String) -> Unit,
    onNavigateToMyWorkflows: () -> Unit,
    onCreateBlank: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: WorkflowTemplatesViewModel = hiltViewModel()
) {
    val readinessEvaluator = viewModel.readinessEvaluator
    val templates = remember { BuiltInTemplates.ALL }
    val categories = remember { BuiltInTemplates.CATEGORIES }

    // Evaluate readiness for all templates
    val scope = rememberCoroutineScope()
    var readinessMap by remember { mutableStateOf<Map<String, WorkflowReadiness>>(emptyMap()) }
    LaunchedEffect(readinessEvaluator) {
        if (readinessEvaluator != null) {
            scope.launch {
                val map = mutableMapOf<String, WorkflowReadiness>()
                templates.forEach { t ->
                    map[t.id] = readinessEvaluator.evaluateWorkflow(t.blueprint)
                }
                readinessMap = map
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflow Templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToMyWorkflows) {
                        Text("My Workflows")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Start Empty card
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onCreateBlank)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Start Empty", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Build a workflow from scratch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Text(
                    "Or choose a starter template",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            categories.forEach { category ->
                val categoryTemplates = templates.filter { it.category == category }
                if (categoryTemplates.isNotEmpty()) {
                    item {
                        Text(
                            category,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(categoryTemplates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            readiness = readinessMap[template.id],
                            onUse = { onUseTemplate(template.id) },
                            onSetup = onNavigateToSettings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: BuiltInTemplates.BuiltInTemplate,
    readiness: WorkflowReadiness? = null,
    onUse: () -> Unit,
    onSetup: () -> Unit = {}
) {
    var showDetail by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDetail = !showDetail },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = templateIcon(template.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (showDetail) 5 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Readiness badge
                if (readiness != null) {
                    Spacer(Modifier.width(8.dp))
                    if (readiness.isFullyReady) {
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
                            label = { Text("Setup", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp)) },
                            modifier = Modifier.height(26.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.tertiary,
                                leadingIconContentColor = MaterialTheme.colorScheme.tertiary
                            )
                        )
                    }
                }
            }

            if (showDetail) {
                Spacer(Modifier.height(12.dp))

                // Use cases
                Text("Use cases", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    template.useCases.forEach { useCase ->
                        AssistChip(
                            onClick = {},
                            label = { Text(useCase, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Action summary
                val actionCount = template.blueprint.actions.size
                val actionTypes = template.blueprint.actions.map { it.type.displayName }.distinct().joinToString(", ")
                Text(
                    "$actionCount action(s): $actionTypes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Setup notes
                if (template.setupNotes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("To get started", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    template.setupNotes.forEachIndexed { i, note ->
                        Text(
                            "${i + 1}. $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // CTA button — show quick-setup dialog if not ready
            val needsSetup = readiness != null && !readiness.isFullyReady
            Button(
                onClick = {
                    if (needsSetup) showSetupDialog = true
                    else onUse()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use Template")
            }
        }
    }

    // Quick-setup dialog
    if (showSetupDialog && readiness != null) {
        val issues = readiness.needsSetupActions.map { ar ->
            com.automatist.app.domain.actions.WorkflowActionRegistry.getInfo(ar.type).displayName +
                ": " + ar.requirements.filter { it.status == com.automatist.app.domain.readiness.ReadinessStatus.NEEDS_SETUP }
                    .joinToString(", ") { it.requirement.label }
        }
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = { Text("Setup Needed") },
            text = {
                Column {
                    Text("This template needs some configuration before it can run:")
                    Spacer(Modifier.height(8.dp))
                    issues.forEach { issue ->
                        Text("- $issue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("You can set this up now or continue and configure later.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { showSetupDialog = false; onSetup() }) {
                    Text("Set Up Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupDialog = false; onUse() }) {
                    Text("Continue Anyway")
                }
            }
        )
    }
}

private fun templateIcon(templateId: String) = when (templateId) {
    "morning_commute" -> Icons.Default.WbSunny
    "article_summarizer" -> Icons.AutoMirrored.Filled.Article
    "stock_tracker" -> Icons.AutoMirrored.Filled.TrendingUp
    "flight_tracker" -> Icons.Default.FlightTakeoff
    else -> Icons.Default.AutoAwesome
}

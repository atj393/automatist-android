package com.synapse.app.feature.workflow.templates

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
import com.synapse.app.domain.templates.BuiltInTemplates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowTemplatesScreen(
    onBack: () -> Unit,
    onUseTemplate: (String) -> Unit, // passes built-in template ID
    onNavigateToMyWorkflows: () -> Unit,
    onCreateBlank: () -> Unit = {}
) {
    val templates = remember { BuiltInTemplates.ALL }
    val categories = remember { BuiltInTemplates.CATEGORIES }

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
                            onUse = { onUseTemplate(template.id) }
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
    onUse: () -> Unit
) {
    var showDetail by remember { mutableStateOf(false) }

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

                Spacer(Modifier.height(12.dp))
            }

            // CTA button
            Button(
                onClick = onUse,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use Template")
            }
        }
    }
}

private fun templateIcon(templateId: String) = when (templateId) {
    "morning_brief" -> Icons.Default.WbSunny
    "article_summarizer" -> Icons.AutoMirrored.Filled.Article
    "meeting_prep" -> Icons.AutoMirrored.Filled.EventNote
    "content_repurposer" -> Icons.Default.Share
    "competitor_monitor" -> Icons.AutoMirrored.Filled.TrendingUp
    "research_digest" -> Icons.Default.Science
    else -> Icons.Default.AutoAwesome
}

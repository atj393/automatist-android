package com.synapse.app.feature.workflow.run

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synapse.app.domain.models.PersistedStage
import com.synapse.app.domain.models.WorkflowRunStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunDetailScreen(
    runId: Long,
    onBack: () -> Unit,
    viewModel: WorkflowRunDetailViewModel = hiltViewModel()
) {
    val run by viewModel.run.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val r = run
        if (r == null) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(r.templateName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (r.status) {
                                WorkflowRunStatus.COMPLETED -> "Completed"
                                WorkflowRunStatus.FAILED -> "Failed"
                                WorkflowRunStatus.RUNNING -> "Running"
                            }
                        )
                    },
                    leadingIcon = {
                        when (r.status) {
                            WorkflowRunStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                            WorkflowRunStatus.FAILED -> Icon(Icons.Default.Error, null, modifier = Modifier.size(16.dp))
                            WorkflowRunStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                )
                AssistChip(onClick = {}, label = { Text(r.triggerType.replaceFirstChar { it.uppercase() }) })
            }

            // Metadata
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                    MetadataRow("Started", formatTimestamp(r.startedAtMillis))
                    if (r.completedAtMillis != null) {
                        MetadataRow("Completed", formatTimestamp(r.completedAtMillis!!))
                    }
                    if (r.durationMs != null) {
                        MetadataRow("Duration", formatDuration(r.durationMs!!))
                    }
                    if (r.providerType != null) {
                        MetadataRow("Provider", r.providerType!!.displayName)
                    }
                    if (r.profileName.isNotBlank()) {
                        MetadataRow("Profile", r.profileName)
                    }
                    if (r.modelId.isNotBlank()) {
                        MetadataRow("Model", r.modelId)
                    }
                }
            }

            // Token usage
            if (r.promptTokens != null || r.completionTokens != null || r.totalTokens != null) {
                Text("Token Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        MetadataRow("Prompt", r.promptTokens?.toString() ?: "—")
                        MetadataRow("Completion", r.completionTokens?.toString() ?: "—")
                        MetadataRow("Total", r.totalTokens?.toString() ?: "—")
                    }
                }
            }

            // Execution log — persisted step-by-step history
            val stages = r.persistedStages
            if (stages.isNotEmpty()) {
                Text("Execution Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        stages.forEach { stage ->
                            PersistedStageRow(stage)
                        }
                    }
                }
            }

            // Output — format-aware rendering (social outputs render as per-platform cards)
            if (r.outputText.isNotBlank()) {
                OutputDisplay(
                    outputText = r.outputText,
                    outputFormat = r.outputFormat,
                    isSocialOutput = r.isSocialOutput
                )
            }

            // Error — dual layer: readable + raw technical detail
            if (r.errorMessage != null) {
                var showRawDetail by remember { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Error, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Error",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            r.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        if (!r.errorDetail.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
                            )
                            TextButton(
                                onClick = { showRawDetail = !showRawDetail },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    if (showRawDetail) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (showRawDetail) "Hide technical details" else "Show technical details",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }

                            if (showRawDetail) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Text(
                                        r.errorDetail!!,
                                        modifier = Modifier.padding(10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Copy full error
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val fullText = buildString {
                                    appendLine("=== Synapse Workflow Error ===")
                                    appendLine()
                                    appendLine("Workflow: ${r.templateName}")
                                    appendLine("Error: ${r.errorMessage}")
                                    appendLine()
                                    appendLine("Run ID: ${r.id}")
                                    appendLine("Trigger: ${r.triggerType}")
                                    if (r.providerType != null) appendLine("Provider: ${r.providerType!!.displayName}")
                                    if (r.profileName.isNotBlank()) appendLine("Profile: ${r.profileName}")
                                    if (r.modelId.isNotBlank()) appendLine("Model: ${r.modelId}")
                                    appendLine("Stage: ${r.currentStage}")
                                    appendLine("Started: ${formatTimestamp(r.startedAtMillis)}")
                                    if (r.completedAtMillis != null) appendLine("Failed at: ${formatTimestamp(r.completedAtMillis!!)}")
                                    if (!r.errorDetail.isNullOrBlank()) {
                                        appendLine()
                                        appendLine("--- Technical Details ---")
                                        appendLine(r.errorDetail)
                                    }
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Error Details", fullText))
                                scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Error Details", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Full error report copy — all metadata + technical detail
            if (r.status == WorkflowRunStatus.FAILED) {
                OutlinedButton(
                    onClick = {
                        val report = FullErrorReportBuilder.fromWorkflowRun(r)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Full Error Report", report))
                        scope.launch { snackbarHostState.showSnackbar("Full error report copied") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Full Error Report")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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

@Composable
private fun PersistedStageRow(stage: PersistedStage) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (stage.status) {
                "COMPLETED" -> Icon(
                    Icons.Default.CheckCircle, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                "FAILED" -> Icon(
                    Icons.Default.Cancel, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                else -> Icon(
                    Icons.Default.RadioButtonUnchecked, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(stage.label, style = MaterialTheme.typography.bodyMedium)
            if (stage.detail.isNotBlank()) {
                val parts = stage.detail.split("\n---\n", limit = 2)
                Text(
                    parts[0],
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stage.status == "FAILED") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

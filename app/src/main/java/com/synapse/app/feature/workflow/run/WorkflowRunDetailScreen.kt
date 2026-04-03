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
import com.synapse.app.domain.models.WorkflowRunStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunDetailScreen(
    runId: Long,
    onBack: () -> Unit,
    viewModel: WorkflowRunDetailViewModel = hiltViewModel()
) {
    val run by viewModel.run.collectAsState()
    val context = LocalContext.current

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
        }
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

            // Output
            if (r.outputText.isNotBlank()) {
                Text("Output", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card {
                    Text(
                        r.outputText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Workflow Output", r.outputText))
                    }) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, r.outputText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Output"))
                    }) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }

            // Error
            if (r.errorMessage != null) {
                Text("Error", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        r.errorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
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

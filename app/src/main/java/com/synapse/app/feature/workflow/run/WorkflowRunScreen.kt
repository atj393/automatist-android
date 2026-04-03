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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunScreen(
    onBack: () -> Unit,
    viewModel: WorkflowRunViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run: ${state.templateName}") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status header
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        state.isCompleted -> MaterialTheme.colorScheme.primaryContainer
                        state.isFailed -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (state.isCompleted) {
                        Icon(Icons.Default.CheckCircle, "Done", tint = MaterialTheme.colorScheme.primary)
                    } else if (state.isFailed) {
                        Icon(Icons.Default.Error, "Failed", tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            state.currentStageLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (state.durationMs != null) {
                            Text(
                                "Duration: ${formatDuration(state.durationMs!!)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Progress stages
            Text("Execution Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            state.stages.forEach { stage ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        when (stage.status) {
                            StageStatus.PENDING -> Icon(
                                Icons.Default.RadioButtonUnchecked, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            StageStatus.RUNNING -> CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                            )
                            StageStatus.COMPLETED -> Icon(
                                Icons.Default.CheckCircle, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            StageStatus.FAILED -> Icon(
                                Icons.Default.Cancel, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stage.label, style = MaterialTheme.typography.bodyMedium)
                        if (stage.detail.isNotBlank()) {
                            Text(
                                stage.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Token usage
            if (state.tokenUsage != null) {
                Spacer(Modifier.height(4.dp))
                Text("Token Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        val usage = state.tokenUsage!!
                        val estimatedLabel = if (usage.isEstimated) " (estimated)" else ""
                        TokenRow("Prompt tokens$estimatedLabel", usage.promptTokens)
                        TokenRow("Completion tokens$estimatedLabel", usage.completionTokens)
                        TokenRow("Total tokens$estimatedLabel", usage.totalTokens)
                        if (state.providerType != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Provider: ${state.providerType!!.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Output
            if (state.outputText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Output", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card {
                    Text(
                        state.outputText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Workflow Output", state.outputText))
                    }) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.outputText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Output"))
                    }) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }

            // Error message
            if (state.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        state.errorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TokenRow(label: String, value: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            value?.toString() ?: "—",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s"
    else "${seconds / 60}m ${seconds % 60}s"
}

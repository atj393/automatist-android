package com.automatist.app.feature.workflow.run

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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: WorkflowRunViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Blocked by missing setup
        if (state.isBlockedBySetup) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Setup Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Some actions need to be set up before this workflow can run:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        state.setupIssues.forEach { issue ->
                            Text(
                                "- $issue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Button(onClick = onNavigateToSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Settings")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Go Back")
                }
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
                        // Surface the auto-retry badge inline with the header so
                        // users aren't surprised by new stage rows appearing on
                        // the same screen after a failed attempt.
                        if (state.isAutoRetrying && state.autoRetryAttempt > 0) {
                            Text(
                                "Auto-retry ${state.autoRetryAttempt} of ${com.automatist.app.domain.models.WorkflowRun.MAX_AUTO_RETRIES}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
            Text("Run Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            state.stages.forEach { stage ->
                LiveStageRow(stage = stage, context = context, snackbarHostState = snackbarHostState)
            }

            // Provider / profile info
            if (state.providerType != null || state.profileName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("AI Provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        if (state.profileName.isNotBlank()) {
                            TokenRow("Profile", state.profileName)
                        }
                        if (state.providerType != null) {
                            TokenRow("Provider", state.providerType!!.displayName)
                        }
                        if (state.modelId.isNotBlank()) {
                            TokenRow("Model", state.modelId)
                        }

                        if (state.tokenUsage != null) {
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(6.dp))
                            UsageSection(state.tokenUsage!!)
                        }
                    }
                }
            } else if (state.tokenUsage != null) {
                // Fallback: show just token usage if no profile info (shouldn't happen normally)
                Spacer(Modifier.height(4.dp))
                Text("AI Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        UsageSection(state.tokenUsage!!)
                    }
                }
            }

            // Output — versioned or single output display
            if (state.outputText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                if (state.versions.size > 1 || state.synthesisInput.isNotBlank()) {
                    // Multi-version display with regeneration support
                    VersionedOutputDisplay(
                        versions = state.versions.ifEmpty {
                            // Backward compat: wrap single output as version 1
                            listOf(com.automatist.app.domain.models.OutputVersion(
                                version = 1,
                                outputText = state.outputText,
                                isSocialOutput = state.isSocialOutput
                            ))
                        },
                        outputFormat = state.outputFormat,
                        onRegenerate = if (state.synthesisInput.isNotBlank() && state.isCompleted)
                            { { viewModel.regenerate() } } else null,
                        isRegenerating = state.isRegenerating
                    )
                } else {
                    OutputDisplay(
                        outputText = state.outputText,
                        outputFormat = state.outputFormat,
                        isSocialOutput = state.isSocialOutput
                    )
                }
            }

            // Error section — dual layer: readable summary + expandable raw detail
            if (state.errorMessage != null) {
                ErrorDetailCard(
                    errorMessage = state.errorMessage!!,
                    errorDetail = state.errorDetail,
                    runId = state.runId,
                    profileName = state.profileName,
                    modelId = state.modelId,
                    context = context,
                    onCopied = { scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") } }
                )
            }

            // Full error report copy — includes execution log + all metadata
            if (state.isFailed) {
                // Retry button — prominent, above the copy report button
                Button(
                    onClick = { viewModel.retryRun() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }

                OutlinedButton(
                    onClick = {
                        val report = FullErrorReportBuilder.fromRunUiState(state)
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

@Composable
private fun TokenRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/**
 * Renders token/usage metadata honestly:
 *  - Cloud providers: exact counts as before (no "(estimated)" suffix, no extras).
 *  - Local/Fake providers: counts marked estimated; extra rows show char-level
 *    metrics the provider actually knows; an honesty note explains the caveats;
 *    an explicit truncation warning appears when input was shortened to fit the
 *    on-device model's context window.
 */
@Composable
private fun UsageSection(usage: com.automatist.app.domain.models.TokenUsage) {
    val estimatedLabel = if (usage.isEstimated) " (estimated)" else ""
    TokenRow("Prompt tokens$estimatedLabel", usage.promptTokens)
    TokenRow("Completion tokens$estimatedLabel", usage.completionTokens)
    TokenRow("Total tokens$estimatedLabel", usage.totalTokens)

    // Char-level metrics — only rendered when the provider reported them
    // (local/fake). Cloud providers leave these null and we skip the rows.
    val hasChars = usage.inputChars != null || usage.outputChars != null
    if (hasChars || usage.contextCeilingTokens != null) {
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(6.dp))
        usage.inputChars?.let { TokenRow("Prompt chars", it) }
        usage.outputChars?.let { TokenRow("Output chars", it) }
        usage.contextCeilingTokens?.let { TokenRow("Context ceiling (tokens)", it) }
    }

    // Truncation warning — stronger copy when we know input was shortened.
    if (usage.wasTruncated == true) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Input was shortened to fit the on-device model's context window.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Medium
        )
    }

    // Estimation note — softer copy when usage is estimated but we don't know
    // (or have ruled out) truncation. Helps users understand why counts are
    // approximate without implying something went wrong.
    if (usage.isEstimated && usage.wasTruncated != true) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Usage is estimated for on-device models. Long inputs may be shortened " +
                "before generation to fit the model's context window.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Error Detail Card ──

@Composable
private fun ErrorDetailCard(
    errorMessage: String,
    errorDetail: String?,
    runId: Long?,
    profileName: String,
    modelId: String,
    context: Context,
    onCopied: () -> Unit = {}
) {
    var showRawDetail by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            // Readable error summary
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
                errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            // Toggle for raw technical detail
            if (!errorDetail.isNullOrBlank()) {
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
                            errorDetail,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }

            // Copy full error button
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    val fullText = buildCopyableErrorText(
                        errorMessage, errorDetail, runId, profileName, modelId
                    )
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Error Details", fullText))
                    onCopied()
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

private fun buildCopyableErrorText(
    errorMessage: String,
    errorDetail: String?,
    runId: Long?,
    profileName: String,
    modelId: String
): String = buildString {
    appendLine("=== Automatist Workflow Error ===")
    appendLine()
    appendLine("Error: $errorMessage")
    appendLine()
    if (runId != null) appendLine("Run ID: $runId")
    if (profileName.isNotBlank()) appendLine("Profile: $profileName")
    if (modelId.isNotBlank()) appendLine("Model: $modelId")
    val timestamp = java.text.SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
    ).format(java.util.Date())
    appendLine("Timestamp: $timestamp")
    if (!errorDetail.isNullOrBlank()) {
        appendLine()
        appendLine("--- Technical Details ---")
        appendLine(errorDetail)
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s"
    else "${seconds / 60}m ${seconds % 60}s"
}

// ── Expandable Stage Row (live run) ──

@Composable
private fun LiveStageRow(
    stage: StageInfo,
    context: Context,
    snackbarHostState: SnackbarHostState
) {
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
        Column(modifier = Modifier.weight(1f)) {
            Text(stage.label, style = MaterialTheme.typography.bodyMedium)

            // Live "Ns elapsed" for the currently running stage. Ticking is
            // isolated to this row via rememberRunningElapsedLabel, so the
            // 1 Hz update doesn't recompose the whole run screen. Rendered
            // as the first line of the detail area so it never displaces the
            // existing stage detail or action-data expander.
            if (stage.status == StageStatus.RUNNING && stage.startedAtMillis > 0L) {
                val elapsed = rememberRunningElapsedLabel(stage.startedAtMillis)
                if (elapsed.isNotBlank()) {
                    Text(
                        elapsed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            if (stage.detail.isNotBlank()) {
                val parts = stage.detail.split("\n---\n", limit = 2)
                Text(
                    parts[0],
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stage.status == StageStatus.FAILED)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (parts.size > 1 && parts[1].isNotBlank()) {
                    Text(
                        parts[1],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            // Expandable detail sections — each stage can have up to three
            // independent expanders. Collapsed by default; heavy text is only
            // composed when the user expands a given section.
            if (stage.sourceText.isNotBlank()) {
                ExpandableDetail(
                    showLabel = "Show source content",
                    hideLabel = "Hide source content",
                    text = stage.sourceText,
                    clipboardLabel = "Source content",
                    snackbarText = "Source content copied",
                    context = context,
                    snackbarHostState = snackbarHostState
                )
            }
            if (stage.promptText.isNotBlank()) {
                val isFinalPrompt = stage.label.startsWith("Generating final output")
                ExpandableDetail(
                    showLabel = if (isFinalPrompt) "Show final prompt" else "Show prompt",
                    hideLabel = if (isFinalPrompt) "Hide final prompt" else "Hide prompt",
                    text = stage.promptText,
                    clipboardLabel = if (isFinalPrompt) "Final prompt" else "Prompt",
                    snackbarText = if (isFinalPrompt) "Final prompt copied" else "Prompt copied",
                    context = context,
                    snackbarHostState = snackbarHostState
                )
            }
            if (stage.actionData.isNotBlank()) {
                ExpandableDetail(
                    showLabel = "Show result",
                    hideLabel = "Hide result",
                    text = stage.actionData,
                    clipboardLabel = "Action result",
                    snackbarText = "Result copied",
                    context = context,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}

/**
 * Reusable collapsed-by-default detail card for a single piece of stage text
 * (source content, prompt, result, final prompt). Heavy text composition only
 * happens when [expanded] is true, so adding three of these to a stage row
 * does not render multi-KB blobs unless the user asks for them.
 *
 * The character count in the collapsed label is cheap (`text.length` is O(1)
 * on a String) and helps the user gauge whether the content is worth
 * expanding.
 */
@Composable
private fun ExpandableDetail(
    showLabel: String,
    hideLabel: String,
    text: String,
    clipboardLabel: String,
    snackbarText: String,
    context: Context,
    snackbarHostState: SnackbarHostState
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TextButton(
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            null, modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (expanded) hideLabel else "$showLabel (${text.length} chars)",
            style = MaterialTheme.typography.labelSmall
        )
    }
    if (expanded) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
                        scope.launch { snackbarHostState.showSnackbar(snackbarText) }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

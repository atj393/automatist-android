package com.automatist.app.feature.workflow.run

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatist.app.domain.models.OutputFormat
import com.automatist.app.domain.models.OutputVersion
import com.automatist.app.domain.models.SocialOutput
import com.automatist.app.domain.models.SocialOutputParser
import kotlinx.coroutines.launch

/**
 * Format-aware output display with rendered/raw toggle and copy/share actions.
 * Used by both WorkflowRunScreen and WorkflowRunDetailScreen.
 * When isSocialOutput is true, renders per-platform cards with individual copy/share.
 */
@Composable
fun OutputDisplay(
    outputText: String,
    outputFormat: OutputFormat,
    modifier: Modifier = Modifier,
    isSocialOutput: Boolean = false
) {
    // Social output mode → render platform cards
    if (isSocialOutput) {
        val socialOutputs = remember(outputText) { SocialOutputParser.parse(outputText) }
        if (socialOutputs.isNotEmpty()) {
            SocialOutputDisplay(
                socialOutputs = socialOutputs,
                rawJson = outputText,
                modifier = modifier
            )
            return
        }
        // Fallback: if parsing fails, render as standard output
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRaw by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header row with title + rendered/raw toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Output",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            // Format badge
            AssistChip(
                onClick = {},
                label = { Text(outputFormat.displayName, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            // Rendered/Raw toggle
            if (outputFormat != OutputFormat.PLAIN_TEXT) {
                FilterChip(
                    selected = showRaw,
                    onClick = { showRaw = !showRaw },
                    label = { Text(if (showRaw) "Raw" else "Rendered", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            if (showRaw) Icons.Default.Code else Icons.Default.Visibility,
                            null, modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier.height(26.dp)
                )
            }
        }

        // Output card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            if (showRaw || outputFormat == OutputFormat.PLAIN_TEXT) {
                // Raw / Plain text display
                Text(
                    outputText,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (showRaw) FontFamily.Monospace else FontFamily.Default
                )
            } else {
                when (outputFormat) {
                    OutputFormat.MARKDOWN, OutputFormat.AUTO -> {
                        MarkdownRendered(outputText)
                    }
                    OutputFormat.JSON -> {
                        JsonRendered(outputText)
                    }
                    else -> {
                        Text(
                            outputText,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Copy / Share row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Workflow Output", outputText))
                scope.launch { snackbarHostState.showSnackbar("Output copied") }
            }) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy")
            }
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, outputText)
                }
                context.startActivity(Intent.createChooser(intent, "Share Output"))
            }) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }
        }

        // Inline snackbar host for copy feedback
        SnackbarHost(snackbarHostState)
    }
}

// ── Social Output Display ──

/**
 * Renders per-platform social output cards with individual copy/share actions.
 */
@Composable
fun SocialOutputDisplay(
    socialOutputs: List<SocialOutput>,
    rawJson: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRaw by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Social Outputs",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            AssistChip(
                onClick = {},
                label = { Text("${socialOutputs.size} platforms", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = showRaw,
                onClick = { showRaw = !showRaw },
                label = { Text(if (showRaw) "Raw JSON" else "Cards", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        if (showRaw) Icons.Default.Code else Icons.Default.Visibility,
                        null, modifier = Modifier.size(14.dp)
                    )
                },
                modifier = Modifier.height(26.dp)
            )
        }

        if (showRaw) {
            // Raw JSON view
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                JsonRendered(rawJson)
            }
        } else {
            // Per-platform cards
            socialOutputs.forEach { output ->
                SocialOutputCard(
                    output = output,
                    onCopied = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )
            }
        }

        // Copy All button
        OutlinedButton(
            onClick = {
                val allText = socialOutputs.joinToString("\n\n---\n\n") { output ->
                    buildString {
                        appendLine("[${output.platform}]")
                        if (output.title.isNotBlank()) appendLine(output.title)
                        append(output.content)
                        if (output.notes.isNotBlank()) {
                            appendLine()
                            append("Notes: ${output.notes}")
                        }
                    }
                }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("All Social Outputs", allText))
                scope.launch { snackbarHostState.showSnackbar("All outputs copied") }
            }
        ) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Copy All Outputs")
        }

        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun SocialOutputCard(
    output: SocialOutput,
    onCopied: (String) -> Unit
) {
    val context = LocalContext.current
    val platformIcon = when (output.platform.lowercase()) {
        "x" -> Icons.Default.Tag
        "linkedin" -> Icons.Default.Work
        "facebook" -> Icons.Default.People
        "medium" -> Icons.Default.Article
        "instagram" -> Icons.Default.CameraAlt
        "threads" -> Icons.Default.Forum
        "briefing" -> Icons.Default.Summarize
        else -> Icons.Default.Share
    }

    // Character limit warning for X
    val charWarning = if (output.platform.equals("X", ignoreCase = true) && output.charCount > 280) {
        "${output.charCount}/280 — over limit"
    } else if (output.platform.equals("X", ignoreCase = true)) {
        "${output.charCount}/280"
    } else null

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            // Platform header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    platformIcon, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    output.platform,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Character count badge
                if (charWarning != null) {
                    val isOverLimit = output.platform.equals("X", ignoreCase = true) && output.charCount > 280
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                charWarning,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverLimit) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            // Title
            if (output.title.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    output.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Content
            Spacer(Modifier.height(8.dp))
            Text(
                output.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Notes
            if (output.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    output.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }

            // Action buttons
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("${output.platform} Output", output.content))
                        onCopied("${output.platform} output copied")
                    },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        val shareText = output.content
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            if (output.title.isNotBlank()) {
                                putExtra(Intent.EXTRA_SUBJECT, output.title)
                            }
                        }
                        context.startActivity(
                            Intent.createChooser(intent, "Share to ${output.platform}")
                        )
                    },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Versioned Output Display ──

/**
 * Renders multiple output versions with tab-style navigation.
 * Each version has its own copy/share. Works for both normal and social outputs.
 */
@Composable
fun VersionedOutputDisplay(
    versions: List<OutputVersion>,
    outputFormat: OutputFormat,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
    isRegenerating: Boolean = false
) {
    if (versions.isEmpty()) return

    var selectedVersion by remember { mutableStateOf(0) }
    // Clamp selected to valid range when versions grow (e.g. after regeneration)
    val clampedSelected = selectedVersion.coerceIn(0, versions.lastIndex)
    if (clampedSelected != selectedVersion) selectedVersion = clampedSelected

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Version tabs
        if (versions.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Versions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                versions.forEachIndexed { index, v ->
                    FilterChip(
                        selected = selectedVersion == index,
                        onClick = { selectedVersion = index },
                        label = { Text("V${v.version}", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        // Current version content
        val current = versions[selectedVersion]
        OutputDisplay(
            outputText = current.outputText,
            outputFormat = outputFormat,
            isSocialOutput = current.isSocialOutput
        )

        // Regenerate button
        if (onRegenerate != null && versions.size < 10) {
            OutlinedButton(
                onClick = onRegenerate,
                enabled = !isRegenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRegenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Generating...")
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate Another Version")
                }
            }
        }
    }
}

// ── Markdown Renderer ──

@Composable
private fun MarkdownRendered(text: String) {
    val colorScheme = MaterialTheme.colorScheme
    val annotated = remember(text) { parseMarkdown(text, colorScheme) }

    Text(
        annotated,
        modifier = Modifier.padding(14.dp),
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = 22.sp
    )
}

/**
 * Lightweight Compose-native markdown parser.
 * Handles: headings, bold, italic, bullet lists, horizontal rules, inline code.
 */
private fun parseMarkdown(
    text: String,
    colorScheme: androidx.compose.material3.ColorScheme
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()

            when {
                // Horizontal rule
                trimmed.matches(Regex("""^-{3,}$""")) || trimmed.matches(Regex("""^\*{3,}$""")) -> {
                    appendLine("————————————————")
                }
                // H1
                trimmed.startsWith("# ") -> {
                    appendInlineFormatted(
                        trimmed.removePrefix("# "),
                        SpanStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    )
                    appendLine()
                }
                // H2
                trimmed.startsWith("## ") -> {
                    appendInlineFormatted(
                        trimmed.removePrefix("## "),
                        SpanStyle(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    )
                    appendLine()
                }
                // H3
                trimmed.startsWith("### ") -> {
                    appendInlineFormatted(
                        trimmed.removePrefix("### "),
                        SpanStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    )
                    appendLine()
                }
                // Bullet list
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append("  \u2022 ")
                    appendInlineFormatted(trimmed.drop(2), null)
                    appendLine()
                }
                // Numbered list (e.g. "1. ")
                trimmed.matches(Regex("""^\d+\.\s.*""")) -> {
                    val num = trimmed.substringBefore(".")
                    val content = trimmed.substringAfter(". ")
                    append("  $num. ")
                    appendInlineFormatted(content, null)
                    appendLine()
                }
                // Empty line
                trimmed.isBlank() -> {
                    appendLine()
                }
                // Normal paragraph
                else -> {
                    appendInlineFormatted(line, null)
                    appendLine()
                }
            }
            i++
        }
    }
}

/**
 * Appends a line with inline formatting: **bold**, *italic*, `code`.
 */
private fun AnnotatedString.Builder.appendInlineFormatted(text: String, baseStyle: SpanStyle?) {
    if (baseStyle != null) pushStyle(baseStyle)

    var pos = 0
    val pattern = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")
    for (match in pattern.findAll(text)) {
        // Append text before the match
        if (match.range.first > pos) {
            append(text.substring(pos, match.range.first))
        }
        val bold = match.groupValues[1]
        val italic = match.groupValues[2]
        val code = match.groupValues[3]
        when {
            bold.isNotEmpty() -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            }
            italic.isNotEmpty() -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            }
            code.isNotEmpty() -> {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = androidx.compose.ui.graphics.Color(0x1A000000))) {
                    append(code)
                }
            }
        }
        pos = match.range.last + 1
    }
    // Remainder
    if (pos < text.length) {
        append(text.substring(pos))
    }

    if (baseStyle != null) pop()
}

// ── JSON Renderer ──

@Composable
private fun JsonRendered(text: String) {
    val isValid = remember(text) {
        try {
            kotlinx.serialization.json.Json.parseToJsonElement(text)
            true
        } catch (_: Exception) {
            false
        }
    }
    val prettyJson = remember(text) {
        try {
            val json = kotlinx.serialization.json.Json { prettyPrint = true }
            val element = kotlinx.serialization.json.Json.parseToJsonElement(text)
            json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
        } catch (_: Exception) {
            text // fallback to raw if not valid JSON
        }
    }

    Column(modifier = Modifier.padding(14.dp)) {
        // Validity badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isValid) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Valid JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Warning, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Output is not valid JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // JSON content in monospace with horizontal scroll
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                prettyJson,
                modifier = Modifier
                    .padding(10.dp)
                    .horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}

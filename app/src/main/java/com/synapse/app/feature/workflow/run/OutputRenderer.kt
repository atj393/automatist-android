package com.synapse.app.feature.workflow.run

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
import com.synapse.app.domain.models.OutputFormat
import kotlinx.coroutines.launch

/**
 * Format-aware output display with rendered/raw toggle and copy/share actions.
 * Used by both WorkflowRunScreen and WorkflowRunDetailScreen.
 */
@Composable
fun OutputDisplay(
    outputText: String,
    outputFormat: OutputFormat,
    modifier: Modifier = Modifier
) {
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

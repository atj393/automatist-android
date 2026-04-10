package com.synapse.app.domain.engine

import com.synapse.app.domain.models.InputCompactionMode

/**
 * Deterministic text compaction for auto-collected workflow content.
 * Reduces token usage before final AI synthesis while preserving meaning.
 *
 * This must NEVER be applied to user-authored text (instructions, prompts, config).
 * Only auto-collected action outputs (RSS, URL, API, weather, route, etc.).
 */
object TextCompactor {

    data class CompactionResult(
        val text: String,
        val originalLength: Int,
        val compactedLength: Int
    ) {
        val reductionPercent: Int
            get() = if (originalLength == 0) 0
            else ((originalLength - compactedLength) * 100) / originalLength
    }

    fun compact(text: String, mode: InputCompactionMode): CompactionResult {
        val originalLength = text.length
        val result = when (mode) {
            InputCompactionMode.NONE -> text
            InputCompactionMode.LIGHT -> lightCompact(text)
            InputCompactionMode.AGGRESSIVE -> aggressiveCompact(text)
        }
        return CompactionResult(
            text = result,
            originalLength = originalLength,
            compactedLength = result.length
        )
    }

    // ── Light Compaction ──

    private fun lightCompact(text: String): String {
        var result = text

        // Strip residual HTML tags
        result = result.replace(Regex("<[^>]{1,200}>"), " ")

        // Remove HTML entities
        result = result
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#\\d{1,5};"), " ")
            .replace(Regex("&[a-zA-Z]{2,8};"), " ")

        // Remove tracking/UTM query parameters from URLs inline
        result = result.replace(Regex("""(https?://\S+?)\?(?:utm_\S+|ref=\S+|source=\S+|fbclid=\S+|gclid=\S+)"""), "$1")

        // Remove decorative symbol runs (3+ consecutive decorative chars)
        result = result.replace(Regex("""[★☆●○◆◇▪▫►◄↑↓←→✦✧✩✪✫✬✭✮✯✰⭐🌟💫⚡🔥]{3,}"""), "")

        // Collapse runs of emoji that add no semantic value (3+ in a row)
        result = result.replace(Regex("""(\p{So})\1{2,}"""), "$1")

        // Normalize whitespace: collapse multiple spaces to one
        result = result.replace(Regex("""[ \t]+"""), " ")

        // Collapse 3+ consecutive blank lines to 2
        result = result.replace(Regex("""\n\s*\n(\s*\n)+"""), "\n\n")

        // Trim leading/trailing whitespace per line
        result = result.lines().joinToString("\n") { it.trim() }

        // Remove common boilerplate noise lines
        result = removeBoilerplateLines(result)

        return result.trim()
    }

    // ── Aggressive Compaction ──

    private fun aggressiveCompact(text: String): String {
        // Start with light cleanup
        var result = lightCompact(text)

        // Split into lines for deduplication and filtering
        val lines = result.lines().toMutableList()

        // Deduplicate identical or near-identical lines
        val deduplicated = deduplicateLines(lines)

        // Remove short low-information lines (under 15 chars, non-headline, non-numeric)
        val filtered = deduplicated.filter { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@filter true // keep blank lines as separators
            if (trimmed.length < 15) {
                // Keep lines that start with numbers, dates, or look like titles/headers
                containsInfoSignal(trimmed)
            } else true
        }

        // Collapse repeated metadata patterns (e.g., "Published: ...", "Author: ..." appearing many times)
        val collapsed = collapseRepeatedMetadata(filtered)

        // Trim long paragraphs: keep first and last sentence if paragraph > 500 chars
        val trimmed = collapsed.map { line ->
            if (line.length > 500) {
                trimParagraph(line)
            } else line
        }

        result = trimmed.joinToString("\n")

        // Final whitespace normalization
        result = result.replace(Regex("""\n\s*\n(\s*\n)+"""), "\n\n")

        return result.trim()
    }

    // ── Helpers ──

    private val BOILERPLATE_PATTERNS = listOf(
        Regex("""^(cookie|privacy|terms|copyright|all rights reserved|subscribe|sign up|log ?in|advertisement|sponsored|skip to|back to top|read more|continue reading|share this|follow us).*""", RegexOption.IGNORE_CASE),
        Regex("""^(related articles?|you may also like|recommended|trending|popular|most read).*""", RegexOption.IGNORE_CASE),
        Regex("""^\s*\|.*\|.*\|\s*$"""), // table divider lines
        Regex("""^[-=]{10,}$"""), // long divider lines
        Regex("""^[\s•·\-*]{1,3}$""") // lines that are just a bullet or dash
    )

    private fun removeBoilerplateLines(text: String): String {
        return text.lines().filter { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@filter true
            BOILERPLATE_PATTERNS.none { it.matches(trimmed) }
        }.joinToString("\n")
    }

    /** Remove lines that are identical or differ only in whitespace/punctuation. */
    private fun deduplicateLines(lines: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return lines.filter { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@filter true // keep blank separators
            val normalized = trimmed.lowercase()
                .replace(Regex("""[^\w\s]"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (normalized.length < 5) return@filter true // keep very short lines as-is
            seen.add(normalized) // returns false if already present
        }
    }

    /** Check if a short line contains meaningful info signals (numbers, dates, titles). */
    private fun containsInfoSignal(text: String): Boolean {
        // Contains a digit — likely a date, metric, count
        if (text.any { it.isDigit() }) return true
        // Starts with uppercase — likely a title or heading
        if (text.firstOrNull()?.isUpperCase() == true && text.length > 3) return true
        // Contains a colon — likely a label: value pair
        if (':' in text) return true
        // Looks like a section header (e.g., "Title:", "Summary:")
        if (text.endsWith(":")) return true
        return false
    }

    /**
     * If a metadata prefix (like "Published:", "Author:", "Source:") appears
     * more than twice, keep only the first two occurrences.
     */
    private fun collapseRepeatedMetadata(lines: List<String>): List<String> {
        val metadataCounts = mutableMapOf<String, Int>()
        return lines.filter { line ->
            val trimmed = line.trim()
            val prefixMatch = Regex("""^(Title|Author|Published|Date|Source|By|Via|From|Updated|Category|Tags?|Label):\s""", RegexOption.IGNORE_CASE)
                .find(trimmed)
            if (prefixMatch != null) {
                val prefix = prefixMatch.groupValues[1].lowercase()
                val count = metadataCounts.getOrDefault(prefix, 0) + 1
                metadataCounts[prefix] = count
                count <= 2
            } else true
        }
    }

    /** Trim a long paragraph to its first two and last sentence. */
    private fun trimParagraph(text: String): String {
        // Split on sentence boundaries (period + space or newline)
        val sentences = text.split(Regex("""(?<=[.!?])\s+""")).filter { it.isNotBlank() }
        if (sentences.size <= 3) return text

        val kept = mutableListOf<String>()
        kept.add(sentences[0])
        kept.add(sentences[1])
        kept.add("[...]")
        kept.add(sentences.last())
        return kept.joinToString(" ")
    }
}

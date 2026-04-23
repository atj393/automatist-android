package com.automatist.app.data.providers

import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.offline.OfflineModelEntry

/**
 * Stateless prompt builder for the on-device AI paths.
 *
 * Split from [LocalAIArticleTransformProvider] so it can be unit-tested without
 * the provider's Context / MediaPipeInferenceEngine / OfflineModelRepository
 * dependencies. Pure string construction, no side effects.
 *
 * Two entry points:
 *  - [buildForMediaPipe] — downloadable path. Caps the system instruction and
 *    user content independently so the combined prompt fits the local model's
 *    tight input-token budget.
 *  - [buildForAICore]   — Gemini Nano path. Has a larger effective context, so
 *    it only caps the user content at [OfflineModelEntry.contextWindowChars].
 *
 * Both paths wrap user content in `---BEGIN CONTENT---` / `---END CONTENT---`
 * delimiters to reduce prompt-injection risk from untrusted text and request
 * plain-text output only.
 */
internal object LocalPromptBuilder {

    /**
     * Instruction cap for the MediaPipe path. 900 chars ≈ 300 tokens under the
     * engine's conservative 3 chars/token estimator, leaving ~980 tokens for
     * user content within the MAX_INPUT_TOKENS = 1280 ceiling. Long cloud-style
     * system prompts (social mode = 1500-2500 chars) are truncated to this
     * size before being sent to MediaPipe.
     */
    const val INSTRUCTION_CHARS = 900

    /**
     * Tight content cap for the local-only social path. The engine's social
     * system prompt (even the compact variant we emit below) plus a normal
     * content body at 2500 chars brings the combined prompt close to the
     * 1280-token input ceiling on a Gemma 3 1B int4. 1600 chars of content
     * leaves comfortable headroom for prompt + schema + output, so scheduled
     * overnight runs on the default "News to Social Posts" template complete
     * reliably instead of timing out. Cloud paths are unaffected — this only
     * applies when [buildForMediaPipe] detects a social-mode override.
     */
    const val SOCIAL_CONTENT_CHARS = 1600

    /**
     * Fallback instruction cap used on the retry path — if the first build
     * somehow overshoots the token estimate (e.g. dense non-English text that
     * tokenises tighter than 3 chars/token), the provider rebuilds with a much
     * shorter instruction so the retry almost certainly fits.
     */
    const val FALLBACK_INSTRUCTION_CHARS = 400

    /**
     * Fallback content cap for the retry path. Paired with [FALLBACK_INSTRUCTION_CHARS]
     * this guarantees the combined prompt stays under ~2000 chars ≈ 666 tokens —
     * well inside MAX_INPUT_TOKENS.
     */
    const val FALLBACK_CONTENT_CHARS = 1600

    private const val TRUNCATION_MARKER = "\n\n[instructions truncated]"

    /**
     * Budget-aware prompt builder for the MediaPipe downloadable path.
     *
     * Caps [ArticleInput.systemPromptOverride] independently of user content.
     * The workflow engine authors that override for cloud LLMs with ~100k-token
     * contexts — in social or custom-workflow mode it can be 1500–2500 chars,
     * which on top of 2500 chars of content blows past the 1280-token input
     * ceiling of a 1B int4 model. A single content-only truncation cannot fix
     * that: the instruction is often the dominant overhead.
     */
    fun buildForMediaPipe(
        input: ArticleInput,
        type: TransformType,
        entry: OfflineModelEntry,
        instructionCap: Int = INSTRUCTION_CHARS
    ): String {
        val rawOverride = input.systemPromptOverride

        // Local-only substitution: when the engine authored a social-output
        // prompt for a cloud provider (recognisable by its schema signature),
        // swap it for a compact local variant that asks for a bare JSON array
        // of {platform, content} — no title/notes, no per-platform tone prose,
        // no duplicated formatting rules. SocialOutputParser.parseTolerant
        // accepts both the bare-array and outputs-wrapped shapes, so the
        // rendered-output pipeline is unaffected.
        if (rawOverride != null && isSocialOverride(rawOverride)) {
            val compact = compactLocalSocialFor(rawOverride)
            val safeContent = input.text.take(SOCIAL_CONTENT_CHARS)
            return wrapPrompt(compact, safeContent)
        }

        val rawInstruction = rawOverride ?: defaultInstructionFor(type)
        val instruction = if (rawInstruction.length > instructionCap) {
            rawInstruction.take(instructionCap - TRUNCATION_MARKER.length) + TRUNCATION_MARKER
        } else {
            rawInstruction
        }
        val safeContent = input.text.take(entry.contextWindowChars)
        return wrapPrompt(instruction, safeContent)
    }

    /**
     * Signature match for the engine's social-mode prompt. The engine's
     * [WorkflowExecutionEngine.buildSocialSystemPrompt] always emits the
     * literal `{"outputs":[{"platform":` schema fragment — stable across
     * cloud template variants. We use that fragment as a cheap, precise
     * detector so a user-authored custom instruction containing "social"
     * text doesn't accidentally trip this path.
     */
    internal fun isSocialOverride(override: String): Boolean =
        override.contains("\"outputs\":[{\"platform\":")

    /**
     * Build a compact local-only social instruction from the cloud override.
     *
     * Keeps the essentials:
     *  - intent ("one post per platform, distinct")
     *  - platform list (extracted from the cloud override's Platforms block)
     *  - global/custom directives if the template author added any
     *  - strict-JSON + schema — but the **bare array** shape (no `outputs`
     *    wrapper, no title/notes) which is materially shorter and easier for
     *    a 1B int4 model to emit correctly
     *
     * Drops the per-platform tone hints, the nested schema wrapper, and the
     * "double-quoted strings, commas between fields" restatement. Cuts the
     * system-prompt footprint from ~700–900 chars to ~250 chars for the
     * default News-to-Social template, which frees up input-token budget for
     * actual source content and reduces prefill latency substantially.
     */
    private fun compactLocalSocialFor(cloudOverride: String): String {
        val platforms = extractPlatforms(cloudOverride)
        val globalInstruction = extractGlobalInstruction(cloudOverride)

        return buildString {
            append("Write one short social post per platform from the content below. ")
            append("Each post must be distinct and tailored to its platform.")
            if (globalInstruction.isNotBlank()) {
                append("\nExtra: ")
                append(globalInstruction)
            }
            if (platforms.isNotEmpty()) {
                append("\nPlatforms: ")
                append(platforms.joinToString(", "))
            }
            append("\n\nOutput STRICT valid JSON only — no prose, no code fences. Use this exact shape:")
            append("\n[{\"platform\":\"X\",\"content\":\"…\"}]")
            append("\nOne object per platform. Double-quoted strings. Nothing outside the array.")
        }
    }

    /**
     * Pull the platform list out of the cloud override. The engine emits
     * `Platforms:\n- X\n- LinkedIn\n- Facebook` and a trailing
     * `Platforms to generate: X, LinkedIn, Facebook` hint — we read the
     * bulleted block because it's more precise (each bullet is one platform).
     * Falls back to the trailing hint if the bulleted block is missing.
     */
    private fun extractPlatforms(cloudOverride: String): List<String> {
        val bulletRegex = Regex("""(?m)^-\s+([^:\n]+?)(?::|$)""")
        val bulleted = bulletRegex.findAll(cloudOverride)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (bulleted.isNotEmpty()) return bulleted

        val trailing = Regex("""Platforms to generate:\s*(.+)""").find(cloudOverride)
        return trailing?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    /** Extract the `Global: ...` line from the cloud override if present. */
    private fun extractGlobalInstruction(cloudOverride: String): String {
        val match = Regex("""(?m)^Global:\s*(.+)$""").find(cloudOverride)
        return match?.groupValues?.get(1)?.trim().orEmpty()
    }

    /**
     * Prompt builder for the AICore (Gemini Nano) path. System-managed Gemini
     * Nano can handle considerably more context than a 1B int4, so this path
     * keeps the engine's full `systemPromptOverride` and only caps user content
     * at the catalog [OfflineModelEntry.contextWindowChars].
     */
    fun buildForAICore(input: ArticleInput, type: TransformType, entry: OfflineModelEntry?): String {
        val instruction = input.systemPromptOverride ?: defaultInstructionFor(type)
        val maxChars = entry?.contextWindowChars ?: 3_000
        val safeContent = input.text.take(maxChars)
        return wrapPrompt(instruction, safeContent)
    }

    private fun defaultInstructionFor(type: TransformType): String = when (type) {
        TransformType.SUMMARY ->
            "Write a concise summary of the following content."
        TransformType.THREAD ->
            "Convert the following content into 5–7 short, tweet-sized bullet points. " +
                "Each bullet should stand alone and be under 280 characters."
        TransformType.PRO_POST ->
            "Rewrite the following as a polished, professional LinkedIn post. " +
                "Keep it engaging and under 300 words."
        TransformType.MEETING_BRIEF ->
            "Summarize the following meeting notes into a short, structured brief " +
                "with key decisions and action items."
        TransformType.STRATEGIC_QUESTIONS ->
            "Generate exactly 5 strategic questions based on the following meeting notes. " +
                "Each question should provoke deeper thinking about goals or risks."
        TransformType.MORNING_SUMMARY ->
            "Summarize the following news items into a concise morning digest. " +
                "Group related topics and highlight the most important developments."
        TransformType.CUSTOM_WORKFLOW ->
            "Process the following content as a helpful AI assistant."
    }

    private fun wrapPrompt(instruction: String, content: String): String = buildString {
        appendLine(instruction)
        appendLine()
        appendLine("---BEGIN CONTENT---")
        appendLine(content)
        append("---END CONTENT---")
    }
}

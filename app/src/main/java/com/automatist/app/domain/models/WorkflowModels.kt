package com.automatist.app.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Workflow Template (shared model for both built-in blueprints and user workflows) ──

data class WorkflowTemplate(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val trigger: WorkflowTrigger = WorkflowTrigger.Manual,
    val actions: List<WorkflowAction> = emptyList(),
    val globalInstruction: String = "",
    val outputConfig: WorkflowOutputConfig = WorkflowOutputConfig(),
    val notifyOnCompletion: Boolean = false,
    val notifyOnStart: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val lastRunAtMillis: Long? = null,
    val lastRunStatus: WorkflowRunStatus? = null,
    // ── Template-system fields ──
    val sourceTemplateId: String = "",         // built-in template ID this was created from ("" for legacy)
    val category: String = "",                 // UI grouping: "News & Content", "Communication", etc.
    val customization: TemplateCustomization = TemplateCustomization(),
    // ── Profile routing ──
    val defaultProfileId: String = "", // workflow-level default profile ("" = use app default)
    // ── Auto-retry ──
    /**
     * When true, a failed run automatically retries up to
     * [WorkflowRun.MAX_AUTO_RETRIES] times before the run chain is finally
     * marked failed. Each retry reuses [ResumeSnapshot] when safe, so the
     * expensive earlier stages (RSS/URL fetch, per-action preprocessing) are
     * only rerun when resume is unsafe. Defaults to OFF for backwards
     * compatibility — existing workflows keep their current fail-fast behaviour
     * until the user explicitly opts in.
     */
    val autoRetryEnabled: Boolean = false
)

// ── Template Customization Rules ──

@Serializable
data class TemplateCustomization(
    val editableSections: Set<EditableSection> = EditableSection.entries.toSet(),
    val lockedActionIds: Set<String> = emptySet(), // action IDs that cannot be removed
    val canAddActions: Boolean = true,
    val canRemoveActions: Boolean = true
)

@Serializable
enum class EditableSection {
    BASICS,
    TRIGGER,
    ACTIONS,
    INSTRUCTIONS,
    OUTPUT,
    NOTIFICATIONS
}

// ── Trigger ──

@Serializable
sealed interface WorkflowTrigger {
    @Serializable
    @SerialName("manual")
    data object Manual : WorkflowTrigger

    @Serializable
    @SerialName("daily")
    data class Daily(
        val hour: Int = 8,
        val minute: Int = 0
    ) : WorkflowTrigger

    @Serializable
    @SerialName("weekly")
    data class Weekly(
        val daysOfWeek: Set<Int> = setOf(1),
        val hour: Int = 8,
        val minute: Int = 0
    ) : WorkflowTrigger

    @Serializable
    @SerialName("interval")
    data class Interval(
        val intervalMinutes: Int = 60
    ) : WorkflowTrigger {
        /**
         * Human-readable label used throughout the UI. Keeps the compact
         * "Every 15m" / "Every 3h" shorthand for tight chips and row
         * summaries. Full sentences with correct plurals ("Every 1 minute",
         * "Every 3 hours") are produced by [displayLabelVerbose] for places
         * that have horizontal room.
         */
        val displayLabel: String get() = when {
            intervalMinutes < 60 -> "Every ${intervalMinutes}m"
            intervalMinutes == 60 -> "Every hour"
            intervalMinutes % 60 == 0 -> "Every ${intervalMinutes / 60}h"
            else -> "Every ${intervalMinutes / 60}h ${intervalMinutes % 60}m"
        }

        /** Long-form label with correct singular/plural handling. */
        val displayLabelVerbose: String get() = when {
            intervalMinutes <= 0 -> "Every 0 minutes"
            intervalMinutes == 1 -> "Every 1 minute"
            intervalMinutes < 60 -> "Every $intervalMinutes minutes"
            intervalMinutes == 60 -> "Every 1 hour"
            intervalMinutes % 60 == 0 -> "Every ${intervalMinutes / 60} hours"
            else -> {
                val h = intervalMinutes / 60
                val m = intervalMinutes % 60
                val hPart = if (h == 1) "1 hour" else "$h hours"
                val mPart = if (m == 1) "1 minute" else "$m minutes"
                "Every $hPart $mPart"
            }
        }
    }

    @Serializable
    @SerialName("notification")
    data class NotificationKeyword(
        val keywords: List<String> = emptyList()
    ) : WorkflowTrigger
}

// ── Action Types ──

@Serializable
enum class WorkflowActionType(val displayName: String) {
    FETCH_URL("Fetch URL"),
    PASTE_TEXT("Paste Text"),
    FETCH_RSS_FEED("Fetch RSS Feed"),
    FETCH_API_GET("Fetch API (GET)"),
    USE_SAVED_NOTE("Saved Note"),
    USE_PREVIOUS_OUTPUT("Previous Workflow Output"),
    FETCH_RSS_MULTI("Multi-Feed RSS"),
    FETCH_WEATHER("Weather Data"),
    FETCH_ROUTE_TIME("Route / Commute Time"),
    USE_ACTION_OUTPUT("Use Action Output"),
    AI_PROMPT("AI Prompt")
}

// ── Action Model ──

@Serializable
data class WorkflowAction(
    val id: String,
    val type: WorkflowActionType,
    val label: String = "",
    val sourceData: String = "",
    val instruction: String = "",
    val order: Int = 0,
    val isEnabled: Boolean = true,
    val extraConfig: String = "",
    val profileId: String = "", // AI profile override ("" = inherit workflow default)
    // Per-action text compaction applied to this action's prepared output before
    // the main workflow prompt stage. Defaults to AGGRESSIVE; old workflows that
    // pre-date this field deserialize with the default via ignoreUnknownKeys.
    val compaction: InputCompactionMode = InputCompactionMode.AGGRESSIVE
)

// ── Per-Action Config Models ──

@Serializable
data class RssFeedConfig(
    val maxItems: Int = 5,
    val includeTitle: Boolean = true,
    val includeSummary: Boolean = true,
    val includeLink: Boolean = false,
    val includePublishedDate: Boolean = false,
    val keywordFilter: String = ""
)

@Serializable
data class ApiGetConfig(
    val queryParams: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val extractionHint: String = ""
)

@Serializable
data class SavedNoteReference(
    val noteId: Long = 0,
    val noteTitle: String = ""
)

@Serializable
enum class OutputSelectionMode {
    LATEST_SUCCESSFUL,
    SPECIFIC_RUN
}

@Serializable
data class PreviousOutputConfig(
    val sourceWorkflowId: Long = 0,
    val sourceWorkflowName: String = "",
    val selectionMode: OutputSelectionMode = OutputSelectionMode.LATEST_SUCCESSFUL,
    val specificRunId: Long? = null,
    val includeMetadata: Boolean = false
)

@Serializable
data class MultiFeedRssConfig(
    val feedUrls: List<String> = emptyList(),
    val maxItems: Int = 10,
    val includeTitle: Boolean = true,
    val includeSummary: Boolean = true,
    val includeLink: Boolean = false,
    val includePublishedDate: Boolean = false,
    val keywordFilter: String = "",
    val deduplicateByTitle: Boolean = true,
    val sortNewestFirst: Boolean = true
)

// ── Weather Config ──

@Serializable
enum class WeatherUnits(val displayName: String) {
    METRIC("Celsius"),
    IMPERIAL("Fahrenheit")
}

@Serializable
data class WeatherConfig(
    val location: String = "",          // city name, zip code, or "lat,lon"
    val units: WeatherUnits = WeatherUnits.METRIC,
    val includeForecast: Boolean = false,
    val forecastDays: Int = 1
)

object WeatherService {
    const val SERVICE_KEY = "openweathermap"
    const val BASE_URL = "https://api.openweathermap.org/data/2.5"
}

// ── Route Config ──

@Serializable
enum class TravelMode(val displayName: String) {
    DRIVING("Driving"),
    TRANSIT("Public Transit"),
    WALKING("Walking"),
    BICYCLING("Bicycling")
}

@Serializable
data class RouteConfig(
    val origin: String = "",            // address or "lat,lon"
    val destination: String = "",       // address or "lat,lon"
    val travelMode: TravelMode = TravelMode.DRIVING
)

object RouteService {
    const val SERVICE_KEY = "openrouteservice"
    const val BASE_URL = "https://api.openrouteservice.org"
}

// ── Use Action Output Config ──

@Serializable
data class ActionOutputConfig(
    val sourceActionId: String = "",
    val sourceActionLabel: String = ""
)

// ── AI Prompt Config ──

@Serializable
enum class AiPromptOutputFormat(val displayName: String) {
    PLAIN_TEXT("Plain Text"),
    MARKDOWN("Markdown"),
    JSON("JSON"),
    CUSTOM("Custom")
}

@Serializable
data class AiPromptConfig(
    val promptText: String = "",
    val outputFormat: AiPromptOutputFormat = AiPromptOutputFormat.PLAIN_TEXT,
    val profileId: String = "" // AI profile override ("" = inherit workflow default)
)

// ── Saved Note ──

data class SavedNote(
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

// ── Output Config ──

@Serializable
enum class WorkflowOutputType(val displayName: String) {
    BRIEFING("Briefing"),
    SOCIAL_POST("Social Post"),
    BOTH("Both"),
    CUSTOM("Custom")
}

@Serializable
enum class OutputFormat(val displayName: String, val description: String) {
    MARKDOWN("Markdown", "Headings, bullets, bold — rendered beautifully"),
    PLAIN_TEXT("Plain Text", "Simple readable text, no formatting syntax"),
    JSON("JSON", "Structured JSON data — pretty-printed in the app"),
    AUTO("Auto", "Let the AI choose the best format")
}

@Serializable
enum class InputCompactionMode(val displayName: String, val description: String) {
    NONE("None", "Send fetched text as-is"),
    LIGHT("Light", "Clean whitespace, HTML tags, and boilerplate noise"),
    AGGRESSIVE("Aggressive", "Deduplicate, trim, and keep only the most information-dense content")
}

@Serializable
data class WorkflowOutputConfig(
    val outputType: WorkflowOutputType = WorkflowOutputType.BRIEFING,
    val outputFormat: OutputFormat = OutputFormat.MARKDOWN,
    val customInstruction: String = "",
    val socialPlatforms: Set<SocialPlatform> = emptySet(),
    val saveToHistory: Boolean = true,
    val outputProfileId: String = "", // profile override for final output generation ("" = inherit workflow default)
    // ── Social media output config ──
    val socialGlobalInstruction: String = "", // shared instruction for all social outputs
    val platformInstructions: Map<String, String> = emptyMap(), // per-platform instructions (key = platform name)
    val customPlatforms: List<String> = emptyList(), // user-defined custom platform names
    // ── Input compaction ──
    val inputCompaction: InputCompactionMode = InputCompactionMode.NONE,
    // ── Output versions ──
    val numberOfOutputs: Int = 1 // 1..10 — generate multiple alternative versions from same input
)

// ── Social Output (parsed from structured JSON response) ──

@Serializable
data class SocialOutput(
    val platform: String,
    val content: String,
    val title: String = "",
    val notes: String = ""
) {
    val charCount: Int get() = content.length
}

/**
 * Utility to parse social output JSON from AI response.
 * Expected schema: {"outputs": [{"platform": "X", "content": "...", "title": "...", "notes": "..."}]}
 *
 * Two parse modes:
 *  - [parse] — strict. Used by cloud-quality outputs and by [isSocialJson] for
 *    cheap "is this likely a social JSON" checks. Returns empty on any
 *    malformation.
 *  - [parseTolerant] — strict-first-then-repair. Local/offline models (notably
 *    Gemma 3 1B int4) often emit almost-valid JSON (missing commas, code
 *    fences, trailing commentary). When strict fails, this tries conservative
 *    repairs and re-parses. Cloud outputs never reach the repair code because
 *    they pass strict parse on the first attempt, so this is a pure additive
 *    behaviour — no risk to OpenAI / Anthropic / Gemini output handling.
 */
object SocialOutputParser {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * Result of [parseTolerant]. Carries the parsed outputs plus whether the
     * input had to be repaired first. The caller can use [wasRepaired] to show
     * a subtle UI hint, but the rendered content should use [outputs]
     * directly — [originalRaw] always preserves the true model output so the
     * Raw view can show it verbatim.
     */
    data class ParseResult(
        val outputs: List<SocialOutput>,
        val wasRepaired: Boolean,
        val originalRaw: String,
        /**
         * Repaired JSON string if repair was both needed and successful;
         * null when strict parse succeeded or repair failed entirely. Useful
         * for a "Repaired JSON" diagnostic view — NOT for the Raw view.
         */
        val repairedJson: String? = null
    )

    fun parse(rawJson: String): List<SocialOutput> {
        return try {
            val trimmed = rawJson.trim()
            val element = json.parseToJsonElement(trimmed)
            // Two accepted shapes — both are treated as first-class "strict":
            //   A) canonical wrapped object: { "outputs": [ {platform,content,...} ] }
            //   B) bare array: [ {platform,content,...}, ... ]
            // Local models (notably Gemma 3 1B int4) sometimes drop the wrapper
            // and emit shape B even when the prompt explicitly asks for shape A.
            // Cloud providers reliably emit shape A, so supporting B here adds
            // zero risk to cloud rendering while recovering the local-model case.
            val itemsArray = itemsArrayFromRoot(element) ?: return emptyList()
            itemsArray.mapNotNull { item ->
                val itemObj = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                SocialOutput(
                    platform = itemObj["platform"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "Unknown",
                    content = itemObj["content"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "",
                    title = itemObj["title"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "",
                    notes = itemObj["notes"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Return the array of social-output items given either shape. Shape A pulls
     * from the `outputs` key; shape B treats the root array itself as the list.
     * A shape-B array only qualifies when at least one entry looks social (has
     * a `platform` field) so a random array of primitives or mismatched objects
     * isn't accidentally accepted as social output.
     */
    private fun itemsArrayFromRoot(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonArray? {
        when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                val arr = element["outputs"] as? kotlinx.serialization.json.JsonArray
                return arr
            }
            is kotlinx.serialization.json.JsonArray -> {
                val looksSocial = element.any { item ->
                    (item as? kotlinx.serialization.json.JsonObject)?.containsKey("platform") == true
                }
                return if (looksSocial) element else null
            }
            else -> return null
        }
    }

    /**
     * Best-effort parse that tries strict first and falls back to a conservative
     * JSON repair. The [raw] string is never mutated in the result — [ParseResult.originalRaw]
     * always reflects the true model output so the Raw view can show it verbatim.
     */
    fun parseTolerant(raw: String): ParseResult {
        // 1. Strict first. Cloud outputs hit this and never touch the repair code.
        val strict = parse(raw)
        if (strict.isNotEmpty()) {
            return ParseResult(
                outputs = strict,
                wasRepaired = false,
                originalRaw = raw,
                repairedJson = null
            )
        }

        // 2. Conservative repair. Returns null if nothing repairable applies.
        val repaired = repairJson(raw) ?: return ParseResult(emptyList(), false, raw, null)
        // Don't count "no-op" repairs as repairs — if the string didn't actually
        // change, strict already failed and running parse() again won't help.
        if (repaired == raw) return ParseResult(emptyList(), false, raw, null)

        val afterRepair = parse(repaired)
        return if (afterRepair.isNotEmpty()) {
            ParseResult(
                outputs = afterRepair,
                wasRepaired = true,
                originalRaw = raw,
                repairedJson = repaired
            )
        } else {
            // Repair produced something, but it still isn't valid social JSON.
            // Fall back to "parsing failed" — the UI will show raw-only.
            ParseResult(emptyList(), false, raw, null)
        }
    }

    fun isSocialJson(text: String): Boolean {
        return try {
            val trimmed = text.trim()
            if (!trimmed.startsWith("{")) return false
            val element = json.parseToJsonElement(trimmed)
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return false
            obj.containsKey("outputs")
        } catch (_: Exception) {
            false
        }
    }

    // ── JSON repair heuristics ──
    //
    // Every fix below is conservative. The idea is to cover the cases we've
    // actually observed from Gemma 3 1B int4 (markdown fences, stray prose
    // before/after the object, trailing commas, the occasional missing comma
    // between fields, smart quotes). Anything riskier — unquoted keys, nested
    // structural rewrites — is intentionally NOT attempted: a failed render is
    // better than a silently misrepresented one.

    private fun repairJson(raw: String): String? {
        if (raw.isBlank()) return null
        var s = raw.trim()

        // Strip leading/trailing markdown code fences: ```json ... ``` or ``` ... ```
        s = stripCodeFences(s)

        // Narrow to the outermost balanced JSON value (object OR array). Handles
        // stray prose around the payload, and crucially handles the local-model
        // "bare array" case — previously we only extracted `{...}`, so a valid
        // top-level array still tripped the repair path and never rendered.
        extractOutermostJsonValue(s)?.let { s = it }

        // Convert smart/curly quotes to straight ASCII quotes. Gemma sometimes
        // emits '“' / '”' around strings, which kotlinx.serialization rejects.
        s = s
            .replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')

        // Remove trailing commas before } or ] — common Gemma tic.
        s = s.replace(Regex(""",\s*(?=[}\]])"""), "")

        // Insert missing commas at three structural patterns:
        //   a) end-of-string directly followed by start-of-next-key string
        //   b) }  directly followed by { (array of objects, missing comma)
        //   c) ]  directly followed by " (string key after a closing array)
        s = s.replace(Regex("""("(?:[^"\\]|\\.)*")(\s*[\r\n]+\s*)(")"""), "$1,$2$3")
        s = s.replace(Regex("""(\})(\s*[\r\n]+\s*)(\{)"""), "$1,$2$3")
        s = s.replace(Regex("""(\])(\s*[\r\n]+\s*)(")"""), "$1,$2$3")

        return s.takeIf { it != raw }
    }

    private fun stripCodeFences(s: String): String {
        val fenceStart = Regex("""^\s*```(?:json|JSON)?\s*""")
        val fenceEnd = Regex("""\s*```\s*$""")
        return s.replace(fenceStart, "").replace(fenceEnd, "").trim()
    }

    /**
     * Locate the outermost balanced JSON object OR array within [s]. Picks
     * whichever opening token appears first in the string, so prose before a
     * `[...]` payload lifts the array correctly and the same for an object.
     * Ignores braces inside string literals. Returns null if no balanced
     * payload exists — callers keep the unmodified input in that case.
     */
    private fun extractOutermostJsonValue(s: String): String? {
        val objIdx = s.indexOf('{')
        val arrIdx = s.indexOf('[')
        val start = when {
            objIdx < 0 && arrIdx < 0 -> return null
            objIdx < 0 -> arrIdx
            arrIdx < 0 -> objIdx
            else -> minOf(objIdx, arrIdx)
        }
        val openChar = s[start]
        val closeChar = if (openChar == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until s.length) {
            val c = s[i]
            if (escape) { escape = false; continue }
            if (inString) {
                when (c) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
                continue
            }
            when {
                c == '"' -> inString = true
                c == openChar -> depth++
                c == closeChar -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

// ── Run ──

enum class WorkflowRunStatus {
    RUNNING,
    COMPLETED,
    FAILED
}

data class WorkflowRun(
    val id: Long = 0,
    val templateId: Long,
    val templateName: String,
    val triggerType: String = "manual",
    val status: WorkflowRunStatus = WorkflowRunStatus.RUNNING,
    val currentStage: String = "",
    val outputText: String = "",
    val outputFormat: OutputFormat = OutputFormat.MARKDOWN,
    val providerType: ProviderType? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val durationMs: Long? = null,
    val errorMessage: String? = null,
    val errorDetail: String? = null,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null,
    val profileName: String = "",
    val modelId: String = "",
    val isSocialOutput: Boolean = false,
    val stagesJson: String = "",
    val synthesisInput: String = "",   // frozen combined input for regeneration
    val versionsJson: String = "",     // JSON: List<OutputVersion>
    // Resume snapshot captured on failure so retry can safely skip
    // already-successful work. Blank for runs that didn't capture one
    // (older runs, completed runs, non-resumable failure stages).
    val resumeSnapshotJson: String = "",
    /**
     * Which automatic retry this run represents.
     * - 0: the initial attempt (or a manual retry the user kicked off)
     * - 1..[MAX_AUTO_RETRIES]: an automatic retry triggered after the
     *   previous run in the same chain failed
     *
     * Auto-retry stops once this would exceed [MAX_AUTO_RETRIES], giving a
     * hard ceiling of 1 initial attempt + [MAX_AUTO_RETRIES] auto-retries.
     */
    val autoRetryAttempt: Int = 0,
    /**
     * Points to the run that started this auto-retry chain (the initial
     * attempt) so run history can show "Attempt 2/4" grouping. Null for
     * initial runs and manual retries.
     */
    val parentRunId: Long? = null
) {
    companion object {
        /**
         * Max number of automatic retries. With the initial attempt this
         * gives 4 total tries before the chain is considered finally failed.
         */
        const val MAX_AUTO_RETRIES = 3
    }
    /**
     * Parse social outputs from outputText when isSocialOutput is true.
     * Uses the tolerant parser so almost-valid local-model JSON still yields
     * cards. The true raw output is always on [outputText] itself — this
     * accessor is for rendered/structured consumption only.
     */
    val socialOutputs: List<SocialOutput>
        get() = if (isSocialOutput) SocialOutputParser.parseTolerant(outputText).outputs else emptyList()

    /** Parse persisted stage history from stagesJson. */
    val persistedStages: List<PersistedStage>
        get() = PersistedStage.parseList(stagesJson)

    /** Parse output versions from versionsJson. */
    val outputVersions: List<OutputVersion>
        get() = OutputVersion.parseList(versionsJson)

    /** Parse resume snapshot, or null if blank / unparseable. */
    val resumeSnapshot: ResumeSnapshot?
        get() = ResumeSnapshot.parse(resumeSnapshotJson)

    /** Whether this run has enough context for regeneration. */
    val canRegenerate: Boolean
        get() = synthesisInput.isNotBlank() && status == WorkflowRunStatus.COMPLETED

    /**
     * User-friendly attempt context. Present only when there's something to
     * say — an initial manual or scheduled run returns blank so the UI can
     * skip rendering the chip. Everything else (auto-retry attempts, manual
     * reruns, and the final failed attempt of a chain) gets a concise label.
     *
     * Distinguishes:
     *  - Auto-retry attempts   → "Auto-retry 2 of 3" / "Final attempt" when N == MAX
     *  - Manual reruns         → "Manual rerun"   (triggerType == "manual-resume")
     *  - Initial runs          → blank
     *
     * Based on metadata that's already persisted on [WorkflowRun]
     * ([autoRetryAttempt], [triggerType]) — no extra columns, no extra reads.
     */
    val retryDisplayLabel: String
        get() = when {
            autoRetryAttempt in 1..MAX_AUTO_RETRIES -> {
                if (autoRetryAttempt == MAX_AUTO_RETRIES && status == WorkflowRunStatus.FAILED) {
                    "Final attempt ($autoRetryAttempt of $MAX_AUTO_RETRIES)"
                } else {
                    "Auto-retry $autoRetryAttempt of $MAX_AUTO_RETRIES"
                }
            }
            triggerType == "manual-resume" -> "Manual rerun"
            else -> ""
        }

    /** True when this run is part of an auto-retry chain (not an initial run). */
    val isAutoRetryAttempt: Boolean get() = autoRetryAttempt > 0

    /** True when this run was explicitly kicked off by a user's Run Again tap. */
    val isManualRerun: Boolean get() = triggerType == "manual-resume"
}

// ── Resume Snapshot (captured on safe-to-resume failures) ──

/**
 * Immutable snapshot of what the engine had computed up to the point of
 * failure. Attached to failed [WorkflowRun] rows so the retry path can
 * resume from the first failed step instead of rerunning everything.
 *
 * The [fingerprint] is a stable hash of the workflow definition at run time.
 * A retry validates that the CURRENT template still matches this fingerprint
 * before reusing any snapshot state — if the user edited the workflow after
 * the failure, the snapshot is treated as stale and a full rerun happens.
 *
 * [reachedStage] documents how far execution got so the retry can pick the
 * right entry point. v1 supports "PROCESSING" (final-generation failure); the
 * schema leaves room for per-action resume in a future revision.
 *
 * [synthesisInput] is the frozen combined prompt that was about to be sent
 * to the model. Non-blank ONLY when reachedStage == "PROCESSING".
 *
 * [actionOutputs] captures every successful action's prepared text, keyed by
 * [WorkflowAction.id]. v1 doesn't reuse these for final-gen resume (the
 * combined input is enough), but they're captured for future action-level
 * resume so we don't need another migration later.
 */
@Serializable
data class ResumeSnapshot(
    val fingerprint: String,
    val reachedStage: String,
    val synthesisInput: String = "",
    val actionOutputs: List<ResumedActionOutput> = emptyList(),
    val schemaVersion: Int = 1
) {
    /**
     * True if this snapshot has enough state to safely resume a retry for
     * [template]: the fingerprint still matches and we reached a stage we
     * actually know how to resume from.
     */
    fun canResumeFor(template: WorkflowTemplate): Boolean {
        if (fingerprint.isBlank()) return false
        if (fingerprint != computeWorkflowFingerprint(template)) return false
        return when (reachedStage) {
            STAGE_PROCESSING -> synthesisInput.isNotBlank()
            else -> false
        }
    }

    companion object {
        const val STAGE_PROCESSING = "PROCESSING"

        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun parse(raw: String): ResumeSnapshot? {
            if (raw.isBlank()) return null
            return try {
                json.decodeFromString<ResumeSnapshot>(raw)
            } catch (_: Exception) {
                null
            }
        }

        fun toJson(snapshot: ResumeSnapshot): String = try {
            json.encodeToString(serializer(), snapshot)
        } catch (_: Exception) {
            ""
        }
    }
}

@Serializable
data class ResumedActionOutput(
    val actionId: String,
    val preparedText: String,
    val instructionApplied: Boolean = false
)

/**
 * Deterministic fingerprint of the workflow definition that affects execution.
 * Covers the action list (id/type/sourceData/instruction/extraConfig/order/
 * isEnabled/compaction/profileId), the global instruction, the output config,
 * and the workflow-level default profile.
 *
 * Two templates with the same fingerprint are considered interchangeable for
 * the purpose of resuming a prior failed run. The hash is a plain string
 * concatenation rather than SHA-256 — we care about equality, not collision
 * resistance, and avoiding crypto keeps this cheap + test-friendly.
 */
fun computeWorkflowFingerprint(template: WorkflowTemplate): String {
    val sb = StringBuilder()
    // Field separator: a newline char literal kept to one line.
    val sep: Char = '\u000A'
    sb.append("gi=").append(template.globalInstruction.hashCode()).append(sep)
    sb.append("dp=").append(template.defaultProfileId).append(sep)
    // Output config: use the existing JSON encoder for stability (same one the
    // Room mappers use) so we are indirectly testing the same serialisation
    // path users will see.
    val j = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val ocJson = try {
        j.encodeToString(WorkflowOutputConfig.serializer(), template.outputConfig)
    } catch (_: Exception) { "" }
    sb.append("oc=").append(ocJson.hashCode()).append(sep)
    sb.append("acts=")
    for (a in template.actions.sortedBy { it.order }) {
        sb.append(a.id).append('|')
        sb.append(a.type.name).append('|')
        sb.append(a.order).append('|')
        sb.append(a.isEnabled).append('|')
        sb.append(a.compaction.name).append('|')
        sb.append(a.profileId).append('|')
        sb.append(a.sourceData.hashCode()).append('|')
        sb.append(a.instruction.hashCode()).append('|')
        sb.append(a.extraConfig.hashCode()).append(sep)
    }
    return sb.toString().hashCode().toString(16)
}

// ── Persisted Stage (for run detail history) ──

@Serializable
data class PersistedStage(
    val label: String,
    val status: String, // "COMPLETED", "FAILED", "RUNNING", "PENDING"
    val detail: String = "",
    val actionData: String = "" // full action result text (redacted/truncated)
) {
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun parseList(stagesJson: String): List<PersistedStage> {
            if (stagesJson.isBlank()) return emptyList()
            return try {
                json.decodeFromString<List<PersistedStage>>(stagesJson)
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJson(stages: List<PersistedStage>): String {
            return try {
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer()), stages)
            } catch (_: Exception) {
                ""
            }
        }
    }
}

// ── Output Version (for multi-version and regeneration support) ──

@Serializable
data class OutputVersion(
    val version: Int,
    val outputText: String,
    val isSocialOutput: Boolean = false,
    val generatedAtMillis: Long = System.currentTimeMillis()
) {
    /** Parse social outputs if this version is social (tolerant — see [WorkflowRun.socialOutputs]). */
    val socialOutputs: List<SocialOutput>
        get() = if (isSocialOutput) SocialOutputParser.parseTolerant(outputText).outputs else emptyList()

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun parseList(versionsJson: String): List<OutputVersion> {
            if (versionsJson.isBlank()) return emptyList()
            return try {
                json.decodeFromString<List<OutputVersion>>(versionsJson)
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJson(versions: List<OutputVersion>): String {
            return try {
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer()), versions)
            } catch (_: Exception) {
                ""
            }
        }
    }
}

data class TokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    /**
     * True when the token counts are provider-side estimates (local / fake)
     * rather than authoritative counts from a cloud API. UI should append
     * " (estimated)" to labels and surface a clarifying note when this is set.
     */
    val isEstimated: Boolean = false,
    /** Actual prompt character count reported by the provider. */
    val inputChars: Int? = null,
    /** Actual output character count reported by the provider. */
    val outputChars: Int? = null,
    /** Input-token ceiling, when the provider enforces one. */
    val contextCeilingTokens: Int? = null,
    /** True if the provider truncated/compressed input to fit its context window. */
    val wasTruncated: Boolean? = null
)

package com.synapse.app.domain.engine

import com.synapse.app.data.network.RssParser
import com.synapse.app.domain.models.*
import com.synapse.app.domain.providers.ArticleTransformProvider
import com.synapse.app.domain.repositories.WorkflowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowExecutionEngine @Inject constructor(
    private val transformProvider: ArticleTransformProvider,
    private val httpClient: OkHttpClient,
    private val rssParser: RssParser,
    private val workflowRepository: WorkflowRepository
) {

    companion object {
        private const val MAX_CHARS_PER_ACTION = 4000
        private const val MAX_TOTAL_CHARS = 16000
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun execute(template: WorkflowTemplate): Flow<ExecutionState> = flow<ExecutionState> {
        val startTime = System.currentTimeMillis()

        emit(ExecutionState.Preparing)

        // Validate
        val enabledActions = template.actions.filter { it.isEnabled }.sortedBy { it.order }
        if (enabledActions.isEmpty()) {
            emit(ExecutionState.Failed("No enabled actions in workflow.", "validation"))
            return@flow
        }

        emit(ExecutionState.ValidatingInputs(enabledActions.size))

        // Execute each action and collect results
        val actionResults = mutableListOf<ActionResult>()

        for ((index, action) in enabledActions.withIndex()) {
            val label = action.label.ifBlank { "${action.type.displayName} #${index + 1}" }

            emit(ExecutionState.ActionStarted(index, enabledActions.size, label, action.type.name))

            val result = executeAction(action)

            if (result.isSuccess) {
                val text = result.getOrThrow()
                actionResults.add(ActionResult(action, text))
                emit(
                    ExecutionState.ActionCompleted(
                        index, enabledActions.size, label,
                        text.take(100) + if (text.length > 100) "..." else ""
                    )
                )
            } else {
                emit(
                    ExecutionState.ActionFailed(
                        index, enabledActions.size, label,
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                )
                // Continue with other actions — don't abort
            }
        }

        if (actionResults.isEmpty()) {
            emit(ExecutionState.Failed("All actions failed. No data to process.", "actions"))
            return@flow
        }

        // Combine action outputs
        val combinedInput = buildCombinedInput(actionResults, template)
        emit(ExecutionState.ProcessingStarted(combinedInput.length))

        // Build prompt
        val systemPrompt = buildSystemPrompt(template)

        // Resolve profile for final output generation:
        // 1. Output-level override (outputProfileId)
        // 2. Workflow-level default (defaultProfileId)
        // 3. App default (null = router handles it)
        val outputProfileId = template.outputConfig.outputProfileId.ifBlank {
            template.defaultProfileId.ifBlank { null }
        }
        emit(ExecutionState.GeneratingOutput(outputProfileId ?: "default"))

        // Call AI provider with resolved profile
        val input = ArticleInput(
            text = combinedInput,
            systemPromptOverride = systemPrompt,
            profileId = outputProfileId
        )

        val transformResult = transformProvider.transform(input, TransformType.CUSTOM_WORKFLOW)

        transformResult.onSuccess { result ->
            val durationMs = System.currentTimeMillis() - startTime
            val tokenUsage = TokenUsage(
                promptTokens = result.promptTokens,
                completionTokens = result.completionTokens,
                totalTokens = (result.promptTokens ?: 0) + (result.completionTokens ?: 0),
                isEstimated = result.providerType == ProviderType.FAKE
            )
            emit(
                ExecutionState.Completed(
                    outputText = result.outputText,
                    providerType = result.providerType,
                    tokenUsage = tokenUsage,
                    durationMs = durationMs
                )
            )
        }.onFailure { error ->
            emit(ExecutionState.Failed(error.message ?: "AI processing failed", "processing"))
        }
    }.flowOn(Dispatchers.IO)

    // ── Action Dispatch ──

    private suspend fun executeAction(action: WorkflowAction): Result<String> {
        return when (action.type) {
            WorkflowActionType.FETCH_URL -> fetchUrl(action.sourceData)

            WorkflowActionType.PASTE_TEXT -> {
                if (action.sourceData.isBlank()) {
                    Result.failure(Exception("Pasted text is empty"))
                } else {
                    Result.success(action.sourceData.take(MAX_CHARS_PER_ACTION))
                }
            }

            WorkflowActionType.FETCH_RSS_FEED -> executeRssFeed(action)

            WorkflowActionType.FETCH_API_GET -> executeApiGet(action)

            WorkflowActionType.USE_SAVED_NOTE -> executeSavedNote(action)

            WorkflowActionType.USE_PREVIOUS_OUTPUT -> executePreviousOutput(action)

            WorkflowActionType.FETCH_RSS_MULTI -> executeMultiFeedRss(action)
        }
    }

    // ── FETCH_URL ──

    private fun fetchUrl(url: String): Result<String> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Synapse/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val cleaned = cleanHtml(body).take(MAX_CHARS_PER_ACTION)
            Result.success(cleaned)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to fetch URL: ${e.message}"))
        }
    }

    // ── FETCH_RSS_FEED ──

    private suspend fun executeRssFeed(action: WorkflowAction): Result<String> {
        return try {
            val feedUrl = action.sourceData
            if (feedUrl.isBlank()) return Result.failure(Exception("RSS feed URL is empty"))

            val config = if (action.extraConfig.isNotBlank()) {
                json.decodeFromString<RssFeedConfig>(action.extraConfig)
            } else {
                RssFeedConfig()
            }

            // Reuse existing RssParser for the heavy lifting
            val rawText = rssParser.fetchAndParse(listOf(feedUrl))
            if (rawText.isBlank()) return Result.failure(Exception("RSS feed returned no content"))

            // Apply keyword filter if configured
            val filteredText = if (config.keywordFilter.isNotBlank()) {
                val keywords = config.keywordFilter.split(",").map { it.trim().lowercase() }
                val lines = rawText.split("\n\n")
                val filtered = lines.filter { block ->
                    keywords.any { kw -> block.lowercase().contains(kw) }
                }
                if (filtered.isEmpty()) rawText // fallback: return all if filter matches nothing
                else filtered.joinToString("\n\n")
            } else rawText

            Result.success(filteredText.take(MAX_CHARS_PER_ACTION))
        } catch (e: Exception) {
            Result.failure(Exception("RSS feed error: ${e.message}"))
        }
    }

    // ── FETCH_API_GET ──

    private fun executeApiGet(action: WorkflowAction): Result<String> {
        return try {
            var baseUrl = action.sourceData
            if (baseUrl.isBlank()) return Result.failure(Exception("API URL is empty"))

            val config = if (action.extraConfig.isNotBlank()) {
                json.decodeFromString<ApiGetConfig>(action.extraConfig)
            } else {
                ApiGetConfig()
            }

            // Build URL with query params
            val urlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
                ?: return Result.failure(Exception("Invalid URL: $baseUrl"))

            config.queryParams.forEach { (key, value) ->
                urlBuilder.addQueryParameter(key, value)
            }

            val requestBuilder = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "Synapse/1.0")

            // Add custom headers (filter out sensitive ones)
            config.headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("API returned HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty API response"))

            // Try to pretty-format JSON, fallback to raw text
            val normalized = try {
                val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(body)
                jsonElement.toString().take(MAX_CHARS_PER_ACTION)
            } catch (_: Exception) {
                body.take(MAX_CHARS_PER_ACTION)
            }

            // If extraction hint is provided, prepend it
            val result = if (config.extractionHint.isNotBlank()) {
                "[Extraction hint: ${config.extractionHint}]\n$normalized"
            } else normalized

            Result.success(result.take(MAX_CHARS_PER_ACTION))
        } catch (e: Exception) {
            Result.failure(Exception("API GET error: ${e.message}"))
        }
    }

    // ── USE_SAVED_NOTE ──

    private suspend fun executeSavedNote(action: WorkflowAction): Result<String> {
        return try {
            // First try to resolve by noteId from extraConfig
            if (action.extraConfig.isNotBlank()) {
                val ref = json.decodeFromString<SavedNoteReference>(action.extraConfig)
                if (ref.noteId > 0) {
                    val note = workflowRepository.getNoteById(ref.noteId)
                    if (note != null) {
                        return Result.success(note.content.take(MAX_CHARS_PER_ACTION))
                    }
                    return Result.failure(Exception("Saved note not found (ID: ${ref.noteId})"))
                }
            }

            // Fallback: use sourceData as inline content
            if (action.sourceData.isNotBlank()) {
                Result.success(action.sourceData.take(MAX_CHARS_PER_ACTION))
            } else {
                Result.failure(Exception("No saved note selected and no inline content provided"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Saved note error: ${e.message}"))
        }
    }

    // ── USE_PREVIOUS_OUTPUT ──

    private suspend fun executePreviousOutput(action: WorkflowAction): Result<String> {
        return try {
            if (action.extraConfig.isBlank()) {
                return Result.failure(Exception("No previous workflow configured"))
            }

            val config = json.decodeFromString<PreviousOutputConfig>(action.extraConfig)

            val run = when (config.selectionMode) {
                OutputSelectionMode.LATEST_SUCCESSFUL -> {
                    if (config.sourceWorkflowId <= 0) {
                        return Result.failure(Exception("No source workflow selected"))
                    }
                    workflowRepository.getLatestSuccessfulRun(config.sourceWorkflowId)
                }
                OutputSelectionMode.SPECIFIC_RUN -> {
                    val runId = config.specificRunId
                        ?: return Result.failure(Exception("No specific run selected"))
                    workflowRepository.getRunById(runId)
                }
            }

            if (run == null) {
                return Result.failure(
                    Exception("No successful run found for \"${config.sourceWorkflowName}\"")
                )
            }

            if (run.outputText.isBlank()) {
                return Result.failure(Exception("Previous run output is empty"))
            }

            val result = if (config.includeMetadata) {
                buildString {
                    appendLine("[Source: ${run.templateName}]")
                    appendLine("[Run date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(run.startedAtMillis))}]")
                    appendLine("[Status: ${run.status.name}]")
                    appendLine()
                    append(run.outputText)
                }
            } else {
                run.outputText
            }

            Result.success(result.take(MAX_CHARS_PER_ACTION))
        } catch (e: Exception) {
            Result.failure(Exception("Previous output error: ${e.message}"))
        }
    }

    // ── FETCH_RSS_MULTI ──

    private suspend fun executeMultiFeedRss(action: WorkflowAction): Result<String> {
        return try {
            if (action.extraConfig.isBlank()) {
                return Result.failure(Exception("No multi-feed configuration provided"))
            }

            val config = json.decodeFromString<MultiFeedRssConfig>(action.extraConfig)
            if (config.feedUrls.isEmpty()) {
                return Result.failure(Exception("No feed URLs configured"))
            }

            val validUrls = config.feedUrls.filter { it.isNotBlank() }
            if (validUrls.isEmpty()) {
                return Result.failure(Exception("All feed URLs are empty"))
            }

            // Fetch all feeds using existing RssParser (which supports multiple URLs)
            val rawText = rssParser.fetchAndParse(validUrls)
            if (rawText.isBlank()) {
                return Result.failure(Exception("All ${validUrls.size} feed(s) returned no content"))
            }

            // Parse into blocks for filtering and deduplication
            val blocks = rawText.split("\n\n").filter { it.isNotBlank() }

            // Deduplicate by title if enabled
            val deduped = if (config.deduplicateByTitle) {
                val seen = mutableSetOf<String>()
                blocks.filter { block ->
                    val titleLine = block.lines().firstOrNull { it.startsWith("Title:") }
                    val title = titleLine?.removePrefix("Title:")?.trim()?.lowercase() ?: block.take(50).lowercase()
                    seen.add(title)
                }
            } else blocks

            // Apply keyword filter
            val filtered = if (config.keywordFilter.isNotBlank()) {
                val keywords = config.keywordFilter.split(",").map { it.trim().lowercase() }
                val matches = deduped.filter { block ->
                    keywords.any { kw -> block.lowercase().contains(kw) }
                }
                matches.ifEmpty { deduped } // fallback to all if no matches
            } else deduped

            // Apply max items limit
            val limited = filtered.take(config.maxItems)

            val result = limited.joinToString("\n\n")

            Result.success(result.take(MAX_CHARS_PER_ACTION))
        } catch (e: Exception) {
            Result.failure(Exception("Multi-feed RSS error: ${e.message}"))
        }
    }

    // ── Utilities ──

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;"), "'")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildCombinedInput(
        results: List<ActionResult>,
        template: WorkflowTemplate
    ): String {
        val builder = StringBuilder()
        for ((i, ar) in results.withIndex()) {
            val label = ar.action.label.ifBlank { "${ar.action.type.displayName} #${i + 1}" }
            builder.appendLine("=== Source ${i + 1}: $label ===")
            if (ar.action.instruction.isNotBlank()) {
                builder.appendLine("[Per-source instruction: ${ar.action.instruction}]")
            }
            builder.appendLine(ar.text)
            builder.appendLine()
        }
        return builder.toString().take(MAX_TOTAL_CHARS)
    }

    private fun buildSystemPrompt(template: WorkflowTemplate): String {
        return buildString {
            append("You are a professional AI assistant executing a custom workflow. ")

            when (template.outputConfig.outputType) {
                WorkflowOutputType.BRIEFING -> append("Generate a structured, scannable briefing from the provided sources. ")
                WorkflowOutputType.SOCIAL_POST -> {
                    append("Generate social media posts from the provided sources. ")
                    if (template.outputConfig.socialPlatforms.isNotEmpty()) {
                        append("Target platforms: ${template.outputConfig.socialPlatforms.joinToString { it.displayName }}. Match tone for each platform. ")
                    }
                }
                WorkflowOutputType.BOTH -> {
                    append("Generate both a structured briefing AND social media posts. ")
                    if (template.outputConfig.socialPlatforms.isNotEmpty()) {
                        append("Target platforms for social: ${template.outputConfig.socialPlatforms.joinToString { it.displayName }}. ")
                    }
                }
                WorkflowOutputType.CUSTOM -> {
                    if (template.outputConfig.customInstruction.isNotBlank()) {
                        append("Custom output instructions: ${template.outputConfig.customInstruction}. ")
                    }
                }
            }

            if (template.globalInstruction.isNotBlank()) {
                append("\n\nAdditional instructions: ${template.globalInstruction}")
            }

            append("\n\nProcess the following source data:\n")
        }
    }

    private data class ActionResult(
        val action: WorkflowAction,
        val text: String
    )
}

package com.automatist.app.domain.engine

import com.automatist.app.data.network.RssParser
import com.automatist.app.domain.models.*
import com.automatist.app.domain.providers.ArticleTransformProvider
import com.automatist.app.domain.repositories.WorkflowRepository
import android.util.Log
import com.automatist.app.platform.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val workflowRepository: WorkflowRepository,
    private val secureStorage: SecureStorage
) {

    companion object {
        private const val TAG = "WorkflowEngine"
        private const val MAX_CHARS_PER_ACTION = 4000
        private const val MAX_TOTAL_CHARS = 16000
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun execute(template: WorkflowTemplate): Flow<ExecutionState> = flow<ExecutionState> {
        Log.i(TAG, "execute() run-start template='${template.name}' id=${template.id} " +
            "thread=${Thread.currentThread().name}")
        val startTime = System.currentTimeMillis()

        // Stage-boundary reset at run start. Clears any transient on-device runtime
        // state left behind by a prior run (e.g. MediaPipe diagnostic timings,
        // partially-consumed session metadata). Cloud providers are stateless so
        // the router short-circuits this for them — see TransformProviderRouter.
        transformProvider.resetForNewStage("run-start:${template.id}")

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
        val actionResultMap = mutableMapOf<String, String>() // action ID → output text

        // Accumulate usage from per-action preprocessing passes so totals are honest.
        var preprocessPromptTokens = 0
        var preprocessCompletionTokens = 0
        var preprocessIsEstimated = false
        var preprocessWasTruncated: Boolean? = null

        for ((index, action) in enabledActions.withIndex()) {
            val label = action.label.ifBlank { "${action.type.displayName} #${index + 1}" }

            emit(ExecutionState.ActionStarted(index, enabledActions.size, label, action.type.name))

            val result = executeAction(action, actionResultMap, template)

            if (result.isSuccess) {
                val rawText = result.getOrThrow()

                // Surface the raw source text to the run page, but only when we
                // know it will differ from the final per-action result (i.e.
                // when there's an instruction that will preprocess it). For
                // no-instruction actions, the "Show result" expander on
                // ActionCompleted is enough — no need to duplicate the same text.
                if (action.instruction.isNotBlank()) {
                    emit(
                        ExecutionState.ActionSourceFetched(
                            actionIndex = index,
                            totalActions = enabledActions.size,
                            actionLabel = label,
                            rawSourceText = rawText
                        )
                    )
                }

                // Pre-source instruction: if the action has a non-blank instruction,
                // run an AI preprocessing pass on the source content BEFORE the main
                // workflow prompt stage. The prepared result replaces the raw text
                // for downstream combination.
                val (preparedText, instructionApplied) = if (action.instruction.isNotBlank()) {
                    // Stage-boundary reset before this action's preprocessing pass so
                    // the local/offline runtime starts with clean transient state for
                    // this specific action. Cheap no-op for cloud providers.
                    Log.i(TAG, "action-preprocess-start index=$index label='$label'")
                    transformProvider.resetForNewStage("action-preprocess:$index:${action.id}")
                    // Surface the upcoming AI preprocessing pass as its own visible
                    // stage so the user can distinguish source-fetching from the
                    // action's prompt actually running. Only emitted when there IS
                    // an instruction to run — no noise for plain fetch actions.
                    emit(
                        ExecutionState.ActionPromptStarted(
                            actionIndex = index,
                            totalActions = enabledActions.size,
                            actionLabel = label,
                            instructionPreview = action.instruction.trim()
                                .take(120)
                                .let { if (action.instruction.length > 120) "$it…" else it },
                            instructionText = action.instruction
                        )
                    )
                    val prep = preprocessActionWithInstruction(action, rawText, template)
                    if (prep != null) {
                        preprocessPromptTokens += prep.promptTokens ?: 0
                        preprocessCompletionTokens += prep.completionTokens ?: 0
                        if (prep.isUsageEstimated) preprocessIsEstimated = true
                        prep.wasTruncated?.let { t ->
                            preprocessWasTruncated = (preprocessWasTruncated ?: false) || t
                        }
                        prep.outputText to true
                    } else {
                        // Preprocessing failed — fall back to raw content so the run
                        // still produces an output. Error is logged; user-facing
                        // surfacing stays minimal because the action itself succeeded.
                        rawText to false
                    }
                } else {
                    rawText to false
                }

                actionResults.add(ActionResult(action, preparedText, instructionApplied))
                actionResultMap[action.id] = preparedText
                emit(
                    ExecutionState.ActionCompleted(
                        index, enabledActions.size, label,
                        preparedText.take(100) + if (preparedText.length > 100) "..." else "",
                        fullResultText = preparedText
                    )
                )
            } else {
                val ex = result.exceptionOrNull()
                val rawDetail = when (ex) {
                    is DiagnosticException -> ex.rawDetail
                    else -> ex?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: ""
                }
                emit(
                    ExecutionState.ActionFailed(
                        index, enabledActions.size, label,
                        ex?.message ?: "Unknown error",
                        rawError = ErrorRedactor.redact(rawDetail)
                    )
                )
                // Continue with other actions — don't abort
            }
        }

        if (actionResults.isEmpty()) {
            Log.i(TAG, "run-end status=failed stage=actions template='${template.name}'")
            transformProvider.resetForNewStage("run-end-failed-actions:${template.id}")
            emit(ExecutionState.Failed("All actions failed. No data to process.", "actions"))
            return@flow
        }

        // Combine action outputs (per-action compaction applied to each action's content).
        val (combinedInput, originalLen) = buildCombinedInput(actionResults, template)
        // Derive a human-readable compaction label from the per-action settings for
        // the ProcessingStarted event. If all actions use the same mode, show it;
        // otherwise show "Mixed". Pure NONE across the board shows as blank.
        val compactionLabel = run {
            val modes = actionResults.map { it.action.compaction }.toSet()
            when {
                modes.isEmpty() -> ""
                modes.size == 1 && modes.first() == InputCompactionMode.NONE -> ""
                modes.size == 1 -> modes.first().displayName
                else -> "Mixed"
            }
        }
        emit(ExecutionState.ProcessingStarted(
            combinedInputLength = combinedInput.length,
            originalInputLength = originalLen,
            compactionMode = compactionLabel
        ))

        // Build prompt
        val systemPrompt = buildSystemPrompt(template)

        // Resolve profile for final output generation:
        // 1. Output-level override (outputProfileId)
        // 2. Workflow-level default (defaultProfileId)
        // 3. App default (null = router handles it)
        val outputProfileId = template.outputConfig.outputProfileId.ifBlank {
            template.defaultProfileId.ifBlank { null }
        }

        // Resolve profile details for diagnostics
        val resolvedProfile = if (outputProfileId != null) {
            workflowRepository.getProfileById(outputProfileId)
        } else {
            workflowRepository.getDefaultProfile()
        }
        val profileName = resolvedProfile?.name ?: "App default"
        val providerName = resolvedProfile?.providerType?.displayName ?: "Fallback"
        val modelId = resolvedProfile?.modelId ?: ""

        // Stage-boundary reset before final generation. Ensures the local/offline
        // runtime enters the main output pass with clean transient state — no
        // leftover timings / metadata from the preceding per-action preprocessing
        // calls. Cheap no-op for cloud providers via the router.
        Log.i(TAG, "final-generation-start profile='$profileName' provider='$providerName' model='$modelId'")
        transformProvider.resetForNewStage("final-generation:${template.id}")

        emit(ExecutionState.GeneratingOutput(
            profileName = profileName,
            providerName = providerName,
            modelId = modelId,
            // Expose the full final prompt (system instruction + user content)
            // so the run page can surface it through a "Show final prompt"
            // expander. The provider layer remains the authority on what
            // actually goes to the model — this is read-only UI metadata.
            systemPrompt = systemPrompt,
            userContent = combinedInput
        ))

        // Detect social output mode
        val isSocialMode = isSocialOutputMode(template)
        val numVersions = template.outputConfig.numberOfOutputs.coerceIn(1, 10)

        // Generate N versions from the same frozen input
        val input = ArticleInput(
            text = combinedInput,
            systemPromptOverride = systemPrompt,
            profileId = outputProfileId
        )

        val versions = mutableListOf<OutputVersion>()
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var lastProviderType: ProviderType = ProviderType.FAKE
        // Honest-usage metadata aggregated across versions. Any version being
        // estimated / truncated flips the aggregate flag to true; chars are summed
        // across versions; ceiling comes from the last provider that reported one.
        var aggregateIsEstimated = false
        var aggregateWasTruncated: Boolean? = null
        var aggregateInputChars: Int? = null
        var aggregateOutputChars: Int? = null
        var aggregateContextCeiling: Int? = null

        for (v in 1..numVersions) {
            val transformResult = transformProvider.transform(input, TransformType.CUSTOM_WORKFLOW)

            if (transformResult.isSuccess) {
                val result = transformResult.getOrThrow()
                versions.add(OutputVersion(
                    version = v,
                    outputText = result.outputText,
                    isSocialOutput = isSocialMode
                ))
                totalPromptTokens += result.promptTokens ?: 0
                totalCompletionTokens += result.completionTokens ?: 0
                lastProviderType = result.providerType
                // Aggregate the honest-usage fields. Null-safe additions: if a
                // provider doesn't report chars (cloud path), we leave the
                // aggregate null rather than lying about zero.
                if (result.isUsageEstimated) aggregateIsEstimated = true
                result.wasTruncated?.let { truncated ->
                    aggregateWasTruncated = (aggregateWasTruncated ?: false) || truncated
                }
                result.inputChars?.let { aggregateInputChars = (aggregateInputChars ?: 0) + it }
                result.outputChars?.let { aggregateOutputChars = (aggregateOutputChars ?: 0) + it }
                result.contextCeilingTokens?.let { aggregateContextCeiling = it }
            } else {
                val error = transformResult.exceptionOrNull()!!
                val rawDetail = when (error) {
                    is DiagnosticException -> error.rawDetail
                    else -> "${error.javaClass.simpleName}: ${error.message}"
                }
                val rawWithContext = buildString {
                    append(ErrorRedactor.redact(rawDetail))
                    if (!rawDetail.contains("Profile:")) {
                        appendLine()
                        appendLine("Profile: $profileName")
                        appendLine("Provider: $providerName")
                        appendLine("Model: $modelId")
                    }
                }
                // If first version fails, fail the whole run
                if (versions.isEmpty()) {
                    Log.i(TAG, "run-end status=failed stage=processing template='${template.name}' " +
                        "profile='$profileName' provider='$providerName' model='$modelId'")
                    transformProvider.resetForNewStage("run-end-failed-processing:${template.id}")
                    // Build a resume snapshot: all actions succeeded and we have
                    // the frozen combined input computed. A later retry can skip
                    // the whole action chain and rerun only the final generation
                    // if the workflow fingerprint still matches.
                    val snapshot = ResumeSnapshot(
                        fingerprint = computeWorkflowFingerprint(template),
                        reachedStage = ResumeSnapshot.STAGE_PROCESSING,
                        synthesisInput = combinedInput,
                        actionOutputs = actionResults.map { ar ->
                            ResumedActionOutput(
                                actionId = ar.action.id,
                                preparedText = ar.text,
                                instructionApplied = ar.instructionApplied
                            )
                        }
                    )
                    emit(ExecutionState.Failed(
                        error.message ?: "AI processing failed (profile: $profileName, provider: $providerName, model: $modelId)",
                        "processing",
                        rawError = rawWithContext.trim(),
                        resumeSnapshot = snapshot
                    ))
                    return@flow
                }
                // If later versions fail, stop generating but keep what we have
                break
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        // Fold preprocessing-pass tokens into final totals so usage is honest.
        val combinedPromptTokens = totalPromptTokens + preprocessPromptTokens
        val combinedCompletionTokens = totalCompletionTokens + preprocessCompletionTokens
        if (preprocessIsEstimated) aggregateIsEstimated = true
        preprocessWasTruncated?.let { t ->
            aggregateWasTruncated = (aggregateWasTruncated ?: false) || t
        }
        val tokenUsage = TokenUsage(
            promptTokens = combinedPromptTokens,
            completionTokens = combinedCompletionTokens,
            totalTokens = combinedPromptTokens + combinedCompletionTokens,
            // Providers are now the source of truth for whether their counts are
            // estimated (via TransformResult.isUsageEstimated). We keep the legacy
            // "FAKE means estimated" fallback in case a future provider forgets to
            // set the flag, but the flag itself is authoritative when present.
            isEstimated = aggregateIsEstimated || lastProviderType == ProviderType.FAKE,
            inputChars = aggregateInputChars,
            outputChars = aggregateOutputChars,
            contextCeilingTokens = aggregateContextCeiling,
            wasTruncated = aggregateWasTruncated
        )
        // Stage-boundary reset at run end. Clears any transient state before the
        // next run begins so retries / consecutive runs don't inherit diagnostics
        // from this one. Cheap no-op for cloud providers.
        Log.i(TAG, "run-end status=completed template='${template.name}' duration=${durationMs}ms")
        transformProvider.resetForNewStage("run-end-completed:${template.id}")

        // outputText = first version for backward compatibility
        emit(
            ExecutionState.Completed(
                outputText = versions.first().outputText,
                providerType = lastProviderType,
                tokenUsage = tokenUsage,
                durationMs = durationMs,
                profileName = profileName,
                modelId = modelId,
                isSocialOutput = isSocialMode,
                versions = versions,
                synthesisInput = combinedInput
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Resume a failed workflow run from the first failed step using the supplied
     * [snapshot]. For v1 this only supports the PROCESSING reached stage — the
     * common case where all actions already succeeded and only the final AI
     * generation failed. The action chain is skipped entirely; the frozen
     * combined input captured at the time of failure is replayed through final
     * generation. This saves every expensive RSS fetch, URL fetch, weather
     * call, and per-action preprocessing AI pass from being rerun.
     *
     * The caller is responsible for validating `snapshot.canResumeFor(template)`
     * before invoking this — if the fingerprint no longer matches or the
     * snapshot is empty, the caller must fall back to [execute].
     *
     * The emitted stage events mirror [execute] so the run page UI treats the
     * resumed run like any other: a concise "Reusing N prior step result(s)"
     * row sits where the action stages would normally appear.
     */
    fun resume(
        template: WorkflowTemplate,
        snapshot: ResumeSnapshot
    ): Flow<ExecutionState> = flow<ExecutionState> {
        Log.i(TAG, "resume() run-start template='${template.name}' id=${template.id} " +
            "reachedStage=${snapshot.reachedStage} reusedActions=${snapshot.actionOutputs.size} " +
            "thread=${Thread.currentThread().name}")
        val startTime = System.currentTimeMillis()

        transformProvider.resetForNewStage("resume-start:${template.id}")

        emit(ExecutionState.Preparing)

        val enabledActions = template.actions.filter { it.isEnabled }.sortedBy { it.order }
        if (enabledActions.isEmpty()) {
            // Shouldn't happen for a resumable run, but stay safe.
            emit(ExecutionState.Failed("No enabled actions in workflow.", "validation"))
            return@flow
        }
        emit(ExecutionState.ValidatingInputs(enabledActions.size))

        // Render one concise stage row summarising what we reused from the
        // prior run so the user sees why the action chain is not re-running.
        val reusedLabel = "Reusing ${snapshot.actionOutputs.size} prior step result(s)"
        emit(ExecutionState.ActionStarted(
            actionIndex = 0,
            totalActions = 1,
            actionLabel = reusedLabel,
            actionType = "RESUME"
        ))
        emit(ExecutionState.ActionCompleted(
            actionIndex = 0,
            totalActions = 1,
            actionLabel = reusedLabel,
            resultPreview = "Resumed from prior failed run",
            fullResultText = ""
        ))

        // Use the frozen combined input directly. No action work happens.
        val combinedInput = snapshot.synthesisInput
        emit(ExecutionState.ProcessingStarted(
            combinedInputLength = combinedInput.length,
            originalInputLength = combinedInput.length,
            compactionMode = ""
        ))

        // Resolve profile for final output generation (same rules as execute()).
        val outputProfileId = template.outputConfig.outputProfileId.ifBlank {
            template.defaultProfileId.ifBlank { null }
        }
        val resolvedProfile = if (outputProfileId != null) {
            workflowRepository.getProfileById(outputProfileId)
        } else {
            workflowRepository.getDefaultProfile()
        }
        val profileName = resolvedProfile?.name ?: "App default"
        val providerName = resolvedProfile?.providerType?.displayName ?: "Fallback"
        val modelId = resolvedProfile?.modelId ?: ""

        transformProvider.resetForNewStage("resume-final-generation:${template.id}")

        val systemPrompt = buildSystemPrompt(template)
        emit(ExecutionState.GeneratingOutput(
            profileName = profileName,
            providerName = providerName,
            modelId = modelId,
            systemPrompt = systemPrompt,
            userContent = combinedInput
        ))

        val isSocialMode = isSocialOutputMode(template)
        val numVersions = template.outputConfig.numberOfOutputs.coerceIn(1, 10)

        val input = ArticleInput(
            text = combinedInput,
            systemPromptOverride = systemPrompt,
            profileId = outputProfileId
        )

        val versions = mutableListOf<OutputVersion>()
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var lastProviderType: ProviderType = ProviderType.FAKE
        var aggregateIsEstimated = false
        var aggregateWasTruncated: Boolean? = null
        var aggregateInputChars: Int? = null
        var aggregateOutputChars: Int? = null
        var aggregateContextCeiling: Int? = null

        for (v in 1..numVersions) {
            val transformResult = transformProvider.transform(input, TransformType.CUSTOM_WORKFLOW)
            if (transformResult.isSuccess) {
                val result = transformResult.getOrThrow()
                versions.add(OutputVersion(
                    version = v,
                    outputText = result.outputText,
                    isSocialOutput = isSocialMode
                ))
                totalPromptTokens += result.promptTokens ?: 0
                totalCompletionTokens += result.completionTokens ?: 0
                lastProviderType = result.providerType
                if (result.isUsageEstimated) aggregateIsEstimated = true
                result.wasTruncated?.let { truncated ->
                    aggregateWasTruncated = (aggregateWasTruncated ?: false) || truncated
                }
                result.inputChars?.let { aggregateInputChars = (aggregateInputChars ?: 0) + it }
                result.outputChars?.let { aggregateOutputChars = (aggregateOutputChars ?: 0) + it }
                result.contextCeilingTokens?.let { aggregateContextCeiling = it }
            } else {
                val error = transformResult.exceptionOrNull()!!
                val rawDetail = when (error) {
                    is DiagnosticException -> error.rawDetail
                    else -> "${error.javaClass.simpleName}: ${error.message}"
                }
                val rawWithContext = buildString {
                    append(ErrorRedactor.redact(rawDetail))
                    if (!rawDetail.contains("Profile:")) {
                        appendLine()
                        appendLine("Profile: $profileName")
                        appendLine("Provider: $providerName")
                        appendLine("Model: $modelId")
                    }
                }
                if (versions.isEmpty()) {
                    Log.i(TAG, "resume run-end status=failed stage=processing template='${template.name}'")
                    transformProvider.resetForNewStage("resume-end-failed-processing:${template.id}")
                    // Re-emit a fresh snapshot so a subsequent retry can resume
                    // again (the combined input is still valid — only the final
                    // generation kept failing).
                    emit(ExecutionState.Failed(
                        error.message ?: "AI processing failed (profile: $profileName, provider: $providerName, model: $modelId)",
                        "processing",
                        rawError = rawWithContext.trim(),
                        resumeSnapshot = snapshot.copy(
                            fingerprint = computeWorkflowFingerprint(template)
                        )
                    ))
                    return@flow
                }
                break
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        val tokenUsage = TokenUsage(
            promptTokens = totalPromptTokens,
            completionTokens = totalCompletionTokens,
            totalTokens = totalPromptTokens + totalCompletionTokens,
            isEstimated = aggregateIsEstimated || lastProviderType == ProviderType.FAKE,
            inputChars = aggregateInputChars,
            outputChars = aggregateOutputChars,
            contextCeilingTokens = aggregateContextCeiling,
            wasTruncated = aggregateWasTruncated
        )
        Log.i(TAG, "resume run-end status=completed template='${template.name}' duration=${durationMs}ms")
        transformProvider.resetForNewStage("resume-end-completed:${template.id}")

        emit(
            ExecutionState.Completed(
                outputText = versions.first().outputText,
                providerType = lastProviderType,
                tokenUsage = tokenUsage,
                durationMs = durationMs,
                profileName = profileName,
                modelId = modelId,
                isSocialOutput = isSocialMode,
                versions = versions,
                synthesisInput = combinedInput
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Regenerate a single new version from frozen synthesis input.
     * Does NOT refetch actions — uses the stored combinedInput directly.
     */
    suspend fun regenerate(
        frozenInput: String,
        template: WorkflowTemplate,
        nextVersion: Int
    ): Result<OutputVersion> = withContext(Dispatchers.IO) {
        // Explicit dispatcher switch: callers dispatch this from viewModelScope, which
        // defaults to Main.immediate. Without this wrapper the transform() call (and
        // any inference) would run on Main for the regenerate path, which for local
        // MediaPipe inference means loading a 529 MB model + running generation on
        // the UI thread. Always do inference off-Main.
        Log.i(TAG, "regenerate() start template='${template.name}' id=${template.id} " +
            "nextVersion=$nextVersion thread=${Thread.currentThread().name}")
        // Regenerate is a fresh final-generation pass on a frozen input. Reset
        // the local/offline transient state so the retry starts clean and any
        // phase timings surfaced in error messages reflect this attempt only.
        transformProvider.resetForNewStage("regenerate:${template.id}:v$nextVersion")
        val systemPrompt = buildSystemPrompt(template)
        val isSocialMode = isSocialOutputMode(template)

        val outputProfileId = template.outputConfig.outputProfileId.ifBlank {
            template.defaultProfileId.ifBlank { null }
        }

        val input = ArticleInput(
            text = frozenInput,
            systemPromptOverride = systemPrompt,
            profileId = outputProfileId
        )

        val transformResult = transformProvider.transform(input, TransformType.CUSTOM_WORKFLOW)

        // Reset again after regenerate so the next stage/run doesn't inherit
        // this regenerate's transient state. Runs on both success and failure.
        transformProvider.resetForNewStage("regenerate-end:${template.id}:v$nextVersion")

        transformResult.map { result ->
            OutputVersion(
                version = nextVersion,
                outputText = result.outputText,
                isSocialOutput = isSocialMode
            )
        }
    }

    // ── Action Dispatch ──

    private suspend fun executeAction(
        action: WorkflowAction,
        actionResultMap: Map<String, String>,
        template: WorkflowTemplate
    ): Result<String> {
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

            WorkflowActionType.FETCH_WEATHER -> executeWeather(action)

            WorkflowActionType.FETCH_ROUTE_TIME -> executeRouteTime(action)

            WorkflowActionType.USE_ACTION_OUTPUT -> executeActionOutput(action, actionResultMap)

            WorkflowActionType.AI_PROMPT -> executeAiPrompt(action, actionResultMap, template)
        }
    }

    // ── FETCH_URL ──

    private fun fetchUrl(url: String): Result<String> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Automatist/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val rawBody = try { response.body?.string()?.take(1000) } catch (_: Exception) { null }
                val rawDetail = buildString {
                    appendLine("HTTP ${response.code} ${response.message}")
                    appendLine("URL: $url")
                    if (!rawBody.isNullOrBlank()) { appendLine("Response: $rawBody") }
                }
                return Result.failure(DiagnosticException(
                    message = "HTTP ${response.code}: ${response.message}",
                    rawDetail = rawDetail,
                    httpStatus = response.code
                ))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val cleaned = cleanHtml(body).take(MAX_CHARS_PER_ACTION)
            Result.success(cleaned)
        } catch (e: Exception) {
            Result.failure(DiagnosticException(
                message = "Failed to fetch URL: ${e.message}",
                rawDetail = "Exception: ${e.javaClass.simpleName}: ${e.message}\nURL: $url",
                cause = e
            ))
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
            Result.failure(DiagnosticException(
                message = "RSS feed error: ${e.message}",
                rawDetail = "Exception: ${e.javaClass.simpleName}: ${e.message}\nFeed URL: ${action.sourceData}",
                cause = e
            ))
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
                .header("User-Agent", "Automatist/1.0")

            // Add custom headers (filter out sensitive ones)
            config.headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val rawBody = try { response.body?.string()?.take(1000) } catch (_: Exception) { null }
                val rawDetail = buildString {
                    appendLine("HTTP ${response.code} ${response.message}")
                    appendLine("URL: $baseUrl")
                    if (!rawBody.isNullOrBlank()) { appendLine("Response: $rawBody") }
                }
                return Result.failure(DiagnosticException(
                    message = "API returned HTTP ${response.code}: ${response.message}",
                    rawDetail = rawDetail,
                    httpStatus = response.code
                ))
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
            Result.failure(DiagnosticException(
                message = "API GET error: ${e.message}",
                rawDetail = "Exception: ${e.javaClass.simpleName}: ${e.message}\nURL: ${action.sourceData}",
                cause = e
            ))
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

    // ── FETCH_WEATHER ──

    private suspend fun executeWeather(action: WorkflowAction): Result<String> {
        return try {
            if (action.extraConfig.isBlank()) {
                return Result.failure(Exception("No weather configuration provided"))
            }

            val config = json.decodeFromString<WeatherConfig>(action.extraConfig)
            if (config.location.isBlank()) {
                return Result.failure(Exception("Weather location is required"))
            }

            val apiKey = secureStorage.getServiceKey(WeatherService.SERVICE_KEY)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(Exception("OpenWeatherMap API key not configured. Add it in Settings."))
            }

            val units = when (config.units) {
                WeatherUnits.METRIC -> "metric"
                WeatherUnits.IMPERIAL -> "imperial"
            }

            // Build current weather URL
            val urlBuilder = "${WeatherService.BASE_URL}/weather".toHttpUrlOrNull()?.newBuilder()
                ?: return Result.failure(Exception("Invalid weather API URL"))

            urlBuilder.addQueryParameter("q", config.location)
            urlBuilder.addQueryParameter("appid", apiKey)
            urlBuilder.addQueryParameter("units", units)

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "Automatist/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val rawBody = try { response.body?.string() } catch (_: Exception) { null }
                val hint = when (response.code) {
                    401 -> "Your OpenWeatherMap API key may be invalid or expired. Check it in Settings → Service Keys."
                    403 -> "Your OpenWeatherMap API key does not have access. Check your plan or regenerate the key."
                    404 -> "Location '${config.location}' was not found by OpenWeatherMap. Try a different city name or use 'City,CountryCode' format (e.g. 'Berlin,DE')."
                    429 -> "OpenWeatherMap rate limit exceeded. Wait a moment and try again."
                    else -> "Check your OpenWeatherMap API key and network connection."
                }
                val rawDetail = buildString {
                    appendLine("HTTP ${response.code} ${response.message}")
                    appendLine("Service: OpenWeatherMap")
                    appendLine("Location: ${config.location}")
                    if (!rawBody.isNullOrBlank()) { appendLine("Response: $rawBody") }
                }
                return Result.failure(DiagnosticException(
                    message = "Weather API returned HTTP ${response.code}. $hint",
                    rawDetail = rawDetail,
                    httpStatus = response.code
                ))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty weather response"))

            // Parse JSON and extract key weather fields
            val weatherData = try {
                val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(body)
                val main = jsonObj.jsonObject["main"]?.jsonObject
                val weather = jsonObj.jsonObject["weather"]?.jsonArray?.firstOrNull()?.jsonObject
                val wind = jsonObj.jsonObject["wind"]?.jsonObject
                val name = jsonObj.jsonObject["name"]?.jsonPrimitive?.content ?: config.location

                val unitLabel = if (config.units == WeatherUnits.METRIC) "C" else "F"
                val speedLabel = if (config.units == WeatherUnits.METRIC) "m/s" else "mph"

                buildString {
                    appendLine("Weather for $name:")
                    main?.let { m ->
                        m["temp"]?.jsonPrimitive?.content?.let { appendLine("  Temperature: $it°$unitLabel") }
                        m["feels_like"]?.jsonPrimitive?.content?.let { appendLine("  Feels like: $it°$unitLabel") }
                        m["humidity"]?.jsonPrimitive?.content?.let { appendLine("  Humidity: $it%") }
                    }
                    weather?.let { w ->
                        w["description"]?.jsonPrimitive?.content?.let { appendLine("  Conditions: $it") }
                    }
                    wind?.let { w ->
                        w["speed"]?.jsonPrimitive?.content?.let { appendLine("  Wind: $it $speedLabel") }
                    }
                }
            } catch (_: Exception) {
                // Fallback: return raw JSON for AI to parse
                body.take(MAX_CHARS_PER_ACTION)
            }

            Result.success(weatherData.take(MAX_CHARS_PER_ACTION))
        } catch (e: Exception) {
            Result.failure(Exception("Weather error: ${e.message}"))
        }
    }

    // ── FETCH_ROUTE_TIME ──

    private suspend fun executeRouteTime(action: WorkflowAction): Result<String> {
        return try {
            if (action.extraConfig.isBlank()) {
                return Result.failure(Exception("No route configuration provided"))
            }

            val config = json.decodeFromString<RouteConfig>(action.extraConfig)
            if (config.origin.isBlank()) return Result.failure(Exception("Origin is required"))
            if (config.destination.isBlank()) return Result.failure(Exception("Destination is required"))

            val apiKey = secureStorage.getServiceKey(RouteService.SERVICE_KEY)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(Exception("OpenRouteService API key not configured. Add it in Settings."))
            }

            // Use ORS directions API — geocode-capable via address strings
            val profile = when (config.travelMode) {
                TravelMode.DRIVING -> "driving-car"
                TravelMode.TRANSIT -> "driving-car" // ORS free tier: no transit; fallback to driving
                TravelMode.WALKING -> "foot-walking"
                TravelMode.BICYCLING -> "cycling-regular"
            }

            // ORS geocode search for origin
            val originCoords = geocodeLocation(config.origin, apiKey)
                ?: return Result.failure(Exception(
                    "Could not geocode origin '${config.origin}'. " +
                    "Try a more complete address with city and country (e.g. '123 Main St, Berlin, Germany') or use coordinates (e.g. '52.52,13.405')."
                ))
            val destCoords = geocodeLocation(config.destination, apiKey)
                ?: return Result.failure(Exception(
                    "Could not geocode destination '${config.destination}'. " +
                    "Try a more complete address with city and country (e.g. '456 Elm St, Berlin, Germany') or use coordinates (e.g. '52.52,13.405')."
                ))

            // ORS directions
            val dirUrl = "${RouteService.BASE_URL}/v2/directions/$profile".toHttpUrlOrNull()?.newBuilder()
                ?: return Result.failure(Exception("Invalid route API URL"))
            dirUrl.addQueryParameter("api_key", apiKey)
            dirUrl.addQueryParameter("start", "${originCoords.first},${originCoords.second}")
            dirUrl.addQueryParameter("end", "${destCoords.first},${destCoords.second}")

            val dirRequest = Request.Builder()
                .url(dirUrl.build())
                .header("User-Agent", "Automatist/1.0")
                .build()

            val dirResponse = httpClient.newCall(dirRequest).execute()
            if (!dirResponse.isSuccessful) {
                val rawBody = try { dirResponse.body?.string() } catch (_: Exception) { null }
                val hint = when (dirResponse.code) {
                    401, 403 -> "Your OpenRouteService API key may be invalid. Check it in Settings → Service Keys."
                    404 -> "No route found between the given locations. Check the addresses."
                    429 -> "OpenRouteService rate limit exceeded. Wait a moment and try again."
                    else -> "Check your OpenRouteService API key and network connection."
                }
                val rawDetail = buildString {
                    appendLine("HTTP ${dirResponse.code} ${dirResponse.message}")
                    appendLine("Service: OpenRouteService")
                    appendLine("Origin: ${config.origin}")
                    appendLine("Destination: ${config.destination}")
                    if (!rawBody.isNullOrBlank()) { appendLine("Response: $rawBody") }
                }
                return Result.failure(DiagnosticException(
                    message = "Route API returned HTTP ${dirResponse.code}. $hint",
                    rawDetail = rawDetail,
                    httpStatus = dirResponse.code
                ))
            }

            val dirBody = dirResponse.body?.string() ?: return Result.failure(Exception("Empty route response"))

            val routeData = try {
                val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(dirBody)
                val features = jsonObj.jsonObject["features"]?.jsonArray
                val segment = features?.firstOrNull()?.jsonObject
                    ?.get("properties")?.jsonObject
                    ?.get("segments")?.jsonArray?.firstOrNull()?.jsonObject

                buildString {
                    appendLine("Route: ${config.origin} → ${config.destination}")
                    appendLine("  Mode: ${config.travelMode.displayName}")
                    segment?.let { s ->
                        s["distance"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let { d ->
                            appendLine("  Distance: ${"%.1f".format(d / 1000)} km")
                        }
                        s["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let { d ->
                            val mins = (d / 60).toInt()
                            if (mins >= 60) appendLine("  Duration: ${mins / 60}h ${mins % 60}m")
                            else appendLine("  Duration: ${mins} min")
                        }
                    }
                }
            } catch (_: Exception) {
                dirBody.take(MAX_CHARS_PER_ACTION)
            }

            Result.success(routeData.take(MAX_CHARS_PER_ACTION))
        } catch (e: Exception) {
            Result.failure(Exception("Route error: ${e.message}"))
        }
    }

    private fun geocodeLocation(query: String, apiKey: String): Pair<Double, Double>? {
        return try {
            // Check if already lat,lon format
            val parts = query.split(",").map { it.trim() }
            if (parts.size == 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    return lon to lat // ORS uses lon,lat order
                }
            }

            // Normalize address: trim excess whitespace, ensure non-empty
            val normalized = query.trim().replace(Regex("\\s+"), " ")
            if (normalized.isBlank()) return null

            // Geocode via ORS
            val url = "${RouteService.BASE_URL}/geocode/search".toHttpUrlOrNull()?.newBuilder()
                ?: return null
            url.addQueryParameter("api_key", apiKey)
            url.addQueryParameter("text", normalized)
            url.addQueryParameter("size", "1")

            val request = Request.Builder().url(url.build()).header("User-Agent", "Automatist/1.0").build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(body)
            val coords = jsonObj.jsonObject["features"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("geometry")?.jsonObject?.get("coordinates")?.jsonArray
            if (coords != null && coords.size >= 2) {
                val lon = coords[0].jsonPrimitive.double
                val lat = coords[1].jsonPrimitive.double
                return lon to lat
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // ── USE_ACTION_OUTPUT ──

    private fun executeActionOutput(
        action: WorkflowAction,
        actionResultMap: Map<String, String>
    ): Result<String> {
        return try {
            if (action.extraConfig.isBlank()) {
                return Result.failure(Exception("No source action configured"))
            }

            val config = json.decodeFromString<ActionOutputConfig>(action.extraConfig)
            if (config.sourceActionId.isBlank()) {
                return Result.failure(Exception("No source action selected"))
            }
            if (config.sourceActionId == action.id) {
                return Result.failure(Exception("Action cannot reference itself"))
            }

            val sourceText = actionResultMap[config.sourceActionId]
                ?: return Result.failure(Exception(
                    "Referenced action \"${config.sourceActionLabel.ifBlank { config.sourceActionId }}\" has no result. " +
                    "It may have failed or been disabled."
                ))

            if (sourceText.isBlank()) {
                return Result.failure(Exception(
                    "Referenced action \"${config.sourceActionLabel.ifBlank { "source" }}\" returned empty output."
                ))
            }

            Result.success(sourceText.take(MAX_CHARS_PER_ACTION))
        } catch (e: Exception) {
            Result.failure(Exception("Action output reference error: ${e.message}"))
        }
    }

    // ── AI_PROMPT ──

    private suspend fun executeAiPrompt(
        action: WorkflowAction,
        actionResultMap: Map<String, String>,
        template: WorkflowTemplate
    ): Result<String> {
        return try {
            if (action.extraConfig.isBlank()) {
                return Result.failure(Exception("No AI prompt configured"))
            }

            val config = json.decodeFromString<AiPromptConfig>(action.extraConfig)
            if (config.promptText.isBlank()) {
                return Result.failure(Exception("AI prompt text is empty"))
            }

            // Compact format instructions. Kept to one clause each; the model
            // only needs a clear directive, not an explanation of Markdown.
            val formatInstruction = when (config.outputFormat) {
                AiPromptOutputFormat.PLAIN_TEXT -> "Plain text only — no Markdown syntax."
                AiPromptOutputFormat.MARKDOWN -> "Use Markdown (## headings, bullets, **bold**)."
                AiPromptOutputFormat.JSON -> "Output STRICT valid JSON only — no prose, no code fences."
                AiPromptOutputFormat.CUSTOM -> ""
            }

            val systemPrompt = buildString {
                append("Follow the user's prompt precisely.")
                if (formatInstruction.isNotBlank()) {
                    append(' ')
                    append(formatInstruction)
                }
            }

            // Build the user message: prompt + any prior context from action results
            val userMessage = buildString {
                append(config.promptText)
                if (actionResultMap.isNotEmpty()) {
                    append("\n\nPrior context:\n")
                    actionResultMap.entries.forEachIndexed { i, (_, text) ->
                        append("Source ${i + 1}: ${text.take(2000)}\n\n")
                    }
                }
            }

            // Resolve profile: action-level config > action-level field > workflow default > app default
            val profileId = config.profileId.ifBlank {
                action.profileId.ifBlank {
                    template.defaultProfileId.ifBlank { null }
                }
            }

            val input = ArticleInput(
                text = userMessage.take(MAX_TOTAL_CHARS),
                systemPromptOverride = systemPrompt,
                profileId = profileId
            )

            val transformResult = transformProvider.transform(input, TransformType.CUSTOM_WORKFLOW)

            if (transformResult.isSuccess) {
                val result = transformResult.getOrThrow()
                Result.success(result.outputText.take(MAX_CHARS_PER_ACTION))
            } else {
                val error = transformResult.exceptionOrNull()!!
                val rawDetail = when (error) {
                    is DiagnosticException -> error.rawDetail
                    else -> "${error.javaClass.simpleName}: ${error.message}"
                }
                Result.failure(DiagnosticException(
                    message = "AI Prompt failed: ${error.message}",
                    rawDetail = ErrorRedactor.redact(rawDetail),
                    cause = error
                ))
            }
        } catch (e: Exception) {
            Result.failure(DiagnosticException(
                message = "AI Prompt error: ${e.message}",
                rawDetail = "Exception: ${e.javaClass.simpleName}: ${e.message}",
                cause = e
            ))
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

    /**
     * Combines action results into a single input string for AI synthesis.
     * Per-action compaction is applied to each action's prepared content.
     * Labels and instructions are never compacted.
     *
     * If an action's instruction was already applied as a preprocessing step,
     * the "[Per-source instruction: ...]" annotation is omitted — the content
     * already reflects the instruction, so repeating it would be redundant and
     * could confuse the model.
     *
     * @return Pair of (combined text, original total text length before compaction)
     */
    private fun buildCombinedInput(
        results: List<ActionResult>,
        template: WorkflowTemplate
    ): Pair<String, Int> {
        val builder = StringBuilder()
        var originalTextLength = 0

        for ((i, ar) in results.withIndex()) {
            val label = ar.action.label.ifBlank { "${ar.action.type.displayName} #${i + 1}" }
            builder.appendLine("=== Source ${i + 1}: $label ===")
            // Only surface the instruction as a hint if preprocessing did NOT
            // already consume it. Otherwise the downstream prompt sees content
            // that already reflects the instruction.
            if (ar.action.instruction.isNotBlank() && !ar.instructionApplied) {
                builder.appendLine("[Per-source instruction: ${ar.action.instruction}]")
            }

            // Compact only the auto-collected action text, not labels/instructions.
            originalTextLength += ar.text.length
            val mode = ar.action.compaction
            val actionText = if (mode != InputCompactionMode.NONE) {
                TextCompactor.compact(ar.text, mode).text
            } else {
                ar.text
            }
            builder.appendLine(actionText)
            builder.appendLine()
        }

        val combined = builder.toString().take(MAX_TOTAL_CHARS)
        return combined to originalTextLength
    }

    /**
     * Runs the action's pre-source instruction as a real preprocessing AI pass on
     * the raw action content. Returns the transform result on success, or null
     * if the call failed (caller falls back to raw content).
     *
     * The profile used is: action.profileId > template.defaultProfileId > app default.
     * This matches the routing rules for the main output pass.
     */
    private suspend fun preprocessActionWithInstruction(
        action: WorkflowAction,
        rawText: String,
        template: WorkflowTemplate
    ): TransformResult? {
        return try {
            val profileId = action.profileId.ifBlank {
                template.defaultProfileId.ifBlank { null }
            }
            // Compact preprocessing system prompt: ~130 chars + instruction
            // (was ~290 chars + instruction). The preprocessing pass runs once
            // per action with a non-blank instruction, so savings compound
            // linearly with the number of such actions in a workflow.
            val systemPrompt = buildString {
                append("Apply this instruction to the content below. Return only the transformed text — no commentary, no headings.\n\n")
                append("Instruction: ")
                append(action.instruction.trim())
            }
            val preparedInput = ArticleInput(
                text = rawText.take(MAX_CHARS_PER_ACTION),
                systemPromptOverride = systemPrompt,
                profileId = profileId
            )
            val result = transformProvider.transform(preparedInput, TransformType.CUSTOM_WORKFLOW)
            if (result.isSuccess) {
                result.getOrThrow()
            } else {
                val err = result.exceptionOrNull()
                Log.w(TAG, "Pre-source instruction failed for action '${action.label}': ${err?.message}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pre-source instruction threw for action '${action.label}'", e)
            null
        }
    }

    /** Returns true when the workflow should produce structured multi-platform social outputs. */
    private fun isSocialOutputMode(template: WorkflowTemplate): Boolean {
        val cfg = template.outputConfig
        if (cfg.outputType != WorkflowOutputType.SOCIAL_POST && cfg.outputType != WorkflowOutputType.BOTH) return false
        val allPlatforms = cfg.socialPlatforms.map { it.displayName } + cfg.customPlatforms
        return allPlatforms.isNotEmpty()
    }

    /**
     * Builds the workflow's final-generation system prompt.
     *
     * Rewritten 2026-04 to be materially shorter. The previous version had:
     *  - a verbose "You are a professional AI assistant…" preamble,
     *  - format instructions duplicated across modes,
     *  - a multi-line JSON schema block for social mode (~300 chars),
     *  - redundant "Process the following source data:" footer.
     *
     * Total overhead dropped from ~1100 chars (social + 2 platforms) and ~480
     * chars (standard briefing) to ~570 / ~200 respectively — ~50% shorter
     * without losing a single semantic requirement. Motivated by Gemma 3 1B
     * int4 timing out on overnight scheduled runs when the engine's long
     * prompt blew past the local model's token budget.
     */
    private fun buildSystemPrompt(template: WorkflowTemplate): String {
        val socialMode = isSocialOutputMode(template)

        return buildString {
            if (socialMode) {
                buildSocialSystemPrompt(this, template)
            } else {
                buildStandardSystemPrompt(this, template)
            }

            if (template.globalInstruction.isNotBlank()) {
                append("\n\nExtra: ${template.globalInstruction.trim()}")
            }

            append("\n\nSources:\n")
        }
    }

    private fun buildStandardSystemPrompt(sb: StringBuilder, template: WorkflowTemplate) {
        val cfg = template.outputConfig
        when (cfg.outputType) {
            WorkflowOutputType.BRIEFING -> sb.append("Write a structured, scannable briefing from the sources below.")
            WorkflowOutputType.SOCIAL_POST -> {
                sb.append("Write social media posts from the sources below.")
                if (cfg.socialPlatforms.isNotEmpty()) {
                    sb.append(" Platforms: ${cfg.socialPlatforms.joinToString { it.displayName }}. Match each platform's tone.")
                }
            }
            WorkflowOutputType.BOTH -> {
                sb.append("Write a structured briefing AND social media posts from the sources below.")
                if (cfg.socialPlatforms.isNotEmpty()) {
                    sb.append(" Social platforms: ${cfg.socialPlatforms.joinToString { it.displayName }}.")
                }
            }
            WorkflowOutputType.CUSTOM -> {
                if (cfg.customInstruction.isNotBlank()) {
                    sb.append(cfg.customInstruction.trim())
                } else {
                    sb.append("Process the sources below.")
                }
            }
        }

        // Output format. Kept short; the previous version repeated "Format your
        // response using…" and then enumerated syntax exhaustively — smaller
        // models ignore the prose anyway.
        when (cfg.outputFormat) {
            OutputFormat.MARKDOWN -> sb.append(
                "\n\nFormat: Markdown (## headings, bullets, **bold**, --- separators). Do not wrap the whole response in a code block."
            )
            OutputFormat.PLAIN_TEXT -> sb.append(
                "\n\nFormat: plain text only. No Markdown syntax (#, *, backticks)."
            )
            OutputFormat.JSON -> sb.append(
                "\n\nOutput STRICT valid JSON only — no prose, no code fences."
            )
            OutputFormat.AUTO -> { /* no format constraint */ }
        }
    }

    /**
     * Build structured JSON prompt for multi-platform social output generation.
     *
     * The schema is expressed as a single inline example — e.g.
     * `{"outputs":[{"platform":"X","content":"...","title":"","notes":""}]}` —
     * rather than a multi-line block. Smaller models follow inline examples
     * just as well, and the prompt saves ~150 chars (~50 tokens).
     *
     * `SocialOutputParser.parseTolerant` repairs missing commas / markdown
     * fences / stray prose as a safety net, so the prompt doesn't need to
     * repeat formatting rules three times.
     */
    private fun buildSocialSystemPrompt(sb: StringBuilder, template: WorkflowTemplate) {
        val cfg = template.outputConfig
        val allPlatforms = cfg.socialPlatforms.map { it.displayName } + cfg.customPlatforms

        sb.append("Write distinct social posts from the sources below — one per platform, each tailored (no duplicates).")

        if (cfg.socialGlobalInstruction.isNotBlank()) {
            sb.append("\n\nGlobal: ${cfg.socialGlobalInstruction.trim()}")
        }

        sb.append("\n\nPlatforms:")
        for (platform in allPlatforms) {
            val instruction = cfg.platformInstructions[platform]
            sb.append("\n- $platform")
            if (!instruction.isNullOrBlank()) {
                sb.append(": ${instruction.trim()}")
            } else {
                // Compact per-platform defaults. Keep the distinctive tone cue,
                // drop filler like "and call-to-action. 1-3 short paragraphs."
                when (platform) {
                    "X" -> sb.append(": punchy, under 280 chars. Hashtags sparingly.")
                    "LinkedIn" -> sb.append(": professional hook, 1–3 short paragraphs.")
                    "Facebook" -> sb.append(": conversational, encourages engagement.")
                    "Medium" -> sb.append(": article-style teaser paragraph, thoughtful.")
                    "Instagram" -> sb.append(": caption-style with relevant hashtags.")
                    "Threads" -> sb.append(": narrative, conversational thread tone.")
                }
            }
        }

        if (cfg.outputType == WorkflowOutputType.BOTH) {
            sb.append("\n\nAlso add one entry with platform \"Briefing\" containing a structured briefing.")
        }

        // Single compact schema block. The final rendering layer
        // (SocialOutputParser.parseTolerant) handles missing commas, stray
        // prose, and code fences, so we don't need defensive repetition.
        sb.append("\n\nOutput STRICT valid JSON only — no prose, no code fences. Use this exact shape:")
        sb.append("\n{\"outputs\":[{\"platform\":\"X\",\"content\":\"…\",\"title\":\"\",\"notes\":\"\"}]}")
        sb.append("\nOne object per platform in \"outputs\". Double-quoted strings, commas between fields. title/notes may be \"\".")
        sb.append("\nPlatforms to generate: ${allPlatforms.joinToString(", ")}")
    }

    private data class ActionResult(
        val action: WorkflowAction,
        val text: String,
        val instructionApplied: Boolean = false
    )
}

package com.synapse.app.domain.engine

import com.synapse.app.domain.models.*
import com.synapse.app.domain.providers.ArticleTransformProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowExecutionEngine @Inject constructor(
    private val transformProvider: ArticleTransformProvider,
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val MAX_CHARS_PER_ACTION = 4000
        private const val MAX_TOTAL_CHARS = 16000
    }

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
        emit(ExecutionState.GeneratingOutput("AI Provider"))

        // Call AI provider
        val input = ArticleInput(
            text = combinedInput,
            systemPromptOverride = systemPrompt
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
        }
    }

    private suspend fun fetchUrl(url: String): Result<String> {
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

            // Output type instruction
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

            // Global instruction
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

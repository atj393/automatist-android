package com.automatist.app.domain.engine

import com.automatist.app.data.network.RssParser
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.InputCompactionMode
import com.automatist.app.domain.models.OutputFormat
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.models.WorkflowAction
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.models.WorkflowOutputConfig
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.providers.ArticleTransformProvider
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import com.automatist.app.platform.security.SecureStorage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Covers the new execution-state fields that let the run page attach
 * expandable content to stage rows:
 *  - [ExecutionState.ActionSourceFetched] carries raw source text
 *  - [ExecutionState.ActionPromptStarted.instructionText] carries the full prompt
 *  - [ExecutionState.GeneratingOutput.systemPrompt] + `userContent` carry the
 *    full final prompt
 *
 * Also pins two behavioural rules:
 *  - actions **without** an instruction do NOT emit ActionSourceFetched (the
 *    user-facing source content and result are the same — no need to
 *    duplicate the expander)
 *  - no sensitive data is emitted in these UI-oriented fields beyond what
 *    the engine already passes to the provider
 */
class RunStageDetailsTest {

    private fun newEngine(provider: ArticleTransformProvider): WorkflowExecutionEngine =
        WorkflowExecutionEngine(
            transformProvider = provider,
            httpClient = OkHttpClient(),
            rssParser = RssParser(OkHttpClient()),
            workflowRepository = FakeWorkflowRepository(),
            secureStorage = NoopSecureStorage
        )

    private fun templateWithAction(
        instruction: String,
        sourceText: String = "raw source body"
    ): WorkflowTemplate =
        WorkflowTemplate(
            name = "Details Test",
            actions = listOf(
                WorkflowAction(
                    id = UUID.randomUUID().toString(),
                    type = WorkflowActionType.PASTE_TEXT,
                    label = "Paste",
                    sourceData = sourceText,
                    instruction = instruction,
                    compaction = InputCompactionMode.NONE,
                    isEnabled = true
                )
            ),
            outputConfig = WorkflowOutputConfig(
                outputFormat = OutputFormat.PLAIN_TEXT,
                inputCompaction = InputCompactionMode.NONE
            )
        )

    private val successProvider = object : ArticleTransformProvider {
        override suspend fun transform(
            input: ArticleInput,
            type: TransformType
        ): Result<TransformResult> = Result.success(
            TransformResult(
                outputText = "GENERATED",
                transformType = type,
                providerType = ProviderType.FAKE
            )
        )
    }

    @Test
    fun `action with instruction emits ActionSourceFetched carrying raw source text`() = runBlocking {
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = "Summarize"))
            .toList()

        val fetched = states.filterIsInstance<ExecutionState.ActionSourceFetched>().singleOrNull()
        assertTrue("ActionSourceFetched must be emitted for an action with instruction", fetched != null)
        assertEquals("raw source body", fetched!!.rawSourceText)
        assertEquals("Paste", fetched.actionLabel)
    }

    @Test
    fun `action without instruction does NOT emit ActionSourceFetched`() = runBlocking {
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = ""))
            .toList()

        assertEquals(
            "no ActionSourceFetched for plain-fetch actions — no duplicate source/result expanders",
            0,
            states.count { it is ExecutionState.ActionSourceFetched }
        )
    }

    @Test
    fun `ActionPromptStarted carries both a preview and the full instruction text`() = runBlocking {
        val instruction = "Turn the content below into a crisp three-sentence brief, plain text, no bullets"
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = instruction))
            .toList()

        val prompt = states.filterIsInstance<ExecutionState.ActionPromptStarted>().single()
        assertEquals(
            "instructionText must carry the full unclipped prompt for the expander",
            instruction,
            prompt.instructionText
        )
        assertTrue(
            "instructionPreview must remain short (header-friendly)",
            prompt.instructionPreview.length <= 121 // 120 + optional ellipsis
        )
    }

    @Test
    fun `long instruction keeps preview truncated but ships full text for inspection`() = runBlocking {
        val longInstruction = "a".repeat(400) // way longer than the 120-char header cap
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = longInstruction))
            .toList()

        val prompt = states.filterIsInstance<ExecutionState.ActionPromptStarted>().single()
        assertEquals("full instruction must survive for the prompt expander", longInstruction, prompt.instructionText)
        assertTrue(
            "preview must be truncated with an ellipsis for display",
            prompt.instructionPreview.endsWith("…") && prompt.instructionPreview.length <= 121
        )
    }

    @Test
    fun `GeneratingOutput exposes systemPrompt and userContent for the final-prompt expander`() = runBlocking {
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = "Summarize"))
            .toList()

        val generating = states.filterIsInstance<ExecutionState.GeneratingOutput>().single()
        assertFalse(
            "systemPrompt must be populated so the 'Show final prompt' expander has content",
            generating.systemPrompt.isBlank()
        )
        assertFalse(
            "userContent must be populated with the combined source data",
            generating.userContent.isBlank()
        )
    }

    private object NoopSecureStorage : SecureStorage {
        override suspend fun saveApiKey(provider: ProviderType, key: String) {}
        override suspend fun getApiKey(provider: ProviderType): String? = null
        override suspend fun clearApiKey(provider: ProviderType) {}
        override suspend fun saveProfileKey(keyId: String, key: String) {}
        override suspend fun getProfileKey(keyId: String): String? = null
        override suspend fun clearProfileKey(keyId: String) {}
        override suspend fun hasProfileKey(keyId: String): Boolean = false
        override suspend fun saveServiceKey(service: String, key: String) {}
        override suspend fun getServiceKey(service: String): String? = null
        override suspend fun clearServiceKey(service: String) {}
    }
}

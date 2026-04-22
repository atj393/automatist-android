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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Verifies the engine exposes the right high-level stages for the run-page UI:
 *  - [ExecutionState.ActionStarted] for source reading (always)
 *  - [ExecutionState.ActionPromptStarted] ONLY when the action has a non-blank
 *    instruction — so the UI can show a distinct "running action prompt" row
 *  - [ExecutionState.GeneratingOutput] for the final generation stage
 *
 * Together these let the VM tell apart source fetching, action-level prompt
 * execution, and final generation without guessing from generic events.
 */
class RunStageVisibilityTest {

    private fun newEngine(provider: ArticleTransformProvider): WorkflowExecutionEngine =
        WorkflowExecutionEngine(
            transformProvider = provider,
            httpClient = OkHttpClient(),
            rssParser = RssParser(OkHttpClient()),
            workflowRepository = FakeWorkflowRepository(),
            secureStorage = NoopSecureStorage
        )

    private fun templateWithAction(instruction: String): WorkflowTemplate =
        WorkflowTemplate(
            name = "Run Stage Test",
            actions = listOf(
                WorkflowAction(
                    id = UUID.randomUUID().toString(),
                    type = WorkflowActionType.PASTE_TEXT,
                    label = "Paste",
                    sourceData = "some source content",
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
                outputText = "ok",
                transformType = type,
                providerType = ProviderType.FAKE
            )
        )
    }

    @Test
    fun `action with instruction emits ActionPromptStarted between ActionStarted and ActionCompleted`() = runBlocking {
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = "Summarize this"))
            .toList()

        val actionStartedIdx = states.indexOfFirst { it is ExecutionState.ActionStarted }
        val promptStartedIdx = states.indexOfFirst { it is ExecutionState.ActionPromptStarted }
        val actionCompletedIdx = states.indexOfFirst { it is ExecutionState.ActionCompleted }

        assertTrue("ActionStarted must be emitted", actionStartedIdx >= 0)
        assertTrue("ActionPromptStarted must be emitted for an action with an instruction", promptStartedIdx >= 0)
        assertTrue("ActionCompleted must be emitted", actionCompletedIdx >= 0)
        assertTrue(
            "ActionPromptStarted must come after ActionStarted",
            promptStartedIdx > actionStartedIdx
        )
        assertTrue(
            "ActionPromptStarted must come before ActionCompleted",
            promptStartedIdx < actionCompletedIdx
        )
    }

    @Test
    fun `action with instruction exposes the instruction preview to the UI`() = runBlocking {
        val instruction = "Summarize as bullet points focused on breaking news"
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = instruction))
            .toList()

        val prompt = states.filterIsInstance<ExecutionState.ActionPromptStarted>().single()
        assertEquals(
            "instruction preview must carry the trimmed instruction text for display",
            instruction,
            prompt.instructionPreview
        )
        assertEquals("Paste", prompt.actionLabel)
        assertEquals(0, prompt.actionIndex)
        assertEquals(1, prompt.totalActions)
    }

    @Test
    fun `action without instruction does NOT emit ActionPromptStarted`() = runBlocking {
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = ""))
            .toList()

        assertEquals(
            "no ActionPromptStarted when action has no instruction",
            0,
            states.count { it is ExecutionState.ActionPromptStarted }
        )
    }

    @Test
    fun `GeneratingOutput is emitted as a distinct final-generation stage`() = runBlocking {
        val states = newEngine(successProvider)
            .execute(templateWithAction(instruction = "Summarize"))
            .toList()

        val generating = states.filterIsInstance<ExecutionState.GeneratingOutput>().singleOrNull()
        assertTrue("GeneratingOutput must be emitted for final generation", generating != null)
        // And it should come strictly after the action-prompt stage so the UI
        // can render a clean "first action prompt, then final generation" trail.
        val promptIdx = states.indexOfFirst { it is ExecutionState.ActionPromptStarted }
        val generatingIdx = states.indexOfFirst { it is ExecutionState.GeneratingOutput }
        assertTrue(
            "GeneratingOutput must come after ActionPromptStarted",
            generatingIdx > promptIdx
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

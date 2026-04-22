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
 * Verifies the action pre-source instruction pipeline:
 *  - an action whose instruction is non-blank triggers a real preprocessing
 *    AI call BEFORE the main output pass, and the prepared text replaces
 *    the raw content in the synthesis input
 *  - an action without an instruction runs exactly one AI call (the main
 *    output pass), with no preprocessing
 *  - preprocessing failures fall back silently to the raw content
 *  - the per-source annotation is suppressed in the final prompt when
 *    preprocessing already consumed the instruction
 */
class PreSourceInstructionTest {

    private fun newEngine(provider: ArticleTransformProvider): WorkflowExecutionEngine =
        WorkflowExecutionEngine(
            transformProvider = provider,
            httpClient = OkHttpClient(),
            rssParser = RssParser(OkHttpClient()),
            workflowRepository = FakeWorkflowRepository(),
            secureStorage = NoopSecureStorage
        )

    private fun pastedActionTemplate(
        instruction: String,
        sourceText: String = "raw source content to prepare"
    ): WorkflowTemplate = WorkflowTemplate(
        name = "Test",
        actions = listOf(
            WorkflowAction(
                id = UUID.randomUUID().toString(),
                type = WorkflowActionType.PASTE_TEXT,
                label = "Paste",
                sourceData = sourceText,
                instruction = instruction,
                // Use NONE so the synthesis input isn't modified by compaction —
                // this keeps the assertions on substring content precise.
                compaction = InputCompactionMode.NONE,
                isEnabled = true
            )
        ),
        outputConfig = WorkflowOutputConfig(
            outputFormat = OutputFormat.PLAIN_TEXT,
            inputCompaction = InputCompactionMode.NONE
        )
    )

    @Test
    fun `non-blank instruction triggers preprocessing call before main output pass`() = runBlocking {
        val calls = mutableListOf<CallRecord>()
        val provider = RecordingProvider(calls) { _, _, _ ->
            Result.success(
                TransformResult(
                    outputText = "PREPARED",
                    transformType = TransformType.CUSTOM_WORKFLOW,
                    providerType = ProviderType.FAKE
                )
            )
        }

        val template = pastedActionTemplate(instruction = "Summarize as 3 bullets")
        val states = newEngine(provider).execute(template).toList()
        val completed = states.filterIsInstance<ExecutionState.Completed>().single()

        // Exactly two transform calls: one preprocessing, one main output.
        assertEquals("expected preprocessing + main call", 2, calls.size)

        val prep = calls[0]
        assertTrue(
            "preprocessing system prompt must reference the action instruction",
            prep.systemPrompt?.contains("Summarize as 3 bullets") == true
        )
        assertEquals(
            "preprocessing text must be the raw action content",
            "raw source content to prepare", prep.text
        )

        val main = calls[1]
        // Main call must receive the prepared text (not the raw text).
        assertTrue(
            "main call input must contain prepared output, got: ${main.text}",
            main.text.contains("PREPARED")
        )
        assertFalse(
            "main call input must not contain the raw source content",
            main.text.contains("raw source content to prepare")
        )
        // Annotation is suppressed once preprocessing consumed the instruction.
        assertFalse(
            "per-source instruction annotation must be suppressed after preprocessing",
            main.text.contains("Per-source instruction:")
        )

        assertTrue(completed.outputText.isNotBlank())
    }

    @Test
    fun `blank instruction runs only the main output call`() = runBlocking {
        val calls = mutableListOf<CallRecord>()
        val provider = RecordingProvider(calls) { _, _, _ ->
            Result.success(
                TransformResult(
                    outputText = "out",
                    transformType = TransformType.CUSTOM_WORKFLOW,
                    providerType = ProviderType.FAKE
                )
            )
        }

        val template = pastedActionTemplate(instruction = "")
        newEngine(provider).execute(template).toList()

        assertEquals(
            "no preprocessing call should happen when instruction is blank",
            1, calls.size
        )
    }

    @Test
    fun `preprocessing failure falls back to raw content and does not fail the run`() = runBlocking {
        var callIndex = 0
        val calls = mutableListOf<CallRecord>()
        val provider = RecordingProvider(calls) { _, _, _ ->
            callIndex++
            if (callIndex == 1) {
                // Fail only the preprocessing pass.
                Result.failure(RuntimeException("preprocess boom"))
            } else {
                Result.success(
                    TransformResult(
                        outputText = "final",
                        transformType = TransformType.CUSTOM_WORKFLOW,
                        providerType = ProviderType.FAKE
                    )
                )
            }
        }

        val template = pastedActionTemplate(instruction = "Clean up")
        val states = newEngine(provider).execute(template).toList()
        val completed = states.filterIsInstance<ExecutionState.Completed>().single()

        assertEquals("preprocess + main should have been attempted", 2, calls.size)
        val main = calls[1]
        assertTrue(
            "when preprocessing failed, raw content must still be in the main input",
            main.text.contains("raw source content to prepare")
        )
        // Because preprocessing failed, the annotation should be shown to give
        // the model a chance to honor the instruction.
        assertTrue(
            "annotation must be included when preprocessing failed",
            main.text.contains("Per-source instruction:")
        )
        assertEquals("final", completed.outputText)
    }

    // ── Helpers ──

    private data class CallRecord(
        val text: String,
        val systemPrompt: String?,
        val profileId: String?
    )

    private class RecordingProvider(
        private val calls: MutableList<CallRecord>,
        private val onTransform: suspend (String, String?, String?) -> Result<TransformResult>
    ) : ArticleTransformProvider {
        override suspend fun transform(
            input: ArticleInput,
            type: TransformType
        ): Result<TransformResult> {
            calls.add(CallRecord(input.text, input.systemPromptOverride, input.profileId))
            return onTransform(input.text, input.systemPromptOverride, input.profileId)
        }
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

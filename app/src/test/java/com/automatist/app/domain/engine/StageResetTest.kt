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
 * Verifies that the workflow execution engine invokes
 * [ArticleTransformProvider.resetForNewStage] at every stage boundary — so
 * on-device runtimes (MediaPipe / AICore) start each stage with clean
 * transient state. Regression guard for the observed "works on third try"
 * behaviour where stale per-run diagnostic state bled into the next stage.
 */
class StageResetTest {

    private fun newEngine(provider: ArticleTransformProvider): WorkflowExecutionEngine =
        WorkflowExecutionEngine(
            transformProvider = provider,
            httpClient = OkHttpClient(),
            rssParser = RssParser(OkHttpClient()),
            workflowRepository = FakeWorkflowRepository(),
            secureStorage = NoopSecureStorage
        )

    private fun pastedActionTemplate(instruction: String = ""): WorkflowTemplate =
        WorkflowTemplate(
            name = "Test",
            actions = listOf(
                WorkflowAction(
                    id = UUID.randomUUID().toString(),
                    type = WorkflowActionType.PASTE_TEXT,
                    label = "Paste",
                    sourceData = "source text",
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

    @Test
    fun `execute resets at run-start and run-end on success`() = runBlocking {
        val provider = ResetRecordingProvider(
            onTransform = {
                Result.success(
                    TransformResult(
                        outputText = "ok",
                        transformType = TransformType.CUSTOM_WORKFLOW,
                        providerType = ProviderType.FAKE
                    )
                )
            }
        )

        val states = newEngine(provider).execute(pastedActionTemplate()).toList()
        states.filterIsInstance<ExecutionState.Completed>().single()

        val reasons = provider.resetReasons
        assertTrue(
            "first reset must be at run-start, got: $reasons",
            reasons.firstOrNull()?.startsWith("run-start:") == true
        )
        assertTrue(
            "must reset before final generation, got: $reasons",
            reasons.any { it.startsWith("final-generation:") }
        )
        assertTrue(
            "must reset at run-end on success, got: $reasons",
            reasons.any { it.startsWith("run-end-completed:") }
        )
    }

    @Test
    fun `execute resets before per-action preprocessing when instruction is non-blank`() = runBlocking {
        val provider = ResetRecordingProvider(
            onTransform = {
                Result.success(
                    TransformResult(
                        outputText = "ok",
                        transformType = TransformType.CUSTOM_WORKFLOW,
                        providerType = ProviderType.FAKE
                    )
                )
            }
        )

        newEngine(provider).execute(pastedActionTemplate(instruction = "Summarize"))
            .toList()

        assertTrue(
            "must reset before action preprocessing, got: ${provider.resetReasons}",
            provider.resetReasons.any { it.startsWith("action-preprocess:") }
        )
    }

    @Test
    fun `execute does NOT call action-preprocess reset when instruction is blank`() = runBlocking {
        val provider = ResetRecordingProvider(
            onTransform = {
                Result.success(
                    TransformResult(
                        outputText = "ok",
                        transformType = TransformType.CUSTOM_WORKFLOW,
                        providerType = ProviderType.FAKE
                    )
                )
            }
        )

        newEngine(provider).execute(pastedActionTemplate(instruction = ""))
            .toList()

        assertEquals(
            "no action-preprocess reset expected when instruction is blank",
            0,
            provider.resetReasons.count { it.startsWith("action-preprocess:") }
        )
    }

    @Test
    fun `execute resets at run-end on final-generation failure`() = runBlocking {
        val provider = ResetRecordingProvider(
            onTransform = { Result.failure(RuntimeException("boom")) }
        )

        newEngine(provider).execute(pastedActionTemplate()).toList()

        assertTrue(
            "must reset at run-end on processing failure, got: ${provider.resetReasons}",
            provider.resetReasons.any { it.startsWith("run-end-failed-processing:") }
        )
    }

    @Test
    fun `regenerate resets before and after the generation attempt`() = runBlocking {
        val provider = ResetRecordingProvider(
            onTransform = {
                Result.success(
                    TransformResult(
                        outputText = "v2",
                        transformType = TransformType.CUSTOM_WORKFLOW,
                        providerType = ProviderType.FAKE
                    )
                )
            }
        )

        newEngine(provider).regenerate(
            frozenInput = "frozen",
            template = pastedActionTemplate(),
            nextVersion = 2
        )

        assertTrue(
            "regenerate must reset before and after, got: ${provider.resetReasons}",
            provider.resetReasons.any { it.startsWith("regenerate:") } &&
                provider.resetReasons.any { it.startsWith("regenerate-end:") }
        )
    }

    // ── Helpers ──

    private class ResetRecordingProvider(
        private val onTransform: suspend () -> Result<TransformResult>
    ) : ArticleTransformProvider {
        val resetReasons: MutableList<String> = mutableListOf()

        override suspend fun resetForNewStage(reason: String) {
            resetReasons.add(reason)
        }

        override suspend fun transform(
            input: ArticleInput,
            type: TransformType
        ): Result<TransformResult> = onTransform()
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

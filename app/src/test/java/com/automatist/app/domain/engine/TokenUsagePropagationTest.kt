package com.automatist.app.domain.engine

import com.automatist.app.data.network.RssParser
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.OutputFormat
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TokenUsage
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Verifies the engine aggregates and forwards the honest-usage metadata from
 * [TransformResult] into [TokenUsage]. Regression guard for the bug where local
 * runs showed prompt=0 / completion=0 / total=0 because the provider defaulted
 * those fields to null and the engine coerced them to 0 without flagging the
 * result as estimated.
 */
class TokenUsagePropagationTest {

    private fun newEngine(provider: ArticleTransformProvider): WorkflowExecutionEngine =
        WorkflowExecutionEngine(
            transformProvider = provider,
            httpClient = OkHttpClient(),
            rssParser = RssParser(OkHttpClient()),
            workflowRepository = FakeWorkflowRepository(),
            secureStorage = NoopSecureStorage
        )

    private fun templateWithPastedAction(): WorkflowTemplate = WorkflowTemplate(
        name = "Test",
        actions = listOf(
            WorkflowAction(
                id = UUID.randomUUID().toString(),
                type = WorkflowActionType.PASTE_TEXT,
                label = "Paste",
                sourceData = "some source text",
                isEnabled = true
            )
        ),
        outputConfig = WorkflowOutputConfig(outputFormat = OutputFormat.PLAIN_TEXT)
    )

    @Test
    fun `local-like result propagates chars, ceiling, truncation and isEstimated`() = runBlocking {
        // Provider returns what a local run would produce: estimated tokens, chars,
        // ceiling, truncation flag, isUsageEstimated = true.
        val provider = StubProvider { _, type ->
            Result.success(
                TransformResult(
                    outputText = "local summary",
                    transformType = type,
                    providerType = ProviderType.LOCAL_AI,
                    promptTokens = 420,
                    completionTokens = 85,
                    isUsageEstimated = true,
                    inputChars = 1260,
                    outputChars = 255,
                    wasTruncated = true,
                    contextCeilingTokens = 1280
                )
            )
        }

        val usage = runEngineAndCollectUsage(provider)

        assertEquals(420, usage.promptTokens)
        assertEquals(85, usage.completionTokens)
        assertEquals(505, usage.totalTokens)
        assertTrue("estimated flag must propagate", usage.isEstimated)
        assertEquals(1260, usage.inputChars)
        assertEquals(255, usage.outputChars)
        assertEquals(1280, usage.contextCeilingTokens)
        assertEquals(true, usage.wasTruncated)
    }

    @Test
    fun `cloud-like result has no char metrics and is not estimated`() = runBlocking {
        // Cloud provider: exact tokens, no char metrics, isUsageEstimated left false.
        val provider = StubProvider { _, type ->
            Result.success(
                TransformResult(
                    outputText = "cloud summary",
                    transformType = type,
                    providerType = ProviderType.OPENAI,
                    promptTokens = 150,
                    completionTokens = 50
                )
            )
        }

        val usage = runEngineAndCollectUsage(provider)

        assertEquals(150, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(200, usage.totalTokens)
        assertFalse("cloud counts are authoritative — must not be marked estimated", usage.isEstimated)
        assertNull("no inputChars for cloud path", usage.inputChars)
        assertNull("no outputChars for cloud path", usage.outputChars)
        assertNull("no ceiling for cloud path", usage.contextCeilingTokens)
        assertNull("wasTruncated is null for cloud path (not applicable)", usage.wasTruncated)
    }

    @Test
    fun `successful local run never produces zero-zero-zero usage`() = runBlocking {
        // Regression guard for the original bug: local run showed 0/0/0 because
        // provider returned null tokens. Now the provider always populates
        // estimated values, so total should be non-zero for any real output.
        val provider = StubProvider { _, type ->
            Result.success(
                TransformResult(
                    outputText = "x".repeat(600),
                    transformType = type,
                    providerType = ProviderType.LOCAL_AI,
                    promptTokens = 300,
                    completionTokens = 200,
                    isUsageEstimated = true,
                    inputChars = 900,
                    outputChars = 600
                )
            )
        }

        val usage = runEngineAndCollectUsage(provider)

        assertFalse("prompt tokens must not be 0 after a real local generation",
            usage.promptTokens == 0)
        assertFalse("completion tokens must not be 0 after a real local generation",
            usage.completionTokens == 0)
        assertFalse("total tokens must not be 0 after a real local generation",
            usage.totalTokens == 0)
        assertTrue(usage.isEstimated)
    }

    @Test
    fun `wasTruncated aggregates to true across versions if any version truncated`() = runBlocking {
        // Two versions from same input: one truncated, one not.
        // Aggregate must be true so we warn the user honestly.
        var call = 0
        val provider = StubProvider { _, type ->
            call++
            Result.success(
                TransformResult(
                    outputText = "v$call",
                    transformType = type,
                    providerType = ProviderType.LOCAL_AI,
                    promptTokens = 100,
                    completionTokens = 20,
                    isUsageEstimated = true,
                    wasTruncated = (call == 1) // only first version truncates
                )
            )
        }

        val template = templateWithPastedAction().copy(
            outputConfig = WorkflowOutputConfig(
                outputFormat = OutputFormat.PLAIN_TEXT,
                numberOfOutputs = 2
            )
        )
        val states = newEngine(provider).execute(template).toList()
        val completed = states.filterIsInstance<ExecutionState.Completed>().single()

        assertEquals("if any version truncated, aggregate should be true",
            true, completed.tokenUsage.wasTruncated)
    }

    @Test
    fun `engine sums inputChars and outputChars across versions`() = runBlocking {
        var call = 0
        val provider = StubProvider { _, type ->
            call++
            Result.success(
                TransformResult(
                    outputText = "ver$call",
                    transformType = type,
                    providerType = ProviderType.LOCAL_AI,
                    promptTokens = 100,
                    completionTokens = 50,
                    isUsageEstimated = true,
                    inputChars = 500,
                    outputChars = 120
                )
            )
        }

        val template = templateWithPastedAction().copy(
            outputConfig = WorkflowOutputConfig(
                outputFormat = OutputFormat.PLAIN_TEXT,
                numberOfOutputs = 3
            )
        )
        val states = newEngine(provider).execute(template).toList()
        val completed = states.filterIsInstance<ExecutionState.Completed>().single()

        assertEquals(1500, completed.tokenUsage.inputChars)
        assertEquals(360, completed.tokenUsage.outputChars)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun runEngineAndCollectUsage(provider: ArticleTransformProvider): TokenUsage {
        val engine = newEngine(provider)
        val states = engine.execute(templateWithPastedAction()).toList()
        val completed = states.filterIsInstance<ExecutionState.Completed>().single()
        return completed.tokenUsage
    }

    private class StubProvider(
        private val onTransform: suspend (ArticleInput, TransformType) -> Result<TransformResult>
    ) : ArticleTransformProvider {
        override suspend fun transform(input: ArticleInput, type: TransformType) =
            onTransform(input, type)
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

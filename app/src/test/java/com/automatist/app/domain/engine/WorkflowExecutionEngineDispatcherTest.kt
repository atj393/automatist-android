package com.automatist.app.domain.engine

import com.automatist.app.data.network.RssParser
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.OutputFormat
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.models.WorkflowOutputConfig
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.providers.ArticleTransformProvider
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import com.automatist.app.platform.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Verifies that inference-triggering engine methods always dispatch off the caller's
 * thread. Regression guard for the UI freeze fix: `regenerate()` used to run
 * `transformProvider.transform()` on the calling thread (Main when invoked from
 * viewModelScope.launch), which meant MediaPipe model load happened on the UI thread.
 */
class WorkflowExecutionEngineDispatcherTest {

    /** Captures which thread the transform call actually runs on. */
    private class ThreadCapturingProvider : ArticleTransformProvider {
        @Volatile var observedThreadName: String? = null
        @Volatile var wasCalled: Boolean = false

        override suspend fun transform(
            input: ArticleInput,
            type: TransformType
        ): Result<TransformResult> {
            wasCalled = true
            observedThreadName = Thread.currentThread().name
            return Result.success(
                TransformResult(
                    outputText = "fake output",
                    transformType = type,
                    providerType = ProviderType.FAKE
                )
            )
        }
    }

    /** Minimal secure storage stub — regenerate never reads keys. */
    private class NoopSecureStorage : SecureStorage {
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

    private fun newEngine(provider: ArticleTransformProvider): WorkflowExecutionEngine {
        return WorkflowExecutionEngine(
            transformProvider = provider,
            httpClient = OkHttpClient(),
            rssParser = RssParser(OkHttpClient()),
            workflowRepository = FakeWorkflowRepository(),
            secureStorage = NoopSecureStorage()
        )
    }

    @Test
    fun `regenerate dispatches transform off the caller's thread`() = runBlocking(Dispatchers.Unconfined) {
        // Caller runs on whatever thread Dispatchers.Unconfined picks (typically the
        // calling test thread). If regenerate did NOT switch dispatchers, transform
        // would observe that same thread name. The fix wraps it in
        // withContext(Dispatchers.IO), so we expect an IO-named thread instead.
        val callerThreadName = Thread.currentThread().name
        val provider = ThreadCapturingProvider()
        val engine = newEngine(provider)

        val template = WorkflowTemplate(
            name = "Test",
            outputConfig = WorkflowOutputConfig(outputFormat = OutputFormat.PLAIN_TEXT)
        )

        engine.regenerate(
            frozenInput = "some frozen input",
            template = template,
            nextVersion = 2
        )

        assertEquals("transform should have been invoked", true, provider.wasCalled)
        assertNotNull(provider.observedThreadName)
        assertFalse(
            "regenerate must dispatch off the caller's thread. caller=$callerThreadName, " +
                "observed=${provider.observedThreadName}",
            provider.observedThreadName == callerThreadName
        )
    }

    @Test
    fun `regenerate observed thread is an IO-pool worker`() = runBlocking {
        // The engine explicitly wraps regenerate in withContext(Dispatchers.IO). On the
        // JVM default coroutines runtime, those threads are named "DefaultDispatcher-worker-N"
        // or "kotlinx.coroutines.DefaultExecutor". We only assert it's not the caller's
        // thread and not the Android main-looper thread (which we'd see as "main" if
        // the test accidentally ran there). Keep the assertion loose to avoid coupling
        // to internal coroutines thread-naming.
        val provider = ThreadCapturingProvider()
        val engine = newEngine(provider)

        engine.regenerate(
            frozenInput = "x",
            template = WorkflowTemplate(name = "T"),
            nextVersion = 1
        )

        val observed = provider.observedThreadName
        assertNotNull(observed)
        assertFalse(
            "transform must not run on the Android main thread. observed=$observed",
            observed == "main"
        )
    }
}

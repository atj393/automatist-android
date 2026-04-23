package com.automatist.app.domain.engine

import com.automatist.app.data.network.RssParser
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.InputCompactionMode
import com.automatist.app.domain.models.OutputFormat
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.ResumeSnapshot
import com.automatist.app.domain.models.ResumedActionOutput
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.models.WorkflowAction
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.models.WorkflowOutputConfig
import com.automatist.app.domain.models.WorkflowRun
import com.automatist.app.domain.models.WorkflowRunStatus
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.models.computeWorkflowFingerprint
import com.automatist.app.domain.providers.ArticleTransformProvider
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import com.automatist.app.platform.security.SecureStorage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Covers the retry-from-failure behaviour:
 * - fingerprint stability + change-detection
 * - [ResumeSnapshot.canResumeFor] gating
 * - [WorkflowExecutionEngine.execute] attaches a snapshot on processing-stage failure
 * - [WorkflowExecutionEngine.resume] skips action execution and rebuilds Completed
 *   from the frozen synthesis input
 */
class WorkflowResumeTest {

    // ── Providers under test ──

    /** Always succeeds with a canned output and records call count + last input. */
    private class RecordingProvider(
        private val output: String = "ok"
    ) : ArticleTransformProvider {
        val calls = AtomicInteger(0)
        @Volatile var lastInputText: String? = null

        override suspend fun transform(
            input: ArticleInput,
            type: TransformType
        ): Result<TransformResult> {
            calls.incrementAndGet()
            lastInputText = input.text
            return Result.success(
                TransformResult(
                    outputText = output,
                    transformType = type,
                    providerType = ProviderType.FAKE
                )
            )
        }
    }

    /** Always fails — used to trigger processing-stage failure in the engine. */
    private class AlwaysFailingProvider : ArticleTransformProvider {
        val calls = AtomicInteger(0)
        override suspend fun transform(
            input: ArticleInput,
            type: TransformType
        ): Result<TransformResult> {
            calls.incrementAndGet()
            return Result.failure(RuntimeException("boom"))
        }
    }

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

    private fun singleActionTemplate(
        id: Long = 1L,
        actionId: String = "act-1",
        sourceData: String = "Some prewritten text content."
    ): WorkflowTemplate = WorkflowTemplate(
        id = id,
        name = "Test Template",
        actions = listOf(
            WorkflowAction(
                id = actionId,
                type = WorkflowActionType.PASTE_TEXT,
                sourceData = sourceData,
                order = 0,
                isEnabled = true,
                compaction = InputCompactionMode.NONE
            )
        ),
        outputConfig = WorkflowOutputConfig(outputFormat = OutputFormat.PLAIN_TEXT)
    )

    // ── Fingerprint tests ──

    @Test
    fun `fingerprint is stable for identical templates`() {
        val a = singleActionTemplate()
        val b = singleActionTemplate()
        assertEquals(computeWorkflowFingerprint(a), computeWorkflowFingerprint(b))
    }

    @Test
    fun `fingerprint changes when an action's sourceData changes`() {
        val before = singleActionTemplate(sourceData = "original")
        val after = singleActionTemplate(sourceData = "edited")
        assertFalse(
            "edits to action source data must change the fingerprint so a stale resume is rejected",
            computeWorkflowFingerprint(before) == computeWorkflowFingerprint(after)
        )
    }

    @Test
    fun `fingerprint changes when output config changes`() {
        val before = singleActionTemplate().copy(
            outputConfig = WorkflowOutputConfig(outputFormat = OutputFormat.PLAIN_TEXT)
        )
        val after = singleActionTemplate().copy(
            outputConfig = WorkflowOutputConfig(outputFormat = OutputFormat.MARKDOWN)
        )
        assertFalse(computeWorkflowFingerprint(before) == computeWorkflowFingerprint(after))
    }

    @Test
    fun `fingerprint changes when action instruction changes`() {
        val before = singleActionTemplate().let { it.copy(actions = it.actions.map { a -> a.copy(instruction = "A") }) }
        val after = singleActionTemplate().let { it.copy(actions = it.actions.map { a -> a.copy(instruction = "B") }) }
        assertFalse(computeWorkflowFingerprint(before) == computeWorkflowFingerprint(after))
    }

    // ── canResumeFor tests ──

    @Test
    fun `canResumeFor returns true when fingerprint matches and input non-empty`() {
        val t = singleActionTemplate()
        val snap = ResumeSnapshot(
            fingerprint = computeWorkflowFingerprint(t),
            reachedStage = ResumeSnapshot.STAGE_PROCESSING,
            synthesisInput = "frozen combined input"
        )
        assertTrue(snap.canResumeFor(t))
    }

    @Test
    fun `canResumeFor returns false when fingerprint differs (workflow edited)`() {
        val original = singleActionTemplate()
        val snap = ResumeSnapshot(
            fingerprint = computeWorkflowFingerprint(original),
            reachedStage = ResumeSnapshot.STAGE_PROCESSING,
            synthesisInput = "frozen"
        )
        val edited = singleActionTemplate(sourceData = "changed")
        assertFalse(snap.canResumeFor(edited))
    }

    @Test
    fun `canResumeFor returns false when synthesisInput is blank`() {
        val t = singleActionTemplate()
        val snap = ResumeSnapshot(
            fingerprint = computeWorkflowFingerprint(t),
            reachedStage = ResumeSnapshot.STAGE_PROCESSING,
            synthesisInput = ""
        )
        assertFalse(snap.canResumeFor(t))
    }

    @Test
    fun `canResumeFor returns false for unknown reachedStage`() {
        val t = singleActionTemplate()
        val snap = ResumeSnapshot(
            fingerprint = computeWorkflowFingerprint(t),
            reachedStage = "SOMETHING_NEW",
            synthesisInput = "frozen"
        )
        assertFalse(snap.canResumeFor(t))
    }

    // ── Snapshot JSON round-trip ──

    @Test
    fun `snapshot survives JSON round-trip via WorkflowRun`() {
        val snap = ResumeSnapshot(
            fingerprint = "abc123",
            reachedStage = ResumeSnapshot.STAGE_PROCESSING,
            synthesisInput = "combined",
            actionOutputs = listOf(
                ResumedActionOutput("a1", "prepared-a", false),
                ResumedActionOutput("a2", "prepared-b", true)
            )
        )
        val jsonText = ResumeSnapshot.toJson(snap)
        assertTrue("toJson should produce non-empty output", jsonText.isNotBlank())

        val run = WorkflowRun(
            templateId = 1L,
            templateName = "T",
            status = WorkflowRunStatus.FAILED,
            resumeSnapshotJson = jsonText
        )
        val parsed = run.resumeSnapshot
        assertNotNull(parsed)
        assertEquals(snap.fingerprint, parsed!!.fingerprint)
        assertEquals(snap.reachedStage, parsed.reachedStage)
        assertEquals(snap.synthesisInput, parsed.synthesisInput)
        assertEquals(snap.actionOutputs, parsed.actionOutputs)
    }

    @Test
    fun `resumeSnapshot is null when json is blank`() {
        val run = WorkflowRun(
            templateId = 1L,
            templateName = "T",
            status = WorkflowRunStatus.FAILED
        )
        assertNull(run.resumeSnapshot)
    }

    @Test
    fun `resumeSnapshot is null when json is malformed`() {
        val run = WorkflowRun(
            templateId = 1L,
            templateName = "T",
            status = WorkflowRunStatus.FAILED,
            resumeSnapshotJson = "{not-valid-json"
        )
        assertNull(run.resumeSnapshot)
    }

    // ── Engine integration: execute() emits snapshot on processing failure ──

    @Test
    fun `execute emits a processing-stage resume snapshot when final generation fails`() = runBlocking {
        val provider = AlwaysFailingProvider()
        val engine = newEngine(provider)
        val template = singleActionTemplate()

        val events = engine.execute(template).toList()

        val failed = events.filterIsInstance<ExecutionState.Failed>().single()
        assertEquals("processing", failed.stage)
        val snap = failed.resumeSnapshot
        assertNotNull("processing-stage failures must carry a resume snapshot", snap)
        assertEquals(ResumeSnapshot.STAGE_PROCESSING, snap!!.reachedStage)
        assertTrue("snapshot must contain frozen synthesis input", snap.synthesisInput.isNotBlank())
        assertEquals(computeWorkflowFingerprint(template), snap.fingerprint)
        assertTrue(
            "snapshot must record the successful action's prepared output",
            snap.actionOutputs.any { it.actionId == "act-1" }
        )
    }

    // ── Engine integration: resume() skips actions and completes from frozen input ──

    @Test
    fun `resume uses the frozen synthesis input and does not re-execute actions`() = runBlocking {
        val recording = RecordingProvider(output = "resumed-ok")
        val engine = newEngine(recording)
        val template = singleActionTemplate()
        val frozen = "=== Source 1: Test Template ===\nSome prewritten text content.\n\n"

        val snapshot = ResumeSnapshot(
            fingerprint = computeWorkflowFingerprint(template),
            reachedStage = ResumeSnapshot.STAGE_PROCESSING,
            synthesisInput = frozen,
            actionOutputs = listOf(
                ResumedActionOutput("act-1", "Some prewritten text content.", false)
            )
        )

        val events = engine.resume(template, snapshot).toList()

        val completed = events.filterIsInstance<ExecutionState.Completed>().single()
        assertEquals("resumed-ok", completed.outputText)
        assertEquals(frozen, completed.synthesisInput)
        // Exactly one transform() call — the final generation. Actions are skipped.
        assertEquals(
            "resume must call transform exactly once for numberOfOutputs=1 — no rerun of action work",
            1, recording.calls.get()
        )
        // The input text passed to the model MUST be the frozen combined input, not a re-derived one.
        assertEquals(frozen, recording.lastInputText)
    }

    @Test
    fun `resume surfaces a reuse stage before processing`() = runBlocking {
        val engine = newEngine(RecordingProvider())
        val template = singleActionTemplate()
        val snap = ResumeSnapshot(
            fingerprint = computeWorkflowFingerprint(template),
            reachedStage = ResumeSnapshot.STAGE_PROCESSING,
            synthesisInput = "x",
            actionOutputs = listOf(ResumedActionOutput("act-1", "prepared", false))
        )

        val events = engine.resume(template, snap).toList()

        val actionCompleted = events.filterIsInstance<ExecutionState.ActionCompleted>()
        assertEquals("resume should emit exactly one reuse-summary action row", 1, actionCompleted.size)
        assertTrue(
            "reuse row should mention how many prior steps were reused",
            actionCompleted.first().actionLabel.contains("Reusing")
        )
        // Processing + generation + completion must still happen.
        assertTrue(events.any { it is ExecutionState.ProcessingStarted })
        assertTrue(events.any { it is ExecutionState.GeneratingOutput })
        assertTrue(events.any { it is ExecutionState.Completed })
    }
}

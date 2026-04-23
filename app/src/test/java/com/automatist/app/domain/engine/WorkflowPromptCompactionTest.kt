package com.automatist.app.domain.engine

import com.automatist.app.data.network.RssParser
import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.InputCompactionMode
import com.automatist.app.domain.models.OutputFormat
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.SocialPlatform
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType
import com.automatist.app.domain.models.WorkflowAction
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.models.WorkflowOutputConfig
import com.automatist.app.domain.models.WorkflowOutputType
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.providers.ArticleTransformProvider
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import com.automatist.app.platform.security.SecureStorage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the prompt compaction pass done in 2026-04.
 *
 * Guards two things at once:
 * 1. **Length bounds** — the compact prompts must stay under the limits we
 *    care about for the local Gemma 3 1B path. If someone later adds another
 *    verbose paragraph, this test fails and flags the regression before the
 *    overnight scheduled runs start timing out again.
 * 2. **Semantic essentials** — the compaction didn't drop anything load-bearing.
 *    Every critical instruction (JSON-only output, exact schema, platform
 *    list, per-platform tone differentiation, format rules) is still present.
 *
 * We exercise `engine.execute()` end-to-end with a recording provider rather
 * than poking private builders; that way the assertions cover the actual
 * prompt the model would receive — including global instruction injection
 * and the "Sources:" footer.
 */
class WorkflowPromptCompactionTest {

    private class RecordingProvider : ArticleTransformProvider {
        @Volatile var lastSystemPrompt: String? = null
        @Volatile var lastUserText: String? = null
        override suspend fun transform(
            input: ArticleInput,
            type: TransformType
        ): Result<TransformResult> {
            lastSystemPrompt = input.systemPromptOverride
            lastUserText = input.text
            return Result.success(
                TransformResult(
                    outputText = "{\"outputs\":[{\"platform\":\"X\",\"content\":\"ok\",\"title\":\"\",\"notes\":\"\"}]}",
                    transformType = type,
                    providerType = ProviderType.FAKE
                )
            )
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

    private fun newEngine(provider: ArticleTransformProvider): WorkflowExecutionEngine =
        WorkflowExecutionEngine(
            transformProvider = provider,
            httpClient = OkHttpClient(),
            rssParser = RssParser(OkHttpClient()),
            workflowRepository = FakeWorkflowRepository(),
            secureStorage = NoopSecureStorage()
        )

    private fun pasteTextTemplate(
        outputConfig: WorkflowOutputConfig,
        globalInstruction: String = ""
    ): WorkflowTemplate = WorkflowTemplate(
        id = 1L,
        name = "Prompt test",
        actions = listOf(
            WorkflowAction(
                id = "act-1",
                type = WorkflowActionType.PASTE_TEXT,
                sourceData = "Sample input content for the workflow.",
                order = 0,
                isEnabled = true,
                compaction = InputCompactionMode.NONE
            )
        ),
        outputConfig = outputConfig,
        globalInstruction = globalInstruction
    )

    private fun runAndCaptureSystemPrompt(template: WorkflowTemplate): String {
        val provider = RecordingProvider()
        val engine = newEngine(provider)
        runBlocking { engine.execute(template).toList() }
        val captured = provider.lastSystemPrompt
        assertNotNull("engine must pass a systemPromptOverride to the provider", captured)
        return captured!!
    }

    // ── Standard briefing / Markdown path ──

    @Test
    fun `standard briefing Markdown prompt is materially shorter than the pre-2026-04 version`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.MARKDOWN
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)

        // Pre-compaction size was ~480 chars. We aim well under 350 to leave
        // headroom for global instruction injection and the "Sources:" footer.
        assertTrue(
            "standard briefing prompt should be <= 350 chars, got ${prompt.length}",
            prompt.length <= 350
        )

        // Essentials still present:
        assertTrue("must say briefing", prompt.contains("briefing"))
        assertTrue("must state Markdown format", prompt.contains("Markdown"))
        assertTrue(
            "must keep critical markdown syntax cues",
            prompt.contains("##") && prompt.contains("**bold**")
        )
        assertTrue("must end with a Sources: cue", prompt.contains("Sources:"))
    }

    @Test
    fun `standard briefing prompt drops verbose preamble and duplicate format paragraph`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.MARKDOWN
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)

        // Dropped phrases from the pre-compaction prompt.
        assertFalse(
            "removed filler preamble",
            prompt.contains("You are a professional AI assistant executing a custom workflow")
        )
        assertFalse(
            "removed redundant format-your-response prose",
            prompt.contains("Format your response using Markdown")
        )
        assertFalse(
            "removed scannable-scaffolding filler",
            prompt.contains("Structure the output for easy scanning")
        )
        assertFalse(
            "removed verbose Sources footer",
            prompt.contains("Process the following source data")
        )
    }

    @Test
    fun `global instruction is still injected on the standard path`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.MARKDOWN
            ),
            globalInstruction = "Keep the tone slightly formal."
        )
        val prompt = runAndCaptureSystemPrompt(template)
        assertTrue(
            "global instruction must survive the compaction",
            prompt.contains("Keep the tone slightly formal.")
        )
    }

    // ── Social path (the News to Social Posts case) ──

    @Test
    fun `social prompt with X and LinkedIn is under the local-model-safe size`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.SOCIAL_POST,
                outputFormat = OutputFormat.JSON,
                socialPlatforms = setOf(SocialPlatform.X, SocialPlatform.LINKEDIN)
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)

        // Pre-compaction size was ~1100 chars with two platforms (including
        // the Format-rules line added earlier). Target is <= 750 chars so a
        // workflow with global instruction + extra platforms still fits under
        // LocalPromptBuilder.INSTRUCTION_CHARS = 900 without truncation.
        assertTrue(
            "social prompt should be <= 750 chars, got ${prompt.length}",
            prompt.length <= 750
        )
    }

    @Test
    fun `social prompt preserves every essential JSON instruction`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.SOCIAL_POST,
                outputFormat = OutputFormat.JSON,
                socialPlatforms = setOf(SocialPlatform.X, SocialPlatform.LINKEDIN)
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)

        // 1. Strict-JSON-only directive:
        assertTrue(
            "must still demand strict JSON output",
            prompt.contains("STRICT valid JSON", ignoreCase = true) ||
                prompt.contains("valid JSON", ignoreCase = true)
        )
        assertTrue(
            "must still forbid code fences",
            prompt.contains("code fences", ignoreCase = true) ||
                prompt.contains("no code fences", ignoreCase = true)
        )

        // 2. The exact schema is shown (inline form is fine — smaller models
        // follow inline examples just as well as the old multi-line block).
        assertTrue("must show outputs[] schema", prompt.contains("\"outputs\""))
        assertTrue("must show platform key", prompt.contains("\"platform\""))
        assertTrue("must show content key", prompt.contains("\"content\""))
        assertTrue("must show title key", prompt.contains("\"title\""))
        assertTrue("must show notes key", prompt.contains("\"notes\""))

        // 3. Platform differentiation survives.
        assertTrue("must list X platform", prompt.contains("X"))
        assertTrue("must list LinkedIn platform", prompt.contains("LinkedIn"))
        assertTrue("X tone hint kept (under 280 chars)", prompt.contains("280"))
        assertTrue("LinkedIn tone hint kept (professional)", prompt.contains("professional"))

        // 4. Anti-duplication still enforced.
        assertTrue(
            "must require distinct per-platform content",
            prompt.contains("distinct", ignoreCase = true) ||
                prompt.contains("tailored", ignoreCase = true) ||
                prompt.contains("no duplicates", ignoreCase = true)
        )
    }

    @Test
    fun `social prompt drops the verbose multi-line JSON schema block`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.SOCIAL_POST,
                outputFormat = OutputFormat.JSON,
                socialPlatforms = setOf(SocialPlatform.X, SocialPlatform.LINKEDIN)
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)

        // The old multi-line block used phrases like "The generated content for
        // this platform" and "Optional short title or hook" — those were the
        // single biggest token waste.
        assertFalse(
            "removed verbose content-field description",
            prompt.contains("The generated content for this platform")
        )
        assertFalse(
            "removed verbose title-field description",
            prompt.contains("Optional short title or hook")
        )
        assertFalse(
            "removed verbose notes-field description",
            prompt.contains("Optional notes like suggested hashtags")
        )
    }

    @Test
    fun `BOTH mode keeps briefing-entry instruction in social prompt`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.BOTH,
                outputFormat = OutputFormat.JSON,
                socialPlatforms = setOf(SocialPlatform.X)
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)
        assertTrue(
            "BOTH mode must still instruct the model to add a Briefing entry",
            prompt.contains("Briefing")
        )
    }

    // ── Format-specific guards ──

    @Test
    fun `plain text format stays declarative and short`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.PLAIN_TEXT
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)
        assertTrue(prompt.contains("plain text", ignoreCase = true))
        assertTrue(prompt.contains("No Markdown", ignoreCase = true) || prompt.contains("no markdown", ignoreCase = true))
    }

    @Test
    fun `JSON format keeps strict wording`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.JSON
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)
        assertTrue(
            "JSON output directive must still be present for non-social JSON mode",
            prompt.contains("STRICT valid JSON", ignoreCase = true) ||
                prompt.contains("valid JSON", ignoreCase = true)
        )
    }

    @Test
    fun `sources section separator is the compact single-word form`() {
        val template = pasteTextTemplate(
            WorkflowOutputConfig(
                outputType = WorkflowOutputType.BRIEFING,
                outputFormat = OutputFormat.MARKDOWN
            )
        )
        val prompt = runAndCaptureSystemPrompt(template)
        assertTrue("compact Sources: footer is used", prompt.contains("Sources:"))
        assertEquals(
            "prompt ends with the Sources: footer (plus trailing newline), " +
                "so user content slots in cleanly",
            true, prompt.trimEnd().endsWith("Sources:")
        )
    }
}

package com.automatist.app.domain.readiness

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.offline.FakeOfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelStatus
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Locks in the contract for [WorkflowActionType.AI_PROMPT] readiness.
 *
 * Before this fix, AI Prompt was evaluated against
 * `SecureStorage.getApiKey(defaultProfile.providerType)` — which is always
 * null for LOCAL_AI (offline models have no API key) and for FAKE (demo
 * profile). The action therefore flagged "Needs setup" even when the user
 * had a perfectly usable local or default AI profile configured, and the
 * CTA sent them to Service API Keys — the wrong page.
 *
 * The correct semantics: AI Prompt is ready when a usable default AI
 * Profile exists (key present for cloud, model installed for local, FAKE
 * is always ready). Missing setup routes the user to AI Profiles.
 *
 * These tests guard the behaviour at the evaluator layer — the UI's
 * section-picking logic ([settingsSectionFor]) is a pure function over the
 * same requirement metadata and doesn't need separate integration coverage.
 */
class AIPromptReadinessTest {

    private lateinit var repo: FakeWorkflowRepository
    private lateinit var storage: FakeSecureStorage
    private lateinit var offlineRepo: FakeOfflineModelRepository
    private lateinit var evaluator: ReadinessEvaluator

    @Before
    fun setUp() {
        repo = FakeWorkflowRepository()
        storage = FakeSecureStorage()
        offlineRepo = FakeOfflineModelRepository()
        evaluator = ReadinessEvaluator(storage, repo, offlineRepo)
    }

    // ── Ready paths ──

    @Test
    fun `AI Prompt ready when a cloud default profile has its API key configured`() = runTest {
        repo.seedProfile(
            ProviderProfile(
                id = "p1", name = "My OpenAI", providerType = ProviderType.OPENAI,
                modelId = "gpt-4o-mini", isDefault = true
            )
        )
        storage.setApiKey(ProviderType.OPENAI, "sk-live-…")

        val result = evaluator.evaluateAction(WorkflowActionType.AI_PROMPT)

        assertTrue("cloud default with key must be ready", result.isReady)
        assertEquals(0, result.needsSetupCount)
    }

    @Test
    fun `AI Prompt ready when default profile is LOCAL_AI with the model installed`() = runTest {
        // The exact failure shape from the bug report: a local/offline
        // default profile flagged "Needs setup" because the old logic looked
        // for a LOCAL_AI API key, which doesn't exist.
        val modelId = OfflineModelCatalog.GEMINI_NANO_ID
        repo.seedProfile(
            ProviderProfile(
                id = "p1", name = "Gemini Nano", providerType = ProviderType.LOCAL_AI,
                modelId = modelId, isDefault = true
            )
        )
        offlineRepo.setStatus(modelId, OfflineModelStatus.INSTALLED)

        val result = evaluator.evaluateAction(WorkflowActionType.AI_PROMPT)

        assertTrue(
            "LOCAL_AI default with an installed model must be ready — this is the main user-reported bug",
            result.isReady
        )
    }

    @Test
    fun `AI Prompt ready when default profile is FAKE (demo)`() = runTest {
        // FAKE legitimately has no API key. It is the first-run demo profile
        // and must not block any AI-driven action.
        repo.seedProfile(
            ProviderProfile(
                id = "p1", name = "Demo", providerType = ProviderType.FAKE,
                modelId = "fake", isDefault = true
            )
        )

        val result = evaluator.evaluateAction(WorkflowActionType.AI_PROMPT)

        assertTrue("FAKE default must count as ready", result.isReady)
    }

    // ── Blocked paths ──

    @Test
    fun `AI Prompt blocked when no default AI profile exists`() = runTest {
        val result = evaluator.evaluateAction(WorkflowActionType.AI_PROMPT)

        assertFalse("no default profile → not ready", result.isReady)
        assertEquals(1, result.needsSetupCount)
        val hint = result.requirements.first().actionHint
        assertTrue(
            "hint must direct the user to AI Profiles, not Service Keys (got: '$hint')",
            hint.contains("AI Profile", ignoreCase = true)
        )
    }

    @Test
    fun `AI Prompt blocked when cloud default profile is missing its API key`() = runTest {
        repo.seedProfile(
            ProviderProfile(
                id = "p1", name = "My OpenAI", providerType = ProviderType.OPENAI,
                modelId = "gpt-4o-mini", isDefault = true
            )
        )
        // No storage.setApiKey(OPENAI, …)

        val result = evaluator.evaluateAction(WorkflowActionType.AI_PROMPT)

        assertFalse(result.isReady)
        val hint = result.requirements.first().actionHint
        assertTrue(
            "cloud-missing-key hint must point at AI Profiles, not Service Keys (got: '$hint')",
            hint.contains("AI Profile", ignoreCase = true) || hint.contains("API key", ignoreCase = true)
        )
        assertFalse(
            "hint must not mention Service Keys — that was the bug's wrong navigation target",
            hint.contains("Service", ignoreCase = true)
        )
    }

    @Test
    fun `AI Prompt blocked when LOCAL_AI default profile model is not installed`() = runTest {
        val modelId = OfflineModelCatalog.GEMINI_NANO_ID
        repo.seedProfile(
            ProviderProfile(
                id = "p1", name = "Gemini Nano", providerType = ProviderType.LOCAL_AI,
                modelId = modelId, isDefault = true
            )
        )
        offlineRepo.setStatus(modelId, OfflineModelStatus.NOT_INSTALLED)

        val result = evaluator.evaluateAction(WorkflowActionType.AI_PROMPT)

        assertFalse(result.isReady)
        val hint = result.requirements.first().actionHint
        assertTrue(
            "LOCAL_AI missing-model hint must direct to On-device AI (got: '$hint')",
            hint.contains("On-device AI", ignoreCase = true)
        )
        assertFalse(
            "hint must not mention Service Keys",
            hint.contains("Service Key", ignoreCase = true)
        )
    }

    @Test
    fun `AI Prompt blocked when default profile is disabled`() = runTest {
        repo.seedProfile(
            ProviderProfile(
                id = "p1", name = "Disabled", providerType = ProviderType.OPENAI,
                modelId = "gpt-4o-mini", isDefault = true, isEnabled = false
            )
        )
        storage.setApiKey(ProviderType.OPENAI, "sk-live-…")

        val result = evaluator.evaluateAction(WorkflowActionType.AI_PROMPT)

        assertFalse("a disabled default profile cannot satisfy the requirement", result.isReady)
        val hint = result.requirements.first().actionHint
        assertTrue(
            "hint must tell the user to enable the profile (got: '$hint')",
            hint.contains("Enable", ignoreCase = true)
        )
    }

    // ── Service-key actions must remain unchanged ──

    @Test
    fun `service-key action (Weather) still flags Service Keys, not AI Profiles`() = runTest {
        // Regression guard: our fix targeted the API_KEY branch only.
        // SERVICE_KEY actions must continue to route to Service API Keys.
        val result = evaluator.evaluateAction(WorkflowActionType.FETCH_WEATHER)

        assertFalse("no weather service key configured → blocked", result.isReady)
        val hint = result.requirements.first().actionHint
        assertTrue(
            "Weather hint must still direct to Service Keys (got: '$hint')",
            hint.contains("Service", ignoreCase = true)
        )
    }

    @Test
    fun `service-key action becomes ready once its service key is set`() = runTest {
        storage.setServiceKey(
            com.automatist.app.domain.models.WeatherService.SERVICE_KEY,
            "owm-xyz"
        )

        val result = evaluator.evaluateAction(WorkflowActionType.FETCH_WEATHER)

        assertTrue("Weather with service key must be ready", result.isReady)
    }

    // ── Requirement metadata invariants (fence for the UI's section-picker) ──

    @Test
    fun `AI_PROMPT registry entry declares API_KEY type (not SERVICE_KEY)`() = runTest {
        // The UI's settingsSectionFor() function routes by requirement type —
        // API_KEY → AI Profiles, SERVICE_KEY → Service Keys. This test ensures
        // the registry entry lines up with that routing. A future edit that
        // flipped AI_PROMPT to SERVICE_KEY would silently re-introduce the bug.
        val info = com.automatist.app.domain.actions.WorkflowActionRegistry.getInfo(WorkflowActionType.AI_PROMPT)
        assertEquals(1, info.setupRequirements.size)
        assertEquals(
            com.automatist.app.domain.actions.WorkflowActionRegistry.RequirementType.API_KEY,
            info.setupRequirements.first().type
        )
        // And conversely the weather action really is SERVICE_KEY.
        val weatherInfo = com.automatist.app.domain.actions.WorkflowActionRegistry.getInfo(WorkflowActionType.FETCH_WEATHER)
        assertEquals(
            com.automatist.app.domain.actions.WorkflowActionRegistry.RequirementType.SERVICE_KEY,
            weatherInfo.setupRequirements.first().type
        )
    }

    @Test
    fun `evaluator result keeps requirement metadata for the caller to render`() = runTest {
        // The UI renders per-requirement text ("- AI provider API key") using
        // requirement.label. Locking in that the label survives evaluation.
        val result = evaluator.evaluateAction(WorkflowActionType.AI_PROMPT)

        assertNotNull(result.requirements.first().requirement)
        assertTrue(result.requirements.first().requirement.label.isNotBlank())
    }
}

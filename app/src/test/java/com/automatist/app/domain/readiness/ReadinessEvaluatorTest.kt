package com.automatist.app.domain.readiness

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.WeatherService
import com.automatist.app.domain.models.WorkflowAction
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.offline.FakeOfflineModelRepository
import com.automatist.app.domain.offline.FakeOfflineModelResolver
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelStatus
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReadinessEvaluatorTest {

    private lateinit var repo: FakeWorkflowRepository
    private lateinit var storage: FakeSecureStorage
    private lateinit var offlineRepo: FakeOfflineModelRepository
    private lateinit var offlineResolver: FakeOfflineModelResolver
    private lateinit var evaluator: ReadinessEvaluator

    @Before
    fun setUp() {
        repo = FakeWorkflowRepository()
        storage = FakeSecureStorage()
        offlineRepo = FakeOfflineModelRepository()
        offlineResolver = FakeOfflineModelResolver()
        evaluator = ReadinessEvaluator(storage, repo, offlineRepo, offlineResolver)
    }

    // ── Empty workflow (zero actions) ──

    @Test
    fun `empty workflow is fully ready`() = runTest {
        val template = WorkflowTemplate(name = "Blank")

        val result = evaluator.evaluateWorkflow(template)

        assertTrue("Empty workflow should be fully ready", result.isFullyReady)
        assertTrue("No action readiness for empty workflow", result.actionReadiness.isEmpty())
        assertTrue("No profile issues for empty workflow", result.profileIssues.isEmpty())
    }

    @Test
    fun `empty workflow does not report setup count`() = runTest {
        val template = WorkflowTemplate(name = "Blank")

        val result = evaluator.evaluateWorkflow(template)

        assertEquals(0, result.needsSetupActions.size)
        assertEquals(0, result.totalCount)
    }

    @Test
    fun `empty workflow is ready even when default profile has missing API key`() = runTest {
        // Set up a default profile with no API key configured
        repo.seedProfile(
            ProviderProfile(
                id = "prof-1", name = "My OpenAI", providerType = ProviderType.OPENAI,
                modelId = "gpt-4", isDefault = true
            )
        )
        // Intentionally do NOT set an API key for OPENAI

        val template = WorkflowTemplate(name = "Blank")
        val result = evaluator.evaluateWorkflow(template)

        assertTrue("Empty workflow should be ready regardless of profile issues", result.isFullyReady)
        assertTrue("Empty workflow should have no profile issues", result.profileIssues.isEmpty())
    }

    @Test
    fun `empty workflow is ready even when no profiles exist at all`() = runTest {
        val template = WorkflowTemplate(name = "Blank")
        val result = evaluator.evaluateWorkflow(template)

        assertTrue(result.isFullyReady)
        assertFalse("Should report no provider profile exists", result.hasProviderProfile)
    }

    // ── Unused service keys do not trigger warnings ──

    @Test
    fun `workflow with only PASTE_TEXT action does not require service keys`() = runTest {
        val template = WorkflowTemplate(
            name = "Text Only",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        val serviceKeyIssues = result.actionReadiness.flatMap { it.requirements }
            .filter { it.requirement.type == com.automatist.app.domain.actions.WorkflowActionRegistry.RequirementType.SERVICE_KEY }
            .filter { it.status == ReadinessStatus.NEEDS_SETUP }

        assertTrue("No service key warnings for PASTE_TEXT workflow", serviceKeyIssues.isEmpty())
    }

    // ── Used/missing service key still triggers warning ──

    @Test
    fun `weather action warns when service key is missing`() = runTest {
        val template = WorkflowTemplate(
            name = "Weather Check",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.FETCH_WEATHER, label = "Weather")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse("Workflow with missing weather key should not be fully ready", result.isFullyReady)
        assertTrue("Weather action should need setup", result.needsSetupActions.isNotEmpty())
        assertEquals(WorkflowActionType.FETCH_WEATHER, result.needsSetupActions.first().type)
    }

    @Test
    fun `weather action is ready when service key is configured`() = runTest {
        storage.setServiceKey(WeatherService.SERVICE_KEY, "test-key-123")

        val template = WorkflowTemplate(
            name = "Weather Check",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.FETCH_WEATHER, label = "Weather")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        val weatherReadiness = result.actionReadiness.find { it.type == WorkflowActionType.FETCH_WEATHER }
        assertNotNull(weatherReadiness)
        assertTrue("Weather action should be ready with key configured", weatherReadiness!!.isReady)
    }

    // ── Real profile issues still warn for non-empty workflows ──

    @Test
    fun `non-empty workflow warns when default profile has missing API key`() = runTest {
        repo.seedProfile(
            ProviderProfile(
                id = "prof-1", name = "My OpenAI", providerType = ProviderType.OPENAI,
                modelId = "gpt-4", isDefault = true
            )
        )
        // No API key set

        val template = WorkflowTemplate(
            name = "Has Actions",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse("Should not be fully ready with missing API key", result.isFullyReady)
        assertTrue("Should report profile issues", result.profileIssues.isNotEmpty())
        assertTrue(
            "Profile issue should mention API key",
            result.profileIssues.any { it.contains("API key") }
        )
    }

    @Test
    fun `non-empty workflow warns when explicit profile is disabled`() = runTest {
        repo.seedProfile(
            ProviderProfile(
                id = "prof-disabled", name = "Disabled Profile", providerType = ProviderType.ANTHROPIC,
                modelId = "claude-3", isDefault = false, isEnabled = false
            )
        )

        val template = WorkflowTemplate(
            name = "Has Actions",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            ),
            defaultProfileId = "prof-disabled"
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse(result.isFullyReady)
        assertTrue(result.profileIssues.any { it.contains("disabled") })
    }

    @Test
    fun `non-empty workflow is ready when profile and key are properly configured`() = runTest {
        repo.seedProfile(
            ProviderProfile(
                id = "prof-1", name = "My OpenAI", providerType = ProviderType.OPENAI,
                modelId = "gpt-4", isDefault = true
            )
        )
        storage.setApiKey(ProviderType.OPENAI, "sk-test-key")

        val template = WorkflowTemplate(
            name = "Good Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertTrue("Fully configured workflow should be ready", result.isFullyReady)
        assertTrue(result.profileIssues.isEmpty())
    }

    // ── LOCAL_AI (On-device) profile readiness ──

    @Test
    fun `LOCAL_AI profile with model installed is ready`() = runTest {
        offlineRepo.setStatus(OfflineModelCatalog.GEMINI_NANO_ID, OfflineModelStatus.INSTALLED)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-offline", name = "On-device", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMINI_NANO_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertTrue("LOCAL_AI workflow with installed model should be fully ready", result.isFullyReady)
        assertTrue("No profile issues expected", result.profileIssues.isEmpty())
    }

    @Test
    fun `LOCAL_AI profile with model NOT_INSTALLED is not ready and suggests check`() = runTest {
        // Model status defaults to NOT_INSTALLED in FakeOfflineModelRepository
        repo.seedProfile(
            ProviderProfile(
                id = "prof-offline", name = "On-device", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMINI_NANO_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse("LOCAL_AI workflow with unchecked model should NOT be ready", result.isFullyReady)
        assertTrue("Should report issue", result.profileIssues.isNotEmpty())
        assertTrue(
            "NOT_INSTALLED issue should mention availability not checked",
            result.profileIssues.any { it.contains("not been checked") || it.contains("Check Availability") }
        )
    }

    @Test
    fun `LOCAL_AI profile with UNSUPPORTED model is not ready and explains device requirements`() = runTest {
        offlineRepo.setStatus(OfflineModelCatalog.GEMINI_NANO_ID, OfflineModelStatus.UNSUPPORTED)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-offline", name = "On-device", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMINI_NANO_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse("LOCAL_AI workflow on unsupported device should NOT be ready", result.isFullyReady)
        assertTrue(
            "UNSUPPORTED issue should explain device requirements",
            result.profileIssues.any { it.contains("not supported") && it.contains("Android 14") }
        )
        assertTrue(
            "UNSUPPORTED issue should suggest switching to cloud",
            result.profileIssues.any { it.contains("cloud-based") }
        )
    }

    @Test
    fun `LOCAL_AI profile with FAILED model is not ready and suggests retry`() = runTest {
        offlineRepo.setStatus(OfflineModelCatalog.GEMINI_NANO_ID, OfflineModelStatus.FAILED)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-offline", name = "On-device", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMINI_NANO_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse("LOCAL_AI workflow with failed check should NOT be ready", result.isFullyReady)
        assertTrue(
            "FAILED issue should mention check failed and suggest retry",
            result.profileIssues.any { it.contains("failed") && it.contains("Retry") }
        )
    }

    @Test
    fun `LOCAL_AI profile with DOWNLOADING model is not ready and asks to wait`() = runTest {
        offlineRepo.setStatus(OfflineModelCatalog.GEMINI_NANO_ID, OfflineModelStatus.DOWNLOADING)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-offline", name = "On-device", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMINI_NANO_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse("LOCAL_AI workflow with check in progress should NOT be ready", result.isFullyReady)
        assertTrue(
            "DOWNLOADING issue should mention checking availability",
            result.profileIssues.any { it.contains("checking availability") }
        )
    }

    @Test
    fun `LOCAL_AI profile does not require an API key check`() = runTest {
        offlineRepo.setStatus(OfflineModelCatalog.GEMINI_NANO_ID, OfflineModelStatus.INSTALLED)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-offline", name = "On-device", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMINI_NANO_ID, isDefault = true
            )
        )
        // No API key set — should not matter for LOCAL_AI

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertTrue("LOCAL_AI profile should not need an API key", result.isFullyReady)
        assertTrue(result.profileIssues.none { it.contains("API key") })
    }

    @Test
    fun `existing cloud API profile readiness not affected by offline model state`() = runTest {
        // Cloud profile properly configured
        repo.seedProfile(
            ProviderProfile(
                id = "prof-cloud", name = "My OpenAI", providerType = ProviderType.OPENAI,
                modelId = "gpt-4o", isDefault = true
            )
        )
        storage.setApiKey(ProviderType.OPENAI, "sk-test-key")
        // Offline model NOT installed — must not affect cloud profile evaluation

        val template = WorkflowTemplate(
            name = "Cloud Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertTrue("Cloud workflow should still be ready regardless of offline model state", result.isFullyReady)
        assertTrue(result.profileIssues.isEmpty())
    }

    // ── Downloadable LOCAL_AI (Gemma 3n E2B) readiness ──

    @Test
    fun `downloadable LOCAL_AI profile with model installed is ready`() = runTest {
        offlineRepo.setStatus(OfflineModelCatalog.GEMMA_3N_E2B_ID, OfflineModelStatus.INSTALLED)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-gemma", name = "Gemma Offline", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMMA_3N_E2B_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertTrue("Downloadable LOCAL_AI workflow with installed model should be ready", result.isFullyReady)
        assertTrue(result.profileIssues.isEmpty())
    }

    @Test
    fun `downloadable LOCAL_AI profile with model NOT_INSTALLED suggests download`() = runTest {
        // Gemma 3n E2B defaults to NOT_INSTALLED
        repo.seedProfile(
            ProviderProfile(
                id = "prof-gemma", name = "Gemma Offline", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMMA_3N_E2B_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse("Downloadable LOCAL_AI workflow without download should NOT be ready", result.isFullyReady)
        assertTrue("Should report issue", result.profileIssues.isNotEmpty())
        assertTrue(
            "NOT_INSTALLED issue for downloadable model should mention Download",
            result.profileIssues.any { it.contains("Download") }
        )
    }

    @Test
    fun `downloadable LOCAL_AI profile does not require API key`() = runTest {
        offlineRepo.setStatus(OfflineModelCatalog.GEMMA_3N_E2B_ID, OfflineModelStatus.INSTALLED)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-gemma", name = "Gemma Offline", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMMA_3N_E2B_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertTrue("Downloadable LOCAL_AI profile should not need an API key", result.isFullyReady)
        assertTrue(result.profileIssues.none { it.contains("API key") })
    }

    @Test
    fun `downloadable LOCAL_AI and system-managed LOCAL_AI coexist independently`() = runTest {
        // Gemini Nano installed, Gemma 3n E2B NOT installed
        offlineRepo.setStatus(OfflineModelCatalog.GEMINI_NANO_ID, OfflineModelStatus.INSTALLED)
        // Gemma 3n E2B defaults to NOT_INSTALLED

        // Profile uses Gemma 3n E2B — should not be ready
        repo.seedProfile(
            ProviderProfile(
                id = "prof-gemma", name = "Gemma Offline", providerType = ProviderType.LOCAL_AI,
                modelId = OfflineModelCatalog.GEMMA_3N_E2B_ID, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(
                WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input")
            )
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse(
            "Gemma 3n E2B profile should not be ready just because Gemini Nano is installed",
            result.isFullyReady
        )
    }

    // ── Custom (user-added) LOCAL_AI model readiness ──

    private fun customEntry(id: String) = com.automatist.app.domain.offline.OfflineModelEntry(
        id = id,
        displayName = "My Custom Model",
        description = "Custom MediaPipe model.",
        sizeLabel = "~521 MB download",
        isSystemManaged = false,
        runtimeType = com.automatist.app.domain.offline.OfflineRuntimeType.DOWNLOADABLE,
        downloadUrl = "https://example.com/model.task",
        downloadSizeBytes = 521_000_000L,
        fileSha256 = "a".repeat(64),
        modelFileName = "$id.task",
        contextWindowChars = 2_500,
        minimumRamMb = 3_000,
        isUserAdded = true
    )

    @Test
    fun `custom LOCAL_AI profile with installed model resolves and is ready`() = runTest {
        val id = "custom-abc"
        offlineResolver.seedCustom(customEntry(id))
        offlineRepo.setStatus(id, OfflineModelStatus.INSTALLED)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-custom", name = "Custom Offline", providerType = ProviderType.LOCAL_AI,
                modelId = id, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input"))
        )

        val result = evaluator.evaluateWorkflow(template)

        assertTrue("Installed custom model should make workflow ready", result.isFullyReady)
        assertTrue(result.profileIssues.isEmpty())
    }

    @Test
    fun `custom LOCAL_AI profile NOT_INSTALLED suggests Download not Check Availability`() = runTest {
        val id = "custom-def"
        offlineResolver.seedCustom(customEntry(id)) // defaults to NOT_INSTALLED
        repo.seedProfile(
            ProviderProfile(
                id = "prof-custom", name = "Custom Offline", providerType = ProviderType.LOCAL_AI,
                modelId = id, isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input"))
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse(result.isFullyReady)
        assertTrue(
            "Custom downloadable model must be treated as downloadable (Download, not Check Availability)",
            result.profileIssues.any { it.contains("Download") }
        )
        assertTrue(
            "Custom model issue should use its display name",
            result.profileIssues.any { it.contains("My Custom Model") }
        )
    }

    @Test
    fun `LOCAL_AI profile with unknown custom model fails safely without mislabeling`() = runTest {
        // Profile points at a custom model ID that the resolver does NOT know
        // (e.g. the source was removed). Must NOT silently fall back to a built-in model.
        offlineRepo.setStatus("custom-removed", OfflineModelStatus.INSTALLED)
        repo.seedProfile(
            ProviderProfile(
                id = "prof-ghost", name = "Ghost Offline", providerType = ProviderType.LOCAL_AI,
                modelId = "custom-removed", isDefault = true
            )
        )

        val template = WorkflowTemplate(
            name = "Offline Workflow",
            actions = listOf(WorkflowAction(id = "a1", type = WorkflowActionType.PASTE_TEXT, label = "Input"))
        )

        val result = evaluator.evaluateWorkflow(template)

        assertFalse("Unknown custom model must not be considered ready", result.isFullyReady)
        assertTrue("Should report an issue", result.profileIssues.isNotEmpty())
        assertTrue(
            "Issue should state the model is no longer configured",
            result.profileIssues.any { it.contains("no longer configured") }
        )
        assertTrue(
            "Must not mislabel the missing model as the built-in Gemini Nano (Pixel/Galaxy)",
            result.profileIssues.none { it.contains("Pixel 8+") }
        )
    }
}

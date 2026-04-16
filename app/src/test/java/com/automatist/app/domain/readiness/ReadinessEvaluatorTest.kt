package com.automatist.app.domain.readiness

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.WeatherService
import com.automatist.app.domain.models.WorkflowAction
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReadinessEvaluatorTest {

    private lateinit var repo: FakeWorkflowRepository
    private lateinit var storage: FakeSecureStorage
    private lateinit var evaluator: ReadinessEvaluator

    @Before
    fun setUp() {
        repo = FakeWorkflowRepository()
        storage = FakeSecureStorage()
        evaluator = ReadinessEvaluator(storage, repo)
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
}

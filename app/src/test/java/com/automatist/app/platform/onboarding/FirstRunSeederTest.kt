package com.automatist.app.platform.onboarding

import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.workflow.FakeWorkflowRepository
import com.automatist.app.domain.workflow.WorkflowPortabilityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirstRunSeederTest {

    private lateinit var repo: FakeWorkflowRepository
    private lateinit var state: FakeSeedingStateStore
    private lateinit var portability: WorkflowPortabilityManager
    private lateinit var seeder: FirstRunSeeder

    // A minimal News-to-Social payload that exercises the exact path production uses —
    // empty defaultProfileId / outputProfileId / action profileId so the workflow
    // inherits whatever the app default is.
    private val seededJson = """
        {
          "schemaVersion": 1,
          "type": "automatist-workflow",
          "exportedAt": "2026-04-21T00:00:00Z",
          "app": {"name": "Automatist", "exportFormatVersion": 1},
          "workflow": {
            "name": "News to Social",
            "description": "",
            "trigger": {"type": "interval", "intervalMinutes": 15},
            "actions": [{
              "type": "FETCH_RSS_FEED",
              "label": "News Feed",
              "sourceData": "https://techcrunch.com/feed/",
              "instruction": "pick items",
              "order": 0,
              "isEnabled": true,
              "extraConfig": {},
              "profileId": ""
            }],
            "globalInstruction": "",
            "outputConfig": {
              "outputType": "SOCIAL_POST",
              "outputFormat": "MARKDOWN",
              "customInstruction": "",
              "socialPlatforms": ["X", "LINKEDIN"],
              "saveToHistory": true,
              "outputProfileId": "",
              "socialGlobalInstruction": "",
              "platformInstructions": {},
              "customPlatforms": [],
              "inputCompaction": "AGGRESSIVE",
              "numberOfOutputs": 1
            },
            "notifyOnCompletion": true,
            "notifyOnStart": true,
            "sourceTemplateId": "news_to_social",
            "category": "Social Media",
            "customization": {
              "editableSections": ["BASICS","TRIGGER","ACTIONS","INSTRUCTIONS","OUTPUT","NOTIFICATIONS"],
              "lockedActionIds": [],
              "canAddActions": true,
              "canRemoveActions": true
            },
            "defaultProfileId": ""
          },
          "references": {"profiles": [], "savedNotes": []}
        }
    """.trimIndent()

    @Before
    fun setUp() {
        repo = FakeWorkflowRepository()
        state = FakeSeedingStateStore()
        portability = WorkflowPortabilityManager(repo)
        seeder = FirstRunSeeder(
            workflowRepository = repo,
            seedingState = state,
            portabilityManager = portability,
            jsonSource = SeededWorkflowJsonSource { seededJson }
        )
    }

    @Test
    fun `fresh install seeds one fake profile and one News to Social workflow`() = runTest {
        seeder.seedIfNeeded()

        val profiles = repo.getAllProfiles().first()
        assertEquals(1, profiles.size)
        assertEquals(ProviderType.FAKE, profiles[0].providerType)
        assertEquals(profiles[0].id, repo.getDefaultProfile()?.id)

        val templates = repo.getAllTemplates().first()
        assertEquals(1, templates.size)
        assertEquals("News to Social", templates[0].name)
        assertEquals("news_to_social", templates[0].sourceTemplateId)
        assertTrue("start notifications on by default", templates[0].notifyOnStart)
        assertTrue("completion notifications on by default", templates[0].notifyOnCompletion)

        // Seeded workflow should leave profile IDs blank so it inherits app default.
        assertEquals("", templates[0].defaultProfileId)
        assertEquals("", templates[0].outputConfig.outputProfileId)

        assertEquals(FirstRunSeeder.CURRENT_VERSION, state.version)
        assertEquals(profiles[0].id, state.fakeProfileId)
    }

    @Test
    fun `re-running seed is a no-op — no duplicate workflow or profile`() = runTest {
        seeder.seedIfNeeded()
        seeder.seedIfNeeded()
        seeder.seedIfNeeded()

        assertEquals(1, repo.getAllProfiles().first().size)
        assertEquals(1, repo.getAllTemplates().first().size)
    }

    @Test
    fun `existing user's default profile is not overwritten`() = runTest {
        // Simulate an existing install with a user-created default profile.
        val userDefault = ProviderProfile(
            id = "user-1",
            name = "My OpenAI",
            providerType = ProviderType.OPENAI,
            modelId = "gpt-4",
            isDefault = true
        )
        repo.seedProfile(userDefault)

        seeder.seedIfNeeded()

        // Fake profile still gets created (cleans up data model) but must NOT replace default.
        assertEquals("user-1", repo.getDefaultProfile()?.id)
    }

    @Test
    fun `does not duplicate fake profile when one already exists`() = runTest {
        val preExistingFake = ProviderProfile(
            id = "pre-fake",
            name = "Old Fake",
            providerType = ProviderType.FAKE,
            modelId = "fake-demo"
        )
        repo.seedProfile(preExistingFake)

        seeder.seedIfNeeded()

        val fakes = repo.getAllProfiles().first().filter { it.providerType == ProviderType.FAKE }
        assertEquals("should reuse the existing fake, not create another", 1, fakes.size)
        assertEquals("pre-fake", state.fakeProfileId)
    }

    @Test
    fun `does not duplicate workflow if one with same sourceTemplateId already exists`() = runTest {
        // Simulate an install where the flag was somehow lost but the seeded workflow is still there.
        val existing = com.automatist.app.domain.models.WorkflowTemplate(
            name = "News to Social",
            sourceTemplateId = "news_to_social"
        )
        repo.seedTemplate(existing)
        // Version 0 — would normally re-run seeding.
        state.version = 0

        seeder.seedIfNeeded()

        assertEquals(1, repo.getAllTemplates().first().size)
    }

    // ── Legacy Article Briefing cleanup (v2 migration) ──

    private val legacyAbDescription =
        "Transform shared articles and text into summaries, threads, or professional posts. Your starter workflow."

    private fun legacyArticleBriefing(
        id: Long = 0,
        lastRunAtMillis: Long? = null,
        name: String = "Article Briefing",
        description: String = legacyAbDescription,
        sourceTemplateId: String = "article_summarizer"
    ) = com.automatist.app.domain.models.WorkflowTemplate(
        id = id,
        name = name,
        description = description,
        sourceTemplateId = sourceTemplateId,
        lastRunAtMillis = lastRunAtMillis
    )

    @Test
    fun `v2 cleanup removes stale auto-seeded Article Briefing on upgrade`() = runTest {
        // Simulate upgrade: user already at v1 with a seeded News to Social workflow,
        // plus a stale Article Briefing carried over from pre-v1.
        state.version = 1
        repo.seedTemplate(legacyArticleBriefing())
        repo.seedTemplate(
            com.automatist.app.domain.models.WorkflowTemplate(
                name = "News to Social",
                sourceTemplateId = "news_to_social"
            )
        )

        seeder.seedIfNeeded()

        val remaining = repo.getAllTemplates().first()
        assertEquals(1, remaining.size)
        assertEquals("News to Social", remaining[0].name)
        assertEquals(FirstRunSeeder.CURRENT_VERSION, state.version)
    }

    @Test
    fun `v2 cleanup does not touch user-renamed Article Briefing`() = runTest {
        state.version = 1
        val renamed = legacyArticleBriefing(name = "My Morning Brief")
        repo.seedTemplate(renamed)

        seeder.seedIfNeeded()

        val remaining = repo.getAllTemplates().first()
        assertTrue("user-renamed workflow must be preserved",
            remaining.any { it.name == "My Morning Brief" })
    }

    @Test
    fun `v2 cleanup does not touch Article Briefing that has been run`() = runTest {
        state.version = 1
        val used = legacyArticleBriefing(lastRunAtMillis = 1_700_000_000_000L)
        repo.seedTemplate(used)

        seeder.seedIfNeeded()

        val remaining = repo.getAllTemplates().first()
        assertTrue("once-run workflow must be preserved",
            remaining.any { it.name == "Article Briefing" })
    }

    @Test
    fun `v2 cleanup does not touch Article Briefing with edited description`() = runTest {
        state.version = 1
        val edited = legacyArticleBriefing(description = "My custom description")
        repo.seedTemplate(edited)

        seeder.seedIfNeeded()

        val remaining = repo.getAllTemplates().first()
        assertTrue("description-edited workflow must be preserved",
            remaining.any { it.name == "Article Briefing" })
    }

    @Test
    fun `v2 cleanup runs at most once — second pass is a no-op`() = runTest {
        state.version = 1
        repo.seedTemplate(legacyArticleBriefing())

        seeder.seedIfNeeded()
        // A user could then create their own fresh "Article Briefing" via the template
        // browser. The cleanup must not come back and delete that.
        repo.seedTemplate(legacyArticleBriefing(id = 999))

        seeder.seedIfNeeded()

        val remaining = repo.getAllTemplates().first()
        assertTrue("user-created copy after cleanup must survive",
            remaining.any { it.name == "Article Briefing" })
    }

    @Test
    fun `fresh install marks all four Getting Started steps based on post-seed state`() = runTest {
        seeder.seedIfNeeded()

        assertTrue("provider step should be marked after fake profile seed", state.providerSetupDone)
        assertTrue("profile step should be marked after fake profile seed", state.profileSetupDone)
        assertTrue("default step should be marked (fake set as default)", state.defaultSetupDone)
        assertTrue("workflow step should be marked after News to Social seed", state.workflowSetupDone)
    }

    @Test
    fun `fresh install runs both v1 seed and v2 cleanup (cleanup no-op)`() = runTest {
        // current=0: v1 seed runs, v2 cleanup runs but finds nothing to delete.
        seeder.seedIfNeeded()

        val templates = repo.getAllTemplates().first()
        assertEquals(1, templates.size)
        assertEquals("News to Social", templates[0].name)
        assertEquals(FirstRunSeeder.CURRENT_VERSION, state.version)
    }

    @Test
    fun `promotes fake to default when no default exists at all`() = runTest {
        // No profiles and no default at start.
        assertNull(repo.getDefaultProfile())

        seeder.seedIfNeeded()

        val defaultProfile = repo.getDefaultProfile()
        assertNotNull(defaultProfile)
        assertEquals(ProviderType.FAKE, defaultProfile?.providerType)
    }
}

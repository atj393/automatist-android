package com.automatist.app.domain.workflow

import com.automatist.app.domain.models.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WorkflowPortabilityManagerTest {

    private lateinit var repo: FakeWorkflowRepository
    private lateinit var manager: WorkflowPortabilityManager

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        repo = FakeWorkflowRepository()
        manager = WorkflowPortabilityManager(repo)
    }

    // ── Helpers ──

    private fun makeAction(
        id: String = "action-1",
        type: WorkflowActionType = WorkflowActionType.PASTE_TEXT,
        label: String = "Test Action",
        sourceData: String = "some text",
        extraConfig: String = "",
        profileId: String = ""
    ) = WorkflowAction(
        id = id, type = type, label = label,
        sourceData = sourceData, instruction = "", order = 0,
        isEnabled = true, extraConfig = extraConfig, profileId = profileId
    )

    private fun makeTemplate(
        id: Long = 0,
        name: String = "My Workflow",
        actions: List<WorkflowAction> = listOf(makeAction()),
        trigger: WorkflowTrigger = WorkflowTrigger.Manual,
        isEnabled: Boolean = true,
        defaultProfileId: String = "",
        outputConfig: WorkflowOutputConfig = WorkflowOutputConfig()
    ) = WorkflowTemplate(
        id = id, name = name, description = "A test workflow",
        isEnabled = isEnabled, trigger = trigger, actions = actions,
        globalInstruction = "Summarize", outputConfig = outputConfig,
        notifyOnCompletion = true, notifyOnStart = false,
        sourceTemplateId = "test-template", category = "Testing",
        defaultProfileId = defaultProfileId
    )

    private fun makeProfile(
        id: String = "profile-1",
        name: String = "My Claude",
        providerType: ProviderType = ProviderType.ANTHROPIC,
        modelId: String = "claude-sonnet-4-20250514"
    ) = ProviderProfile(id = id, name = name, providerType = providerType, modelId = modelId)

    // ═════════════════════════════════════════════════════════════
    //  DUPLICATE
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `duplicate creates workflow with new ID`() = runTest {
        val sourceId = repo.seedTemplate(makeTemplate(name = "Original"))
        val result = manager.duplicateWorkflow(sourceId)
        assertTrue(result.newWorkflowId != sourceId)
        assertTrue(result.newWorkflowId > 0)
    }

    @Test
    fun `duplicate generates new action IDs`() = runTest {
        val actions = listOf(makeAction(id = "orig-1"), makeAction(id = "orig-2"))
        val sourceId = repo.seedTemplate(makeTemplate(actions = actions))

        manager.duplicateWorkflow(sourceId)

        val duplicated = repo.getTemplateById(sourceId + 1)!!
        val newIds = duplicated.actions.map { it.id }.toSet()
        assertFalse("orig-1" in newIds)
        assertFalse("orig-2" in newIds)
        assertEquals(2, newIds.size)
    }

    @Test
    fun `duplicate preserves workflow definition fields`() = runTest {
        val source = makeTemplate(
            name = "Original",
            actions = listOf(makeAction(label = "Step 1")),
            trigger = WorkflowTrigger.Daily(hour = 9, minute = 30),
            defaultProfileId = "profile-1",
            outputConfig = WorkflowOutputConfig(
                outputType = WorkflowOutputType.SOCIAL_POST,
                inputCompaction = InputCompactionMode.AGGRESSIVE
            )
        )
        val sourceId = repo.seedTemplate(source)

        val result = manager.duplicateWorkflow(sourceId)
        val dup = repo.getTemplateById(result.newWorkflowId)!!

        assertEquals(source.description, dup.description)
        assertEquals(source.globalInstruction, dup.globalInstruction)
        assertEquals(source.trigger, dup.trigger)
        assertEquals(source.notifyOnCompletion, dup.notifyOnCompletion)
        assertEquals(source.notifyOnStart, dup.notifyOnStart)
        assertEquals(source.sourceTemplateId, dup.sourceTemplateId)
        assertEquals(source.category, dup.category)
        assertEquals(source.defaultProfileId, dup.defaultProfileId)
        assertEquals(source.outputConfig, dup.outputConfig)
        assertEquals(1, dup.actions.size)
        assertEquals("Step 1", dup.actions[0].label)
        assertEquals(WorkflowActionType.PASTE_TEXT, dup.actions[0].type)
    }

    @Test
    fun `duplicate creates workflow that is disabled`() = runTest {
        val sourceId = repo.seedTemplate(makeTemplate(isEnabled = true))
        val result = manager.duplicateWorkflow(sourceId)
        val dup = repo.getTemplateById(result.newWorkflowId)!!
        assertFalse(dup.isEnabled)
    }

    @Test
    fun `duplicate clears runtime state`() = runTest {
        val source = makeTemplate().copy(
            lastRunAtMillis = 1000L,
            lastRunStatus = WorkflowRunStatus.COMPLETED
        )
        val sourceId = repo.seedTemplate(source)
        val result = manager.duplicateWorkflow(sourceId)
        val dup = repo.getTemplateById(result.newWorkflowId)!!
        assertNull(dup.lastRunAtMillis)
        assertNull(dup.lastRunStatus)
    }

    @Test
    fun `duplicate generates unique name on conflict`() = runTest {
        repo.seedTemplate(makeTemplate(name = "My Workflow"))
        val sourceId = repo.seedTemplate(makeTemplate(name = "My Workflow"))

        val result = manager.duplicateWorkflow(sourceId)
        assertEquals("My Workflow (Copy)", result.newName)
    }

    @Test
    fun `duplicate generates numbered copy on multiple conflicts`() = runTest {
        repo.seedTemplate(makeTemplate(name = "W"))
        repo.seedTemplate(makeTemplate(name = "W (Copy)"))
        val sourceId = repo.seedTemplate(makeTemplate(name = "W"))

        val result = manager.duplicateWorkflow(sourceId)
        assertEquals("W (Copy 2)", result.newName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate throws for non-existent workflow`() = runTest {
        manager.duplicateWorkflow(999)
    }

    // ═════════════════════════════════════════════════════════════
    //  EXPORT
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `export creates valid envelope with schema markers`() = runTest {
        val sourceId = repo.seedTemplate(makeTemplate(name = "Export Test"))
        val result = manager.exportWorkflow(sourceId)

        val envelope = json.decodeFromString<WorkflowExportEnvelope>(result.json)
        assertEquals(1, envelope.schemaVersion)
        assertEquals("automatist-workflow", envelope.type)
        assertTrue(envelope.exportedAt.isNotBlank())
        assertEquals("Export Test", envelope.workflow.name)
    }

    @Test
    fun `export includes workflow definition fields`() = runTest {
        val source = makeTemplate(
            name = "Full Export",
            actions = listOf(makeAction(label = "A1"), makeAction(id = "a2", label = "A2")),
            trigger = WorkflowTrigger.Weekly(daysOfWeek = setOf(1, 3), hour = 10, minute = 0),
            outputConfig = WorkflowOutputConfig(outputType = WorkflowOutputType.BOTH)
        )
        val sourceId = repo.seedTemplate(source)
        val result = manager.exportWorkflow(sourceId)
        val envelope = json.decodeFromString<WorkflowExportEnvelope>(result.json)
        val wf = envelope.workflow

        assertEquals("Full Export", wf.name)
        assertEquals("A test workflow", wf.description)
        assertEquals("Summarize", wf.globalInstruction)
        assertEquals(2, wf.actions.size)
        assertEquals("A1", wf.actions[0].label)
        assertEquals(WorkflowOutputType.BOTH, wf.outputConfig.outputType)
        assertEquals("test-template", wf.sourceTemplateId)
        assertEquals("Testing", wf.category)
        assertTrue(wf.notifyOnCompletion)
        assertFalse(wf.notifyOnStart)
    }

    @Test
    fun `export excludes run history and transient state`() = runTest {
        val source = makeTemplate().copy(
            lastRunAtMillis = 5000L,
            lastRunStatus = WorkflowRunStatus.FAILED
        )
        val sourceId = repo.seedTemplate(source)
        val result = manager.exportWorkflow(sourceId)

        // Runtime state fields are not part of the export DTO
        assertFalse(result.json.contains("lastRunAtMillis"))
        assertFalse(result.json.contains("lastRunStatus"))
        // The workflow-level isEnabled is excluded from export (only action-level isEnabled exists)
        val envelope = json.decodeFromString<WorkflowExportEnvelope>(result.json)
        // WorkflowExportDto has no isEnabled field — verify the envelope round-trips cleanly
        assertEquals("My Workflow", envelope.workflow.name)
    }

    @Test
    fun `export includes safe profile metadata without keys`() = runTest {
        val profile = makeProfile(id = "p-1", name = "My Profile")
        repo.seedProfile(profile)
        val source = makeTemplate(defaultProfileId = "p-1")
        val sourceId = repo.seedTemplate(source)

        val result = manager.exportWorkflow(sourceId)
        val envelope = json.decodeFromString<WorkflowExportEnvelope>(result.json)

        assertEquals(1, envelope.references.profiles.size)
        val ref = envelope.references.profiles[0]
        assertEquals("p-1", ref.profileId)
        assertEquals("My Profile", ref.name)
        assertEquals("ANTHROPIC", ref.providerType)
        assertEquals("claude-sonnet-4-20250514", ref.modelId)

        // Ensure no actual key material
        assertFalse(result.json.contains("apiKey"))
        assertFalse(result.json.contains("secretKey"))
    }

    @Test
    fun `export sanitizes sensitive API GET config headers`() = runTest {
        val configJson = """{"headers":{"Authorization":"Bearer secret123","Accept":"application/json"},"queryParams":{},"extractionHint":""}"""
        val action = makeAction(
            type = WorkflowActionType.FETCH_API_GET,
            extraConfig = configJson
        )
        val sourceId = repo.seedTemplate(makeTemplate(actions = listOf(action)))

        val result = manager.exportWorkflow(sourceId)

        assertTrue(result.json.contains("[REDACTED]"))
        assertFalse(result.json.contains("secret123"))
        assertTrue(result.json.contains("application/json")) // non-sensitive preserved
        assertTrue(result.warnings.any { "redacted" in it.lowercase() })
    }

    @Test
    fun `export warns about suspicious sourceData URLs`() = runTest {
        val action = makeAction(
            type = WorkflowActionType.FETCH_URL,
            label = "Weather",
            sourceData = "https://api.example.com/data?api_key=SECRET&format=json"
        )
        val sourceId = repo.seedTemplate(makeTemplate(actions = listOf(action)))

        val result = manager.exportWorkflow(sourceId)
        assertTrue(result.warnings.any { "sensitive values in URLs" in it })
        assertTrue(result.warnings.any { "Weather" in it })
    }

    @Test
    fun `export does not warn for clean URLs`() = runTest {
        val action = makeAction(
            type = WorkflowActionType.FETCH_URL,
            sourceData = "https://example.com/feed?format=json&limit=10"
        )
        val sourceId = repo.seedTemplate(makeTemplate(actions = listOf(action)))

        val result = manager.exportWorkflow(sourceId)
        assertTrue(result.warnings.none { "sensitive values in URLs" in it })
    }

    @Test
    fun `export does not fail when warnings are generated`() = runTest {
        val action = makeAction(
            type = WorkflowActionType.FETCH_URL,
            sourceData = "https://api.example.com?token=abc"
        )
        val sourceId = repo.seedTemplate(makeTemplate(actions = listOf(action)))

        val result = manager.exportWorkflow(sourceId)
        assertTrue(result.json.isNotBlank())
        assertTrue(result.warnings.isNotEmpty())
        // Export still produced valid JSON
        val envelope = json.decodeFromString<WorkflowExportEnvelope>(result.json)
        assertEquals("automatist-workflow", envelope.type)
    }

    @Test
    fun `export generates safe filename`() = runTest {
        val sourceId = repo.seedTemplate(makeTemplate(name = "My Cool Workflow!"))
        val result = manager.exportWorkflow(sourceId)
        assertEquals("My_Cool_Workflow.json", result.suggestedFilename)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `export throws for non-existent workflow`() = runTest {
        manager.exportWorkflow(999)
    }

    // ═════════════════════════════════════════════════════════════
    //  IMPORT
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `import round-trip creates new local workflow`() = runTest {
        val sourceId = repo.seedTemplate(makeTemplate(name = "Round Trip"))
        val exported = manager.exportWorkflow(sourceId)
        val imported = manager.importWorkflow(exported.json)

        assertTrue(imported.workflowId > 0)
        assertTrue(imported.workflowId != sourceId)
        val wf = repo.getTemplateById(imported.workflowId)!!
        assertEquals("A test workflow", wf.description)
        assertEquals("Summarize", wf.globalInstruction)
    }

    @Test
    fun `imported workflow gets new IDs`() = runTest {
        val actions = listOf(makeAction(id = "orig-id", label = "A"))
        val sourceId = repo.seedTemplate(makeTemplate(actions = actions))
        val exported = manager.exportWorkflow(sourceId)

        val imported = manager.importWorkflow(exported.json)
        val wf = repo.getTemplateById(imported.workflowId)!!

        assertNotEquals(sourceId, wf.id)
        assertEquals(1, wf.actions.size)
        assertNotEquals("orig-id", wf.actions[0].id)
        assertEquals("A", wf.actions[0].label)
    }

    @Test
    fun `imported workflow is disabled by default`() = runTest {
        val sourceId = repo.seedTemplate(makeTemplate(isEnabled = true))
        val exported = manager.exportWorkflow(sourceId)
        val imported = manager.importWorkflow(exported.json)
        val wf = repo.getTemplateById(imported.workflowId)!!
        assertFalse(wf.isEnabled)
    }

    @Test
    fun `imported workflow has no runtime state`() = runTest {
        val sourceId = repo.seedTemplate(makeTemplate())
        val exported = manager.exportWorkflow(sourceId)
        val imported = manager.importWorkflow(exported.json)
        val wf = repo.getTemplateById(imported.workflowId)!!
        assertNull(wf.lastRunAtMillis)
        assertNull(wf.lastRunStatus)
    }

    @Test
    fun `import validates schema type`() = runTest {
        val badJson = """{"schemaVersion":1,"type":"not-automatist","exportedAt":"","app":{},"workflow":{"name":"X"},"references":{}}"""
        try {
            manager.importWorkflow(badJson)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Not an Automatist workflow file"))
        }
    }

    @Test
    fun `import validates schema version`() = runTest {
        val futureJson = """{"schemaVersion":99,"type":"automatist-workflow","exportedAt":"","app":{},"workflow":{"name":"X"},"references":{}}"""
        try {
            manager.importWorkflow(futureJson)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unsupported schema version"))
        }
    }

    @Test
    fun `import fails gracefully for invalid JSON`() = runTest {
        try {
            manager.importWorkflow("not json at all {{{")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid workflow file"))
        }
    }

    @Test
    fun `import fails for empty string`() = runTest {
        try {
            manager.importWorkflow("")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid workflow file"))
        }
    }

    @Test
    fun `import renames workflow on title conflict`() = runTest {
        repo.seedTemplate(makeTemplate(name = "Existing"))

        val sourceId = repo.seedTemplate(makeTemplate(name = "Existing"))
        val exported = manager.exportWorkflow(sourceId)
        // Delete source so only the first "Existing" conflicts
        repo.deleteTemplate(sourceId)

        val imported = manager.importWorkflow(exported.json)
        assertEquals("Existing (Copy)", imported.workflowName)
        assertTrue(imported.warnings.any { "Renamed" in it })
    }

    @Test
    fun `import resolves profile by exact ID match`() = runTest {
        val profile = makeProfile(id = "p-1")
        repo.seedProfile(profile)

        val source = makeTemplate(defaultProfileId = "p-1")
        val sourceId = repo.seedTemplate(source)
        val exported = manager.exportWorkflow(sourceId)

        // Delete source, but keep profile
        repo.deleteTemplate(sourceId)
        val imported = manager.importWorkflow(exported.json)
        val wf = repo.getTemplateById(imported.workflowId)!!
        assertEquals("p-1", wf.defaultProfileId)
    }

    @Test
    fun `import resolves profile by provider and model fallback`() = runTest {
        // Source had profile "p-old"; importing device has "p-new" with same provider+model
        repo.seedProfile(makeProfile(id = "p-new", name = "Different Name"))

        repo.seedTemplate(makeTemplate(defaultProfileId = "p-old"))
        // The export won't include p-old ref since it doesn't exist in repo. Build manually:
        val envelope = WorkflowExportEnvelope(
            workflow = WorkflowExportDto(
                name = "Profile Test",
                defaultProfileId = "p-old"
            ),
            references = ExportReferences(
                profiles = listOf(
                    SafeProfileRefDto(
                        profileId = "p-old",
                        name = "Old Profile",
                        providerType = "ANTHROPIC",
                        modelId = "claude-sonnet-4-20250514"
                    )
                )
            )
        )
        val jsonStr = Json { prettyPrint = true; encodeDefaults = true }
            .encodeToString(WorkflowExportEnvelope.serializer(), envelope)

        val imported = manager.importWorkflow(jsonStr)
        val wf = repo.getTemplateById(imported.workflowId)!!
        assertEquals("p-new", wf.defaultProfileId)
    }

    @Test
    fun `import warns about unresolved profiles`() = runTest {
        // No profiles in repo — all refs will be unresolved
        val envelope = WorkflowExportEnvelope(
            workflow = WorkflowExportDto(
                name = "Unresolved",
                defaultProfileId = "p-missing"
            ),
            references = ExportReferences(
                profiles = listOf(
                    SafeProfileRefDto("p-missing", "Gone", "OPENAI", "gpt-4o")
                )
            )
        )
        val jsonStr = Json { prettyPrint = true; encodeDefaults = true }
            .encodeToString(WorkflowExportEnvelope.serializer(), envelope)

        val imported = manager.importWorkflow(jsonStr)
        val wf = repo.getTemplateById(imported.workflowId)!!

        assertEquals("", wf.defaultProfileId) // cleared
        assertTrue(imported.warnings.any { "profile" in it.lowercase() })
    }

    @Test
    fun `import recreates saved notes and warns`() = runTest {
        // Note ID 42 does not exist locally
        val noteConfig = """{"noteId":42,"noteTitle":"Old Note"}"""
        val envelope = WorkflowExportEnvelope(
            workflow = WorkflowExportDto(
                name = "Note Test",
                actions = listOf(
                    ActionExportDto(
                        type = WorkflowActionType.USE_SAVED_NOTE,
                        label = "Note",
                        extraConfig = json.parseToJsonElement(noteConfig)
                    )
                )
            ),
            references = ExportReferences(
                savedNotes = listOf(
                    SavedNoteSnapshotDto(originalNoteId = 42, title = "Old Note", content = "Note body")
                )
            )
        )
        val jsonStr = Json { prettyPrint = true; encodeDefaults = true }
            .encodeToString(WorkflowExportEnvelope.serializer(), envelope)

        val imported = manager.importWorkflow(jsonStr)
        assertTrue(imported.warnings.any { "saved note" in it.lowercase() })
    }

    @Test
    fun `import clears non-portable previous-output references`() = runTest {
        val prevConfig = """{"sourceWorkflowId":99,"sourceWorkflowName":"Other","selectionMode":"LATEST_SUCCESSFUL","specificRunId":5}"""
        val envelope = WorkflowExportEnvelope(
            workflow = WorkflowExportDto(
                name = "Prev Output Test",
                actions = listOf(
                    ActionExportDto(
                        type = WorkflowActionType.USE_PREVIOUS_OUTPUT,
                        label = "Prev",
                        extraConfig = json.parseToJsonElement(prevConfig)
                    )
                )
            )
        )
        val jsonStr = Json { prettyPrint = true; encodeDefaults = true }
            .encodeToString(WorkflowExportEnvelope.serializer(), envelope)

        val imported = manager.importWorkflow(jsonStr)
        val wf = repo.getTemplateById(imported.workflowId)!!

        val config = wf.actions[0].extraConfig
        assertTrue(config.contains("\"sourceWorkflowId\":0"))
        assertFalse(config.contains("specificRunId"))
        assertTrue(imported.warnings.any { "Previous Output" in it })
    }

    // ═════════════════════════════════════════════════════════════
    //  WARNINGS / SAFETY (focused unit tests)
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `findActionsWithSuspiciousUrls detects api_key param`() {
        val actions = listOf(
            makeAction(sourceData = "https://api.example.com?api_key=SECRET", label = "Weather")
        )
        val result = manager.findActionsWithSuspiciousUrls(actions)
        assertEquals(listOf("Weather"), result)
    }

    @Test
    fun `findActionsWithSuspiciousUrls detects token param`() {
        val actions = listOf(
            makeAction(sourceData = "https://example.com/data?token=abc123&format=json")
        )
        val result = manager.findActionsWithSuspiciousUrls(actions)
        assertEquals(1, result.size)
    }

    @Test
    fun `findActionsWithSuspiciousUrls detects auth param`() {
        val actions = listOf(
            makeAction(sourceData = "https://example.com?auth=mykey")
        )
        assertEquals(1, manager.findActionsWithSuspiciousUrls(actions).size)
    }

    @Test
    fun `findActionsWithSuspiciousUrls detects secret param`() {
        val actions = listOf(
            makeAction(sourceData = "https://example.com?client_secret=xyz")
        )
        assertEquals(1, manager.findActionsWithSuspiciousUrls(actions).size)
    }

    @Test
    fun `findActionsWithSuspiciousUrls detects password param`() {
        val actions = listOf(
            makeAction(sourceData = "https://example.com?password=hunter2")
        )
        assertEquals(1, manager.findActionsWithSuspiciousUrls(actions).size)
    }

    @Test
    fun `findActionsWithSuspiciousUrls ignores safe URLs`() {
        val actions = listOf(
            makeAction(sourceData = "https://example.com/feed?format=json&limit=5"),
            makeAction(sourceData = "https://example.com/page"),
            makeAction(sourceData = "just some text, not a URL"),
            makeAction(sourceData = "")
        )
        assertTrue(manager.findActionsWithSuspiciousUrls(actions).isEmpty())
    }

    @Test
    fun `findActionsWithSuspiciousUrls uses action label or falls back to type name`() {
        val withLabel = makeAction(
            label = "My Fetch",
            sourceData = "https://x.com?key=abc"
        )
        val withoutLabel = makeAction(
            label = "",
            type = WorkflowActionType.FETCH_URL,
            sourceData = "https://x.com?key=abc"
        )
        val result1 = manager.findActionsWithSuspiciousUrls(listOf(withLabel))
        assertEquals("My Fetch", result1[0])

        val result2 = manager.findActionsWithSuspiciousUrls(listOf(withoutLabel))
        assertEquals("Fetch URL", result2[0])
    }

    @Test
    fun `isSensitiveKey matches expected patterns`() {
        assertTrue(manager.isSensitiveKey("key"))
        assertTrue(manager.isSensitiveKey("api_key"))
        assertTrue(manager.isSensitiveKey("apikey"))
        assertTrue(manager.isSensitiveKey("api-key"))
        assertTrue(manager.isSensitiveKey("Authorization"))
        assertTrue(manager.isSensitiveKey("auth"))
        assertTrue(manager.isSensitiveKey("token"))
        assertTrue(manager.isSensitiveKey("access_token"))
        assertTrue(manager.isSensitiveKey("secret"))
        assertTrue(manager.isSensitiveKey("client_secret"))
        assertTrue(manager.isSensitiveKey("password"))
        assertTrue(manager.isSensitiveKey("bearer"))
        assertTrue(manager.isSensitiveKey("private_key"))
        assertTrue(manager.isSensitiveKey("private-key"))
    }

    @Test
    fun `isSensitiveKey does not match safe names`() {
        assertFalse(manager.isSensitiveKey("format"))
        assertFalse(manager.isSensitiveKey("limit"))
        assertFalse(manager.isSensitiveKey("page"))
        assertFalse(manager.isSensitiveKey("q"))
        assertFalse(manager.isSensitiveKey("id"))
        assertFalse(manager.isSensitiveKey("callback"))
    }

    @Test
    fun `sanitizeAndStructureConfig redacts sensitive API GET headers`() {
        val config = """{"headers":{"Authorization":"Bearer xyz","Content-Type":"application/json"},"queryParams":{"api_key":"secret","format":"json"},"extractionHint":""}"""
        val action = makeAction(type = WorkflowActionType.FETCH_API_GET, extraConfig = config)

        val (element, wasSanitized) = manager.sanitizeAndStructureConfig(action)

        assertTrue(wasSanitized)
        val output = element.toString()
        assertTrue(output.contains("[REDACTED]"))
        assertFalse(output.contains("Bearer xyz"))
        assertFalse(output.contains("\"secret\""))
        assertTrue(output.contains("application/json"))
        assertTrue(output.contains("\"json\"")) // safe query param preserved
    }

    @Test
    fun `sanitizeAndStructureConfig does not sanitize non-API-GET actions`() {
        val config = """{"maxItems":5}"""
        val action = makeAction(type = WorkflowActionType.FETCH_RSS_FEED, extraConfig = config)

        val (element, wasSanitized) = manager.sanitizeAndStructureConfig(action)

        assertFalse(wasSanitized)
        assertTrue(element.toString().contains("maxItems"))
    }

    @Test
    fun `sanitizeAndStructureConfig handles blank config`() {
        val action = makeAction(extraConfig = "")
        val (element, wasSanitized) = manager.sanitizeAndStructureConfig(action)
        assertNull(element)
        assertFalse(wasSanitized)
    }
}

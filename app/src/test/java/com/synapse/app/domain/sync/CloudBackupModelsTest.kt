package com.synapse.app.domain.sync

import com.synapse.app.domain.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CloudBackupModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Test
    fun `backup envelope has correct type marker`() {
        val envelope = CloudBackupEnvelope(workflows = emptyList())
        assertEquals("synapse-workflow-backup", envelope.type)
        assertEquals(1, envelope.schemaVersion)
    }

    @Test
    fun `backup envelope round-trips through JSON`() {
        val workflow = WorkflowExportEnvelope(
            workflow = WorkflowExportDto(name = "Test Workflow", description = "A test")
        )
        val backup = CloudBackupEnvelope(
            syncedAt = "2026-04-10T12:00:00Z",
            workflows = listOf(workflow)
        )

        val serialized = json.encodeToString(backup)
        val deserialized = json.decodeFromString<CloudBackupEnvelope>(serialized)

        assertEquals(backup.type, deserialized.type)
        assertEquals(backup.schemaVersion, deserialized.schemaVersion)
        assertEquals(1, deserialized.workflows.size)
        assertEquals("Test Workflow", deserialized.workflows[0].workflow.name)
    }

    @Test
    fun `backup envelope does not contain API key fields`() {
        val workflow = WorkflowExportEnvelope(
            workflow = WorkflowExportDto(
                name = "My Workflow",
                defaultProfileId = "profile-123"
            ),
            references = ExportReferences(
                profiles = listOf(
                    SafeProfileRefDto("profile-123", "Claude", "ANTHROPIC", "claude-sonnet-4-20250514")
                )
            )
        )
        val backup = CloudBackupEnvelope(workflows = listOf(workflow))
        val serialized = json.encodeToString(backup)

        assertFalse(serialized.contains("apiKey"))
        assertFalse(serialized.contains("secretKey"))
        assertFalse(serialized.contains("api_key"))
        assertTrue(serialized.contains("profile-123"))
        assertTrue(serialized.contains("ANTHROPIC"))
    }

    @Test
    fun `empty backup is valid`() {
        val backup = CloudBackupEnvelope(
            syncedAt = "2026-04-10T12:00:00Z",
            workflows = emptyList()
        )
        val serialized = json.encodeToString(backup)
        val deserialized = json.decodeFromString<CloudBackupEnvelope>(serialized)

        assertEquals(0, deserialized.workflows.size)
        assertEquals("synapse-workflow-backup", deserialized.type)
    }

    @Test
    fun `backup contains portable workflow envelopes not raw DB entities`() {
        val backup = CloudBackupEnvelope(
            workflows = listOf(
                WorkflowExportEnvelope(
                    workflow = WorkflowExportDto(name = "W1"),
                    references = ExportReferences()
                )
            )
        )
        val serialized = json.encodeToString(backup)

        // Should contain the portable export format markers
        assertTrue(serialized.contains("synapse-workflow"))
        assertTrue(serialized.contains("synapse-workflow-backup"))
        // Should NOT contain Room entity fields
        assertFalse(serialized.contains("triggerJson"))
        assertFalse(serialized.contains("actionsJson"))
        assertFalse(serialized.contains("outputConfigJson"))
        assertFalse(serialized.contains("lastRunAtMillis"))
        assertFalse(serialized.contains("lastRunStatus"))
    }

    @Test
    fun `drive filename is correct`() {
        assertEquals("synapse_workflows.json", CloudBackupEnvelope.DRIVE_FILENAME)
    }

    @Test
    fun `backup schema version validation`() {
        val futureBackup = """{"schemaVersion":99,"type":"synapse-workflow-backup","syncedAt":"","app":{},"workflows":[]}"""
        val parsed = json.decodeFromString<CloudBackupEnvelope>(futureBackup)
        assertTrue(parsed.schemaVersion > 1)
    }

    @Test
    fun `backup with wrong type is detectable`() {
        val wrongType = """{"schemaVersion":1,"type":"not-synapse","syncedAt":"","app":{},"workflows":[]}"""
        val parsed = json.decodeFromString<CloudBackupEnvelope>(wrongType)
        assertNotEquals(CloudBackupEnvelope.BACKUP_TYPE, parsed.type)
    }
}

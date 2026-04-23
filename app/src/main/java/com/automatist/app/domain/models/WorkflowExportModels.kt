package com.automatist.app.domain.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Export Envelope ──

@Serializable
data class WorkflowExportEnvelope(
    val schemaVersion: Int = 1,
    val type: String = "automatist-workflow",
    val exportedAt: String = "",
    val app: ExportAppInfo = ExportAppInfo(),
    val workflow: WorkflowExportDto,
    val references: ExportReferences = ExportReferences()
)

@Serializable
data class ExportAppInfo(
    val name: String = "Automatist",
    val exportFormatVersion: Int = 1
)

// ── Workflow DTO ──

@Serializable
data class WorkflowExportDto(
    val name: String,
    val description: String = "",
    val trigger: WorkflowTrigger = WorkflowTrigger.Manual,
    val actions: List<ActionExportDto> = emptyList(),
    val globalInstruction: String = "",
    val outputConfig: WorkflowOutputConfig = WorkflowOutputConfig(),
    val notifyOnCompletion: Boolean = false,
    val notifyOnStart: Boolean = false,
    // Auto-retry opt-in carried across export/import. Default false keeps
    // pre-v17 export payloads valid (they just keep auto-retry OFF on import).
    val autoRetryEnabled: Boolean = false,
    val sourceTemplateId: String = "",
    val category: String = "",
    val customization: TemplateCustomization = TemplateCustomization(),
    val defaultProfileId: String = ""
)

// ── Action DTO (extraConfig as structured JSON, not double-encoded string) ──

@Serializable
data class ActionExportDto(
    val type: WorkflowActionType,
    val label: String = "",
    val sourceData: String = "",
    val instruction: String = "",
    val order: Int = 0,
    val isEnabled: Boolean = true,
    val extraConfig: JsonElement? = null,
    val profileId: String = "",
    // Per-action compaction; default keeps old exports (without this field)
    // behaving with the same default as fresh workflows.
    val compaction: InputCompactionMode = InputCompactionMode.AGGRESSIVE
)

// ── References (safe metadata only — never contains secrets) ──

@Serializable
data class SafeProfileRefDto(
    val profileId: String,
    val name: String,
    val providerType: String,
    val modelId: String
)

@Serializable
data class SavedNoteSnapshotDto(
    val originalNoteId: Long,
    val title: String,
    val content: String
)

@Serializable
data class ExportReferences(
    val profiles: List<SafeProfileRefDto> = emptyList(),
    val savedNotes: List<SavedNoteSnapshotDto> = emptyList()
)

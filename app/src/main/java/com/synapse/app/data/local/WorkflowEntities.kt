package com.synapse.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.synapse.app.domain.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// ── Template Entity ──

@Entity(tableName = "workflow_templates")
data class WorkflowTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val triggerJson: String,           // JSON: WorkflowTrigger
    val actionsJson: String,           // JSON: List<WorkflowAction>
    val globalInstruction: String,
    val outputConfigJson: String,      // JSON: WorkflowOutputConfig
    val notifyOnCompletion: Boolean,
    val notifyOnStart: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastRunAtMillis: Long?,
    val lastRunStatus: String?,        // WorkflowRunStatus.name or null
    // Template-system columns (v4)
    val sourceTemplateId: String = "",
    val category: String = "",
    val customizationJson: String = "", // JSON: TemplateCustomization
    val defaultProfileId: String = ""   // provider profile ID for workflow-level routing (v5)
)

// ── Run Entity ──

@Entity(
    tableName = "workflow_runs",
    foreignKeys = [
        ForeignKey(
            entity = WorkflowTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class WorkflowRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val templateName: String,
    val triggerType: String,
    val status: String,           // WorkflowRunStatus.name
    val currentStage: String,
    val outputText: String,
    val outputFormat: String = "MARKDOWN", // OutputFormat.name
    val providerType: String?,    // ProviderType.name or null
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val durationMs: Long?,
    val errorMessage: String?,
    val errorDetail: String?,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val profileName: String = "",
    val modelId: String = "",
    val isSocialOutput: Boolean = false
)

// ── Provider Profile Entity ──

@Entity(tableName = "provider_profiles")
data class ProviderProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val providerType: String,   // ProviderType.name
    val modelId: String,
    val isDefault: Boolean,
    val isEnabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

fun ProviderProfileEntity.toDomain() = ProviderProfile(
    id = id,
    name = name,
    providerType = ProviderType.valueOf(providerType),
    modelId = modelId,
    isDefault = isDefault,
    isEnabled = isEnabled,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun ProviderProfile.toEntity() = ProviderProfileEntity(
    id = id,
    name = name,
    providerType = providerType.name,
    modelId = modelId,
    isDefault = isDefault,
    isEnabled = isEnabled,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

// ── Saved Note Entity ──

@Entity(tableName = "saved_notes")
data class SavedNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

// ── Mappers: SavedNote ──

fun SavedNoteEntity.toDomain() = SavedNote(
    id = id,
    title = title,
    content = content,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun SavedNote.toEntity() = SavedNoteEntity(
    id = id,
    title = title,
    content = content,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

// ── Mappers: Template ──

fun WorkflowTemplateEntity.toDomain() = WorkflowTemplate(
    id = id,
    name = name,
    description = description,
    isEnabled = isEnabled,
    trigger = json.decodeFromString<WorkflowTrigger>(triggerJson),
    actions = json.decodeFromString<List<WorkflowAction>>(actionsJson),
    globalInstruction = globalInstruction,
    outputConfig = json.decodeFromString<WorkflowOutputConfig>(outputConfigJson),
    notifyOnCompletion = notifyOnCompletion,
    notifyOnStart = notifyOnStart,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    lastRunAtMillis = lastRunAtMillis,
    lastRunStatus = lastRunStatus?.let { WorkflowRunStatus.valueOf(it) },
    sourceTemplateId = sourceTemplateId,
    category = category,
    customization = if (customizationJson.isNotBlank()) {
        try { json.decodeFromString<TemplateCustomization>(customizationJson) }
        catch (_: Exception) { TemplateCustomization() }
    } else TemplateCustomization(),
    defaultProfileId = defaultProfileId
)

fun WorkflowTemplate.toEntity() = WorkflowTemplateEntity(
    id = id,
    name = name,
    description = description,
    isEnabled = isEnabled,
    triggerJson = json.encodeToString(trigger),
    actionsJson = json.encodeToString(actions),
    globalInstruction = globalInstruction,
    outputConfigJson = json.encodeToString(outputConfig),
    notifyOnCompletion = notifyOnCompletion,
    notifyOnStart = notifyOnStart,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    lastRunAtMillis = lastRunAtMillis,
    lastRunStatus = lastRunStatus?.name,
    sourceTemplateId = sourceTemplateId,
    category = category,
    customizationJson = json.encodeToString(customization),
    defaultProfileId = defaultProfileId
)

// ── Mappers: Run ──

fun WorkflowRunEntity.toDomain() = WorkflowRun(
    id = id,
    templateId = templateId,
    templateName = templateName,
    triggerType = triggerType,
    status = WorkflowRunStatus.valueOf(status),
    currentStage = currentStage,
    outputText = outputText,
    outputFormat = try { OutputFormat.valueOf(outputFormat) } catch (_: Exception) { OutputFormat.MARKDOWN },
    providerType = providerType?.let { ProviderType.valueOf(it) },
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    totalTokens = totalTokens,
    durationMs = durationMs,
    errorMessage = errorMessage,
    errorDetail = errorDetail,
    startedAtMillis = startedAtMillis,
    completedAtMillis = completedAtMillis,
    profileName = profileName,
    modelId = modelId,
    isSocialOutput = isSocialOutput
)

fun WorkflowRun.toEntity() = WorkflowRunEntity(
    id = id,
    templateId = templateId,
    templateName = templateName,
    triggerType = triggerType,
    status = status.name,
    currentStage = currentStage,
    outputText = outputText,
    outputFormat = outputFormat.name,
    providerType = providerType?.name,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    totalTokens = totalTokens,
    durationMs = durationMs,
    errorMessage = errorMessage,
    errorDetail = errorDetail,
    startedAtMillis = startedAtMillis,
    completedAtMillis = completedAtMillis,
    profileName = profileName,
    modelId = modelId,
    isSocialOutput = isSocialOutput
)

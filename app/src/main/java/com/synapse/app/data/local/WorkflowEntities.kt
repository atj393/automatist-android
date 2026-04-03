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
    val triggerJson: String,       // JSON: WorkflowTrigger
    val actionsJson: String,       // JSON: List<WorkflowAction>
    val globalInstruction: String,
    val outputConfigJson: String,  // JSON: WorkflowOutputConfig
    val notifyOnCompletion: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastRunAtMillis: Long?,
    val lastRunStatus: String?     // WorkflowRunStatus.name or null
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
    val providerType: String?,    // ProviderType.name or null
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val durationMs: Long?,
    val errorMessage: String?,
    val startedAtMillis: Long,
    val completedAtMillis: Long?
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
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    lastRunAtMillis = lastRunAtMillis,
    lastRunStatus = lastRunStatus?.let { WorkflowRunStatus.valueOf(it) }
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
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    lastRunAtMillis = lastRunAtMillis,
    lastRunStatus = lastRunStatus?.name
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
    providerType = providerType?.let { ProviderType.valueOf(it) },
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    totalTokens = totalTokens,
    durationMs = durationMs,
    errorMessage = errorMessage,
    startedAtMillis = startedAtMillis,
    completedAtMillis = completedAtMillis
)

fun WorkflowRun.toEntity() = WorkflowRunEntity(
    id = id,
    templateId = templateId,
    templateName = templateName,
    triggerType = triggerType,
    status = status.name,
    currentStage = currentStage,
    outputText = outputText,
    providerType = providerType?.name,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    totalTokens = totalTokens,
    durationMs = durationMs,
    errorMessage = errorMessage,
    startedAtMillis = startedAtMillis,
    completedAtMillis = completedAtMillis
)

package com.synapse.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.synapse.app.domain.models.HistoryItem
import com.synapse.app.domain.models.ProviderType
import com.synapse.app.domain.models.TransformType
import com.synapse.app.domain.models.WorkflowType

@Entity(tableName = "history_items")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowType: String,
    val inputPreview: String,
    val transformType: String,
    val outputText: String,
    val providerType: String,
    val createdAtMillis: Long
)

fun HistoryEntity.toDomain() = HistoryItem(
    id = id,
    workflowType = WorkflowType.valueOf(workflowType),
    inputPreview = inputPreview,
    transformType = TransformType.valueOf(transformType),
    outputText = outputText,
    providerType = ProviderType.valueOf(providerType),
    createdAtMillis = createdAtMillis
)

fun HistoryItem.toEntity() = HistoryEntity(
    id = id,
    workflowType = workflowType.name,
    inputPreview = inputPreview,
    transformType = transformType.name,
    outputText = outputText,
    providerType = providerType.name,
    createdAtMillis = createdAtMillis
)

package com.synapse.app.domain.models

data class ArticleInput(
    val text: String
)

data class TransformResult(
    val outputText: String,
    val transformType: TransformType,
    val providerType: ProviderType
)

data class HistoryItem(
    val id: Long = 0,
    val workflowType: WorkflowType,
    val inputPreview: String,
    val transformType: TransformType,
    val outputText: String,
    val providerType: ProviderType,
    val createdAtMillis: Long
)

package com.synapse.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.domain.models.*
import com.synapse.app.domain.repositories.HistoryRepository
import com.synapse.app.domain.repositories.WorkflowRepository
import com.synapse.app.domain.templates.BuiltInTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    private val workflowRepository: WorkflowRepository
) : ViewModel() {

    val recentHistory: StateFlow<List<HistoryItem>> = historyRepository.getHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val customWorkflows: StateFlow<List<WorkflowTemplate>> = workflowRepository.getAllTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        seedSampleWorkflowIfNeeded()
    }

    /**
     * Seeds a sample "Article Briefing" workflow on first use if no workflows exist.
     * This gives new users a useful starting point that works with the share-to-app flow.
     * The seeded workflow is fully editable — it's a normal user workflow instance.
     */
    private fun seedSampleWorkflowIfNeeded() {
        viewModelScope.launch {
            val existing = workflowRepository.getAllTemplates().first()
            if (existing.isNotEmpty()) return@launch // Already has workflows, don't re-seed

            val articleTemplate = BuiltInTemplates.findById("article_summarizer") ?: return@launch
            val bp = articleTemplate.blueprint

            val now = System.currentTimeMillis()
            workflowRepository.saveTemplate(
                WorkflowTemplate(
                    name = "Article Briefing",
                    description = "Transform shared articles and text into summaries, threads, or professional posts. Your starter workflow.",
                    isEnabled = true,
                    trigger = bp.trigger,
                    actions = bp.actions,
                    globalInstruction = bp.globalInstruction,
                    outputConfig = bp.outputConfig,
                    notifyOnCompletion = false,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    sourceTemplateId = articleTemplate.id,
                    category = articleTemplate.category
                    // No customization restrictions — fully editable
                )
            )
        }
    }
}

package com.synapse.app.feature.workflow.templates

import androidx.lifecycle.ViewModel
import com.synapse.app.domain.readiness.ReadinessEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WorkflowTemplatesViewModel @Inject constructor(
    val readinessEvaluator: ReadinessEvaluator
) : ViewModel()

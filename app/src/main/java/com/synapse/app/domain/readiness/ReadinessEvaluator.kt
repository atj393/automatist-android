package com.synapse.app.domain.readiness

import com.synapse.app.domain.actions.WorkflowActionRegistry
import com.synapse.app.domain.actions.WorkflowActionRegistry.RequirementType
import com.synapse.app.domain.actions.WorkflowActionRegistry.SetupRequirement
import com.synapse.app.domain.models.WorkflowAction
import com.synapse.app.domain.models.WorkflowActionType
import com.synapse.app.domain.models.WorkflowTemplate
import com.synapse.app.domain.repositories.WorkflowRepository
import com.synapse.app.platform.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Readiness status for a single setup requirement.
 */
enum class ReadinessStatus {
    READY,              // requirement is satisfied
    NEEDS_SETUP,        // user action needed (add key, grant permission, etc.)
    NOT_APPLICABLE      // this requirement doesn't apply
}

/**
 * Evaluated status of a single requirement.
 */
data class RequirementStatus(
    val requirement: SetupRequirement,
    val status: ReadinessStatus,
    val actionHint: String = ""  // e.g. "Add in Settings → Service Keys"
)

/**
 * Overall readiness for an action or workflow.
 */
data class ActionReadiness(
    val type: WorkflowActionType,
    val isReady: Boolean,
    val requirements: List<RequirementStatus>
) {
    val needsSetupCount: Int get() = requirements.count { it.status == ReadinessStatus.NEEDS_SETUP }
}

data class WorkflowReadiness(
    val isFullyReady: Boolean,
    val actionReadiness: List<ActionReadiness>,
    val hasProviderProfile: Boolean
) {
    val readyCount: Int get() = actionReadiness.count { it.isReady }
    val totalCount: Int get() = actionReadiness.size
    val needsSetupActions: List<ActionReadiness> get() = actionReadiness.filter { !it.isReady }
}

/**
 * Evaluates real readiness state by checking SecureStorage and repository.
 * Reusable across Action Catalog, workflow editor, template preview, etc.
 */
@Singleton
class ReadinessEvaluator @Inject constructor(
    private val secureStorage: SecureStorage,
    private val workflowRepository: WorkflowRepository
) {

    /**
     * Evaluate readiness for a single action type.
     */
    suspend fun evaluateAction(type: WorkflowActionType): ActionReadiness {
        val info = WorkflowActionRegistry.getInfo(type)
        if (info.setupRequirements.isEmpty()) {
            return ActionReadiness(type, isReady = true, requirements = emptyList())
        }

        val statuses = info.setupRequirements.map { req -> evaluateRequirement(req) }
        val isReady = statuses.all { it.status == ReadinessStatus.READY }

        return ActionReadiness(type, isReady, statuses)
    }

    /**
     * Evaluate readiness for an entire workflow template.
     */
    suspend fun evaluateWorkflow(template: WorkflowTemplate): WorkflowReadiness {
        val actionTypes = template.actions.map { it.type }.distinct()
        val actionReadiness = actionTypes.map { evaluateAction(it) }

        // Check if any provider profile or activeProvider exists
        val hasProfile = workflowRepository.getDefaultProfile() != null

        val isFullyReady = actionReadiness.all { it.isReady }

        return WorkflowReadiness(
            isFullyReady = isFullyReady,
            actionReadiness = actionReadiness,
            hasProviderProfile = hasProfile
        )
    }

    /**
     * Evaluate a single setup requirement.
     */
    suspend fun evaluateRequirement(req: SetupRequirement): RequirementStatus {
        return when (req.type) {
            RequirementType.SERVICE_KEY -> {
                val key = req.serviceKey?.let { secureStorage.getServiceKey(it) }
                if (!key.isNullOrBlank()) {
                    RequirementStatus(req, ReadinessStatus.READY)
                } else {
                    RequirementStatus(req, ReadinessStatus.NEEDS_SETUP, "Add in Settings → Service Keys")
                }
            }

            RequirementType.API_KEY -> {
                // For AI provider keys, check via the provider-type-based storage
                // This is a general check — specific provider checked at runtime by router
                val defaultProfile = workflowRepository.getDefaultProfile()
                if (defaultProfile != null) {
                    val key = secureStorage.getApiKey(defaultProfile.providerType)
                    if (!key.isNullOrBlank()) {
                        RequirementStatus(req, ReadinessStatus.READY)
                    } else {
                        RequirementStatus(req, ReadinessStatus.NEEDS_SETUP, "Add API key in Settings")
                    }
                } else {
                    RequirementStatus(req, ReadinessStatus.NEEDS_SETUP, "Set up a provider profile in Settings")
                }
            }

            RequirementType.PERMISSION -> {
                // Permission-on-demand: we don't check at browse time.
                // Status is "will be requested when needed"
                RequirementStatus(req, ReadinessStatus.READY, "Permission requested when used")
            }

            RequirementType.NONE -> {
                RequirementStatus(req, ReadinessStatus.READY)
            }
        }
    }
}

package com.automatist.app.domain.readiness

import com.automatist.app.domain.actions.WorkflowActionRegistry
import com.automatist.app.domain.actions.WorkflowActionRegistry.RequirementType
import com.automatist.app.domain.actions.WorkflowActionRegistry.SetupRequirement
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.platform.security.SecureStorage
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
    val hasProviderProfile: Boolean,
    val profileIssues: List<String> = emptyList()
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
     * Evaluate readiness for an entire workflow template, including profile chain validation.
     */
    suspend fun evaluateWorkflow(template: WorkflowTemplate): WorkflowReadiness {
        // Empty workflows are clean drafts — nothing to validate yet.
        // Profile and service-key checks only become relevant once actions are added.
        if (template.actions.isEmpty()) {
            return WorkflowReadiness(
                isFullyReady = true,
                actionReadiness = emptyList(),
                hasProviderProfile = workflowRepository.getDefaultProfile() != null,
                profileIssues = emptyList()
            )
        }

        val actionTypes = template.actions.map { it.type }.distinct()
        val actionReadiness = actionTypes.map { evaluateAction(it) }

        val hasProfile = workflowRepository.getDefaultProfile() != null
        val profileIssues = mutableListOf<String>()

        // ── Validate the AI profile chain that will be used at execution time ──
        // Resolution order: outputConfig.outputProfileId → template.defaultProfileId → app default profile → legacy fallback

        val outputProfileId = template.outputConfig.outputProfileId.ifBlank { null }
        val workflowProfileId = template.defaultProfileId.ifBlank { null }
        val effectiveProfileId = outputProfileId ?: workflowProfileId

        if (effectiveProfileId != null) {
            // Workflow or output specifies a profile — validate it
            val profile = workflowRepository.getProfileById(effectiveProfileId)
            if (profile == null) {
                val label = if (outputProfileId != null) "Output profile" else "Workflow default profile"
                profileIssues.add("$label is set but the profile no longer exists. Edit the workflow or choose a different profile.")
            } else {
                if (!profile.isEnabled) {
                    profileIssues.add("Profile '${profile.name}' is disabled. Enable it in Settings or choose a different profile.")
                }
                val key = secureStorage.getApiKey(profile.providerType)
                if (key.isNullOrBlank()) {
                    profileIssues.add("Profile '${profile.name}' uses ${profile.providerType.displayName}, but no API key is configured. Add one in Settings → Provider API Keys.")
                }
            }
        } else {
            // No explicit profile — validate the app default
            val defaultProfile = workflowRepository.getDefaultProfile()
            if (defaultProfile != null) {
                if (!defaultProfile.isEnabled) {
                    profileIssues.add("App default profile '${defaultProfile.name}' is disabled. Enable it in Settings or set a different default.")
                }
                val key = secureStorage.getApiKey(defaultProfile.providerType)
                if (key.isNullOrBlank()) {
                    profileIssues.add("App default profile '${defaultProfile.name}' uses ${defaultProfile.providerType.displayName}, but no API key is configured. Add one in Settings → Provider API Keys.")
                }
            }
            // If no default profile exists, the legacy fallback (FAKE) will be used — that's okay
        }

        val isFullyReady = actionReadiness.all { it.isReady } && profileIssues.isEmpty()

        return WorkflowReadiness(
            isFullyReady = isFullyReady,
            actionReadiness = actionReadiness,
            hasProviderProfile = hasProfile,
            profileIssues = profileIssues
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
                        RequirementStatus(req, ReadinessStatus.NEEDS_SETUP, "Add ${defaultProfile.providerType.displayName} API key in Settings")
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

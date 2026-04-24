package com.automatist.app.domain.readiness

import com.automatist.app.domain.actions.WorkflowActionRegistry
import com.automatist.app.domain.actions.WorkflowActionRegistry.RequirementType
import com.automatist.app.domain.actions.WorkflowActionRegistry.SetupRequirement
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.models.WorkflowActionType
import com.automatist.app.domain.models.WorkflowTemplate
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelStatus
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.platform.security.SecureStorage
import kotlinx.coroutines.flow.first
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
    private val workflowRepository: WorkflowRepository,
    private val offlineModelRepository: OfflineModelRepository
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
                profileIssues.addAll(checkProfileReadiness(profile))
            }
        } else {
            // No explicit profile — validate the app default
            val defaultProfile = workflowRepository.getDefaultProfile()
            if (defaultProfile != null) {
                if (!defaultProfile.isEnabled) {
                    profileIssues.add("App default profile '${defaultProfile.name}' is disabled. Enable it in Settings or set a different default.")
                }
                profileIssues.addAll(checkProfileReadiness(defaultProfile, isDefault = true))
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
     * Checks whether a profile's provider is ready to execute.
     * - Cloud/API providers: verifies an API key is configured.
     * - LOCAL_AI profiles: verifies the offline model is installed.
     * Returns a list of human-readable issue strings (empty = ready).
     */
    private suspend fun checkProfileReadiness(
        profile: com.automatist.app.domain.models.ProviderProfile,
        isDefault: Boolean = false
    ): List<String> {
        val label = if (isDefault) "App default profile '${profile.name}'" else "Profile '${profile.name}'"
        return when (profile.providerType) {
            ProviderType.LOCAL_AI -> {
                val modelId = profile.modelId.ifBlank { OfflineModelCatalog.GEMINI_NANO_ID }
                val entry = OfflineModelCatalog.findById(modelId)
                val modelName = entry?.displayName ?: "On-device AI"
                val isDownloadable = entry?.isSystemManaged == false
                val status = offlineModelRepository.getModelStatus(modelId).first()
                when (status) {
                    OfflineModelStatus.INSTALLED -> emptyList()
                    OfflineModelStatus.NOT_INSTALLED -> {
                        val action = if (isDownloadable) "Download" else "Check Availability"
                        listOf(
                            "$label uses $modelName, but it is not yet available on this device. " +
                                "Open Settings → On-device AI and tap \"$action\"."
                        )
                    }
                    OfflineModelStatus.UNSUPPORTED -> {
                        val reason = if (isDownloadable) {
                            "This device does not meet the minimum requirements for $modelName."
                        } else {
                            "$modelName is not supported on this device. " +
                                "It requires Android 14+ and a compatible Pixel 8+ or Galaxy S24+ device."
                        }
                        listOf(
                            "$label uses $modelName. $reason " +
                                "Switch to a cloud-based AI profile instead."
                        )
                    }
                    OfflineModelStatus.DOWNLOADING -> {
                        val action = if (isDownloadable) "downloading" else "checking availability"
                        listOf(
                            "$label uses $modelName, which is currently $action. " +
                                "Please wait a moment and try again."
                        )
                    }
                    OfflineModelStatus.FAILED -> {
                        val action = if (isDownloadable) "Retry download" else "Retry"
                        listOf(
                            "$label uses $modelName, but the setup failed. " +
                                "Open Settings → On-device AI and tap \"$action\" to try again."
                        )
                    }
                }
            }
            ProviderType.FAKE -> emptyList() // no key required
            else -> {
                val key = secureStorage.getApiKey(profile.providerType)
                if (key.isNullOrBlank()) {
                    listOf("$label uses ${profile.providerType.displayName}, but no API key is configured. Add one in Settings → Provider API Keys.")
                } else {
                    emptyList()
                }
            }
        }
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
                // "API_KEY" here is really "AI-provider readiness" — i.e. the
                // action (AI Prompt today, future AI-driven actions tomorrow)
                // needs a usable AI Profile. The prior version blindly called
                // `secureStorage.getApiKey(providerType)`, which is WRONG for
                // LOCAL_AI (no API key exists; the model file is what matters)
                // and for FAKE (a demo profile that legitimately has no key).
                // That produced "Needs setup" on AI Prompt even when the user
                // had a perfectly valid local or default profile.
                //
                // Correct logic: reuse [checkProfileReadiness] so LOCAL_AI
                // checks the offline model status, FAKE is always ready, and
                // cloud providers still check their API key. The CTA hint
                // points to AI Profiles (not Service Keys).
                val defaultProfile = workflowRepository.getDefaultProfile()
                if (defaultProfile == null) {
                    return RequirementStatus(
                        req,
                        ReadinessStatus.NEEDS_SETUP,
                        "Set a default AI Profile in Settings"
                    )
                }
                if (!defaultProfile.isEnabled) {
                    return RequirementStatus(
                        req,
                        ReadinessStatus.NEEDS_SETUP,
                        "Enable the default AI Profile in Settings"
                    )
                }
                val issues = checkProfileReadiness(defaultProfile, isDefault = true)
                if (issues.isEmpty()) {
                    RequirementStatus(req, ReadinessStatus.READY)
                } else {
                    // Profile exists but can't execute (missing cloud key, or
                    // model not installed). Short hint here — workflow-level
                    // readiness surfaces the longer message.
                    val hint = when (defaultProfile.providerType) {
                        ProviderType.LOCAL_AI -> "Set up the on-device model in Settings → On-device AI"
                        ProviderType.FAKE -> "Set a default AI Profile in Settings"
                        else -> "Add ${defaultProfile.providerType.displayName} API key in Settings → AI Profiles"
                    }
                    RequirementStatus(req, ReadinessStatus.NEEDS_SETUP, hint)
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

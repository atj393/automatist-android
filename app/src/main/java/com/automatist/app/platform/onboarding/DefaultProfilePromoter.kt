package com.automatist.app.platform.onboarding

import com.automatist.app.data.local.SeedingStateStore
import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.repositories.WorkflowRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safely promotes a newly-created usable profile to app default, but only when the
 * current default is still the auto-seeded Local Fake profile. If the user has
 * already picked a different default, we never silently override it.
 *
 * Rule:
 *  - Only triggers on profile create (not edit) via [maybePromoteOnCreate].
 *  - Only considers usable (non-FAKE, enabled) profiles as promotion candidates.
 *  - Only promotes if the current default's id matches [SettingsRepository.getSeededFakeProfileId].
 *  - Clears the seeded-fake marker after promotion so we don't keep over-promoting.
 */
@Singleton
class DefaultProfilePromoter @Inject constructor(
    private val workflowRepository: WorkflowRepository,
    private val seedingState: SeedingStateStore
) {

    /**
     * Called immediately after a brand-new profile has been saved. No-op for edits
     * (pass `wasNewProfile = false`), no-op for FAKE profiles, no-op when the user
     * has already committed to a non-seeded default.
     *
     * @return true if the profile was promoted to default, false otherwise.
     */
    suspend fun maybePromoteOnCreate(
        newProfile: ProviderProfile,
        wasNewProfile: Boolean
    ): Boolean {
        if (!wasNewProfile) return false
        if (!isUsable(newProfile)) return false

        val seededFakeId = seedingState.getSeededFakeProfileId() ?: return false
        val currentDefault = workflowRepository.getDefaultProfile()

        // Only promote when the current default is still the seeded fake profile.
        // Any other state — user cleared defaults, user already picked another, seeded
        // fake was deleted — means the user has already made a choice we shouldn't override.
        if (currentDefault == null || currentDefault.id != seededFakeId) return false

        workflowRepository.setDefaultProfile(newProfile.id)
        seedingState.clearSeededFakeProfileId()
        return true
    }

    private fun isUsable(profile: ProviderProfile): Boolean =
        profile.isEnabled && profile.providerType != ProviderType.FAKE
}

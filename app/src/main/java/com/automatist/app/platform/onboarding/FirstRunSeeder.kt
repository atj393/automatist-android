package com.automatist.app.platform.onboarding

import android.content.Context
import android.util.Log
import com.automatist.app.data.local.SeedingStateStore
import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.domain.workflow.WorkflowPortabilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin indirection over the bundled asset so [FirstRunSeeder] can be unit-tested
 * without an Android Context.
 */
fun interface SeededWorkflowJsonSource {
    fun load(): String
}

/** Production implementation — reads the bundled workflow asset. */
@Singleton
class AssetSeededWorkflowJsonSource @Inject constructor(
    @ApplicationContext private val context: Context
) : SeededWorkflowJsonSource {
    override fun load(): String =
        context.assets.open(FirstRunSeeder.SEEDED_WORKFLOW_ASSET).bufferedReader()
            .use { it.readText() }
}

@Singleton
class FirstRunSeeder @Inject constructor(
    private val workflowRepository: WorkflowRepository,
    private val seedingState: SeedingStateStore,
    private val portabilityManager: WorkflowPortabilityManager,
    private val jsonSource: SeededWorkflowJsonSource
) {

    companion object {
        private const val TAG = "FirstRunSeeder"
        // v1: seed Local Fake profile + News to Social workflow.
        // v2: one-time cleanup of the legacy auto-seeded "Article Briefing" workflow
        //     that previous app versions created via DashboardViewModel.
        const val CURRENT_VERSION = 2
        const val SEEDED_WORKFLOW_SOURCE_ID = "news_to_social"
        const val SEEDED_WORKFLOW_ASSET = "seeded_workflows/news_to_social.json"

        // Exact signature of the legacy auto-seed. A workflow matching ALL of these
        // fields AND never run is guaranteed to be the auto-seed; user-modified or
        // user-run copies won't match and are preserved.
        private const val LEGACY_AB_NAME = "Article Briefing"
        private const val LEGACY_AB_SOURCE_TEMPLATE_ID = "article_summarizer"
        private const val LEGACY_AB_DESCRIPTION =
            "Transform shared articles and text into summaries, threads, or professional posts. Your starter workflow."
    }

    /**
     * Idempotent first-run seeding. Safe to call on every app launch: returns fast
     * if the current seeding version has already been applied.
     *
     * Seeds, in order:
     *  1. A Local Fake provider profile (promoted to app default only if no default exists).
     *  2. The News to Social workflow from bundled asset JSON.
     *
     * Never overwrites user-created workflows, never overwrites an explicit default profile.
     */
    suspend fun seedIfNeeded() {
        val current = seedingState.getSeededDefaultsVersion()
        if (current >= CURRENT_VERSION) return

        if (current < 1) {
            try {
                seedFakeProfileIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "Fake profile seed failed: ${e.message}", e)
            }

            try {
                seedNewsToSocialIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "News to Social seed failed: ${e.message}", e)
            }
        }

        if (current < 2) {
            try {
                cleanupLegacyArticleBriefingIfStale()
            } catch (e: Exception) {
                Log.e(TAG, "Legacy Article Briefing cleanup failed: ${e.message}", e)
            }
        }

        try {
            reflectSetupProgressFromSeededState()
        } catch (e: Exception) {
            Log.e(TAG, "Getting-Started mark-forward failed: ${e.message}", e)
        }

        seedingState.setSeededDefaultsVersion(CURRENT_VERSION)
    }

    /**
     * After seeding (or legacy cleanup), sync the "Getting Started" checklist flags
     * to actual post-seed state so the user doesn't see "0 of 4 steps complete" while
     * already having a usable profile + default + workflow. Only flips false→true —
     * never clears a flag the user set earlier.
     */
    private suspend fun reflectSetupProgressFromSeededState() {
        val profiles = workflowRepository.getAllProfiles().first()
        if (profiles.isNotEmpty()) {
            // A Local Fake counts as "chose Local Demo" for step 1, and as a profile for step 2.
            seedingState.markProviderSetupDone()
            seedingState.markProfileSetupDone()
        }
        if (workflowRepository.getDefaultProfile() != null) {
            seedingState.markDefaultSetupDone()
        }
        val templates = workflowRepository.getAllTemplates().first()
        if (templates.isNotEmpty()) {
            seedingState.markWorkflowSetupDone()
        }
    }

    /**
     * Creates a Local Fake profile only if no FAKE profile already exists on this install.
     * Records its ID so the auto-promote logic later knows which profile is "safely replaceable".
     * Only promotes it to app default if no default is currently set — we never overwrite a user
     * choice (even if that choice was made before this seeder ran).
     */
    private suspend fun seedFakeProfileIfNeeded() {
        val existingProfiles = workflowRepository.getAllProfiles().first()
        val existingFake = existingProfiles.firstOrNull { it.providerType == ProviderType.FAKE }

        val fakeId: String = if (existingFake != null) {
            existingFake.id
        } else {
            val now = System.currentTimeMillis()
            val profile = ProviderProfile(
                id = UUID.randomUUID().toString(),
                name = "Local Fake Demo",
                providerType = ProviderType.FAKE,
                modelId = "fake-demo",
                isDefault = false,
                isFallback = false,
                isEnabled = true,
                createdAtMillis = now,
                updatedAtMillis = now,
                providerPresetId = "fake"
            )
            workflowRepository.saveProfile(profile)
            profile.id
        }

        seedingState.setSeededFakeProfileId(fakeId)

        val currentDefault = workflowRepository.getDefaultProfile()
        if (currentDefault == null) {
            workflowRepository.setDefaultProfile(fakeId)
        }
    }

    /**
     * Imports the bundled News to Social workflow via [WorkflowPortabilityManager].
     * Skips if a workflow with the seeded source ID already exists, even if the
     * version flag somehow got reset — no duplicates under any circumstance.
     *
     * The imported workflow comes in with [defaultProfileId]/[outputProfileId] blank,
     * which is exactly what we want: router resolution falls through to the app default
     * profile, so the workflow automatically follows whatever the current default is.
     */
    /**
     * One-time cleanup for installs that carry over an auto-seeded "Article Briefing"
     * workflow from pre-v1 app versions. Earlier builds created it via
     * DashboardViewModel.seedSampleWorkflowIfNeeded(); when users updated to the
     * News-to-Social-only onboarding, their old workflow stuck around.
     *
     * Only deletes rows that match the full legacy signature AND have never been run.
     * Any user edit to the name/description, or a single successful run, flips at
     * least one check and keeps the workflow safe.
     */
    private suspend fun cleanupLegacyArticleBriefingIfStale() {
        val candidates = workflowRepository.getAllTemplates().first().filter { wf ->
            wf.name == LEGACY_AB_NAME &&
                wf.sourceTemplateId == LEGACY_AB_SOURCE_TEMPLATE_ID &&
                wf.description == LEGACY_AB_DESCRIPTION &&
                wf.lastRunAtMillis == null
        }
        for (c in candidates) {
            workflowRepository.deleteTemplate(c.id)
            Log.i(TAG, "Removed stale auto-seeded legacy workflow id=${c.id} ('${c.name}')")
        }
    }

    private suspend fun seedNewsToSocialIfNeeded() {
        val existing = workflowRepository.getAllTemplates().first()
        val alreadySeeded = existing.any { it.sourceTemplateId == SEEDED_WORKFLOW_SOURCE_ID }
        if (alreadySeeded) return

        val json = jsonSource.load()
        val result = portabilityManager.importWorkflow(json)

        // Import creates workflows disabled by default (safer for a surprise install);
        // leave that behavior intact — the user enables scheduling when they're ready.
        Log.i(TAG, "Seeded '${result.workflowName}' (id=${result.workflowId})")
    }
}

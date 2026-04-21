package com.automatist.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.automatist.app.domain.models.AppSettings
import com.automatist.app.domain.models.BriefConfig
import com.automatist.app.domain.models.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Narrow view of the persistent state the first-run seeder + default-profile promoter
 * need. Lets those collaborators be unit-tested without an Android Context.
 */
interface SeedingStateStore {
    suspend fun getSeededDefaultsVersion(): Int
    suspend fun setSeededDefaultsVersion(version: Int)
    suspend fun getSeededFakeProfileId(): String?
    suspend fun setSeededFakeProfileId(id: String)
    suspend fun clearSeededFakeProfileId()
    // Getting-Started flags — exposed so FirstRunSeeder can reflect post-seed reality
    // and users don't see "0 of 4 steps complete" despite already having a usable setup.
    suspend fun markProviderSetupDone()
    suspend fun markProfileSetupDone()
    suspend fun markDefaultSetupDone()
    suspend fun markWorkflowSetupDone()
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SeedingStateStore {
    private val ACTIVE_PROVIDER_KEY = stringPreferencesKey("active_provider")
    private val BRIEF_CONFIG_KEY = stringPreferencesKey("brief_config")

    // ── Getting Started completion markers (user-intent-based) ──
    private val SETUP_PROVIDER_DONE = stringPreferencesKey("setup_provider_done")
    private val SETUP_PROFILE_DONE = stringPreferencesKey("setup_profile_done")
    private val SETUP_DEFAULT_DONE = stringPreferencesKey("setup_default_done")
    private val SETUP_WORKFLOW_DONE = stringPreferencesKey("setup_workflow_done")

    // ── First-run seeding tracking ──
    private val SEEDED_DEFAULTS_VERSION = intPreferencesKey("seeded_defaults_version")
    private val SEEDED_FAKE_PROFILE_ID = stringPreferencesKey("seeded_fake_profile_id")
    private val NOTIFICATION_ONBOARDING_SHOWN = stringPreferencesKey("notification_onboarding_shown")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val providerName = prefs[ACTIVE_PROVIDER_KEY] ?: ProviderType.FAKE.name
        val provider = try { ProviderType.valueOf(providerName) } catch (e: Exception) { ProviderType.FAKE }
        AppSettings(activeProvider = provider)
    }

    val briefConfig: Flow<BriefConfig> = context.settingsDataStore.data.map { prefs ->
        val jsonString = prefs[BRIEF_CONFIG_KEY]
        if (jsonString.isNullOrBlank()) {
            BriefConfig()
        } else {
            try {
                Json.decodeFromString<BriefConfig>(jsonString)
            } catch (e: Exception) {
                BriefConfig()
            }
        }
    }

    suspend fun setActiveProvider(providerType: ProviderType) {
        context.settingsDataStore.edit { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] = providerType.name
        }
    }

    suspend fun setBriefConfig(config: BriefConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[BRIEF_CONFIG_KEY] = Json.encodeToString(config)
        }
    }

    // ── Getting Started markers ──
    // These track explicit user actions, not inferred/auto-generated state.

    data class SetupProgress(
        val providerDone: Boolean = false,
        val profileDone: Boolean = false,
        val defaultDone: Boolean = false,
        val workflowDone: Boolean = false
    )

    val setupProgress: Flow<SetupProgress> = context.settingsDataStore.data.map { prefs ->
        SetupProgress(
            providerDone = prefs[SETUP_PROVIDER_DONE] == "true",
            profileDone = prefs[SETUP_PROFILE_DONE] == "true",
            defaultDone = prefs[SETUP_DEFAULT_DONE] == "true",
            workflowDone = prefs[SETUP_WORKFLOW_DONE] == "true"
        )
    }

    override suspend fun markProviderSetupDone() {
        context.settingsDataStore.edit { it[SETUP_PROVIDER_DONE] = "true" }
    }

    override suspend fun markProfileSetupDone() {
        context.settingsDataStore.edit { it[SETUP_PROFILE_DONE] = "true" }
    }

    override suspend fun markDefaultSetupDone() {
        context.settingsDataStore.edit { it[SETUP_DEFAULT_DONE] = "true" }
    }

    override suspend fun markWorkflowSetupDone() {
        context.settingsDataStore.edit { it[SETUP_WORKFLOW_DONE] = "true" }
    }

    // ── First-run seeding ──
    //
    // SEEDED_DEFAULTS_VERSION tracks which bundle of seed data has been applied to
    // this install. It's an integer so future app versions can migrate users forward
    // (e.g. bumping to v2 re-seeds any new defaults we add) without re-seeding what
    // already exists. Current version: 1.

    override suspend fun getSeededDefaultsVersion(): Int =
        context.settingsDataStore.data.map { it[SEEDED_DEFAULTS_VERSION] ?: 0 }.first()

    override suspend fun setSeededDefaultsVersion(version: Int) {
        context.settingsDataStore.edit { it[SEEDED_DEFAULTS_VERSION] = version }
    }

    /** Profile ID of the auto-seeded Local Fake profile, for safe auto-promote checks. */
    override suspend fun getSeededFakeProfileId(): String? =
        context.settingsDataStore.data.map { it[SEEDED_FAKE_PROFILE_ID] }.first()

    override suspend fun setSeededFakeProfileId(id: String) {
        context.settingsDataStore.edit { it[SEEDED_FAKE_PROFILE_ID] = id }
    }

    override suspend fun clearSeededFakeProfileId() {
        context.settingsDataStore.edit { it.remove(SEEDED_FAKE_PROFILE_ID) }
    }

    /** Whether we've already shown the first-run notification-permission rationale. */
    suspend fun wasNotificationOnboardingShown(): Boolean =
        context.settingsDataStore.data.map { it[NOTIFICATION_ONBOARDING_SHOWN] == "true" }.first()

    suspend fun markNotificationOnboardingShown() {
        context.settingsDataStore.edit { it[NOTIFICATION_ONBOARDING_SHOWN] = "true" }
    }
}

package com.automatist.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.automatist.app.domain.models.AppSettings
import com.automatist.app.domain.models.BriefConfig
import com.automatist.app.domain.models.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ACTIVE_PROVIDER_KEY = stringPreferencesKey("active_provider")
    private val BRIEF_CONFIG_KEY = stringPreferencesKey("brief_config")

    // ── Getting Started completion markers (user-intent-based) ──
    private val SETUP_PROVIDER_DONE = stringPreferencesKey("setup_provider_done")
    private val SETUP_PROFILE_DONE = stringPreferencesKey("setup_profile_done")
    private val SETUP_DEFAULT_DONE = stringPreferencesKey("setup_default_done")
    private val SETUP_WORKFLOW_DONE = stringPreferencesKey("setup_workflow_done")

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

    suspend fun markProviderSetupDone() {
        context.settingsDataStore.edit { it[SETUP_PROVIDER_DONE] = "true" }
    }

    suspend fun markProfileSetupDone() {
        context.settingsDataStore.edit { it[SETUP_PROFILE_DONE] = "true" }
    }

    suspend fun markDefaultSetupDone() {
        context.settingsDataStore.edit { it[SETUP_DEFAULT_DONE] = "true" }
    }

    suspend fun markWorkflowSetupDone() {
        context.settingsDataStore.edit { it[SETUP_WORKFLOW_DONE] = "true" }
    }
}

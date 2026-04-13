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
}

package com.synapse.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.synapse.app.domain.models.AppSettings
import com.synapse.app.domain.models.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ACTIVE_PROVIDER_KEY = stringPreferencesKey("active_provider")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val providerName = prefs[ACTIVE_PROVIDER_KEY] ?: ProviderType.FAKE.name
        val provider = try { ProviderType.valueOf(providerName) } catch (e: Exception) { ProviderType.FAKE }
        AppSettings(activeProvider = provider)
    }

    suspend fun setActiveProvider(providerType: ProviderType) {
        context.settingsDataStore.edit { prefs ->
            prefs[ACTIVE_PROVIDER_KEY] = providerType.name
        }
    }
}

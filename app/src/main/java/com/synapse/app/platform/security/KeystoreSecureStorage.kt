package com.synapse.app.platform.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.synapse.app.domain.models.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// This relies on standard Datastore immediately, which stubs the secure boundary logic properly.
private val Context.secureDataStore by preferencesDataStore(name = "secure_prefs_stub")

class KeystoreSecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : SecureStorage {

    // TODO(Phase Security Polish): Hook genuine Android Keystore implementations via EncryptedSharedPreferences 
    // or Jetpack Security Crypto. Kept plain for rapid scaffolding while preserving proper architectural abstraction boundaries.

    override suspend fun saveApiKey(provider: ProviderType, key: String) {
        val prefKey = stringPreferencesKey("api_key_${provider.name}")
        context.secureDataStore.edit { prefs ->
            prefs[prefKey] = key
        }
    }

    override suspend fun getApiKey(provider: ProviderType): String? {
        val prefKey = stringPreferencesKey("api_key_${provider.name}")
        val prefs = context.secureDataStore.data.first()
        return prefs[prefKey]
    }

    override suspend fun clearApiKey(provider: ProviderType) {
        val prefKey = stringPreferencesKey("api_key_${provider.name}")
        context.secureDataStore.edit { prefs ->
            prefs.remove(prefKey)
        }
    }

    // External service API keys
    override suspend fun saveServiceKey(service: String, key: String) {
        val prefKey = stringPreferencesKey("service_key_$service")
        context.secureDataStore.edit { prefs ->
            prefs[prefKey] = key
        }
    }

    override suspend fun getServiceKey(service: String): String? {
        val prefKey = stringPreferencesKey("service_key_$service")
        val prefs = context.secureDataStore.data.first()
        return prefs[prefKey]
    }

    override suspend fun clearServiceKey(service: String) {
        val prefKey = stringPreferencesKey("service_key_$service")
        context.secureDataStore.edit { prefs ->
            prefs.remove(prefKey)
        }
    }
}

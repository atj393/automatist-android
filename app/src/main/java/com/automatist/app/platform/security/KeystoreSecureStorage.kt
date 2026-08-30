package com.automatist.app.platform.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.automatist.app.domain.models.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val SECURE_PREFS_NAME = "secure_prefs"

class KeystoreSecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : SecureStorage {

    // AES256-GCM keys are generated in and never leave the Android Keystore;
    // EncryptedSharedPreferences uses them to encrypt both the file's keys and values.
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun saveApiKey(provider: ProviderType, key: String) {
        put("api_key_${provider.name}", key)
    }

    override suspend fun getApiKey(provider: ProviderType): String? {
        return get("api_key_${provider.name}")
    }

    override suspend fun clearApiKey(provider: ProviderType) {
        remove("api_key_${provider.name}")
    }

    // Per-profile API keys (for custom / OPENAI_COMPATIBLE providers)
    override suspend fun saveProfileKey(keyId: String, key: String) {
        put("profile_key_$keyId", key)
    }

    override suspend fun getProfileKey(keyId: String): String? {
        return get("profile_key_$keyId")
    }

    override suspend fun clearProfileKey(keyId: String) {
        remove("profile_key_$keyId")
    }

    override suspend fun hasProfileKey(keyId: String): Boolean {
        return !getProfileKey(keyId).isNullOrBlank()
    }

    // External service API keys
    override suspend fun saveServiceKey(service: String, key: String) {
        put("service_key_$service", key)
    }

    override suspend fun getServiceKey(service: String): String? {
        return get("service_key_$service")
    }

    override suspend fun clearServiceKey(service: String) {
        remove("service_key_$service")
    }

    private suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    private suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    private suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }
}

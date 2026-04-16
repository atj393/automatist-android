package com.automatist.app.domain.readiness

import com.automatist.app.domain.models.ProviderType
import com.automatist.app.platform.security.SecureStorage

/**
 * In-memory fake for testing readiness evaluation.
 */
class FakeSecureStorage : SecureStorage {

    private val apiKeys = mutableMapOf<ProviderType, String>()
    private val profileKeys = mutableMapOf<String, String>()
    private val serviceKeys = mutableMapOf<String, String>()

    // ── Test helpers ──

    fun setApiKey(provider: ProviderType, key: String) { apiKeys[provider] = key }
    fun setServiceKey(service: String, key: String) { serviceKeys[service] = key }

    // ── SecureStorage implementation ──

    override suspend fun saveApiKey(provider: ProviderType, key: String) { apiKeys[provider] = key }
    override suspend fun getApiKey(provider: ProviderType): String? = apiKeys[provider]
    override suspend fun clearApiKey(provider: ProviderType) { apiKeys.remove(provider) }

    override suspend fun saveProfileKey(keyId: String, key: String) { profileKeys[keyId] = key }
    override suspend fun getProfileKey(keyId: String): String? = profileKeys[keyId]
    override suspend fun clearProfileKey(keyId: String) { profileKeys.remove(keyId) }
    override suspend fun hasProfileKey(keyId: String): Boolean = profileKeys.containsKey(keyId)

    override suspend fun saveServiceKey(service: String, key: String) { serviceKeys[service] = key }
    override suspend fun getServiceKey(service: String): String? = serviceKeys[service]
    override suspend fun clearServiceKey(service: String) { serviceKeys.remove(service) }
}

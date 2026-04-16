package com.automatist.app.platform.security

import com.automatist.app.domain.models.ProviderType

interface SecureStorage {
    // AI provider API keys (keyed by built-in ProviderType)
    suspend fun saveApiKey(provider: ProviderType, key: String)
    suspend fun getApiKey(provider: ProviderType): String?
    suspend fun clearApiKey(provider: ProviderType)

    // Per-profile API keys (keyed by arbitrary string ID, used for custom/OPENAI_COMPATIBLE providers)
    suspend fun saveProfileKey(keyId: String, key: String)
    suspend fun getProfileKey(keyId: String): String?
    suspend fun clearProfileKey(keyId: String)
    suspend fun hasProfileKey(keyId: String): Boolean

    // External service API keys (weather, maps, etc.)
    suspend fun saveServiceKey(service: String, key: String)
    suspend fun getServiceKey(service: String): String?
    suspend fun clearServiceKey(service: String)
}

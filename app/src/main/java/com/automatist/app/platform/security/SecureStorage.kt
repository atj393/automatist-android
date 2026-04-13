package com.automatist.app.platform.security

import com.automatist.app.domain.models.ProviderType

interface SecureStorage {
    // AI provider API keys
    suspend fun saveApiKey(provider: ProviderType, key: String)
    suspend fun getApiKey(provider: ProviderType): String?
    suspend fun clearApiKey(provider: ProviderType)

    // External service API keys (weather, maps, etc.)
    suspend fun saveServiceKey(service: String, key: String)
    suspend fun getServiceKey(service: String): String?
    suspend fun clearServiceKey(service: String)
}

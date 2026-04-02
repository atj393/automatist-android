package com.synapse.app.platform.security

import com.synapse.app.domain.models.ProviderType

interface SecureStorage {
    suspend fun saveApiKey(provider: ProviderType, key: String)
    suspend fun getApiKey(provider: ProviderType): String?
    suspend fun clearApiKey(provider: ProviderType)
}

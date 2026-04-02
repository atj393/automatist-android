package com.synapse.app.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.data.local.SettingsRepository
import com.synapse.app.domain.models.ProviderType
import com.synapse.app.platform.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    val appSettings = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _apiKeys = MutableStateFlow<Map<ProviderType, Boolean>>(emptyMap())
    val apiKeysLoaded = _apiKeys.asStateFlow()

    init {
        loadKeysStatus()
    }

    private fun loadKeysStatus() {
        viewModelScope.launch {
            val status = mutableMapOf<ProviderType, Boolean>()
            ProviderType.entries.filter { it != ProviderType.FAKE }.forEach { provider ->
                val key = secureStorage.getApiKey(provider)
                status[provider] = !key.isNullOrBlank()
            }
            _apiKeys.value = status
        }
    }

    fun setActiveProvider(provider: ProviderType) {
        viewModelScope.launch {
            settingsRepository.setActiveProvider(provider)
        }
    }

    fun saveApiKey(provider: ProviderType, key: String) {
        viewModelScope.launch {
            secureStorage.saveApiKey(provider, key)
            loadKeysStatus()
        }
    }

    fun removeApiKey(provider: ProviderType) {
        viewModelScope.launch {
            secureStorage.clearApiKey(provider)
            loadKeysStatus()
            
            // Revert safely back to Sandbox FAKE variant if their live configured variant deletes entirely
            if (appSettings.value?.activeProvider == provider) {
                setActiveProvider(ProviderType.FAKE)
            }
        }
    }
}

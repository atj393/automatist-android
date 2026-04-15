package com.automatist.app.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.data.local.SettingsRepository
import com.automatist.app.domain.models.*
import kotlinx.coroutines.flow.first
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.platform.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class VaultUiState(
    // Provider API keys
    val providerKeyStatus: Map<ProviderType, Boolean> = emptyMap(),
    val activeProvider: ProviderType = ProviderType.FAKE,

    // Service keys
    val serviceKeyStatus: Map<String, Boolean> = emptyMap(),

    // Provider profiles
    val profiles: List<ProviderProfile> = emptyList(),

    // Profile editor
    val isProfileEditorOpen: Boolean = false,
    val editingProfile: ProviderProfile? = null,
    val profileEditorName: String = "",
    val profileEditorProvider: ProviderType = ProviderType.OPENAI,
    val profileEditorModel: String = "",
    val profileEditorError: String? = null,

    // Checklist: whether the user has at least one workflow
    val hasAnyWorkflow: Boolean = false
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureStorage: SecureStorage,
    private val workflowRepository: WorkflowRepository
) : ViewModel() {

    companion object {
        val SERVICE_KEYS = listOf(
            ServiceKeyInfo(
                id = "openweathermap",
                displayName = "OpenWeatherMap",
                description = "Weather data for workflows",
                helpText = "Create a free API key to fetch current weather conditions in your workflows.",
                signUpUrl = "https://home.openweathermap.org/api_keys",
                steps = listOf(
                    "Open the link below and create a free account (or sign in)",
                    "Go to 'API keys' in your account",
                    "Copy the default key or generate a new one",
                    "Paste it here and tap Save"
                )
            ),
            ServiceKeyInfo(
                id = "openrouteservice",
                displayName = "OpenRouteService",
                description = "Route and commute time data",
                helpText = "Create a free API key to get travel time and distance in your workflows.",
                signUpUrl = "https://api.openrouteservice.org/",
                steps = listOf(
                    "Open the link below and create a free account",
                    "Go to your dashboard and create a new token/key",
                    "Copy the generated API key",
                    "Paste it here and tap Save"
                )
            )
        )
    }

    data class ServiceKeyInfo(
        val id: String,
        val displayName: String,
        val description: String,
        val helpText: String = "",
        val signUpUrl: String = "",
        val steps: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(VaultUiState())
    val state = _state.asStateFlow()

    private val profiles = workflowRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadAllStatus()
        autoMigrateLegacyProvider()
        viewModelScope.launch {
            profiles.collect { profileList ->
                _state.update { it.copy(profiles = profileList) }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(activeProvider = settings.activeProvider) }
            }
        }
        viewModelScope.launch {
            workflowRepository.getAllTemplates().collect { templates ->
                _state.update { it.copy(hasAnyWorkflow = templates.isNotEmpty()) }
            }
        }
    }

    /**
     * Auto-create a provider profile from the legacy activeProvider if:
     * - No profiles exist yet
     * - The legacy activeProvider is a real cloud provider (not FAKE)
     * - That provider has an API key configured
     *
     * This runs once on Settings open and creates a smooth migration path.
     */
    private fun autoMigrateLegacyProvider() {
        viewModelScope.launch {
            val existingProfiles = workflowRepository.getAllProfiles().first()
            if (existingProfiles.isNotEmpty()) return@launch

            val settings = settingsRepository.settings.first()
            val provider = settings.activeProvider
            if (provider == ProviderType.FAKE) return@launch

            val hasKey = !secureStorage.getApiKey(provider).isNullOrBlank()
            if (!hasKey) return@launch

            val defaultModel = ProviderModels.defaultModelFor(provider)
            val now = System.currentTimeMillis()

            val profile = ProviderProfile(
                id = UUID.randomUUID().toString(),
                name = "${provider.displayName} (Migrated)",
                providerType = provider,
                modelId = defaultModel,
                isDefault = true,
                isEnabled = true,
                createdAtMillis = now,
                updatedAtMillis = now
            )

            workflowRepository.saveProfile(profile)
        }
    }

    private fun loadAllStatus() {
        viewModelScope.launch {
            // Provider API keys
            val providerStatus = mutableMapOf<ProviderType, Boolean>()
            ProviderType.entries.filter { it != ProviderType.FAKE }.forEach { provider ->
                providerStatus[provider] = !secureStorage.getApiKey(provider).isNullOrBlank()
            }

            // Service keys
            val serviceStatus = mutableMapOf<String, Boolean>()
            SERVICE_KEYS.forEach { info ->
                serviceStatus[info.id] = !secureStorage.getServiceKey(info.id).isNullOrBlank()
            }

            _state.update { it.copy(providerKeyStatus = providerStatus, serviceKeyStatus = serviceStatus) }
        }
    }

    // ── Provider API Keys ──

    fun setActiveProvider(provider: ProviderType) {
        viewModelScope.launch { settingsRepository.setActiveProvider(provider) }
    }

    fun saveProviderKey(provider: ProviderType, key: String) {
        viewModelScope.launch {
            secureStorage.saveApiKey(provider, key)
            loadAllStatus()
        }
    }

    fun removeProviderKey(provider: ProviderType) {
        viewModelScope.launch {
            secureStorage.clearApiKey(provider)
            if (_state.value.activeProvider == provider) {
                settingsRepository.setActiveProvider(ProviderType.FAKE)
            }
            loadAllStatus()
        }
    }

    // ── Service Keys ──

    fun saveServiceKey(serviceId: String, key: String) {
        viewModelScope.launch {
            secureStorage.saveServiceKey(serviceId, key)
            loadAllStatus()
        }
    }

    fun removeServiceKey(serviceId: String) {
        viewModelScope.launch {
            secureStorage.clearServiceKey(serviceId)
            loadAllStatus()
        }
    }

    // ── Provider Profiles ──

    fun openNewProfile() {
        _state.update {
            it.copy(
                isProfileEditorOpen = true,
                editingProfile = null,
                profileEditorName = "",
                profileEditorProvider = ProviderType.OPENAI,
                profileEditorModel = ProviderModels.defaultModelFor(ProviderType.OPENAI),
                profileEditorError = null
            )
        }
    }

    fun openEditProfile(profile: ProviderProfile) {
        _state.update {
            it.copy(
                isProfileEditorOpen = true,
                editingProfile = profile,
                profileEditorName = profile.name,
                profileEditorProvider = profile.providerType,
                profileEditorModel = profile.modelId,
                profileEditorError = null
            )
        }
    }

    fun closeProfileEditor() {
        _state.update { it.copy(isProfileEditorOpen = false, profileEditorError = null) }
    }

    fun updateProfileName(name: String) = _state.update { it.copy(profileEditorName = name) }
    fun updateProfileProvider(provider: ProviderType) = _state.update {
        it.copy(
            profileEditorProvider = provider,
            profileEditorModel = ProviderModels.defaultModelFor(provider)
        )
    }
    fun updateProfileModel(model: String) = _state.update { it.copy(profileEditorModel = model) }

    fun saveProfile() {
        val current = _state.value
        if (current.profileEditorName.isBlank()) {
            _state.update { it.copy(profileEditorError = "Name is required.") }
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = current.editingProfile

            val profile = ProviderProfile(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = current.profileEditorName.trim(),
                providerType = current.profileEditorProvider,
                modelId = current.profileEditorModel,
                isDefault = existing?.isDefault ?: (current.profiles.isEmpty()),
                isEnabled = true,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )

            workflowRepository.saveProfile(profile)
            _state.update { it.copy(isProfileEditorOpen = false, profileEditorError = null) }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { workflowRepository.deleteProfile(id) }
    }

    fun setDefaultProfile(id: String) {
        viewModelScope.launch { workflowRepository.setDefaultProfile(id) }
    }

    fun toggleProfileEnabled(profile: ProviderProfile) {
        viewModelScope.launch {
            workflowRepository.saveProfile(
                profile.copy(
                    isEnabled = !profile.isEnabled,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }
}

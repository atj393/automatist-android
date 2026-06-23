package com.automatist.app.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.data.local.SettingsRepository
import com.automatist.app.data.offline.OfflineModelRegistry
import com.automatist.app.domain.models.*
import kotlinx.coroutines.flow.first
import com.automatist.app.domain.offline.CustomOfflineModelInput
import com.automatist.app.domain.offline.DownloadProgress
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelEntry
import com.automatist.app.domain.offline.OfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelStatus
import com.automatist.app.domain.repositories.WorkflowRepository
import com.automatist.app.platform.onboarding.DefaultProfilePromoter
import com.automatist.app.platform.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class VaultUiState(
    val providerKeyStatus: Map<ProviderType, Boolean> = emptyMap(),
    val serviceKeyStatus: Map<String, Boolean> = emptyMap(),
    val customServiceKeys: List<String> = emptyList(),
    val profiles: List<ProviderProfile> = emptyList(),

    // Profile editor
    val isProfileEditorOpen: Boolean = false,
    val editingProfile: ProviderProfile? = null,
    val editorName: String = "",
    val editorCatalogEntry: CatalogEntry = ProviderCatalog.ALL_ENTRIES.first(),
    val editorModel: String = "",              // selected from suggested list
    val editorCustomModel: String = "",        // typed custom model
    val editorUseCustomModel: Boolean = false,
    val editorCustomBaseUrl: String = "",       // for presets/custom - may be pre-filled
    val editorCustomProviderName: String = "",  // for Custom Provider only
    val editorApiKey: String = "",
    val editorHasExistingKey: Boolean = false,
    val editorError: String? = null,

    // Getting Started
    val setupProviderDone: Boolean = false,
    val setupProfileDone: Boolean = false,
    val setupDefaultDone: Boolean = false,
    val setupWorkflowDone: Boolean = false,

    // On-device AI
    /** Built-in and user-added local models shown in Settings. */
    val offlineModels: List<OfflineModelEntry> = OfflineModelCatalog.ALL_MODELS,
    /** Current status for each offline model, keyed by model ID. */
    val offlineModelStatuses: Map<String, OfflineModelStatus> = emptyMap(),
    /** Download progress for each model, keyed by model ID. Only meaningful during DOWNLOADING. */
    val offlineDownloadProgress: Map<String, DownloadProgress> = emptyMap(),
    /** Shown as a transient info message (e.g. when download not yet available). */
    val offlineInfoMessage: String? = null
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureStorage: SecureStorage,
    private val workflowRepository: WorkflowRepository,
    private val offlineModelRepository: OfflineModelRepository,
    private val offlineModelRegistry: OfflineModelRegistry,
    private val defaultProfilePromoter: DefaultProfilePromoter
) : ViewModel() {

    companion object {
        val BUILT_IN_SERVICE_KEYS = listOf(
            ServiceKeyInfo("openweathermap", "OpenWeatherMap", "Weather data for workflows",
                "Create a free API key to fetch current weather conditions.",
                "https://home.openweathermap.org/api_keys",
                listOf("Create a free account at OpenWeatherMap", "Go to 'API keys'", "Copy the key", "Paste here")),
            ServiceKeyInfo("openrouteservice", "OpenRouteService", "Route and commute time data",
                "Create a free API key to get travel time and distance.",
                "https://api.openrouteservice.org/",
                listOf("Create a free account", "Create a token", "Copy the API key", "Paste here")),
            ServiceKeyInfo("newsapi", "NewsAPI", "News headlines and articles",
                "Fetch top headlines and search news.",
                "https://newsapi.org/register",
                listOf("Register at NewsAPI.org", "Copy your API key", "Paste here")),
            ServiceKeyInfo("serpapi", "SerpApi", "Web search results",
                "Fetch Google search results.",
                "https://serpapi.com/manage-api-key",
                listOf("Sign up at serpapi.com", "Copy your API key", "Paste here"))
        )
        val SERVICE_KEYS get() = BUILT_IN_SERVICE_KEYS
        private const val CUSTOM_SERVICE_KEYS_PREF = "custom_service_key_ids"
    }

    data class ServiceKeyInfo(
        val id: String, val displayName: String, val description: String,
        val helpText: String = "", val signUpUrl: String = "", val steps: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(VaultUiState())
    val state = _state.asStateFlow()

    private val profiles = workflowRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadAllStatus()
        viewModelScope.launch { profiles.collect { list -> _state.update { it.copy(profiles = list) } } }
        viewModelScope.launch {
            settingsRepository.setupProgress.collect { p ->
                _state.update { it.copy(setupProviderDone = p.providerDone, setupProfileDone = p.profileDone, setupDefaultDone = p.defaultDone, setupWorkflowDone = p.workflowDone) }
            }
        }
        collectOfflineModels()
    }

    // ── On-device AI ──

    /** Active download Jobs keyed by model ID, for cancellation support. */
    private val downloadJobs = mutableMapOf<String, Job>()

    private val observedOfflineModelIds = mutableSetOf<String>()

    private fun collectOfflineModels() {
        viewModelScope.launch {
            offlineModelRegistry.models.collect { models ->
                _state.update { it.copy(offlineModels = models) }
                models.forEach(::observeOfflineModel)
            }
        }
    }

    private fun observeOfflineModel(model: OfflineModelEntry) {
        if (!observedOfflineModelIds.add(model.id)) return
        viewModelScope.launch {
            offlineModelRepository.getModelStatus(model.id).collect { status ->
                _state.update { it.copy(offlineModelStatuses = it.offlineModelStatuses + (model.id to status)) }
            }
        }
        if (!model.isSystemManaged) {
            viewModelScope.launch {
                offlineModelRepository.getDownloadProgress(model.id).collect { progress ->
                    _state.update { it.copy(offlineDownloadProgress = it.offlineDownloadProgress + (model.id to progress)) }
                }
            }
        }
    }

    fun addCustomOfflineModel(input: CustomOfflineModelInput) {
        viewModelScope.launch {
            runCatching { offlineModelRegistry.addCustomModel(input) }
                .onSuccess { model ->
                    _state.update {
                        it.copy(
                            offlineInfoMessage =
                                "${model.displayName} was added. Review the source and tap Download when ready."
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            offlineInfoMessage =
                                error.message ?: "Could not add this model. Check the model details and try again."
                        )
                    }
                }
        }
    }

    /**
     * Check Gemini Nano availability via Android AICore.
     *
     * Triggers [OfflineModelRepository.requestDownload], which:
     * 1. Sets status to DOWNLOADING ("Checking…" in the UI) immediately.
     * 2. Calls the AICore system service to determine if Gemini Nano is available.
     * 3. Updates status to INSTALLED, NOT_INSTALLED, UNSUPPORTED, or FAILED based on result.
     *
     * The status update propagates automatically through the [offlineModelStatuses] Flow;
     * no manual UI refresh is required.
     */
    fun requestOfflineModelDownload(modelId: String) {
        // Cancel any existing download for this model before starting a new one
        downloadJobs.remove(modelId)?.cancel()
        downloadJobs[modelId] = viewModelScope.launch {
            try {
                offlineModelRepository.requestDownload(modelId)
            } finally {
                downloadJobs.remove(modelId)
            }
        }
    }

    /** Cancel an in-progress model download. Stops network I/O and resets state. */
    fun cancelOfflineModelDownload(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
        viewModelScope.launch { offlineModelRepository.cancelDownload(modelId) }
    }

    /** Remove an installed offline model from device storage. */
    fun removeOfflineModel(modelId: String) {
        viewModelScope.launch { offlineModelRepository.removeModel(modelId) }
    }

    /** Dismiss the transient offline info message. */
    fun clearOfflineInfoMessage() = _state.update { it.copy(offlineInfoMessage = null) }

    private fun loadAllStatus() {
        viewModelScope.launch {
            val providerStatus = mutableMapOf<ProviderType, Boolean>()
            // LOCAL_AI excluded: it uses no API key (offline model status tracked separately)
            ProviderType.entries.filter { it != ProviderType.FAKE && it != ProviderType.OPENAI_COMPATIBLE && it != ProviderType.LOCAL_AI }.forEach {
                providerStatus[it] = !secureStorage.getApiKey(it).isNullOrBlank()
            }
            val serviceStatus = mutableMapOf<String, Boolean>()
            BUILT_IN_SERVICE_KEYS.forEach { serviceStatus[it.id] = !secureStorage.getServiceKey(it.id).isNullOrBlank() }
            val customIds = loadCustomServiceKeyIds()
            customIds.forEach { serviceStatus[it] = !secureStorage.getServiceKey(it).isNullOrBlank() }
            _state.update { it.copy(providerKeyStatus = providerStatus, serviceKeyStatus = serviceStatus, customServiceKeys = customIds) }
        }
    }

    private suspend fun loadCustomServiceKeyIds(): List<String> {
        val raw = secureStorage.getServiceKey(CUSTOM_SERVICE_KEYS_PREF)
        return if (raw.isNullOrBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }
    private suspend fun saveCustomServiceKeyIds(ids: List<String>) {
        secureStorage.saveServiceKey(CUSTOM_SERVICE_KEYS_PREF, ids.joinToString(","))
    }

    // ── Service Keys ──
    fun saveServiceKey(id: String, key: String) { viewModelScope.launch { secureStorage.saveServiceKey(id, key); loadAllStatus() } }
    fun removeServiceKey(id: String) { viewModelScope.launch { secureStorage.clearServiceKey(id); loadAllStatus() } }
    fun addCustomServiceKey(id: String, key: String) {
        viewModelScope.launch {
            val s = id.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_"); if (s.isBlank()) return@launch
            secureStorage.saveServiceKey(s, key)
            val ids = loadCustomServiceKeyIds().toMutableList(); if (s !in ids) { ids.add(s); saveCustomServiceKeyIds(ids) }
            loadAllStatus()
        }
    }
    fun removeCustomServiceKey(id: String) {
        viewModelScope.launch {
            secureStorage.clearServiceKey(id)
            val ids = loadCustomServiceKeyIds().toMutableList(); ids.remove(id); saveCustomServiceKeyIds(ids); loadAllStatus()
        }
    }

    // ── Profile Editor ──

    fun openNewProfile() {
        val entry = ProviderCatalog.ALL_ENTRIES.first() // OpenAI
        _state.update {
            it.copy(
                isProfileEditorOpen = true, editingProfile = null, editorName = "",
                editorCatalogEntry = entry, editorModel = entry.defaultModel,
                editorCustomModel = "", editorUseCustomModel = false,
                editorCustomBaseUrl = entry.presetBaseUrl, editorCustomProviderName = "",
                editorApiKey = "", editorHasExistingKey = false, editorError = null
            )
        }
        checkEditorKey(entry)
    }

    /**
     * Opens the profile editor prefilled for a specific on-device model.
     * Called from the On-device AI section's "Create Profile" CTA after install.
     */
    fun openNewOfflineProfile(modelId: String) {
        val entry = ProviderCatalog.LOCAL_AI_ENTRY
        val modelName = _state.value.offlineModels.find { it.id == modelId }?.displayName ?: "On-device AI"
        val isBuiltInModel = modelId in entry.suggestedModels.map { it.first }
        _state.update {
            it.copy(
                isProfileEditorOpen = true, editingProfile = null,
                editorName = modelName,
                editorCatalogEntry = entry,
                editorModel = if (isBuiltInModel) modelId else entry.defaultModel,
                editorCustomModel = if (isBuiltInModel) "" else modelId,
                editorUseCustomModel = !isBuiltInModel,
                editorCustomBaseUrl = "", editorCustomProviderName = "",
                editorApiKey = "", editorHasExistingKey = false, editorError = null
            )
        }
    }

    /** Returns true if a LOCAL_AI profile already exists for the given model ID. */
    fun hasProfileForModel(modelId: String): Boolean {
        return _state.value.profiles.any {
            it.providerType == ProviderType.LOCAL_AI && it.modelId == modelId
        }
    }

    fun openEditProfile(profile: ProviderProfile) {
        val entry = ProviderCatalog.resolveForProfile(profile)
        val builtInModels = entry.suggestedModels.map { it.first }
        val modelInList = profile.modelId in builtInModels
        _state.update {
            it.copy(
                isProfileEditorOpen = true, editingProfile = profile, editorName = profile.name,
                editorCatalogEntry = entry,
                editorModel = if (modelInList) profile.modelId else (entry.defaultModel),
                editorCustomModel = if (!modelInList) profile.modelId else "",
                editorUseCustomModel = !modelInList && entry.suggestedModels.isNotEmpty(),
                editorCustomBaseUrl = profile.customBaseUrl.ifBlank { entry.presetBaseUrl },
                editorCustomProviderName = if (entry.category == CatalogCategory.CUSTOM) profile.name else "",
                editorApiKey = "", editorHasExistingKey = false, editorError = null
            )
        }
        viewModelScope.launch {
            val hasKey = when {
                profile.usesPerProfileKey -> secureStorage.hasProfileKey(profile.customApiKeyId)
                profile.providerType == ProviderType.FAKE -> true
                profile.providerType == ProviderType.LOCAL_AI -> true // no API key required
                entry.category == CatalogCategory.NATIVE -> !secureStorage.getApiKey(profile.providerType).isNullOrBlank()
                else -> false
            }
            _state.update { it.copy(editorHasExistingKey = hasKey) }
        }
    }

    fun closeProfileEditor() { _state.update { it.copy(isProfileEditorOpen = false, editorError = null) } }

    fun selectCatalogEntry(entry: CatalogEntry) {
        val currentName = _state.value.editorName
        // Auto-prefill profile name for OFFLINE entries when the name is blank
        // or was previously auto-filled from another provider's display name.
        val autoName = if (entry.category == CatalogCategory.OFFLINE) {
            val modelName = entry.suggestedModels
                .find { it.first == entry.defaultModel }?.second
                ?: entry.displayName
            if (currentName.isBlank() || isAutoFilledName(currentName)) modelName else currentName
        } else {
            // Clear auto-filled name when switching away from OFFLINE,
            // but keep user-typed names.
            if (isAutoFilledName(currentName)) "" else currentName
        }
        _state.update {
            it.copy(
                editorName = autoName,
                editorCatalogEntry = entry, editorModel = entry.defaultModel,
                editorCustomModel = "", editorUseCustomModel = false,
                editorCustomBaseUrl = entry.presetBaseUrl, editorCustomProviderName = "",
                editorApiKey = "", editorHasExistingKey = false
            )
        }
        checkEditorKey(entry)
    }

    /** Returns true if the name matches a known auto-filled model/provider name. */
    private fun isAutoFilledName(name: String): Boolean {
        val autoNames = _state.value.offlineModels.map { it.displayName }.toSet() +
            setOf(ProviderCatalog.LOCAL_AI_ENTRY.displayName)
        return name in autoNames
    }

    private fun checkEditorKey(entry: CatalogEntry) {
        viewModelScope.launch {
            val hasKey = when {
                entry.runtimeType == ProviderType.FAKE -> true
                entry.runtimeType == ProviderType.LOCAL_AI -> true // no API key required for on-device AI
                entry.category == CatalogCategory.NATIVE -> !secureStorage.getApiKey(entry.runtimeType).isNullOrBlank()
                else -> false // preset/custom always per-profile, new profile has no key yet
            }
            _state.update { it.copy(editorHasExistingKey = hasKey) }
        }
    }

    fun updateEditorName(n: String) = _state.update { it.copy(editorName = n) }
    fun updateEditorModel(m: String) {
        val s = _state.value
        // When switching between on-device models, update the auto-filled name too
        val newName = if (s.editorCatalogEntry.category == CatalogCategory.OFFLINE && isAutoFilledName(s.editorName)) {
            s.offlineModels.find { it.id == m }?.displayName ?: s.editorName
        } else s.editorName
        _state.update { it.copy(editorModel = m, editorName = newName) }
    }
    fun updateEditorCustomModel(m: String) = _state.update { it.copy(editorCustomModel = m) }
    fun toggleEditorCustomModel(use: Boolean) = _state.update { it.copy(editorUseCustomModel = use) }
    fun updateEditorCustomBaseUrl(u: String) = _state.update { it.copy(editorCustomBaseUrl = u) }
    fun updateEditorCustomProviderName(n: String) = _state.update { it.copy(editorCustomProviderName = n) }
    fun updateEditorApiKey(k: String) = _state.update { it.copy(editorApiKey = k) }

    fun removeKeyForCurrentEditor() {
        viewModelScope.launch {
            val s = _state.value; val existing = s.editingProfile
            if (existing != null && existing.usesPerProfileKey) secureStorage.clearProfileKey(existing.customApiKeyId)
            else if (s.editorCatalogEntry.category == CatalogCategory.NATIVE && s.editorCatalogEntry.runtimeType != ProviderType.FAKE)
                secureStorage.clearApiKey(s.editorCatalogEntry.runtimeType)
            _state.update { it.copy(editorHasExistingKey = false, editorApiKey = "") }
            loadAllStatus()
        }
    }

    fun saveProfile() {
        val s = _state.value
        val entry = s.editorCatalogEntry
        if (s.editorName.isBlank()) { _state.update { it.copy(editorError = "Name is required.") }; return }

        val isCustom = entry.category == CatalogCategory.CUSTOM
        val isPreset = entry.category == CatalogCategory.PRESET
        val needsBaseUrl = isCustom || isPreset
        val needsModel = entry.runtimeType != ProviderType.FAKE

        // Resolve model
        val finalModel = if (s.editorUseCustomModel || entry.suggestedModels.isEmpty()) {
            val m = s.editorCustomModel.trim()
            if (needsModel && m.isBlank()) { _state.update { it.copy(editorError = "Model is required.") }; return }
            m
        } else s.editorModel

        // Resolve base URL
        val finalBaseUrl = if (needsBaseUrl) {
            val u = s.editorCustomBaseUrl.trim()
            if (u.isBlank()) { _state.update { it.copy(editorError = "Base URL is required.") }; return }
            u
        } else ""

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = s.editingProfile
            val usesPerProfile = entry.usesPerProfileKey

            // Handle key
            val apiKeyId: String
            if (usesPerProfile) {
                apiKeyId = existing?.customApiKeyId?.takeIf { it.isNotBlank() }
                    ?: "profile_${existing?.id ?: UUID.randomUUID().toString()}"
                if (s.editorApiKey.isNotBlank()) secureStorage.saveProfileKey(apiKeyId, s.editorApiKey)
                if (s.editorApiKey.isNotBlank()) settingsRepository.markProviderSetupDone()
            } else if (entry.runtimeType != ProviderType.FAKE && entry.runtimeType != ProviderType.LOCAL_AI) {
                apiKeyId = ""
                if (s.editorApiKey.isNotBlank()) {
                    secureStorage.saveApiKey(entry.runtimeType, s.editorApiKey)
                    settingsRepository.markProviderSetupDone()
                }
            } else {
                // FAKE or LOCAL_AI: no API key required
                apiKeyId = ""
                settingsRepository.markProviderSetupDone()
            }

            val profile = ProviderProfile(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = s.editorName.trim(),
                providerType = entry.runtimeType,
                modelId = if (entry.runtimeType == ProviderType.FAKE) "fake-demo" else finalModel,
                isDefault = existing?.isDefault ?: (s.profiles.isEmpty()),
                isFallback = existing?.isFallback ?: false,
                isEnabled = true,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
                customBaseUrl = finalBaseUrl,
                customApiKeyId = apiKeyId,
                providerPresetId = entry.id
            )

            workflowRepository.saveProfile(profile)
            settingsRepository.markProfileSetupDone()

            // Auto-promote this new profile to app default, but only when the current
            // default is still the seeded Local Fake profile. Never overrides an explicit
            // user choice. No-op for profile edits.
            val wasNewProfile = existing == null
            val promoted = defaultProfilePromoter.maybePromoteOnCreate(profile, wasNewProfile)

            if (profile.isDefault || promoted) settingsRepository.markDefaultSetupDone()

            _state.update { it.copy(isProfileEditorOpen = false, editorError = null) }
            loadAllStatus()
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            val p = workflowRepository.getProfileById(id)
            if (p?.usesPerProfileKey == true && p.customApiKeyId.isNotBlank()) secureStorage.clearProfileKey(p.customApiKeyId)
            workflowRepository.deleteProfile(id)
        }
    }
    fun setDefaultProfile(id: String) { viewModelScope.launch { workflowRepository.setDefaultProfile(id); settingsRepository.markDefaultSetupDone() } }
    fun setFallbackProfile(id: String) { viewModelScope.launch { workflowRepository.setFallbackProfile(id) } }
    fun toggleProfileEnabled(p: ProviderProfile) {
        viewModelScope.launch { workflowRepository.saveProfile(p.copy(isEnabled = !p.isEnabled, updatedAtMillis = System.currentTimeMillis())) }
    }
    fun markWorkflowSetupDone() { viewModelScope.launch { settingsRepository.markWorkflowSetupDone() } }
}

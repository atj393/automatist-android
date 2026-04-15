package com.automatist.app.feature.vault

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatist.app.BuildConfig
import com.automatist.app.domain.models.ProviderModels
import com.automatist.app.domain.models.ProviderProfile
import com.automatist.app.domain.models.ProviderType

enum class SettingsSection(val key: String) {
    AI_SETUP("section_ai_setup"),
    PROFILES("section_profiles"),
    DEFAULTS("section_defaults"),
    PROVIDER_KEYS("section_provider_keys"),
    SERVICE_KEYS("section_service_keys"),
    LEGACY("section_legacy")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onNavigateToTemplates: () -> Unit = {},
    initialSection: String = "",
    viewModel: VaultViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Collapsible state for provider and service key sections
    var expandedProviderKeys by remember { mutableStateOf(emptySet<ProviderType>()) }
    var expandedServiceKeys by remember { mutableStateOf(emptySet<String>()) }

    // Checklist progress
    val hasAnyProviderKey = state.providerKeyStatus.values.any { it }
    val hasAnyProfile = state.profiles.isNotEmpty()
    val hasDefaultProfile = state.profiles.any { it.isDefault }
    val hasAnyWorkflow = state.hasAnyWorkflow
    val isDemoMode = state.activeProvider == ProviderType.FAKE && !hasAnyProviderKey
    val allSetupDone = (hasAnyProviderKey || isDemoMode) && hasAnyProfile && hasDefaultProfile && hasAnyWorkflow
    val showSetupBanner = !allSetupDone

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", fontWeight = FontWeight.Bold)
                        Text(
                            "Configure AI providers, keys, and preferences",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        if (state.isProfileEditorOpen) {
            ProfileEditor(
                state = state,
                providerKeyStatus = state.providerKeyStatus,
                onNameChange = viewModel::updateProfileName,
                onProviderChange = viewModel::updateProfileProvider,
                onModelChange = viewModel::updateProfileModel,
                onSave = viewModel::saveProfile,
                onCancel = viewModel::closeProfileEditor,
                modifier = Modifier.padding(padding)
            )
        } else {
            // Auto-scroll to target section and auto-expand relevant accordions
            LaunchedEffect(initialSection) {
                if (initialSection.isNotBlank()) {
                    kotlinx.coroutines.delay(300)
                    val bannerOffset = if (showSetupBanner) 1 else 0
                    val sectionIndex = when (initialSection) {
                        SettingsSection.AI_SETUP.key,
                        SettingsSection.PROFILES.key,
                        SettingsSection.DEFAULTS.key,
                        SettingsSection.PROVIDER_KEYS.key,
                        SettingsSection.LEGACY.key -> {
                            // All AI-related sections are now inside the unified AI Setup card
                            if (initialSection == SettingsSection.PROVIDER_KEYS.key) {
                                expandedProviderKeys = ProviderType.entries
                                    .filter { it != ProviderType.FAKE }.toSet()
                            }
                            0 + bannerOffset
                        }
                        SettingsSection.SERVICE_KEYS.key -> {
                            expandedServiceKeys = VaultViewModel.SERVICE_KEYS
                                .map { it.id }.toSet()
                            1 + bannerOffset
                        }
                        else -> 0
                    }
                    listState.animateScrollToItem(sectionIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // ── Getting Started Banner ──
                if (showSetupBanner) {
                    item(key = "setup_banner") {
                        SetupBanner(
                            hasProviderKeyOrDemo = hasAnyProviderKey || isDemoMode,
                            hasProfile = hasAnyProfile,
                            hasDefault = hasDefaultProfile,
                            hasWorkflow = hasAnyWorkflow,
                            onScrollToAiSetup = {
                                // Scroll to AI Setup section (first card after banner)
                            },
                            onNavigateToTemplates = onNavigateToTemplates
                        )
                    }
                }

                // ── Unified AI Setup Section ──
                item(key = SettingsSection.AI_SETUP.key) {
                    AiSetupSection(
                        state = state,
                        expandedProviderKeys = expandedProviderKeys,
                        onToggleExpandProvider = { provider ->
                            expandedProviderKeys = if (provider in expandedProviderKeys)
                                expandedProviderKeys - provider
                            else expandedProviderKeys + provider
                        },
                        onExpandAllProviders = {
                            val allProviders = ProviderType.entries.filter { it != ProviderType.FAKE }
                            val allExpanded = allProviders.all { it in expandedProviderKeys }
                            expandedProviderKeys = if (allExpanded) emptySet() else allProviders.toSet()
                        },
                        allProvidersExpanded = ProviderType.entries
                            .filter { it != ProviderType.FAKE }
                            .all { it in expandedProviderKeys },
                        onSaveProviderKey = viewModel::saveProviderKey,
                        onRemoveProviderKey = viewModel::removeProviderKey,
                        onSetActiveProvider = viewModel::setActiveProvider,
                        onOpenNewProfile = viewModel::openNewProfile,
                        onOpenEditProfile = viewModel::openEditProfile,
                        onDeleteProfile = viewModel::deleteProfile,
                        onSetDefaultProfile = viewModel::setDefaultProfile,
                        onToggleProfileEnabled = viewModel::toggleProfileEnabled
                    )
                }

                // ── Service API Keys ──
                item(key = SettingsSection.SERVICE_KEYS.key) {
                    val allServiceIds = VaultViewModel.SERVICE_KEYS.map { it.id }
                    val allExpanded = allServiceIds.all { it in expandedServiceKeys }

                    SettingsSectionCard(
                        icon = Icons.Default.CloudQueue,
                        title = "Service API Keys",
                        subtitle = "Keys for external services used by workflow actions (weather, routes). These are optional.",
                        headerAction = {
                            TextButton(
                                onClick = {
                                    expandedServiceKeys = if (allExpanded) emptySet()
                                    else allServiceIds.toSet()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    if (allExpanded) "Collapse All" else "Expand All",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            VaultViewModel.SERVICE_KEYS.forEach { info ->
                                CollapsibleServiceKeyCard(
                                    info = info,
                                    isConfigured = state.serviceKeyStatus[info.id] == true,
                                    isExpanded = info.id in expandedServiceKeys,
                                    onToggleExpand = {
                                        expandedServiceKeys = if (info.id in expandedServiceKeys)
                                            expandedServiceKeys - info.id
                                        else
                                            expandedServiceKeys + info.id
                                    },
                                    onSave = { key -> viewModel.saveServiceKey(info.id, key) },
                                    onRemove = { viewModel.removeServiceKey(info.id) }
                                )
                            }
                        }
                    }
                }

                // ── About ──
                item(key = "section_about") {
                    SettingsSectionCard(
                        icon = Icons.Default.Info,
                        title = "About",
                        subtitle = "Automatist — Workflow AI utility"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AboutRow("Version", BuildConfig.VERSION_NAME)
                            AboutRow("Storage", "All data stays on device")
                            AboutRow("API keys", "Stored locally, never uploaded")
                            AboutRow("Cloud backup", "Workflow definitions only")
                        }
                    }
                }

                // ── Security ──
                item(key = "section_security") {
                    SettingsSectionCard(
                        icon = Icons.Default.Shield,
                        title = "Security",
                        subtitle = "How Automatist protects your data"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "For your security, install Automatist only from Google Play. Unofficial or modified builds may expose your API keys and workflow data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SecurityRow("API keys", "Stored locally in secure storage. Never included in cloud backup or workflow export.")
                            SecurityRow("Workflow export", "Sensitive fields (auth headers, tokens) are automatically redacted.")
                            SecurityRow("Cloud backup", "Optional. Uses your Google Drive app-private folder. Workflow definitions only.")
                            SecurityRow("Android backup", "API keys are excluded from device backup and transfer.")
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Getting Started Banner — 4-step actionable checklist
// ══════════════════════════════════════════════════════════════

@Composable
private fun SetupBanner(
    hasProviderKeyOrDemo: Boolean,
    hasProfile: Boolean,
    hasDefault: Boolean,
    hasWorkflow: Boolean,
    onScrollToAiSetup: () -> Unit,
    onNavigateToTemplates: () -> Unit
) {
    val completedCount = listOf(hasProviderKeyOrDemo, hasProfile, hasDefault, hasWorkflow).count { it }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Getting Started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "$completedCount of 4 steps complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Step 1: Provider key or demo
            SetupStep(
                number = 1,
                text = if (hasProviderKeyOrDemo) "Provider key added" else "Add a provider API key or choose demo mode",
                isDone = hasProviderKeyOrDemo,
                actionLabel = if (!hasProviderKeyOrDemo) "Set up below" else null,
                onAction = onScrollToAiSetup
            )

            // Step 2: Create profile
            SetupStep(
                number = 2,
                text = if (hasProfile) "AI profile created" else "Create an AI profile",
                isDone = hasProfile,
                actionLabel = if (!hasProfile && hasProviderKeyOrDemo) "Create below" else null,
                onAction = onScrollToAiSetup
            )

            // Step 3: Set default
            SetupStep(
                number = 3,
                text = if (hasDefault) "Default profile set" else "Set a default AI profile",
                isDone = hasDefault,
                actionLabel = if (!hasDefault && hasProfile) "Set below" else null,
                onAction = onScrollToAiSetup
            )

            // Step 4: Create or open a workflow
            SetupStep(
                number = 4,
                text = if (hasWorkflow) "Workflow created" else "Create your first workflow",
                isDone = hasWorkflow,
                actionLabel = if (!hasWorkflow && hasDefault) "Browse Templates" else null,
                onAction = onNavigateToTemplates
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    text: String,
    isDone: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (isDone) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$number. $text",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (!isDone) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && !isDone) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Unified AI Setup Section
// ══════════════════════════════════════════════════════════════

@Composable
private fun AiSetupSection(
    state: VaultUiState,
    expandedProviderKeys: Set<ProviderType>,
    onToggleExpandProvider: (ProviderType) -> Unit,
    onExpandAllProviders: () -> Unit,
    allProvidersExpanded: Boolean,
    onSaveProviderKey: (ProviderType, String) -> Unit,
    onRemoveProviderKey: (ProviderType) -> Unit,
    onSetActiveProvider: (ProviderType) -> Unit,
    onOpenNewProfile: () -> Unit,
    onOpenEditProfile: (ProviderProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSetDefaultProfile: (String) -> Unit,
    onToggleProfileEnabled: (ProviderProfile) -> Unit
) {
    SettingsSectionCard(
        icon = Icons.Default.Psychology,
        title = "AI Setup",
        subtitle = "Provider keys, AI profiles, default profile, and demo mode — everything your workflows need to run."
    ) {
        // ── Subsection A: Supported Providers & API Keys ──
        AiSetupSubheader(
            title = "1. Provider API Keys",
            description = "Automatist supports 3 AI providers. Add your API key to enable a provider."
        )
        Spacer(Modifier.height(4.dp))

        // Expand/Collapse All toggle
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onExpandAllProviders,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    if (allProvidersExpanded) "Collapse All" else "Expand All",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProviderType.entries.filter { it != ProviderType.FAKE }.forEach { provider ->
                CollapsibleProviderKeyCard(
                    provider = provider,
                    isConfigured = state.providerKeyStatus[provider] == true,
                    isExpanded = provider in expandedProviderKeys,
                    onToggleExpand = { onToggleExpandProvider(provider) },
                    onSave = { key -> onSaveProviderKey(provider, key) },
                    onRemove = { onRemoveProviderKey(provider) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))

        // ── Subsection B: AI Profiles ──
        AiSetupSubheader(
            title = "2. AI Profiles",
            description = "Named configurations that pair a provider with a model. Workflows use profiles to control which AI generates output."
        )
        Spacer(Modifier.height(8.dp))

        if (state.profiles.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No profiles yet",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    val guidanceText = if (state.providerKeyStatus.values.none { it }) {
                        "Add a provider API key above first, then create a profile."
                    } else {
                        "You have a provider key configured. Create a profile to start using it."
                    }
                    Text(
                        guidanceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        hasProviderKey = state.providerKeyStatus[profile.providerType] == true,
                        onEdit = { onOpenEditProfile(profile) },
                        onDelete = { onDeleteProfile(profile.id) },
                        onSetDefault = { onSetDefaultProfile(profile.id) },
                        onToggleEnabled = { onToggleProfileEnabled(profile) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenNewProfile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add AI Profile")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))

        // ── Subsection C: Default AI Profile ──
        AiSetupSubheader(
            title = "3. Default AI Profile",
            description = "The profile used when a workflow doesn't specify one."
        )
        Spacer(Modifier.height(8.dp))

        DefaultProfilePicker(
            profiles = state.profiles,
            onSetDefault = onSetDefaultProfile
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))

        // ── Subsection D: Demo & Fallback Mode ──
        AiSetupSubheader(
            title = "4. Demo & Fallback Mode",
            description = "Test workflows without an API key, or set a fallback provider for when no profile is selected."
        )
        Spacer(Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ProviderType.entries.forEach { provider ->
                    val isCloud = provider != ProviderType.FAKE
                    val hasKey = state.providerKeyStatus[provider] == true
                    val canSelect = !isCloud || hasKey
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = state.activeProvider == provider,
                            onClick = { if (canSelect) onSetActiveProvider(provider) },
                            enabled = canSelect
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                provider.displayName,
                                color = if (canSelect) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            if (provider == ProviderType.FAKE) {
                                Text(
                                    "Generates sample output without calling any API",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        if (isCloud && !hasKey) {
                            Text(
                                "Needs key",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Once you create an AI Profile and set it as default, this fallback is rarely used.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun AiSetupSubheader(title: String, description: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ══════════════════════════════════════════════════════════════
// Default Profile Picker (inline within AI Setup)
// ══════════════════════════════════════════════════════════════

@Composable
private fun DefaultProfilePicker(
    profiles: List<ProviderProfile>,
    onSetDefault: (String) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val enabledProfiles = profiles.filter { it.isEnabled }
    val currentDefault = profiles.find { it.isDefault }

    if (profiles.isEmpty()) {
        Text(
            "Create an AI Profile above to set a default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Box {
            Card(
                onClick = { if (enabledProfiles.isNotEmpty()) dropdownExpanded = true },
                colors = CardDefaults.cardColors(
                    containerColor = if (currentDefault != null)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        if (currentDefault != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (currentDefault != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (currentDefault != null) "Current Default" else "No default selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentDefault != null) {
                            Text(
                                currentDefault.displayLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (enabledProfiles.isNotEmpty()) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Change default",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                enabledProfiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.name)
                                    Text(
                                        "${profile.providerType.displayName} / ${profile.modelId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (profile.isDefault) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSetDefault(profile.id)
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Workflows use this profile unless they have a specific override. If no default is set, the app falls back to Demo & Fallback Mode below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ══════════════════════════════════════════════════════════════
// Section Card Wrapper
// ══════════════════════════════════════════════════════════════

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (headerAction != null) {
                    headerAction()
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Profile Card
// ══════════════════════════════════════════════════════════════

@Composable
private fun ProfileCard(
    profile: ProviderProfile,
    hasProviderKey: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isDisabled = !profile.isEnabled

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = if (profile.isDefault) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDisabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                profile.isDefault -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (profile.isDefault) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("Default", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        if (isDisabled) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("Disabled", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${profile.providerType.displayName} / ${profile.modelId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Enable/disable toggle
                Switch(
                    checked = profile.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            // Missing API key warning
            if (!hasProviderKey) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "No API key for ${profile.providerType.displayName}. Add one in Provider API Keys above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!profile.isDefault) {
                    TextButton(onClick = onSetDefault) { Text("Set Default") }
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Profile") },
            text = { Text("Delete \"${profile.name}\"? Workflows using this profile will fall back to the app default.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════
// Collapsible Provider Key Card
// ══════════════════════════════════════════════════════════════

@Composable
private fun CollapsibleProviderKeyCard(
    provider: ProviderType,
    isConfigured: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column {
            // Header — always visible, clickable to expand/collapse
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onToggleExpand)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    provider.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(isConfigured = isConfigured)
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 12.dp, end = 12.dp, bottom = 12.dp
                    )
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))

                    if (isConfigured) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "API key is configured and ready to use.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onRemove) {
                            Text("Remove Key", color = MaterialTheme.colorScheme.error)
                        }
                    } else if (showInput) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("${provider.displayName} API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { showInput = false; keyInput = "" }) { Text("Cancel") }
                            Button(onClick = {
                                if (keyInput.isNotBlank()) {
                                    onSave(keyInput); keyInput = ""; showInput = false
                                }
                            }) { Text("Save") }
                        }
                    } else {
                        Text(
                            "No API key configured. Add one to use ${provider.displayName} in your profiles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { showInput = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Key")
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Collapsible Service Key Card
// ══════════════════════════════════════════════════════════════

@Composable
private fun CollapsibleServiceKeyCard(
    info: VaultViewModel.ServiceKeyInfo,
    isConfigured: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column {
            // Header — always visible, clickable to expand/collapse
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onToggleExpand)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        info.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        info.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(isConfigured = isConfigured)
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 12.dp, end = 12.dp, bottom = 12.dp
                    )
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))

                    if (info.helpText.isNotBlank()) {
                        Text(
                            info.helpText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // Setup steps (collapsible within expanded card)
                    if (info.steps.isNotEmpty()) {
                        TextButton(
                            onClick = { showHelp = !showHelp },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (showHelp) "Hide setup steps" else "How to get this key",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(
                                if (showHelp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, modifier = Modifier.size(16.dp)
                            )
                        }
                        if (showHelp) {
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                info.steps.forEachIndexed { i, step ->
                                    Text(
                                        "${i + 1}. $step",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (info.signUpUrl.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.signUpUrl))
                                            context.startActivity(intent)
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Open ${info.displayName}", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    if (isConfigured) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Key is configured.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onRemove) {
                            Text("Remove Key", color = MaterialTheme.colorScheme.error)
                        }
                    } else if (showInput) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { showInput = false; keyInput = "" }) { Text("Cancel") }
                            Button(onClick = {
                                if (keyInput.isNotBlank()) {
                                    onSave(keyInput); keyInput = ""; showInput = false
                                }
                            }) { Text("Save") }
                        }
                    } else {
                        OutlinedButton(onClick = { showInput = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Key")
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Status Chip
// ══════════════════════════════════════════════════════════════

@Composable
private fun StatusChip(isConfigured: Boolean) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                if (isConfigured) "Configured" else "Not Set",
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingIcon = {
            Icon(
                if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                null,
                modifier = Modifier.size(14.dp),
                tint = if (isConfigured) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        },
        modifier = Modifier.height(26.dp)
    )
}

// ══════════════════════════════════════════════════════════════
// About / Security rows
// ══════════════════════════════════════════════════════════════

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SecurityRow(label: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Icon(
            Icons.Default.CheckCircle, null,
            modifier = Modifier.size(14.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Profile Editor — with provider readiness indicators
// ══════════════════════════════════════════════════════════════

@Composable
private fun ProfileEditor(
    state: VaultUiState,
    providerKeyStatus: Map<ProviderType, Boolean>,
    onNameChange: (String) -> Unit,
    onProviderChange: (ProviderType) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (state.editingProfile != null) "Edit Profile" else "New AI Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Create a named AI configuration to use in your workflows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.profileEditorName,
            onValueChange = onNameChange,
            label = { Text("Profile Name *") },
            placeholder = { Text("e.g. GPT-4o Fast, Claude Deep") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = state.profileEditorError != null
        )

        Text("Provider", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(
            "Automatist supports these AI providers. Select one with a configured API key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            ProviderType.entries.filter { it != ProviderType.FAKE }.forEach { provider ->
                val hasKey = providerKeyStatus[provider] == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = state.profileEditorProvider == provider,
                        onClick = { onProviderChange(provider) }
                    )
                    Text(provider.displayName, modifier = Modifier.weight(1f))
                    if (hasKey) {
                        Icon(
                            Icons.Default.CheckCircle, "Key configured",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            "No key",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Warning if selected provider has no key
        val selectedHasKey = providerKeyStatus[state.profileEditorProvider] == true
        if (!selectedHasKey) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "No API key configured for ${state.profileEditorProvider.displayName}. " +
                            "This profile won't work until you add a key in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Text("Model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        val models = ProviderModels.modelsFor(state.profileEditorProvider)
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            models.forEach { (modelId, modelName) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.profileEditorModel == modelId,
                        onClick = { onModelChange(modelId) }
                    )
                    Text(modelName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.profileEditorError != null) {
            Text(
                state.profileEditorError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text(if (state.editingProfile != null) "Update" else "Create")
            }
        }
    }
}

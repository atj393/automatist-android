package com.automatist.app.feature.vault

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.automatist.app.domain.models.*
import com.automatist.app.data.offline.ManifestImportPreview
import com.automatist.app.domain.offline.CustomOfflineModelInput
import com.automatist.app.domain.offline.DownloadProgress
import com.automatist.app.domain.offline.OfflineModelEntry
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelStatus
import com.automatist.app.platform.support.SupportConfig

enum class SettingsSection(val key: String) {
    DEFAULT_PROFILE("section_default_profile"),
    AI_SETUP("section_ai_setup"),
    PROFILES("section_profiles"), DEFAULTS("section_defaults"),
    PROVIDER_KEYS("section_provider_keys"),
    SERVICE_KEYS("section_service_keys"),
    OFFLINE_AI("section_offline_ai"),
    SUPPORT("section_support"),
    LEGACY("section_legacy")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit, onNavigateToTemplates: () -> Unit = {},
    initialSection: String = "", viewModel: VaultViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var expandedServiceKeys by remember { mutableStateOf(emptySet<String>()) }
    val showBanner = !(state.setupProviderDone && state.setupProfileDone && state.setupDefaultDone && state.setupWorkflowDone)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.offlineInfoMessage) {
        val msg = state.offlineInfoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
        viewModel.clearOfflineInfoMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Settings", fontWeight = FontWeight.Bold)
                    Text("Profiles, API keys, and app settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isProfileEditorOpen) {
            ProfileEditor(state = state, vm = viewModel, modifier = Modifier.padding(padding))
        } else {
            LaunchedEffect(initialSection) {
                if (initialSection.isNotBlank()) {
                    kotlinx.coroutines.delay(300)
                    val bo = if (showBanner) 1 else 0
                    val idx = when (initialSection) {
                        SettingsSection.DEFAULT_PROFILE.key -> 0 + bo
                        SettingsSection.AI_SETUP.key, SettingsSection.PROFILES.key, SettingsSection.DEFAULTS.key, SettingsSection.PROVIDER_KEYS.key, SettingsSection.LEGACY.key -> 1 + bo
                        SettingsSection.OFFLINE_AI.key -> 2 + bo
                        SettingsSection.SERVICE_KEYS.key -> { expandedServiceKeys = VaultViewModel.BUILT_IN_SERVICE_KEYS.map { it.id }.toSet(); 3 + bo }
                        SettingsSection.SUPPORT.key -> 4 + bo
                        else -> 0
                    }
                    listState.animateScrollToItem(idx)
                }
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {

                if (showBanner) { item(key = "banner") { Banner(state, onNavigateToTemplates) } }

                item(key = SettingsSection.DEFAULT_PROFILE.key) { DefaultProfileSection(state.profiles, viewModel::setDefaultProfile) }

                item(key = SettingsSection.AI_SETUP.key) {
                    ProfilesSection(state, viewModel::openNewProfile, viewModel::openEditProfile, viewModel::deleteProfile,
                        viewModel::setDefaultProfile, viewModel::setFallbackProfile, viewModel::toggleProfileEnabled)
                }

                item(key = SettingsSection.OFFLINE_AI.key) {
                    OnDeviceAISection(
                        models = state.offlineModels,
                        statuses = state.offlineModelStatuses,
                        downloadProgress = state.offlineDownloadProgress,
                        profiles = state.profiles,
                        onCheck = viewModel::requestOfflineModelDownload,
                        onRecheck = viewModel::requestOfflineModelDownload,
                        onRemove = viewModel::removeOfflineModel,
                        onCancelDownload = viewModel::cancelOfflineModelDownload,
                        onAddCustomModel = viewModel::addCustomOfflineModel,
                        onForgetCustomModel = viewModel::removeCustomModelSource,
                        onCreateProfile = viewModel::openNewOfflineProfile,
                        hasProfileForModel = viewModel::hasProfileForModel,
                        manifestInProgress = state.manifestImportInProgress,
                        manifestPreview = state.manifestPreview,
                        manifestError = state.manifestImportError,
                        onImportManifest = viewModel::importManifest,
                        onConfirmManifest = viewModel::confirmManifestImport,
                        onCancelManifest = viewModel::cancelManifestImport
                    )
                }

                item(key = SettingsSection.SERVICE_KEYS.key) {
                    ServiceKeysSection(state, expandedServiceKeys,
                        { id -> expandedServiceKeys = if (id in expandedServiceKeys) expandedServiceKeys - id else expandedServiceKeys + id },
                        viewModel::saveServiceKey, viewModel::removeServiceKey, viewModel::addCustomServiceKey, viewModel::removeCustomServiceKey)
                }

                item(key = "support") { Card2(Icons.Default.HelpOutline, "Support", "Get help and contact us") {
                    LinkRow("Contact support", SupportConfig.CONTACT_SUPPORT_URL)
                    LinkRow("Send feedback", "mailto:${SupportConfig.FEEDBACK_EMAIL}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    LinkRow("Privacy Policy", SupportConfig.PRIVACY_POLICY_URL)
                    LinkRow("Terms of Use", SupportConfig.TERMS_OF_USE_URL)
                } }

                item(key = "about") { Card2(Icons.Default.Info, "About", "Automatist — Workflow AI utility") {
                    IRow("Version", BuildConfig.VERSION_NAME); IRow("Storage", "All data stays on device"); IRow("API keys", "Stored locally, never uploaded")
                    LinkRow("Website", "https://automatist.cloud")
                } }

                item(key = "security") { Card2(Icons.Default.Shield, "Security", "How Automatist protects your data") {
                    Text("Install Automatist only from Google Play.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SRow("API keys", "Stored locally. Never in backup or export.")
                    SRow("Export", "Sensitive fields automatically redacted.")
                    SRow("Cloud backup", "Optional. Google Drive app-private folder.")
                } }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ══════════════════ Getting Started ══════════════════

@Composable
private fun Banner(s: VaultUiState, onTemplates: () -> Unit) {
    val c = listOf(s.setupProviderDone, s.setupProfileDone, s.setupDefaultDone, s.setupWorkflowDone).count { it }
    Card2(Icons.Default.Lightbulb, "Getting Started", "$c of 4 steps complete") {
        St(1, if (s.setupProviderDone) "Provider configured" else "Add an API key or use the built-in demo", s.setupProviderDone)
        St(2, if (s.setupProfileDone) "AI profile created" else "Create an AI profile", s.setupProfileDone)
        St(3, if (s.setupDefaultDone) "Default profile set" else "Set a default AI profile", s.setupDefaultDone)
        St(4, if (s.setupWorkflowDone) "Workflow created" else "Create your first workflow", s.setupWorkflowDone,
            if (!s.setupWorkflowDone && s.setupDefaultDone) "Browse Templates" else null, onTemplates)
    }
}

@Composable
private fun St(n: Int, t: String, done: Boolean, act: String? = null, onAct: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("$n. $t", style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (!done) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (act != null && !done) TextButton(onClick = onAct, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(act, style = MaterialTheme.typography.labelSmall) }
    }
}

// ══════════════════ Default Profile ══════════════════

@Composable
private fun DefaultProfileSection(profiles: List<ProviderProfile>, onSet: (String) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    val en = profiles.filter { it.isEnabled }; val cur = profiles.find { it.isDefault }
    Card2(Icons.Default.Star, "Default AI Profile", "The primary profile used by all workflows unless overridden.") {
        if (profiles.isEmpty()) { Text("Add an AI Profile to set a default.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else {
            Box {
                Card(onClick = { if (en.isNotEmpty()) exp = true }, colors = CardDefaults.cardColors(
                    containerColor = if (cur != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                        Icon(if (cur != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                            tint = if (cur != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (cur != null) "Current Default" else "No default selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (cur != null) { Text(cur.displayLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                        }
                        if (en.isNotEmpty()) Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                    en.forEach { p -> DropdownMenuItem(text = { Text(p.displayLabel) },
                        trailingIcon = { if (p.isDefault) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                        onClick = { onSet(p.id); exp = false }) }
                }
            }
            val fb = profiles.find { it.isFallback }
            if (fb != null) { Spacer(Modifier.height(6.dp)); Text("Fallback: ${fb.displayLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
        }
    }
}

// ══════════════════ AI Profiles ══════════════════

@Composable
private fun ProfilesSection(
    state: VaultUiState, onNew: () -> Unit, onEdit: (ProviderProfile) -> Unit,
    onDel: (String) -> Unit, onSetDef: (String) -> Unit, onSetFb: (String) -> Unit, onToggle: (ProviderProfile) -> Unit
) {
    Card2(Icons.Default.Psychology, "AI Profiles", "Connect to an AI provider (OpenAI, Anthropic, Gemini, and more).") {
        if (state.profiles.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp)); Text("No profiles yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Tap Add AI Profile to get started.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.profiles.forEach { p ->
                    val entry = ProviderCatalog.resolveForProfile(p)
                    val hasKey = when {
                        p.providerType == ProviderType.FAKE -> true
                        p.providerType == ProviderType.LOCAL_AI -> true // no API key required
                        p.usesPerProfileKey -> true // we can't synchronously check, assume present if ID exists
                        else -> state.providerKeyStatus[p.providerType] == true
                    }
                    PCard(p, entry, hasKey, onEdit, onDel, onSetDef, onSetFb, onToggle)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add AI Profile")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PCard(
    p: ProviderProfile, entry: CatalogEntry, hasKey: Boolean,
    onEdit: (ProviderProfile) -> Unit, onDel: (String) -> Unit, onSetDef: (String) -> Unit,
    onSetFb: (String) -> Unit, onToggle: (ProviderProfile) -> Unit
) {
    var showDel by remember { mutableStateOf(false) }

    // Unified surface for all profile cards — status is carried by the accent
    // border + chips, not by shifting the background. Prior version tinted the
    // default row's background with primaryContainer@25% opacity which read as
    // a visual bug more than a highlight, and made default/fallback/disabled
    // variants look inconsistent next to each other in a list.
    val accent = when {
        !p.isEnabled -> null
        p.isDefault -> MaterialTheme.colorScheme.primary
        p.isFallback -> MaterialTheme.colorScheme.tertiary
        else -> null
    }
    val shape = RoundedCornerShape(12.dp)
    val cardModifier = Modifier
        .fillMaxWidth()
        .let { if (accent != null) it.border(BorderStroke(1.5.dp, accent.copy(alpha = 0.65f)), shape) else it }

    Card(
        modifier = cardModifier,
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (p.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header: title + switch. Chips moved out of this row so a long
            // profile name never competes with the Default/Fallback badges
            // for horizontal space.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!p.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${entry.displayName} / ${p.modelId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = p.isEnabled, onCheckedChange = { onToggle(p) })
            }

            // Status row — rendered whenever there's any status worth naming.
            // A disabled profile shows a "Disabled" chip so the user knows
            // why its name is dimmed; a default/fallback profile still shows
            // its badge even if disabled, which helps surface "your default
            // is currently off" at a glance. Order: Disabled → Default →
            // Fallback so the most actionable state reads first.
            if (!p.isEnabled || p.isDefault || p.isFallback) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!p.isEnabled) {
                        StatusChip("Disabled", Icons.Default.PauseCircle, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (p.isDefault) StatusChip("Default", Icons.Default.Star, MaterialTheme.colorScheme.primary)
                    if (p.isFallback) StatusChip("Fallback", Icons.Default.Shield, MaterialTheme.colorScheme.tertiary)
                }
            }

            if (!hasKey && p.providerType != ProviderType.FAKE && p.providerType != ProviderType.LOCAL_AI) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("API key missing — edit to add one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // Action row — FlowRow keeps a 3- or 4-button set from overflowing
            // on narrow screens and wraps cleanly instead of clipping. End
            // alignment keeps the visual consistent with the prior design.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.End)
            ) {
                if (!p.isDefault) TextButton(onClick = { onSetDef(p.id) }) { Text("Set Default") }
                if (!p.isFallback) TextButton(onClick = { onSetFb(p.id) }) { Text("Set Fallback") }
                TextButton(onClick = { onEdit(p) }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = { showDel = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (showDel) AlertDialog(onDismissRequest = { showDel = false }, title = { Text("Delete Profile") },
        text = { Text("Delete \"${p.name}\"?") },
        confirmButton = { TextButton(onClick = { onDel(p.id); showDel = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showDel = false }) { Text("Cancel") } })
}

/**
 * Small filled status badge with a leading icon. Replaces the previous
 * washed-out AssistChip: filled container reads as a deliberate tag at a
 * glance, leading icon reinforces semantic meaning, and height stays under
 * the title row so cards don't grow on wider content.
 */
@Composable
private fun StatusChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(22.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(11.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// ══════════════════ On-device AI ══════════════════

@Composable
private fun OnDeviceAISection(
    models: List<OfflineModelEntry>,
    statuses: Map<String, OfflineModelStatus>,
    downloadProgress: Map<String, DownloadProgress>,
    profiles: List<ProviderProfile>,
    onCheck: (String) -> Unit,
    onRecheck: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onAddCustomModel: (CustomOfflineModelInput) -> Unit,
    onForgetCustomModel: (String) -> Unit,
    onCreateProfile: (String) -> Unit,
    hasProfileForModel: (String) -> Boolean,
    manifestInProgress: Boolean,
    manifestPreview: ManifestImportPreview?,
    manifestError: String?,
    onImportManifest: (String) -> Unit,
    onConfirmManifest: () -> Unit,
    onCancelManifest: () -> Unit
) {
    // Auto-check Gemini Nano availability when this section appears, but only once
    // (only if status is still NOT_INSTALLED, meaning it has never been checked).
    val nanoStatus = statuses[OfflineModelCatalog.GEMINI_NANO_ID] ?: OfflineModelStatus.NOT_INSTALLED
    LaunchedEffect(nanoStatus) {
        if (nanoStatus == OfflineModelStatus.NOT_INSTALLED) {
            onCheck(OfflineModelCatalog.GEMINI_NANO_ID)
        }
    }

    var showAddCustomModel by remember { mutableStateOf(false) }
    var showImportManifest by remember { mutableStateOf(false) }

    Card2(Icons.Default.PhoneAndroid, "On-device AI", "Run AI offline — no internet or API key required.") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            models.forEach { model ->
                val status = statuses[model.id] ?: OfflineModelStatus.NOT_INSTALLED
                val progress = downloadProgress[model.id] ?: DownloadProgress()
                val hasProfile = hasProfileForModel(model.id)
                OfflineModelCard(
                    displayName = model.displayName,
                    description = model.description,
                    sizeLabel = model.sizeLabel,
                    tags = model.tags,
                    status = status,
                    isSystemManaged = model.isSystemManaged,
                    isUserAdded = model.isUserAdded,
                    downloadProgress = progress,
                    hasProfile = hasProfile,
                    onCheck = { onCheck(model.id) },
                    onRecheck = { onRecheck(model.id) },
                    onRemove = { onRemove(model.id) },
                    onCancelDownload = { onCancelDownload(model.id) },
                    onCreateProfile = { onCreateProfile(model.id) },
                    onForgetSource = { onForgetCustomModel(model.id) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HowToAddModelHelp()

        Spacer(Modifier.height(8.dp))
        // Preferred advanced path: import a manifest URL.
        OutlinedButton(
            onClick = { showImportManifest = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import model manifest")
        }
        Spacer(Modifier.height(4.dp))
        // Fallback: enter every field by hand.
        TextButton(
            onClick = { showAddCustomModel = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Enter model details manually")
        }

        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("How On-device AI works", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "On-device models run directly on your phone — no internet or API key required. " +
                    "Gemini Nano is built into select Pixel and Galaxy devices (Android 14+). " +
                    "Gemma 3 runs on most modern Android phones but needs a one-time download. " +
                    "Your cloud AI profiles are unaffected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Honesty card: local models share the phone's RAM and CPU budget with
        // everything else it's doing, so speed and consistency vary. Framed as
        // expectation-setting, not a warning — shown once in Settings, not
        // repeated on every run.
        Spacer(Modifier.height(6.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info, null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "What to expect from on-device models",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Speed and quality depend on your device's available memory and current system load, " +
                        "so results may vary between runs. On-device AI is best for smaller tasks, quick " +
                        "experiments, and testing. For the most consistent results on longer or more complex " +
                        "workflows, use a cloud AI provider (OpenAI, Anthropic, or Gemini).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
    if (showAddCustomModel) {
        AddCustomOfflineModelDialog(
            onDismiss = { showAddCustomModel = false },
            onAdd = { input ->
                onAddCustomModel(input)
                showAddCustomModel = false
            }
        )
    }
    if (showImportManifest) {
        ImportManifestDialog(
            inProgress = manifestInProgress,
            preview = manifestPreview,
            error = manifestError,
            onFetch = onImportManifest,
            onConfirm = {
                onConfirmManifest()
                showImportManifest = false
            },
            onDismiss = {
                onCancelManifest()
                showImportManifest = false
            }
        )
    }
}

/** Compact "How to add a model" help area shown in the On-device AI section. */
@Composable
private fun HowToAddModelHelp() {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Icon(Icons.Default.HelpOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "How to add a model",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                val style = MaterialTheme.typography.bodySmall
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                Text("Automatist runs compatible MediaPipe .task language models locally on your device.", style = style, color = color)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Most Hugging Face models are not compatible with this version. Files such as .gguf, " +
                        ".safetensors, .bin, APKs, scripts, and plug-ins cannot be used.",
                    style = style, color = color
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "For the easiest setup, choose a built-in or verified model. Advanced users can import a " +
                        "compatible model manifest or enter verified model details manually.",
                    style = style, color = color
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Before importing, make sure the publisher provides a direct HTTPS .task file, SHA-256 " +
                        "checksum, public license link, and model/source page.",
                    style = style, color = color
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Automatist verifies the checksum, but cannot guarantee a third-party model's quality, " +
                        "device performance, or license compliance.",
                    style = style, color = color
                )
            }
        }
    }
}

/**
 * Two-stage manifest import dialog.
 *
 * Stage 1: enter the manifest URL and fetch it. Stage 2 (when [preview] is non-null):
 * review the validated details and acknowledge the license before the model source is
 * added. The model file itself is downloaded later, from the model card.
 */
@Composable
private fun ImportManifestDialog(
    inProgress: Boolean,
    preview: ManifestImportPreview?,
    error: String?,
    onFetch: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var manifestUrl by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preview == null) "Import model manifest" else "Review model") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (preview == null) {
                    Text(
                        "Enter the HTTPS URL of a model manifest (a small JSON file the publisher provides). " +
                            "Automatist downloads and checks the manifest only — it does not download the model yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manifestUrl,
                        onValueChange = { manifestUrl = it },
                        label = { Text("HTTPS manifest URL") },
                        singleLine = true,
                        enabled = !inProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (inProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Fetching manifest…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    val input = preview.input
                    ManifestReviewRow("Name", input.displayName)
                    if (preview.version.isNotBlank()) ManifestReviewRow("Version", preview.version)
                    ManifestReviewRow("Source", input.sourceUrl ?: "—")
                    ManifestReviewRow("License", input.licenseUrl)
                    ManifestReviewRow("Approx. size", "${input.downloadSizeMb} MB")
                    ManifestReviewRow("Min RAM", input.minimumRamMb?.let { "$it MB" } ?: "Default")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Automatist will verify the SHA-256 checksum after download, but cannot guarantee the " +
                            "model's quality, performance, or license compliance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { acknowledged = !acknowledged }
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Text(
                            "I have reviewed the model source and license and have permission to use it.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (preview == null) {
                TextButton(onClick = { onFetch(manifestUrl) }, enabled = !inProgress && manifestUrl.isNotBlank()) {
                    Text("Fetch")
                }
            } else {
                TextButton(onClick = onConfirm, enabled = acknowledged) { Text("Add model") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManifestReviewRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddCustomOfflineModelDialog(
    onDismiss: () -> Unit,
    onAdd: (CustomOfflineModelInput) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var modelUrl by remember { mutableStateOf("") }
    var sha256 by remember { mutableStateOf("") }
    var sizeMb by remember { mutableStateOf("") }
    var licenseUrl by remember { mutableStateOf("") }
    var acceptedLicense by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add compatible model") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Automatist can only run MediaPipe .task model files with the built-in local runtime. " +
                        "Do not add APKs, libraries, scripts, .gguf, or .safetensors files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Model name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelUrl,
                    onValueChange = { modelUrl = it },
                    label = { Text("HTTPS .task model URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sha256,
                    onValueChange = { sha256 = it },
                    label = { Text("SHA-256 checksum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sizeMb,
                    onValueChange = { sizeMb = it.filter(Char::isDigit) },
                    label = { Text("Download size (MB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = licenseUrl,
                    onValueChange = { licenseUrl = it },
                    label = { Text("HTTPS license URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { acceptedLicense = !acceptedLicense }
                ) {
                    Checkbox(checked = acceptedLicense, onCheckedChange = { acceptedLicense = it })
                    Text(
                        "I have reviewed the model license and have permission to use it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedSize = sizeMb.toIntOrNull()
                if (parsedSize == null) {
                    error = "Enter the download size in MB."
                } else if (!acceptedLicense) {
                    error = "Confirm that you have reviewed the model license."
                } else {
                    onAdd(
                        CustomOfflineModelInput(
                            displayName = displayName,
                            modelUrl = modelUrl,
                            sha256 = sha256,
                            downloadSizeMb = parsedSize,
                            licenseUrl = licenseUrl
                        )
                    )
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun OfflineModelCard(
    displayName: String,
    description: String,
    sizeLabel: String,
    tags: List<String>,
    status: OfflineModelStatus,
    isSystemManaged: Boolean,
    isUserAdded: Boolean = false,
    downloadProgress: DownloadProgress = DownloadProgress(),
    hasProfile: Boolean = false,
    onCheck: () -> Unit,
    onRecheck: () -> Unit,
    onRemove: () -> Unit,
    onCancelDownload: () -> Unit = {},
    onCreateProfile: () -> Unit = {},
    onForgetSource: () -> Unit = {}
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(sizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OfflineStatusChip(status, isSystemManaged)
            }
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (status) {
                OfflineModelStatus.NOT_INSTALLED -> {
                    val label = if (isSystemManaged) "Check Availability" else "Download ($sizeLabel)"
                    val icon = if (isSystemManaged) Icons.Default.Search else Icons.Default.Download
                    OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Icon(icon, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }
                OfflineModelStatus.DOWNLOADING -> {
                    if (isSystemManaged) {
                        // System-managed: indeterminate spinner ("Checking…")
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Checking…")
                        }
                    } else {
                        // Downloadable: show progress bar with percentage + cancel button
                        val pct = downloadProgress.percent
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (pct >= 0) {
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                )
                                val downloadedMb = downloadProgress.bytesDownloaded / 1_000_000
                                val totalMb = downloadProgress.totalBytes / 1_000_000
                                Text(
                                    "Downloading… $pct% ($downloadedMb / $totalMb MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                // Total size truly unknown — show indeterminate bar with downloaded bytes
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                                val downloadedMb = downloadProgress.bytesDownloaded / 1_000_000
                                Text(
                                    "Downloading… $downloadedMb MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }
                OfflineModelStatus.INSTALLED -> {
                    val installedLabel = if (isSystemManaged) "Available" else "Installed"
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(installedLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (isSystemManaged) {
                            TextButton(onClick = onRecheck) {
                                Text("Recheck", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            TextButton(onClick = { showResetDialog = true }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    // CTA: create a profile to use this model
                    if (!hasProfile) {
                        OutlinedButton(onClick = onCreateProfile, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create AI Profile")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Profile configured — ready to use in workflows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OfflineModelStatus.FAILED -> {
                    val retryLabel = if (isSystemManaged) "Retry check" else "Retry download"
                    OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(retryLabel)
                    }
                }
                OfflineModelStatus.UNSUPPORTED -> {
                    val msg = if (isSystemManaged)
                        "Not supported — requires Android 14+ and a Pixel 8+ or Galaxy S24+ device"
                    else
                        "Not supported on this device"
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            // User-added sources can be forgotten entirely (distinct from removing just
            // the downloaded file). Available in every state so a failed/unsupported
            // custom source can still be cleaned up.
            if (isUserAdded) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showForgetDialog = true }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove from list", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    // Confirmation dialog for resetting a non-system-managed model
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Remove $displayName?") },
            text = { Text("The model file will be deleted from this device. You can download it again later.") },
            confirmButton = { TextButton(onClick = { onRemove(); showResetDialog = false }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }
    // Confirmation dialog for forgetting a user-added custom source entirely
    if (showForgetDialog) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text("Remove $displayName from your models?") },
            text = {
                Text(
                    "This removes the custom model source and deletes any downloaded file. " +
                        "AI profiles that use this model are kept, but will need a different model until you add one again."
                )
            },
            confirmButton = { TextButton(onClick = { onForgetSource(); showForgetDialog = false }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showForgetDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun OfflineStatusChip(status: OfflineModelStatus, isSystemManaged: Boolean = false) {
    val (label, icon, tint) = when (status) {
        OfflineModelStatus.NOT_INSTALLED ->
            if (isSystemManaged) Triple("Not checked", Icons.Default.HelpOutline, MaterialTheme.colorScheme.onSurfaceVariant)
            else Triple("Not installed", Icons.Default.CloudDownload, MaterialTheme.colorScheme.onSurfaceVariant)
        OfflineModelStatus.DOWNLOADING ->
            if (isSystemManaged) Triple("Checking", Icons.Default.Sync, MaterialTheme.colorScheme.primary)
            else Triple("Downloading", Icons.Default.Sync, MaterialTheme.colorScheme.primary)
        OfflineModelStatus.INSTALLED ->
            if (isSystemManaged) Triple("Available", Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary)
            else Triple("Installed", Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary)
        OfflineModelStatus.FAILED ->
            if (isSystemManaged) Triple("Check failed", Icons.Default.Warning, MaterialTheme.colorScheme.error)
            else Triple("Failed", Icons.Default.Warning, MaterialTheme.colorScheme.error)
        OfflineModelStatus.UNSUPPORTED ->
            Triple("Unsupported", Icons.Default.Block, MaterialTheme.colorScheme.error)
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(14.dp), tint = tint) },
        modifier = Modifier.height(26.dp)
    )
}

// ══════════════════ Service API Keys ══════════════════

@Composable
private fun ServiceKeysSection(
    state: VaultUiState, expanded: Set<String>, onToggle: (String) -> Unit,
    onSave: (String, String) -> Unit, onRemove: (String) -> Unit,
    onAddCustom: (String, String) -> Unit, onRemoveCustom: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }; var cn by remember { mutableStateOf("") }; var cv by remember { mutableStateOf("") }
    Card2(Icons.Default.CloudQueue, "Service API Keys", "API keys for weather, mapping, and other services used in your workflows.") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VaultViewModel.BUILT_IN_SERVICE_KEYS.forEach { info ->
                SvcCard(info, state.serviceKeyStatus[info.id] == true, info.id in expanded,
                    { onToggle(info.id) }, { k -> onSave(info.id, k) }, { onRemove(info.id) })
            }
        }
        if (state.customServiceKeys.isNotEmpty()) {
            Spacer(Modifier.height(12.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)); Spacer(Modifier.height(8.dp))
            Text("Custom Service Keys", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.customServiceKeys.forEach { id ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                            Text(id, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onRemoveCustom(id) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)); Spacer(Modifier.height(8.dp))
        if (showAdd) {
            OutlinedTextField(value = cn, onValueChange = { cn = it }, label = { Text("Key Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = cv, onValueChange = { cv = it }, label = { Text("API Key Value") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showAdd = false; cn = ""; cv = "" }) { Text("Cancel") }
                Button(onClick = { if (cn.isNotBlank() && cv.isNotBlank()) { onAddCustom(cn, cv); cn = ""; cv = ""; showAdd = false } }) { Text("Save") }
            }
        } else OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add Custom Service Key")
        }
    }
}

@Composable
private fun SvcCard(info: VaultViewModel.ServiceKeyInfo, ok: Boolean, exp: Boolean, onToggle: () -> Unit, onSave: (String) -> Unit, onRemove: () -> Unit) {
    var ki by remember { mutableStateOf("") }; var si by remember { mutableStateOf(false) }; var sh by remember { mutableStateOf(false) }; val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onToggle).padding(12.dp).fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) { Text(info.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                StatusDot(ok); Spacer(Modifier.width(4.dp)); Icon(if (exp) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = exp, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)); Spacer(Modifier.height(8.dp))
                    if (info.helpText.isNotBlank()) { Text(info.helpText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)); Spacer(Modifier.height(4.dp)) }
                    if (info.steps.isNotEmpty()) {
                        TextButton(onClick = { sh = !sh }, contentPadding = PaddingValues(0.dp)) { Text(if (sh) "Hide steps" else "How to get this key", style = MaterialTheme.typography.labelMedium) }
                        if (sh) { Column(modifier = Modifier.padding(start = 4.dp)) {
                            info.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (info.signUpUrl.isNotBlank()) { Spacer(Modifier.height(6.dp))
                                OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.signUpUrl))) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Open ${info.displayName}", style = MaterialTheme.typography.labelMedium) } }
                        } }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (ok) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Key configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = onRemove) { Text("Remove Key", color = MaterialTheme.colorScheme.error) }
                    } else if (si) { OutlinedTextField(value = ki, onValueChange = { ki = it }, label = { Text("API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { TextButton(onClick = { si = false; ki = "" }) { Text("Cancel") }
                            Button(onClick = { if (ki.isNotBlank()) { onSave(ki); ki = ""; si = false } }) { Text("Save") } }
                    } else OutlinedButton(onClick = { si = true }) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add Key") }
                }
            }
        }
    }
}

@Composable private fun StatusDot(ok: Boolean) {
    AssistChip(onClick = {}, label = { Text(if (ok) "Configured" else "Not Set", style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, modifier = Modifier.size(14.dp),
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }, modifier = Modifier.height(26.dp))
}

// ══════════════════ Reusable ══════════════════

@Composable
private fun Card2(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(12.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)); Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable private fun IRow(l: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
@Composable private fun SRow(l: String, d: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp)); Column { Text(l, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium); Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * Tappable info row for external URLs. Mirrors [IRow]'s label / value layout
 * so it blends into the About card, then adds primary-tinted text + an
 * external-link glyph on the trailing edge so it reads as "link" not "info".
 * The URL is displayed without the `https://` prefix to keep the row tidy.
 * Launch is wrapped in try/catch so a device with no browser fails silently
 * instead of crashing — the row stays informational.
 */
@Composable
private fun LinkRow(label: String, url: String) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    // No browser / no Activity able to handle the intent —
                    // swallow; the row is purely informational in that case.
                }
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                url.removePrefix("https://").removePrefix("http://"),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = "Open $label in browser",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ══════════════════ Profile Editor ══════════════════

@Composable
private fun ProfileEditor(state: VaultUiState, vm: VaultViewModel, modifier: Modifier = Modifier) {
    val entry = state.editorCatalogEntry
    val isNative = entry.category == CatalogCategory.NATIVE
    val isCustom = entry.category == CatalogCategory.CUSTOM
    val isOffline = entry.category == CatalogCategory.OFFLINE
    val isFake = entry.runtimeType == ProviderType.FAKE
    val needsKey = !isFake && !isOffline
    val needsBaseUrl = !isNative && !isOffline
    val hasModels = entry.suggestedModels.isNotEmpty()

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text(if (state.editingProfile != null) "Edit Profile" else "New AI Profile",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // ── Name ──
        OutlinedTextField(value = state.editorName, onValueChange = vm::updateEditorName,
            label = { Text("Profile Name *") }, placeholder = { Text("e.g. My GPT-4o, Groq Fast, Local Llama") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), isError = state.editorError != null)

        // ── Provider Catalog ──
        Text("Provider", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)

        val catalog = ProviderCatalog.ALL_ENTRIES
        val nativeEntries = catalog.filter { it.category == CatalogCategory.NATIVE && it.runtimeType != ProviderType.FAKE }
        val presetEntries = catalog.filter { it.category == CatalogCategory.PRESET }
        val offlineEntries = catalog.filter { it.category == CatalogCategory.OFFLINE }
        val fakeEntry = catalog.find { it.runtimeType == ProviderType.FAKE }
        val customEntry = catalog.find { it.category == CatalogCategory.CUSTOM }

        // Track which sections are expanded (On-device expanded by default since it's first)
        var offlineExpanded by remember { mutableStateOf(true) }
        var providersExpanded by remember { mutableStateOf(!isOffline) }

        // ── On-device AI (FIRST) ──
        if (offlineEntries.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { offlineExpanded = !offlineExpanded }.padding(vertical = 4.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("On-device AI", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("No API key needed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Icon(if (offlineExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = offlineExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    offlineEntries.forEach { e -> ProviderRow(e, entry, vm::selectCatalogEntry) }
                    if (isOffline) {
                        Spacer(Modifier.height(4.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                // Folded the "What to expect" honesty line into
                                // the same info card so a user picking a local
                                // model in the profile editor sees both the
                                // availability hint and the variability hint at
                                // once — no stacked notices.
                                Text(
                                    "Runs fully offline — no API key or internet required. " +
                                    "Download or check availability in Settings → On-device AI before first use. " +
                                    "Speed and consistency depend on your device's available memory and system load; " +
                                    "on-device AI is best for smaller tasks and testing. For the most consistent " +
                                    "results on longer workflows, use a cloud provider.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }

        // ── Cloud & API Providers (collapsible) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { providersExpanded = !providersExpanded }.padding(vertical = 4.dp)
        ) {
            Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cloud & API Providers", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Icon(if (providersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = providersExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                // Native providers
                nativeEntries.forEach { e -> ProviderRow(e, entry, vm::selectCatalogEntry) }
                // Presets
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Text("More Providers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                presetEntries.forEach { e -> ProviderRow(e, entry, vm::selectCatalogEntry) }
                // Local Demo + Custom
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                if (fakeEntry != null) ProviderRow(fakeEntry, entry, vm::selectCatalogEntry)
                if (customEntry != null) ProviderRow(customEntry, entry, vm::selectCatalogEntry)
            }
        }

        // ── Custom Provider info ──
        if (isCustom) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Advanced: Requires an endpoint supporting the OpenAI /v1/chat/completions format. Incorrect URL or model may cause errors.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        // ── Base URL (preset or custom) ──
        if (needsBaseUrl) {
            OutlinedTextField(value = state.editorCustomBaseUrl, onValueChange = vm::updateEditorCustomBaseUrl,
                label = { Text("Base URL *") },
                placeholder = { Text(entry.presetBaseUrl.ifBlank { "https://api.example.com" }) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        // ── Model ──
        if (!isFake) {
            Text("Model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            if (hasModels) {
                Column {
                    entry.suggestedModels.forEach { (mId, mName) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !state.editorUseCustomModel && state.editorModel == mId,
                                onClick = { vm.toggleEditorCustomModel(false); vm.updateEditorModel(mId) })
                            Text(mName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.editorUseCustomModel, onClick = { vm.toggleEditorCustomModel(true) })
                        Text("Custom model ID", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            AnimatedVisibility(visible = state.editorUseCustomModel || !hasModels) {
                OutlinedTextField(value = state.editorCustomModel, onValueChange = vm::updateEditorCustomModel,
                    label = { Text("Model ID *") },
                    placeholder = { Text(entry.defaultModel.ifBlank { "e.g. llama-3-70b-chat" }) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        // ── API Key ──
        if (needsKey) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Text("API Key", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            if (state.editorHasExistingKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp)); Text("API key is configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = vm::removeKeyForCurrentEditor) { Text("Remove Key", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(4.dp))
                Text("Enter a new key below to replace, or leave blank to keep current.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Enter your ${entry.displayName} API key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = state.editorApiKey, onValueChange = vm::updateEditorApiKey,
                label = { Text(if (state.editorHasExistingKey) "New API Key (optional)" else "API Key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }

        // ── Error ──
        if (state.editorError != null) Text(state.editorError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

        Spacer(Modifier.weight(1f, fill = false)); Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::closeProfileEditor, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = vm::saveProfile, modifier = Modifier.weight(1f)) { Text(if (state.editingProfile != null) "Update" else "Create") }
        }
    }
}

@Composable
private fun ProviderRow(e: CatalogEntry, selected: CatalogEntry, onSelect: (CatalogEntry) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSelect(e) }.padding(vertical = 4.dp, horizontal = 4.dp)) {
        RadioButton(selected = selected.id == e.id, onClick = { onSelect(e) })
        Column(modifier = Modifier.weight(1f)) {
            Text(e.displayName, style = MaterialTheme.typography.bodyMedium,
                color = if (e.category == CatalogCategory.CUSTOM) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
            if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        when {
            e.category == CatalogCategory.NATIVE && e.runtimeType != ProviderType.FAKE ->
                Text("Native", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            e.category == CatalogCategory.OFFLINE ->
                Text("Offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

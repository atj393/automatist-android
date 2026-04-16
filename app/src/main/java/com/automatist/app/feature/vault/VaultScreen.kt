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

enum class SettingsSection(val key: String) {
    DEFAULT_PROFILE("section_default_profile"),
    AI_SETUP("section_ai_setup"),
    PROFILES("section_profiles"), DEFAULTS("section_defaults"),
    PROVIDER_KEYS("section_provider_keys"),
    SERVICE_KEYS("section_service_keys"),
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

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("Settings", fontWeight = FontWeight.Bold)
                Text("AI profiles, keys, and preferences", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
    }) { padding ->
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
                        SettingsSection.SERVICE_KEYS.key -> { expandedServiceKeys = VaultViewModel.BUILT_IN_SERVICE_KEYS.map { it.id }.toSet(); 2 + bo }
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

                item(key = SettingsSection.SERVICE_KEYS.key) {
                    ServiceKeysSection(state, expandedServiceKeys,
                        { id -> expandedServiceKeys = if (id in expandedServiceKeys) expandedServiceKeys - id else expandedServiceKeys + id },
                        viewModel::saveServiceKey, viewModel::removeServiceKey, viewModel::addCustomServiceKey, viewModel::removeCustomServiceKey)
                }

                item(key = "about") { Card2(Icons.Default.Info, "About", "Automatist — Workflow AI utility") {
                    IRow("Version", BuildConfig.VERSION_NAME); IRow("Storage", "All data stays on device"); IRow("API keys", "Stored locally, never uploaded")
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
        St(1, if (s.setupProviderDone) "Provider configured" else "Add an API key or choose Local Demo", s.setupProviderDone)
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
        if (profiles.isEmpty()) { Text("Create an AI Profile below to set a default.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    Card2(Icons.Default.Psychology, "AI Profiles", "Each profile bundles a provider, model, and API key.") {
        if (state.profiles.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp)); Text("No profiles yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Tap \"Add AI Profile\" to set up your first provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.profiles.forEach { p ->
                    val entry = ProviderCatalog.resolveForProfile(p)
                    val hasKey = when {
                        p.providerType == ProviderType.FAKE -> true
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

@Composable
private fun PCard(
    p: ProviderProfile, entry: CatalogEntry, hasKey: Boolean,
    onEdit: (ProviderProfile) -> Unit, onDel: (String) -> Unit, onSetDef: (String) -> Unit,
    onSetFb: (String) -> Unit, onToggle: (ProviderProfile) -> Unit
) {
    var showDel by remember { mutableStateOf(false) }
    Card(elevation = CardDefaults.cardElevation(defaultElevation = if (p.isDefault) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = when {
            !p.isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            p.isDefault -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        })) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            color = if (!p.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
                        if (p.isDefault) { Spacer(Modifier.width(6.dp)); Chip("Default", MaterialTheme.colorScheme.primary) }
                        if (p.isFallback) { Spacer(Modifier.width(6.dp)); Chip("Fallback", MaterialTheme.colorScheme.tertiary) }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("${entry.displayName} / ${p.modelId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = p.isEnabled, onCheckedChange = { onToggle(p) })
            }
            if (!hasKey && p.providerType != ProviderType.FAKE) {
                Spacer(Modifier.height(4.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp)); Text("API key missing — edit to add one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!p.isDefault) TextButton(onClick = { onSetDef(p.id) }) { Text("Set Default") }
                if (!p.isFallback) TextButton(onClick = { onSetFb(p.id) }) { Text("Set Fallback") }
                TextButton(onClick = { onEdit(p) }) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit") }
                TextButton(onClick = { showDel = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (showDel) AlertDialog(onDismissRequest = { showDel = false }, title = { Text("Delete Profile") },
        text = { Text("Delete \"${p.name}\"?") },
        confirmButton = { TextButton(onClick = { onDel(p.id); showDel = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showDel = false }) { Text("Cancel") } })
}

@Composable private fun Chip(label: String, color: androidx.compose.ui.graphics.Color) {
    AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.15f)), modifier = Modifier.height(24.dp))
}

// ══════════════════ Service API Keys ══════════════════

@Composable
private fun ServiceKeysSection(
    state: VaultUiState, expanded: Set<String>, onToggle: (String) -> Unit,
    onSave: (String, String) -> Unit, onRemove: (String) -> Unit,
    onAddCustom: (String, String) -> Unit, onRemoveCustom: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }; var cn by remember { mutableStateOf("") }; var cv by remember { mutableStateOf("") }
    Card2(Icons.Default.CloudQueue, "Service API Keys", "Keys for external services (weather, routes). Used by workflow actions.") {
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

// ══════════════════ Profile Editor ══════════════════

@Composable
private fun ProfileEditor(state: VaultUiState, vm: VaultViewModel, modifier: Modifier = Modifier) {
    val entry = state.editorCatalogEntry
    val isNative = entry.category == CatalogCategory.NATIVE
    val isCustom = entry.category == CatalogCategory.CUSTOM
    val isFake = entry.runtimeType == ProviderType.FAKE
    val needsKey = !isFake
    val needsBaseUrl = !isNative
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
        val fakeEntry = catalog.find { it.runtimeType == ProviderType.FAKE }
        val customEntry = catalog.find { it.category == CatalogCategory.CUSTOM }

        // Native providers
        nativeEntries.forEach { e -> ProviderRow(e, entry, vm::selectCatalogEntry) }

        // Divider + presets
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Text("More Providers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        presetEntries.forEach { e -> ProviderRow(e, entry, vm::selectCatalogEntry) }

        // Divider + Local Demo + Custom
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        if (fakeEntry != null) ProviderRow(fakeEntry, entry, vm::selectCatalogEntry)
        if (customEntry != null) ProviderRow(customEntry, entry, vm::selectCatalogEntry)

        // ── Custom Provider Name ──
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
        if (e.category == CatalogCategory.NATIVE && e.runtimeType != ProviderType.FAKE) {
            Text("Native", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

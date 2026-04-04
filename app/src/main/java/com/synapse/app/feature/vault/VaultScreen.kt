package com.synapse.app.feature.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synapse.app.domain.models.ProviderModels
import com.synapse.app.domain.models.ProviderProfile
import com.synapse.app.domain.models.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        if (state.isProfileEditorOpen) {
            ProfileEditor(
                state = state,
                onNameChange = viewModel::updateProfileName,
                onProviderChange = viewModel::updateProfileProvider,
                onModelChange = viewModel::updateProfileModel,
                onSave = viewModel::saveProfile,
                onCancel = viewModel::closeProfileEditor,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // ── Section 1: Provider Profiles ──
                item { SectionTitle("AI Provider Profiles") }
                item {
                    Text(
                        "Configure AI provider and model combinations for your workflows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.profiles.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No profiles yet. The app uses the Local Demo provider by default.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(state.profiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            onEdit = { viewModel.openEditProfile(profile) },
                            onDelete = { viewModel.deleteProfile(profile.id) },
                            onSetDefault = { viewModel.setDefaultProfile(profile.id) }
                        )
                    }
                }

                item {
                    OutlinedButton(onClick = viewModel::openNewProfile, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Profile")
                    }
                }

                // ── Section 2: Provider API Keys ──
                item { Spacer(Modifier.height(8.dp)); SectionTitle("Provider API Keys") }
                item {
                    Text(
                        "API keys are shared across all profiles using the same provider.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ProviderType.entries.filter { it != ProviderType.FAKE }.forEach { provider ->
                    item(key = "key_${provider.name}") {
                        ApiKeyCard(
                            label = provider.displayName,
                            isConfigured = state.providerKeyStatus[provider] == true,
                            onSave = { key -> viewModel.saveProviderKey(provider, key) },
                            onRemove = { viewModel.removeProviderKey(provider) }
                        )
                    }
                }

                // ── Section 3: Service API Keys ──
                item { Spacer(Modifier.height(8.dp)); SectionTitle("Service API Keys") }
                item {
                    Text(
                        "External service keys for workflow actions like weather and route data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                VaultViewModel.SERVICE_KEYS.forEach { info ->
                    item(key = "svc_${info.id}") {
                        ApiKeyCard(
                            label = info.displayName,
                            subtitle = info.description,
                            isConfigured = state.serviceKeyStatus[info.id] == true,
                            onSave = { key -> viewModel.saveServiceKey(info.id, key) },
                            onRemove = { viewModel.removeServiceKey(info.id) }
                        )
                    }
                }

                // ── Section 4: Legacy Provider ──
                item { Spacer(Modifier.height(8.dp)); SectionTitle("Active Provider (Legacy)") }
                item {
                    Text(
                        "Fallback provider when no profile is set. Used by quick-access screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            ProviderType.entries.forEach { provider ->
                                val isCloud = provider != ProviderType.FAKE
                                val hasKey = state.providerKeyStatus[provider] == true
                                val canSelect = !isCloud || hasKey
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    RadioButton(
                                        selected = state.activeProvider == provider,
                                        onClick = { if (canSelect) viewModel.setActiveProvider(provider) },
                                        enabled = canSelect
                                    )
                                    Text(
                                        provider.displayName,
                                        color = if (canSelect) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    if (isCloud && !hasKey) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("(needs key)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ProfileCard(
    profile: ProviderProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = if (profile.isDefault) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isDefault) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (profile.isDefault) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(onClick = {}, label = { Text("Default", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(24.dp))
                        }
                    }
                    Text(
                        "${profile.providerType.displayName} / ${profile.modelId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!profile.isDefault) {
                    TextButton(onClick = onSetDefault) { Text("Set Default") }
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    label: String,
    subtitle: String = "",
    isConfigured: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isConfigured) {
                    AssistChip(
                        onClick = {}, label = { Text("Configured", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.height(26.dp)
                    )
                } else {
                    AssistChip(
                        onClick = {}, label = { Text("Not Set", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Warning, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            if (isConfigured) {
                TextButton(onClick = onRemove) {
                    Text("Remove Key", color = MaterialTheme.colorScheme.error)
                }
            } else if (showInput) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showInput = false; keyInput = "" }) { Text("Cancel") }
                    Button(onClick = {
                        if (keyInput.isNotBlank()) { onSave(keyInput); keyInput = ""; showInput = false }
                    }) { Text("Save") }
                }
            } else {
                TextButton(onClick = { showInput = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add Key")
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    state: VaultUiState,
    onNameChange: (String) -> Unit,
    onProviderChange: (ProviderType) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (state.editingProfile != null) "Edit Profile" else "New Profile",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
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

        Text("Provider", style = MaterialTheme.typography.labelLarge)
        ProviderType.entries.filter { it != ProviderType.FAKE }.forEach { provider ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.profileEditorProvider == provider,
                    onClick = { onProviderChange(provider) }
                )
                Text(provider.displayName)
            }
        }

        Text("Model", style = MaterialTheme.typography.labelLarge)
        val models = ProviderModels.modelsFor(state.profileEditorProvider)
        models.forEach { (modelId, modelName) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.profileEditorModel == modelId,
                    onClick = { onModelChange(modelId) }
                )
                Text(modelName, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.profileEditorError != null) {
            Text(state.profileEditorError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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

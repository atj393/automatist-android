package com.synapse.app.feature.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synapse.app.domain.models.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val settings by viewModel.appSettings.collectAsState()
    val keysLoaded by viewModel.apiKeysLoaded.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault & Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val activeProvider = settings?.activeProvider ?: ProviderType.FAKE

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How Synapse Works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Synapse acts as a manual-final-action tool. Content is only processed by the provider you explicitly select below. " +
                        "By default, we run in Local Fake Demo mode (which simulates outputs privately). To use real AI workflows, " +
                        "enter your API keys below. Your keys never leave this device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text("Active Provider", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderType.entries.forEach { provider ->
                    val isCloud = provider != ProviderType.FAKE
                    val hasKey = keysLoaded[provider] == true
                    val canSelect = !isCloud || hasKey
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        checked = activeProvider == provider,
                        onCheckedChange = { if (canSelect) viewModel.setActiveProvider(provider) },
                        color = if (activeProvider == provider) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = activeProvider == provider,
                                onClick = { if (canSelect) viewModel.setActiveProvider(provider) },
                                enabled = canSelect
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                if (isCloud && !hasKey) {
                                    Text("Requires API Key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (activeProvider == provider) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider()

            Text("Secure Key Storage", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

            ProviderType.entries.filter { it != ProviderType.FAKE }.forEach { provider ->
                ApiKeyEntrySection(
                    provider = provider,
                    isSet = keysLoaded[provider] == true,
                    onSave = { key -> viewModel.saveApiKey(provider, key) },
                    onRemove = { viewModel.removeApiKey(provider) }
                )
            }
        }
    }
}

@Composable
fun ApiKeyEntrySection(
    provider: ProviderType,
    isSet: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(provider.displayName, fontWeight = FontWeight.Bold)
                if (isSet) {
                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text("Configured", color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                } else {
                    Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text("Not Set", color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isSet) {
                Button(
                    onClick = onRemove,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove Key")
                }
            } else {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            onSave(keyInput)
                            keyInput = ""
                        }
                    },
                    enabled = keyInput.isNotBlank()
                ) {
                    Text("Save Securely")
                }
            }
        }
    }
}

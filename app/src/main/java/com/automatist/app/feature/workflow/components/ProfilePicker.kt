package com.automatist.app.feature.workflow.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatist.app.domain.models.ProviderProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePicker(
    label: String,
    hint: String,
    selectedProfileId: String,
    profiles: List<ProviderProfile>,
    onProfileSelected: (String) -> Unit,
    inheritLabel: String = "Use default",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val enabledProfiles = profiles.filter { it.isEnabled }
    val selectedProfile = profiles.find { it.id == selectedProfileId }
    val displayText = selectedProfile?.displayLabel ?: inheritLabel

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                leadingIcon = {
                    Icon(
                        Icons.Default.SmartToy,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selectedProfile != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Inherit option
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(inheritLabel)
                        }
                    },
                    onClick = {
                        onProfileSelected("")
                        expanded = false
                    },
                    leadingIcon = {
                        if (selectedProfileId.isBlank()) {
                            Icon(
                                Icons.Default.Check, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                HorizontalDivider()

                // Profile options (only enabled profiles)
                enabledProfiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${profile.providerType.displayName} / ${profile.modelId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onProfileSelected(profile.id)
                            expanded = false
                        },
                        leadingIcon = {
                            if (selectedProfileId == profile.id) {
                                Icon(
                                    Icons.Default.Check, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        trailingIcon = {
                            if (profile.isDefault) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Default", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(22.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

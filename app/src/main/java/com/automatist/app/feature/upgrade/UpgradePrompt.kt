package com.automatist.app.feature.upgrade

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Modal dialog shown when a free user tries to activate a second workflow.
 * Offers upgrade or switching the active workflow.
 *
 * @param activeWorkflowName The name of the currently active workflow (for context).
 * @param onUpgrade Navigate to the upgrade/purchase screen.
 * @param onSwitchActive Deactivate the current active workflow and activate the new one.
 * @param onDismiss Close the dialog without action.
 */
@Composable
fun ActivationLimitDialog(
    activeWorkflowName: String,
    onUpgrade: () -> Unit,
    onSwitchActive: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                "One Active Workflow at a Time",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The free plan allows one active workflow at a time. " +
                        "\"$activeWorkflowName\" is currently active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "You can switch your active workflow, or upgrade to Pro for unlimited active workflows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpgrade) {
                Text("Upgrade to Pro")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                OutlinedButton(onClick = onSwitchActive) {
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Switch Active")
                }
            }
        }
    )
}

/**
 * Simpler upgrade prompt for contexts where switching is not applicable
 * (e.g., duplicate workflow).
 */
@Composable
fun UpgradePromptDialog(
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                "Unlock Unlimited Active Workflows",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The free plan allows one active workflow at a time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Upgrade to Pro to run unlimited workflows simultaneously.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpgrade) {
                Text("Upgrade to Pro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}

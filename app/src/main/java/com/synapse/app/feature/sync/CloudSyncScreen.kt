package com.synapse.app.feature.sync

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.drive.DriveScopes
import com.synapse.app.data.sync.CloudSyncManager
import com.synapse.app.domain.sync.CloudSyncRepository
import com.synapse.app.domain.sync.SyncResult
import com.synapse.app.domain.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ──

data class CloudSyncUiState(
    val isWorking: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val syncRepository: CloudSyncRepository,
    private val syncManager: CloudSyncManager
) : ViewModel() {

    val syncStatus = syncRepository.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus())

    private val _uiState = MutableStateFlow(CloudSyncUiState())
    val uiState = _uiState.asStateFlow()

    fun onAccountConnected(email: String) {
        viewModelScope.launch {
            syncRepository.setSyncStatus(
                syncRepository.currentStatus().copy(isConnected = true, accountEmail = email)
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            syncRepository.clearSyncStatus()
        }
    }

    fun backupNow() {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            val result = syncManager.backupWorkflows()
            val message = when (result) {
                is SyncResult.Success -> result.message
                is SyncResult.Error -> result.message
            }
            _uiState.update { it.copy(isWorking = false, snackbarMessage = message) }
        }
    }

    fun restoreNow() {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            val result = syncManager.restoreWorkflows()
            val message = when (result) {
                is SyncResult.Success -> result.message
                is SyncResult.Error -> result.message
            }
            _uiState.update { it.copy(isWorking = false, snackbarMessage = message) }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

// ── Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    onBack: () -> Unit,
    viewModel: CloudSyncViewModel = hiltViewModel()
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
            if (account?.email != null) {
                viewModel.onAccountConnected(account.email!!)
            }
        }
    }

    // Snackbar messages
    val snackbarMessage = uiState.snackbarMessage
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Icon(
                Icons.Default.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )

            Text(
                "Cloud Backup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Manually back up and restore your workflows using your Google account. Workflow definitions only — API keys never leave your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ── Account Status ──
            if (syncStatus.isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountCircle, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Connected",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                syncStatus.accountEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { viewModel.disconnect() }) {
                            Text("Disconnect")
                        }
                    }
                }

                // Last backup info
                if (syncStatus.lastBackupAt.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Last backup",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatSyncTime(syncStatus.lastBackupAt),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${syncStatus.lastBackupWorkflowCount} workflow(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Backup / Restore Buttons ──
                Button(
                    onClick = { viewModel.backupNow() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isWorking
                ) {
                    if (uiState.isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Backup Workflows Now")
                }

                OutlinedButton(
                    onClick = { viewModel.restoreNow() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isWorking
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restore Workflows Now")
                }
            } else {
                // ── Not Connected ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CloudOff, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Not connected",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Connect a Google account to back up your workflows to the cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA))
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        signInLauncher.launch(client.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect Google Account")
                }
            }

            Spacer(Modifier.weight(1f))

            // Safety note
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield, null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "API keys are never uploaded. Only workflow definitions are backed up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

private fun formatSyncTime(isoString: String): String {
    return try {
        val instant = java.time.Instant.parse(isoString)
        val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm")
        local.format(formatter)
    } catch (_: Exception) {
        isoString
    }
}

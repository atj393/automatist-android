package com.automatist.app.feature.upgrade

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.BuildConfig
import com.automatist.app.data.billing.BillingManager
import com.automatist.app.data.billing.BillingStatus
import com.automatist.app.data.billing.PurchaseState
import com.automatist.app.domain.access.PlanState
import com.automatist.app.domain.access.ProductAccessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val accessRepository: ProductAccessRepository,
    val billingManager: BillingManager
) : ViewModel() {

    val planState = accessRepository.planState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanState())

    val purchaseState = billingManager.purchaseState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PurchaseState.Idle)

    val productDetails = billingManager.productDetails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val billingStatus = billingManager.billingStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillingStatus.Initializing)

    val isRestoring = billingManager.isRestoring
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _userRestoreInProgress = MutableStateFlow(false)
    val userRestoreInProgress = _userRestoreInProgress.asStateFlow()

    init {
        billingManager.queryProductDetails()
        billingManager.queryOwnedPurchases()
    }

    fun launchPurchase(activity: Activity) {
        billingManager.launchPurchaseFlow(activity)
    }

    fun restorePurchases() {
        _userRestoreInProgress.value = true
        billingManager.queryOwnedPurchases()
    }

    fun retryConnection() {
        billingManager.queryProductDetails()
    }

    fun clearPurchaseState() {
        billingManager.clearPurchaseState()
    }

    fun clearUserRestore() {
        _userRestoreInProgress.value = false
    }

    /**
     * Debug-only toggle for testing Pro entitlement locally.
     * Guarded by BuildConfig.DEBUG — compiled out of release builds by R8.
     */
    fun debugSetPro(unlocked: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            accessRepository.setProUnlocked(unlocked)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    onBack: () -> Unit,
    viewModel: UpgradeViewModel = hiltViewModel()
) {
    val planState by viewModel.planState.collectAsState()
    val purchaseState by viewModel.purchaseState.collectAsState()
    val productDetails by viewModel.productDetails.collectAsState()
    val billingStatus by viewModel.billingStatus.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val userRestoreInProgress by viewModel.userRestoreInProgress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // React to purchase results
    LaunchedEffect(purchaseState) {
        when (val state = purchaseState) {
            is PurchaseState.Success -> {
                snackbarHostState.showSnackbar("Pro unlocked! Enjoy unlimited workflows.")
                viewModel.clearPurchaseState()
            }
            is PurchaseState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearPurchaseState()
            }
            is PurchaseState.Pending -> {
                snackbarHostState.showSnackbar("Purchase pending. Pro will unlock once approved.")
            }
            PurchaseState.Idle -> {}
        }
    }

    // When a user-initiated restore finishes without finding Pro, show a neutral message
    LaunchedEffect(isRestoring) {
        if (!isRestoring && userRestoreInProgress && !planState.isProUnlocked && purchaseState !is PurchaseState.Success) {
            snackbarHostState.showSnackbar("No previous purchase found for this account.")
            viewModel.clearUserRestore()
        }
    }

    val priceText = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pro Upgrade") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )

            Text(
                "Automatist Pro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (planState.isProUnlocked) "You have Pro access"
                else "Unlock unlimited workflows",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Feature list
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProFeatureRow("Unlimited workflow slots")
                    ProFeatureRow("All action types")
                    ProFeatureRow("Full scheduling")
                    ProFeatureRow("Export & import workflows")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Current plan badge
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        "Current plan: ${planState.plan.displayName}",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            Spacer(Modifier.weight(1f))

            if (planState.isProUnlocked) {
                // ── Already Pro ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Pro is active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // ── Billing status indicator ──
                when (val status = billingStatus) {
                    is BillingStatus.Initializing -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Connecting to Google Play...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is BillingStatus.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Could not load purchase info",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    status.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.retryConnection() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    is BillingStatus.Ready -> {
                        // Show nothing extra — the button below handles it
                    }
                }

                // ── Purchase CTA ──
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            viewModel.launchPurchase(activity)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = billingStatus is BillingStatus.Ready && productDetails != null
                ) {
                    Text(
                        when {
                            billingStatus is BillingStatus.Initializing -> "Loading..."
                            priceText != null -> "Upgrade to Pro \u2014 $priceText"
                            else -> "Upgrade to Pro"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (priceText != null) {
                    Text(
                        "One-time purchase. No subscription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ── Restore ──
                TextButton(
                    onClick = { viewModel.restorePurchases() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRestoring
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Checking...")
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore Purchases")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

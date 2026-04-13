package com.synapse.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sealed state for the purchase flow — observed by the UpgradeViewModel.
 */
sealed interface PurchaseState {
    data object Idle : PurchaseState
    data object Pending : PurchaseState
    data class Success(val purchaseToken: String) : PurchaseState
    data class Error(val message: String) : PurchaseState
}

/**
 * Manages Google Play Billing connection, product queries, purchase flow,
 * acknowledgement, and ownership checks for the one-time synapse_pro product.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_ID = "synapse_pro"
    }

    private var billingClient: BillingClient = buildClient()

    private val _proOwned = MutableStateFlow(false)
    val proOwned: StateFlow<Boolean> = _proOwned.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private fun buildClient(): BillingClient =
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

    // ═══════════════════════════════════════════════════════════
    //  CONNECTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Ensures billing client is connected, then runs [block].
     * Reconnects if the client was disconnected.
     */
    private fun ensureConnected(block: () -> Unit) {
        if (billingClient.isReady) {
            block()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    block()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    // ═══════════════════════════════════════════════════════════
    //  PRODUCT DETAILS QUERY
    // ═══════════════════════════════════════════════════════════

    fun queryProductDetails() {
        ensureConnected {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()

            billingClient.queryProductDetailsAsync(params) { result, detailsList ->
                if (result.responseCode == BillingResponseCode.OK) {
                    _productDetails.value = detailsList.firstOrNull()
                    Log.d(TAG, "Product details loaded: ${detailsList.size} product(s)")
                } else {
                    Log.w(TAG, "Product query failed: ${result.debugMessage}")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LAUNCH PURCHASE FLOW
    // ═══════════════════════════════════════════════════════════

    fun launchPurchaseFlow(activity: Activity): Boolean {
        val details = _productDetails.value
        if (details == null) {
            _purchaseState.value = PurchaseState.Error("Product details not available. Try again.")
            return false
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingResponseCode.OK) {
            _purchaseState.value = PurchaseState.Error("Could not start purchase flow.")
            return false
        }
        return true
    }

    // ═══════════════════════════════════════════════════════════
    //  PURCHASE CALLBACK
    // ═══════════════════════════════════════════════════════════

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                purchases?.forEach { purchase -> handlePurchase(purchase) }
            }
            BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Purchase canceled by user")
                _purchaseState.value = PurchaseState.Idle
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Item already owned — refreshing")
                queryOwnedPurchases()
            }
            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                _purchaseState.value = PurchaseState.Error("Purchase failed. Please try again.")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products) return

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                _proOwned.value = true
                _purchaseState.value = PurchaseState.Success(purchase.purchaseToken)
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                _purchaseState.value = PurchaseState.Pending
                Log.d(TAG, "Purchase pending approval")
            }
            else -> {
                Log.d(TAG, "Unhandled purchase state: ${purchase.purchaseState}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ACKNOWLEDGEMENT
    // ═══════════════════════════════════════════════════════════

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            } else {
                Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  QUERY OWNED PURCHASES (restore / refresh)
    // ═══════════════════════════════════════════════════════════

    fun queryOwnedPurchases() {
        ensureConnected {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingResponseCode.OK) {
                    val owned = purchases.any { purchase ->
                        PRODUCT_ID in purchase.products &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    _proOwned.value = owned
                    Log.d(TAG, "Ownership query: proOwned=$owned (${purchases.size} purchase(s))")

                    // Acknowledge any unacknowledged purchases
                    purchases.filter {
                        PRODUCT_ID in it.products &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            !it.isAcknowledged
                    }.forEach { acknowledgePurchase(it) }
                } else {
                    Log.w(TAG, "Ownership query failed: ${result.debugMessage}")
                }
            }
        }
    }

    fun clearPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }
}

package com.automatist.app.data.billing

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
 * Loading/error state for the billing connection + product query.
 */
sealed interface BillingStatus {
    data object Initializing : BillingStatus
    data object Ready : BillingStatus
    data class Error(val message: String) : BillingStatus
}

/**
 * Manages Google Play Billing connection, product queries, purchase flow,
 * acknowledgement, and ownership checks for the one-time automatist_pro product.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "AutomatistBilling"
        const val PRODUCT_ID = "automatist_pro"
    }

    private var billingClient: BillingClient = buildClient()

    private val _proOwned = MutableStateFlow(false)
    val proOwned: StateFlow<Boolean> = _proOwned.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private val _billingStatus = MutableStateFlow<BillingStatus>(BillingStatus.Initializing)
    val billingStatus: StateFlow<BillingStatus> = _billingStatus.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private fun buildClient(): BillingClient =
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

    // ═══════════════════════════════════════════════════════════
    //  CONNECTION
    // ═══════════════════════════════════════════════════════════

    private fun ensureConnected(onFailure: ((String) -> Unit)? = null, block: () -> Unit) {
        if (billingClient.isReady) {
            block()
            return
        }
        Log.d(TAG, "Starting billing connection...")
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val code = result.responseCode
                Log.d(TAG, "Connection result: code=$code, message=${result.debugMessage}")
                if (code == BillingResponseCode.OK) {
                    block()
                } else {
                    val msg = when (code) {
                        BillingResponseCode.BILLING_UNAVAILABLE ->
                            "Google Play Billing is not available on this device."
                        BillingResponseCode.DEVELOPER_ERROR ->
                            "Billing configuration error. Check Play Console setup."
                        BillingResponseCode.FEATURE_NOT_SUPPORTED ->
                            "In-app purchases are not supported on this device."
                        else ->
                            "Could not connect to Google Play (code $code)."
                    }
                    Log.e(TAG, "Connection failed: $msg")
                    onFailure?.invoke(msg)
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
        _billingStatus.value = BillingStatus.Initializing
        Log.d(TAG, "Querying product details for '$PRODUCT_ID'...")

        ensureConnected(
            onFailure = { msg ->
                _billingStatus.value = BillingStatus.Error(msg)
            }
        ) {
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
                val code = result.responseCode
                Log.d(TAG, "Product query result: code=$code, count=${detailsList.size}")

                if (code == BillingResponseCode.OK) {
                    val details = detailsList.firstOrNull()
                    _productDetails.value = details
                    if (details != null) {
                        val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "?"
                        Log.d(TAG, "Product found: ${details.productId}, price=$price")
                        _billingStatus.value = BillingStatus.Ready
                    } else {
                        val msg = "Product '$PRODUCT_ID' not found in Play Console. " +
                            "Verify the product ID exists and is active."
                        Log.e(TAG, msg)
                        _billingStatus.value = BillingStatus.Error(msg)
                    }
                } else {
                    val msg = "Product query failed (code $code): ${result.debugMessage}"
                    Log.e(TAG, msg)
                    _billingStatus.value = BillingStatus.Error(msg)
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
            _purchaseState.value = PurchaseState.Error("Product details not loaded. Try reopening this screen.")
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
        Log.d(TAG, "Launch purchase flow: code=${result.responseCode}")
        if (result.responseCode != BillingResponseCode.OK) {
            _purchaseState.value = PurchaseState.Error("Could not start purchase (code ${result.responseCode}).")
            return false
        }
        return true
    }

    // ═══════════════════════════════════════════════════════════
    //  PURCHASE CALLBACK
    // ═══════════════════════════════════════════════════════════

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d(TAG, "onPurchasesUpdated: code=${result.responseCode}, count=${purchases?.size ?: 0}")
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
                Log.w(TAG, "Purchase failed: code=${result.responseCode}, ${result.debugMessage}")
                _purchaseState.value = PurchaseState.Error("Purchase failed (code ${result.responseCode}).")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        Log.d(TAG, "handlePurchase: products=${purchase.products}, state=${purchase.purchaseState}, ack=${purchase.isAcknowledged}")
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
            Log.d(TAG, "Acknowledge result: code=${result.responseCode}")
            if (result.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  QUERY OWNED PURCHASES (restore / refresh)
    // ═══════════════════════════════════════════════════════════

    fun queryOwnedPurchases() {
        _isRestoring.value = true
        Log.d(TAG, "Querying owned purchases...")

        ensureConnected(
            onFailure = { msg ->
                _isRestoring.value = false
                _purchaseState.value = PurchaseState.Error("Restore failed: $msg")
            }
        ) {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(params) { result, purchases ->
                _isRestoring.value = false
                val code = result.responseCode
                Log.d(TAG, "Owned purchases result: code=$code, count=${purchases.size}")

                if (code == BillingResponseCode.OK) {
                    val ownedPurchase = purchases.find { purchase ->
                        PRODUCT_ID in purchase.products &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    val owned = ownedPurchase != null
                    _proOwned.value = owned

                    if (owned) {
                        Log.d(TAG, "Pro ownership confirmed")
                        _purchaseState.value = PurchaseState.Success(ownedPurchase!!.purchaseToken)
                        if (!ownedPurchase.isAcknowledged) {
                            acknowledgePurchase(ownedPurchase)
                        }
                    } else {
                        Log.d(TAG, "No Pro purchase found (${purchases.size} total purchase(s) checked)")
                        _purchaseState.value = PurchaseState.Idle
                    }
                } else {
                    val msg = "Could not check purchases (code $code)."
                    Log.e(TAG, "Restore failed: $msg ${result.debugMessage}")
                    _purchaseState.value = PurchaseState.Error(msg)
                }
            }
        }
    }

    fun clearPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }
}

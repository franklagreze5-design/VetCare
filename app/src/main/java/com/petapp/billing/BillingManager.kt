package com.petapp.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BillingManager(
    private val context: Context,
    private val onPurchaseValidation: suspend (Purchase) -> ValidationResult = { ValidationResult.Invalid("No validator") }
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _availableProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val availableProducts: StateFlow<List<ProductDetails>> = _availableProducts.asStateFlow()

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>()
    val purchaseEvents = _purchaseEvents.asSharedFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val code: Int, val message: String) : ConnectionState()
    }

    sealed class PurchaseEvent {
        data class PurchaseCompleted(val purchase: Purchase) : PurchaseEvent()
        data class PurchasePending(val purchase: Purchase) : PurchaseEvent()
        data class PurchaseCancelled(val productId: String?) : PurchaseEvent()
        data class PurchaseError(val code: Int, val message: String) : PurchaseEvent()
        data class ValidationSuccess(val plan: SubscriptionPlan) : PurchaseEvent()
        data class ValidationFailed(val reason: String) : PurchaseEvent()
    }

    fun startConnection() {
        if (_connectionState.value is ConnectionState.Connected) return
        _connectionState.value = ConnectionState.Connecting

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    _connectionState.value = ConnectionState.Connected
                    scope.launch { queryAvailableProducts() }
                } else {
                    _connectionState.value = ConnectionState.Error(billingResult.responseCode, billingResult.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = ConnectionState.Disconnected
                startConnection()
            }
        })
    }

    private suspend fun queryAvailableProducts() {
        val productList = SubscriptionPlan.allProductIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingResponseCode.OK) {
            _availableProducts.value = result.productDetailsList ?: emptyList()
        }
    }

    suspend fun launchPurchaseFlow(activity: Activity, productId: String): BillingResult {
        val productDetails = _availableProducts.value.find { it.productId == productId }
            ?: return BillingResult.newBuilder()
                .setResponseCode(BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage("Producto no encontrado: $productId")
                .build()

        val offerToken = getBestOfferToken(productDetails)
            ?: return BillingResult.newBuilder()
                .setResponseCode(BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage("No hay ofertas disponibles")
                .build()

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        return billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun getBestOfferToken(productDetails: ProductDetails): String? {
        val offers = productDetails.subscriptionOfferDetails ?: return null
        val trialOffer = offers.find { offer ->
            offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        }
        return trialOffer?.offerToken ?: offers.firstOrNull()?.offerToken
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            when (billingResult.responseCode) {
                BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
                BillingResponseCode.USER_CANCELED -> _purchaseEvents.emit(PurchaseEvent.PurchaseCancelled(null))
                else -> _purchaseEvents.emit(PurchaseEvent.PurchaseError(billingResult.responseCode, billingResult.debugMessage))
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                _purchaseEvents.emit(PurchaseEvent.PurchaseCompleted(purchase))
                when (val result = onPurchaseValidation(purchase)) {
                    is ValidationResult.Valid -> {
                        acknowledgePurchase(purchase)
                        _purchaseEvents.emit(PurchaseEvent.ValidationSuccess(result.plan))
                    }
                    is ValidationResult.Invalid -> _purchaseEvents.emit(PurchaseEvent.ValidationFailed(result.reason))
                    is ValidationResult.Error -> _purchaseEvents.emit(PurchaseEvent.ValidationFailed(result.exception.message ?: "Error"))
                }
            }
            Purchase.PurchaseState.PENDING -> _purchaseEvents.emit(PurchaseEvent.PurchasePending(purchase))
            else -> {}
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        suspendCancellableCoroutine { continuation ->
            billingClient.acknowledgePurchase(params) { continuation.resume(it) }
        }
    }

    suspend fun queryExistingPurchases(): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        val result = billingClient.queryPurchasesAsync(params)
        return if (result.billingResult.responseCode == BillingResponseCode.OK) result.purchasesList else emptyList()
    }

    suspend fun restorePurchases(): List<Purchase> {
        val purchases = queryExistingPurchases()
        purchases.forEach { if (it.purchaseState == Purchase.PurchaseState.PURCHASED) handlePurchase(it) }
        return purchases
    }

    fun endConnection() {
        billingClient.endConnection()
        _connectionState.value = ConnectionState.Disconnected
    }
}

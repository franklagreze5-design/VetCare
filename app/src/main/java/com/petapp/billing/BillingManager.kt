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

/**
 * BillingManager - Gestiona la conexión con Google Play Billing Library v7
 * 
 * Responsabilidades:
 * - Conectar y mantener conexión con Google Play
 * - Consultar productos disponibles
 * - Lanzar flujo de compra
 * - Procesar compras completadas
 * - Emitir purchaseToken para validación backend
 */
class BillingManager(
    private val context: Context,
    private val onPurchaseValidation: suspend (Purchase) -> ValidationResult
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
    
    /**
     * Estados de conexión con Google Play Billing
     */
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val code: Int, val message: String) : ConnectionState()
    }
    
    /**
     * Eventos de compra emitidos durante el flujo
     */
    sealed class PurchaseEvent {
        data class PurchaseCompleted(val purchase: Purchase) : PurchaseEvent()
        data class PurchasePending(val purchase: Purchase) : PurchaseEvent()
        data class PurchaseCancelled(val productId: String?) : PurchaseEvent()
        data class PurchaseError(val code: Int, val message: String) : PurchaseEvent()
        data class ValidationSuccess(val plan: SubscriptionPlan) : PurchaseEvent()
        data class ValidationFailed(val reason: String) : PurchaseEvent()
    }
    
    /**
     * Inicia conexión con Google Play Billing
     * Debe llamarse al iniciar la app
     */
    fun startConnection() {
        if (_connectionState.value is ConnectionState.Connected) return
        
        _connectionState.value = ConnectionState.Connecting
        
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    _connectionState.value = ConnectionState.Connected
                    scope.launch { queryAvailableProducts() }
                } else {
                    _connectionState.value = ConnectionState.Error(
                        billingResult.responseCode,
                        billingResult.debugMessage
                    )
                }
            }
            
            override fun onBillingServiceDisconnected() {
                _connectionState.value = ConnectionState.Disconnected
                // Intentar reconectar
                startConnection()
            }
        })
    }
    
    /**
     * Consulta los productos de suscripción disponibles
     */
    private suspend fun queryAvailableProducts() {
        val productList = SubscriptionPlan.allProductIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        val result = billingClient.queryProductDetails(params)
        
        if (result.billingResult.responseCode == BillingResponseCode.OK) {
            _availableProducts.value = result.productDetailsList ?: emptyList()
        }
    }
    
    /**
     * Lanza el flujo de compra para un producto específico
     * 
     * @param activity Activity actual para mostrar el diálogo de compra
     * @param productId ID del producto (premium_monthly, premium_yearly, family_plan)
     * @param offerToken Token de la oferta (obtenido de ProductDetails)
     */
    suspend fun launchPurchaseFlow(
        activity: Activity,
        productId: String,
        offerToken: String
    ): BillingResult {
        val productDetails = _availableProducts.value.find { it.productId == productId }
            ?: return BillingResult.newBuilder()
                .setResponseCode(BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage("Producto no encontrado: $productId")
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
    
    /**
     * Lanza compra con selección automática de la mejor oferta
     */
    suspend fun launchPurchaseFlow(
        activity: Activity,
        productId: String
    ): BillingResult {
        val productDetails = _availableProducts.value.find { it.productId == productId }
            ?: return BillingResult.newBuilder()
                .setResponseCode(BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage("Producto no encontrado: $productId")
                .build()
        
        // Obtener la mejor oferta (priorizar trial si disponible)
        val offerToken = getBestOfferToken(productDetails)
            ?: return BillingResult.newBuilder()
                .setResponseCode(BillingResponseCode.ITEM_UNAVAILABLE)
                .setDebugMessage("No hay ofertas disponibles para: $productId")
                .build()
        
        return launchPurchaseFlow(activity, productId, offerToken)
    }
    
    /**
     * Obtiene el mejor token de oferta para un producto
     * Prioriza ofertas con trial gratuito
     */
    private fun getBestOfferToken(productDetails: ProductDetails): String? {
        val subscriptionOfferDetails = productDetails.subscriptionOfferDetails
            ?: return null
        
        // Buscar oferta con trial gratuito primero
        val trialOffer = subscriptionOfferDetails.find { offer ->
            offer.pricingPhases.pricingPhaseList.any { phase ->
                phase.priceAmountMicros == 0L
            }
        }
        
        return trialOffer?.offerToken ?: subscriptionOfferDetails.firstOrNull()?.offerToken
    }
    
    /**
     * Callback de Google Play Billing cuando se actualiza una compra
     */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        scope.launch {
            when (billingResult.responseCode) {
                BillingResponseCode.OK -> {
                    purchases?.forEach { purchase ->
                        handlePurchase(purchase)
                    }
                }
                BillingResponseCode.USER_CANCELED -> {
                    _purchaseEvents.emit(PurchaseEvent.PurchaseCancelled(null))
                }
                else -> {
                    _purchaseEvents.emit(
                        PurchaseEvent.PurchaseError(
                            billingResult.responseCode,
                            billingResult.debugMessage
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Procesa una compra completada
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                _purchaseEvents.emit(PurchaseEvent.PurchaseCompleted(purchase))
                
                // Validar con backend
                val validationResult = onPurchaseValidation(purchase)
                
                when (validationResult) {
                    is ValidationResult.Valid -> {
                        // Acknowledge la compra después de validación exitosa
                        acknowledgePurchase(purchase)
                        _purchaseEvents.emit(PurchaseEvent.ValidationSuccess(validationResult.plan))
                    }
                    is ValidationResult.Invalid -> {
                        _purchaseEvents.emit(PurchaseEvent.ValidationFailed(validationResult.reason))
                    }
                    is ValidationResult.Error -> {
                        _purchaseEvents.emit(
                            PurchaseEvent.ValidationFailed(
                                validationResult.exception.message ?: "Error de validación"
                            )
                        )
                    }
                }
            }
            Purchase.PurchaseState.PENDING -> {
                _purchaseEvents.emit(PurchaseEvent.PurchasePending(purchase))
            }
            else -> {
                // Estado desconocido
            }
        }
    }
    
    /**
     * Confirma (acknowledge) una compra para evitar reembolso automático
     */
    private suspend fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        suspendCancellableCoroutine { continuation ->
            billingClient.acknowledgePurchase(params) { billingResult ->
                continuation.resume(billingResult)
            }
        }
    }
    
    /**
     * Consulta compras existentes para restaurar suscripciones
     */
    suspend fun queryExistingPurchases(): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)
            .build()
        
        val result = billingClient.queryPurchasesAsync(params)
        
        return if (result.billingResult.responseCode == BillingResponseCode.OK) {
            result.purchasesList
        } else {
            emptyList()
        }
    }
    
    /**
     * Restaura compras existentes (útil para reinstalaciones)
     */
    suspend fun restorePurchases(): List<Purchase> {
        val purchases = queryExistingPurchases()
        
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                handlePurchase(purchase)
            }
        }
        
        return purchases
    }
    
    /**
     * Obtiene información de precio formateada para un producto
     */
    fun getFormattedPrice(productId: String): PlanPricing? {
        val productDetails = _availableProducts.value.find { it.productId == productId }
            ?: return null
        
        val subscriptionOffer = productDetails.subscriptionOfferDetails?.firstOrNull()
            ?: return null
        
        val pricingPhase = subscriptionOffer.pricingPhases.pricingPhaseList
            .firstOrNull { it.priceAmountMicros > 0 }
            ?: return null
        
        val hasFreeTrial = subscriptionOffer.pricingPhases.pricingPhaseList
            .any { it.priceAmountMicros == 0L }
        
        val freeTrialDays = subscriptionOffer.pricingPhases.pricingPhaseList
            .firstOrNull { it.priceAmountMicros == 0L }
            ?.billingPeriod
            ?.let { parsePeriodToDays(it) } ?: 0
        
        val billingPeriod = if (pricingPhase.billingPeriod.contains("Y")) {
            BillingPeriod.YEARLY
        } else {
            BillingPeriod.MONTHLY
        }
        
        return PlanPricing(
            productId = productId,
            plan = SubscriptionPlan.fromProductId(productId),
            priceMonthly = pricingPhase.formattedPrice,
            priceYearly = null,
            currencyCode = pricingPhase.priceCurrencyCode,
            billingPeriod = billingPeriod,
            hasFreeTrial = hasFreeTrial,
            freeTrialDays = freeTrialDays
        )
    }
    
    /**
     * Parsea período ISO 8601 a días
     * P7D = 7 días, P1W = 7 días
     */
    private fun parsePeriodToDays(period: String): Int {
        return when {
            period.contains("D") -> {
                period.replace("P", "").replace("D", "").toIntOrNull() ?: 0
            }
            period.contains("W") -> {
                (period.replace("P", "").replace("W", "").toIntOrNull() ?: 0) * 7
            }
            else -> 0
        }
    }
    
    /**
     * Cierra la conexión con Billing Client
     * Llamar cuando ya no se necesite
     */
    fun endConnection() {
        billingClient.endConnection()
        _connectionState.value = ConnectionState.Disconnected
    }
}

/**
 * Resultado de validación importado desde SubscriptionState
 */
sealed class ValidationResult {
    data class Valid(
        val plan: SubscriptionPlan,
        val expiryTime: Long,
        val isInTrial: Boolean
    ) : ValidationResult()
    
    data class Invalid(val reason: String) : ValidationResult()
    
    data class Error(val exception: Exception) : ValidationResult()
}

package com.petapp.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.first

/**
 * Helper class que simplifica el flujo de compra
 * Abstrae la complejidad de BillingManager para uso en ViewModels
 */
class PurchaseHelper(
    private val billingManager: BillingManager
) {
    
    /**
     * Resultado de un intento de compra
     */
    sealed class PurchaseResult {
        data object Success : PurchaseResult()
        data object Cancelled : PurchaseResult()
        data class Error(val code: Int, val message: String) : PurchaseResult()
        data object NotConnected : PurchaseResult()
        data object ProductNotFound : PurchaseResult()
    }
    
    /**
     * Inicia la compra del plan Premium Mensual
     */
    suspend fun purchasePremiumMonthly(activity: Activity): PurchaseResult {
        return launchPurchase(activity, "premium_monthly")
    }
    
    /**
     * Inicia la compra del plan Premium Anual
     */
    suspend fun purchasePremiumYearly(activity: Activity): PurchaseResult {
        return launchPurchase(activity, "premium_yearly")
    }
    
    /**
     * Inicia la compra del plan Family
     */
    suspend fun purchaseFamilyPlan(activity: Activity): PurchaseResult {
        return launchPurchase(activity, "family_plan")
    }
    
    /**
     * Lanza el flujo de compra para un producto específico
     */
    private suspend fun launchPurchase(activity: Activity, productId: String): PurchaseResult {
        // Verificar conexión
        val connectionState = billingManager.connectionState.first()
        if (connectionState !is BillingManager.ConnectionState.Connected) {
            return PurchaseResult.NotConnected
        }
        
        // Lanzar flujo de compra
        val result = billingManager.launchPurchaseFlow(activity, productId)
        
        return mapBillingResult(result)
    }
    
    /**
     * Mapea resultado de billing a PurchaseResult
     */
    private fun mapBillingResult(result: BillingResult): PurchaseResult {
        return when (result.responseCode) {
            BillingResponseCode.OK -> PurchaseResult.Success
            BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
            BillingResponseCode.ITEM_UNAVAILABLE -> PurchaseResult.ProductNotFound
            BillingResponseCode.SERVICE_DISCONNECTED -> PurchaseResult.NotConnected
            else -> PurchaseResult.Error(result.responseCode, result.debugMessage)
        }
    }
    
    /**
     * Obtiene los planes disponibles con sus precios
     */
    fun getAvailablePlans(): List<PlanInfo> {
        val products = billingManager.availableProducts.value
        
        return products.mapNotNull { productDetails ->
            createPlanInfo(productDetails)
        }.sortedBy { it.monthlyPriceAmount }
    }
    
    /**
     * Crea información del plan desde ProductDetails
     */
    private fun createPlanInfo(productDetails: ProductDetails): PlanInfo? {
        val offer = productDetails.subscriptionOfferDetails?.firstOrNull()
            ?: return null
        
        val pricingPhase = offer.pricingPhases.pricingPhaseList
            .firstOrNull { it.priceAmountMicros > 0 }
            ?: return null
        
        val hasFreeTrial = offer.pricingPhases.pricingPhaseList
            .any { it.priceAmountMicros == 0L }
        
        val freeTrialPhase = offer.pricingPhases.pricingPhaseList
            .firstOrNull { it.priceAmountMicros == 0L }
        
        val isYearly = pricingPhase.billingPeriod.contains("Y")
        
        // Calcular precio mensual equivalente para planes anuales
        val monthlyEquivalent = if (isYearly) {
            pricingPhase.priceAmountMicros / 12
        } else {
            pricingPhase.priceAmountMicros
        }
        
        return PlanInfo(
            productId = productDetails.productId,
            plan = SubscriptionPlan.fromProductId(productDetails.productId),
            name = productDetails.name,
            description = productDetails.description,
            formattedPrice = pricingPhase.formattedPrice,
            priceAmountMicros = pricingPhase.priceAmountMicros,
            monthlyPriceAmount = monthlyEquivalent,
            currencyCode = pricingPhase.priceCurrencyCode,
            billingPeriod = if (isYearly) BillingPeriod.YEARLY else BillingPeriod.MONTHLY,
            hasFreeTrial = hasFreeTrial,
            freeTrialDays = freeTrialPhase?.let { parsePeriodToDays(it.billingPeriod) } ?: 0,
            offerToken = offer.offerToken
        )
    }
    
    /**
     * Parsea período ISO 8601 a días
     */
    private fun parsePeriodToDays(period: String): Int {
        return when {
            period.contains("D") -> period.replace("P", "").replace("D", "").toIntOrNull() ?: 0
            period.contains("W") -> (period.replace("P", "").replace("W", "").toIntOrNull() ?: 0) * 7
            else -> 0
        }
    }
    
    /**
     * Restaura compras existentes
     */
    suspend fun restorePurchases(): Boolean {
        val purchases = billingManager.restorePurchases()
        return purchases.isNotEmpty()
    }
    
    /**
     * Verifica si hay una suscripción activa
     */
    suspend fun hasActiveSubscription(): Boolean {
        val purchases = billingManager.queryExistingPurchases()
        return purchases.any { 
            it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED 
        }
    }
}

/**
 * Información completa de un plan para mostrar en UI
 */
data class PlanInfo(
    val productId: String,
    val plan: SubscriptionPlan,
    val name: String,
    val description: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val monthlyPriceAmount: Long,
    val currencyCode: String,
    val billingPeriod: BillingPeriod,
    val hasFreeTrial: Boolean,
    val freeTrialDays: Int,
    val offerToken: String
) {
    /**
     * Calcula el ahorro anual comparado con el plan mensual
     */
    fun calculateYearlySavings(monthlyPlan: PlanInfo?): Long {
        if (billingPeriod != BillingPeriod.YEARLY || monthlyPlan == null) return 0
        val yearlyAtMonthlyRate = monthlyPlan.priceAmountMicros * 12
        return yearlyAtMonthlyRate - priceAmountMicros
    }
    
    /**
     * Calcula el porcentaje de descuento anual
     */
    fun calculateYearlyDiscountPercent(monthlyPlan: PlanInfo?): Int {
        if (billingPeriod != BillingPeriod.YEARLY || monthlyPlan == null) return 0
        val yearlyAtMonthlyRate = monthlyPlan.priceAmountMicros * 12
        if (yearlyAtMonthlyRate == 0L) return 0
        return ((yearlyAtMonthlyRate - priceAmountMicros) * 100 / yearlyAtMonthlyRate).toInt()
    }
    
    /**
     * Texto para el badge de descuento
     */
    fun getDiscountBadgeText(monthlyPlan: PlanInfo?): String? {
        val percent = calculateYearlyDiscountPercent(monthlyPlan)
        return if (percent > 0) "Ahorra $percent%" else null
    }
}

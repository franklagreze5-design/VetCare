package com.petapp.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.first

class PurchaseHelper(private val billingManager: BillingManager) {

    sealed class PurchaseResult {
        data object Success : PurchaseResult()
        data object Cancelled : PurchaseResult()
        data class Error(val code: Int, val message: String) : PurchaseResult()
        data object NotConnected : PurchaseResult()
        data object ProductNotFound : PurchaseResult()
    }

    suspend fun purchasePremiumMonthly(activity: Activity): PurchaseResult = launchPurchase(activity, "premium_monthly")
    suspend fun purchasePremiumYearly(activity: Activity): PurchaseResult = launchPurchase(activity, "premium_yearly")
    suspend fun purchaseFamilyPlan(activity: Activity): PurchaseResult = launchPurchase(activity, "family_plan")

    private suspend fun launchPurchase(activity: Activity, productId: String): PurchaseResult {
        val state = billingManager.connectionState.first()
        if (state !is BillingManager.ConnectionState.Connected) return PurchaseResult.NotConnected
        return mapBillingResult(billingManager.launchPurchaseFlow(activity, productId))
    }

    private fun mapBillingResult(result: BillingResult): PurchaseResult = when (result.responseCode) {
        BillingResponseCode.OK -> PurchaseResult.Success
        BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
        BillingResponseCode.ITEM_UNAVAILABLE -> PurchaseResult.ProductNotFound
        BillingResponseCode.SERVICE_DISCONNECTED -> PurchaseResult.NotConnected
        else -> PurchaseResult.Error(result.responseCode, result.debugMessage)
    }

    fun getAvailablePlans(): List<PlanInfo> =
        billingManager.availableProducts.value.mapNotNull { createPlanInfo(it) }.sortedBy { it.monthlyPriceAmount }

    private fun createPlanInfo(productDetails: ProductDetails): PlanInfo? {
        val offer = productDetails.subscriptionOfferDetails?.firstOrNull() ?: return null
        val pricingPhase = offer.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros > 0 } ?: return null
        val hasFreeTrial = offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        val freeTrialPhase = offer.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L }
        val isYearly = pricingPhase.billingPeriod.contains("Y")

        return PlanInfo(
            productId = productDetails.productId,
            plan = SubscriptionPlan.fromProductId(productDetails.productId),
            name = productDetails.name,
            description = productDetails.description,
            formattedPrice = pricingPhase.formattedPrice,
            priceAmountMicros = pricingPhase.priceAmountMicros,
            monthlyPriceAmount = if (isYearly) pricingPhase.priceAmountMicros / 12 else pricingPhase.priceAmountMicros,
            currencyCode = pricingPhase.priceCurrencyCode,
            billingPeriod = if (isYearly) BillingPeriod.YEARLY else BillingPeriod.MONTHLY,
            hasFreeTrial = hasFreeTrial,
            freeTrialDays = freeTrialPhase?.let { parsePeriodToDays(it.billingPeriod) } ?: 0,
            offerToken = offer.offerToken
        )
    }

    private fun parsePeriodToDays(period: String): Int = when {
        period.contains("D") -> period.replace("P", "").replace("D", "").toIntOrNull() ?: 0
        period.contains("W") -> (period.replace("P", "").replace("W", "").toIntOrNull() ?: 0) * 7
        else -> 0
    }

    suspend fun restorePurchases(): Boolean = billingManager.restorePurchases().isNotEmpty()

    suspend fun hasActiveSubscription(): Boolean =
        billingManager.queryExistingPurchases().any { it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED }
}

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
    fun calculateYearlyDiscountPercent(monthlyPlan: PlanInfo?): Int {
        if (billingPeriod != BillingPeriod.YEARLY || monthlyPlan == null) return 0
        val yearlyAtMonthlyRate = monthlyPlan.priceAmountMicros * 12
        if (yearlyAtMonthlyRate == 0L) return 0
        return ((yearlyAtMonthlyRate - priceAmountMicros) * 100 / yearlyAtMonthlyRate).toInt()
    }
}

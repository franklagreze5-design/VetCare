package com.petapp.billing

// PlanInfo is defined in PurchaseHelper.kt
// This file is kept for compatibility but the main definition is in PurchaseHelper.kt

/**
 * Periodo de facturacion - moved here to avoid duplication
 */
enum class BillingPeriod {
    MONTHLY,
    YEARLY
}

/**
 * Informacion de precio para mostrar en UI
 */
data class PlanPricing(
    val productId: String,
    val plan: SubscriptionPlan,
    val priceMonthly: String,
    val priceYearly: String?,
    val currencyCode: String,
    val billingPeriod: BillingPeriod,
    val hasFreeTrial: Boolean,
    val freeTrialDays: Int
)

/**
 * Constantes de precios para Chile (referencia, el precio real viene de Google Play)
 */
object PricingConstants {
    const val PREMIUM_MONTHLY_CLP = 3990
    const val PREMIUM_YEARLY_CLP = 39990
    const val FAMILY_MONTHLY_CLP = 5490
    
    fun calculateYearlySavings(): Int {
        return (PREMIUM_MONTHLY_CLP * 12) - PREMIUM_YEARLY_CLP
    }
    
    fun calculateYearlyDiscountPercent(): Int {
        val monthlyTotal = PREMIUM_MONTHLY_CLP * 12
        return ((monthlyTotal - PREMIUM_YEARLY_CLP) * 100) / monthlyTotal
    }
}

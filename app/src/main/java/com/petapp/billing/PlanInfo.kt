package com.petapp.billing

/**
 * Información de un plan para mostrar en UI (viene de Google Play)
 */
data class PlanInfo(
    val productId: String,
    val plan: SubscriptionPlan,
    val formattedPrice: String,
    val billingPeriod: BillingPeriod,
    val hasFreeTrial: Boolean = false,
    val freeTrialDays: Int = 0
) {
    /**
     * Calcula el porcentaje de descuento anual comparado con el plan mensual
     */
    fun calculateYearlyDiscountPercent(monthlyPlan: PlanInfo): Int {
        return PricingConstants.calculateYearlyDiscountPercent()
    }
}

/**
 * Resultado de validación del backend
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

package com.petapp.billing

/**
 * Planes de suscripción disponibles en la app
 */
enum class SubscriptionPlan(
    val productId: String?,
    val displayName: String,
    val maxPets: Int,
    val maxRemindersPerMonth: Int,
    val hasAds: Boolean,
    val hasFullMedicalHistory: Boolean,
    val hasVaccines: Boolean,
    val hasFileStorage: Boolean,
    val canShareWithVet: Boolean,
    val hasAiRecommendations: Boolean
) {
    FREE(
        productId = null,
        displayName = "Gratis",
        maxPets = 1,
        maxRemindersPerMonth = 3,
        hasAds = true,
        hasFullMedicalHistory = false,
        hasVaccines = false,
        hasFileStorage = false,
        canShareWithVet = false,
        hasAiRecommendations = false
    ),
    
    PREMIUM(
        productId = "premium_monthly", // También puede ser "premium_yearly"
        displayName = "Premium",
        maxPets = Int.MAX_VALUE, // Ilimitado
        maxRemindersPerMonth = Int.MAX_VALUE,
        hasAds = false,
        hasFullMedicalHistory = true,
        hasVaccines = true,
        hasFileStorage = true,
        canShareWithVet = true,
        hasAiRecommendations = true
    ),
    
    FAMILY(
        productId = "family_plan",
        displayName = "Family",
        maxPets = 5,
        maxRemindersPerMonth = Int.MAX_VALUE,
        hasAds = false,
        hasFullMedicalHistory = true,
        hasVaccines = true,
        hasFileStorage = true,
        canShareWithVet = true,
        hasAiRecommendations = true
    );
    
    companion object {
        /**
         * Obtiene el plan basado en el productId de Google Play
         */
        fun fromProductId(productId: String?): SubscriptionPlan {
            return when (productId) {
                "premium_monthly", "premium_yearly" -> PREMIUM
                "family_plan" -> FAMILY
                else -> FREE
            }
        }
        
        /**
         * Lista de todos los product IDs de suscripción
         */
        val allProductIds = listOf(
            "premium_monthly",
            "premium_yearly", 
            "family_plan"
        )
    }
}

/**
 * Estados posibles de una suscripción
 */
enum class SubscriptionStatus {
    /** Suscripción activa y pagada */
    ACTIVE,
    
    /** Usuario canceló pero aún tiene acceso hasta expiryTime */
    CANCELED,
    
    /** Suscripción expirada, sin acceso premium */
    EXPIRED,
    
    /** Período de gracia por problemas de pago */
    GRACE_PERIOD,
    
    /** Suscripción pausada (en espera) */
    ON_HOLD,
    
    /** En período de prueba gratuita */
    IN_TRIAL
}

/**
 * Información de precio para mostrar en UI
 */
data class PlanPricing(
    val productId: String,
    val plan: SubscriptionPlan,
    val priceMonthly: String,        // Precio formateado: "$3.990"
    val priceYearly: String?,        // Precio anual si aplica
    val currencyCode: String,        // "CLP"
    val billingPeriod: BillingPeriod,
    val hasFreeTrial: Boolean,
    val freeTrialDays: Int
)

/**
 * Período de facturación
 */
enum class BillingPeriod {
    MONTHLY,
    YEARLY
}

/**
 * Constantes de precios para Chile (referencia, el precio real viene de Google Play)
 */
object PricingConstants {
    // Precios de referencia en CLP
    const val PREMIUM_MONTHLY_CLP = 3990
    const val PREMIUM_YEARLY_CLP = 39990
    const val FAMILY_MONTHLY_CLP = 5490
    
    // Descuento anual (mostrar ahorro)
    fun calculateYearlySavings(): Int {
        return (PREMIUM_MONTHLY_CLP * 12) - PREMIUM_YEARLY_CLP
    }
    
    // Porcentaje de descuento anual
    fun calculateYearlyDiscountPercent(): Int {
        val monthlyTotal = PREMIUM_MONTHLY_CLP * 12
        return ((monthlyTotal - PREMIUM_YEARLY_CLP) * 100) / monthlyTotal
    }
}

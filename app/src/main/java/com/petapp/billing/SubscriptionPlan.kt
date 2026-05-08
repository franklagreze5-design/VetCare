package com.petapp.billing

/**
 * Planes de suscripcion disponibles en la app
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
        productId = "premium_monthly",
        displayName = "Premium",
        maxPets = Int.MAX_VALUE,
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
         * Lista de todos los product IDs de suscripcion
         */
        val allProductIds = listOf(
            "premium_monthly",
            "premium_yearly", 
            "family_plan"
        )
    }
}

/**
 * Estados posibles de una suscripcion
 */
enum class SubscriptionStatus {
    ACTIVE,
    CANCELED,
    EXPIRED,
    GRACE_PERIOD,
    ON_HOLD,
    IN_TRIAL
}

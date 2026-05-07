package com.petapp.features

import com.petapp.billing.SubscriptionPlan
import com.petapp.data.UserSubscription
import com.petapp.subscription.UserLimitsData

/**
 * FeatureGate - Control de acceso a funcionalidades según el plan
 * 
 * PRINCIPIO DE SEGURIDAD: Validar siempre en el backend.
 * Este gate es para UX (mostrar/ocultar UI), no para seguridad.
 * La validación real ocurre en Firestore Security Rules y Cloud Functions.
 */
object FeatureGate {
    
    /**
     * Verifica si el usuario puede agregar una nueva mascota
     * 
     * FREE: máximo 1 mascota
     * PREMIUM: ilimitado
     * FAMILY: máximo 5 mascotas
     */
    fun canAddPet(subscription: UserSubscription, currentPetsCount: Int): FeatureAccessResult {
        val plan = subscription.getPlanEnum()
        val maxPets = plan.maxPets
        
        return if (currentPetsCount < maxPets) {
            FeatureAccessResult.Allowed
        } else {
            FeatureAccessResult.Blocked(
                reason = BlockReason.PET_LIMIT_REACHED,
                currentLimit = maxPets,
                suggestedPlan = getSuggestedUpgrade(plan, Feature.UNLIMITED_PETS)
            )
        }
    }
    
    /**
     * Verifica si el usuario puede agregar un recordatorio este mes
     * 
     * FREE: máximo 3 por mes
     * PREMIUM/FAMILY: ilimitado
     */
    fun canAddReminder(subscription: UserSubscription, limits: UserLimitsData): FeatureAccessResult {
        val plan = subscription.getPlanEnum()
        val maxReminders = plan.maxRemindersPerMonth
        
        return if (limits.remindersThisMonth < maxReminders) {
            FeatureAccessResult.Allowed
        } else {
            FeatureAccessResult.Blocked(
                reason = BlockReason.REMINDER_LIMIT_REACHED,
                currentLimit = maxReminders,
                suggestedPlan = getSuggestedUpgrade(plan, Feature.UNLIMITED_REMINDERS)
            )
        }
    }
    
    /**
     * Verifica si el usuario puede acceder al historial médico completo
     */
    fun canAccessFullMedicalHistory(subscription: UserSubscription): FeatureAccessResult {
        val plan = subscription.getPlanEnum()
        
        return if (plan.hasFullMedicalHistory) {
            FeatureAccessResult.Allowed
        } else {
            FeatureAccessResult.Blocked(
                reason = BlockReason.PREMIUM_FEATURE,
                feature = Feature.FULL_MEDICAL_HISTORY,
                suggestedPlan = SubscriptionPlan.PREMIUM
            )
        }
    }
    
    /**
     * Verifica si el usuario puede acceder a vacunas
     */
    fun canAccessVaccines(subscription: UserSubscription): FeatureAccessResult {
        val plan = subscription.getPlanEnum()
        
        return if (plan.hasVaccines) {
            FeatureAccessResult.Allowed
        } else {
            FeatureAccessResult.Blocked(
                reason = BlockReason.PREMIUM_FEATURE,
                feature = Feature.VACCINES,
                suggestedPlan = SubscriptionPlan.PREMIUM
            )
        }
    }
    
    /**
     * Verifica si el usuario puede subir archivos/fotos
     */
    fun canUploadFiles(subscription: UserSubscription): FeatureAccessResult {
        val plan = subscription.getPlanEnum()
        
        return if (plan.hasFileStorage) {
            FeatureAccessResult.Allowed
        } else {
            FeatureAccessResult.Blocked(
                reason = BlockReason.PREMIUM_FEATURE,
                feature = Feature.FILE_STORAGE,
                suggestedPlan = SubscriptionPlan.PREMIUM
            )
        }
    }
    
    /**
     * Verifica si el usuario puede compartir ficha con veterinario
     */
    fun canShareWithVet(subscription: UserSubscription): FeatureAccessResult {
        val plan = subscription.getPlanEnum()
        
        return if (plan.canShareWithVet) {
            FeatureAccessResult.Allowed
        } else {
            FeatureAccessResult.Blocked(
                reason = BlockReason.PREMIUM_FEATURE,
                feature = Feature.SHARE_WITH_VET,
                suggestedPlan = SubscriptionPlan.PREMIUM
            )
        }
    }
    
    /**
     * Verifica si el usuario puede usar recomendaciones IA
     */
    fun canUseAiRecommendations(subscription: UserSubscription): FeatureAccessResult {
        val plan = subscription.getPlanEnum()
        
        return if (plan.hasAiRecommendations) {
            FeatureAccessResult.Allowed
        } else {
            FeatureAccessResult.Blocked(
                reason = BlockReason.PREMIUM_FEATURE,
                feature = Feature.AI_RECOMMENDATIONS,
                suggestedPlan = SubscriptionPlan.PREMIUM
            )
        }
    }
    
    /**
     * Verifica si se deben mostrar anuncios
     */
    fun shouldShowAds(subscription: UserSubscription): Boolean {
        return subscription.getPlanEnum().hasAds
    }
    
    /**
     * Verifica acceso a una feature específica
     */
    fun checkAccess(subscription: UserSubscription, feature: Feature, limits: UserLimitsData? = null): FeatureAccessResult {
        return when (feature) {
            Feature.UNLIMITED_PETS -> {
                val currentPets = limits?.petsCount ?: 0
                canAddPet(subscription, currentPets)
            }
            Feature.UNLIMITED_REMINDERS -> {
                limits?.let { canAddReminder(subscription, it) } 
                    ?: FeatureAccessResult.Allowed
            }
            Feature.FULL_MEDICAL_HISTORY -> canAccessFullMedicalHistory(subscription)
            Feature.VACCINES -> canAccessVaccines(subscription)
            Feature.FILE_STORAGE -> canUploadFiles(subscription)
            Feature.SHARE_WITH_VET -> canShareWithVet(subscription)
            Feature.AI_RECOMMENDATIONS -> canUseAiRecommendations(subscription)
            Feature.AD_FREE -> {
                if (!shouldShowAds(subscription)) {
                    FeatureAccessResult.Allowed
                } else {
                    FeatureAccessResult.Blocked(
                        reason = BlockReason.PREMIUM_FEATURE,
                        feature = Feature.AD_FREE,
                        suggestedPlan = SubscriptionPlan.PREMIUM
                    )
                }
            }
        }
    }
    
    /**
     * Sugiere el plan de upgrade más apropiado
     */
    private fun getSuggestedUpgrade(currentPlan: SubscriptionPlan, feature: Feature): SubscriptionPlan {
        return when {
            currentPlan == SubscriptionPlan.FREE -> {
                // Para límites de mascotas, si el usuario tiene familia, sugerir FAMILY
                if (feature == Feature.UNLIMITED_PETS) {
                    SubscriptionPlan.PREMIUM // Primero sugerir premium, luego family
                } else {
                    SubscriptionPlan.PREMIUM
                }
            }
            currentPlan == SubscriptionPlan.PREMIUM -> {
                // Ya tiene premium, sugerir family solo si necesita más mascotas
                SubscriptionPlan.FAMILY
            }
            else -> SubscriptionPlan.PREMIUM
        }
    }
    
    /**
     * Obtiene la lista de features disponibles para un plan
     */
    fun getAvailableFeatures(plan: SubscriptionPlan): List<Feature> {
        val features = mutableListOf<Feature>()
        
        if (plan.maxPets > 1) features.add(Feature.UNLIMITED_PETS)
        if (plan.maxRemindersPerMonth == Int.MAX_VALUE) features.add(Feature.UNLIMITED_REMINDERS)
        if (plan.hasFullMedicalHistory) features.add(Feature.FULL_MEDICAL_HISTORY)
        if (plan.hasVaccines) features.add(Feature.VACCINES)
        if (plan.hasFileStorage) features.add(Feature.FILE_STORAGE)
        if (plan.canShareWithVet) features.add(Feature.SHARE_WITH_VET)
        if (plan.hasAiRecommendations) features.add(Feature.AI_RECOMMENDATIONS)
        if (!plan.hasAds) features.add(Feature.AD_FREE)
        
        return features
    }
    
    /**
     * Obtiene las features que ganaría el usuario al hacer upgrade
     */
    fun getFeaturesGainedOnUpgrade(
        currentPlan: SubscriptionPlan, 
        targetPlan: SubscriptionPlan
    ): List<Feature> {
        val currentFeatures = getAvailableFeatures(currentPlan)
        val targetFeatures = getAvailableFeatures(targetPlan)
        
        return targetFeatures.filter { it !in currentFeatures }
    }
}

/**
 * Resultado de verificación de acceso a una feature
 */
sealed class FeatureAccessResult {
    /** Acceso permitido */
    data object Allowed : FeatureAccessResult()
    
    /** Acceso bloqueado */
    data class Blocked(
        val reason: BlockReason,
        val feature: Feature? = null,
        val currentLimit: Int? = null,
        val suggestedPlan: SubscriptionPlan
    ) : FeatureAccessResult()
}

/**
 * Razones por las que se bloquea el acceso
 */
enum class BlockReason {
    /** Límite de mascotas alcanzado */
    PET_LIMIT_REACHED,
    
    /** Límite de recordatorios mensuales alcanzado */
    REMINDER_LIMIT_REACHED,
    
    /** Feature solo disponible en plan premium */
    PREMIUM_FEATURE,
    
    /** Suscripción expirada */
    SUBSCRIPTION_EXPIRED
}

/**
 * Features que pueden ser verificadas
 */
enum class Feature(val displayName: String, val description: String) {
    UNLIMITED_PETS(
        "Mascotas ilimitadas",
        "Agrega todas las mascotas que quieras"
    ),
    UNLIMITED_REMINDERS(
        "Recordatorios ilimitados",
        "Crea recordatorios sin límite"
    ),
    FULL_MEDICAL_HISTORY(
        "Historial médico completo",
        "Accede a todo el historial de salud"
    ),
    VACCINES(
        "Control de vacunas",
        "Registra y programa vacunas"
    ),
    FILE_STORAGE(
        "Archivos y fotos",
        "Guarda documentos y fotos médicas"
    ),
    SHARE_WITH_VET(
        "Compartir con veterinario",
        "Envía la ficha a tu veterinario"
    ),
    AI_RECOMMENDATIONS(
        "Recomendaciones IA",
        "Consejos personalizados con inteligencia artificial"
    ),
    AD_FREE(
        "Sin publicidad",
        "Experiencia libre de anuncios"
    )
}

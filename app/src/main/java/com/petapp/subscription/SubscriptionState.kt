package com.petapp.subscription

import com.petapp.billing.PlanPricing
import com.petapp.billing.SubscriptionPlan
import com.petapp.billing.SubscriptionStatus
import com.petapp.data.UserSubscription

/**
 * Estado de UI para la pantalla de suscripción
 */
sealed class SubscriptionUiState {
    /** Cargando información de suscripción */
    data object Loading : SubscriptionUiState()
    
    /** Estado cargado correctamente */
    data class Loaded(
        val currentPlan: SubscriptionPlan,
        val status: SubscriptionStatus,
        val expiryDate: Long?,
        val isAutoRenewing: Boolean,
        val availablePlans: List<PlanPricing>,
        val canStartTrial: Boolean
    ) : SubscriptionUiState()
    
    /** Error al cargar */
    data class Error(val message: String) : SubscriptionUiState()
}

/**
 * Estado del proceso de compra
 */
sealed class PurchaseState {
    /** Sin compra en progreso */
    data object Idle : PurchaseState()
    
    /** Iniciando flujo de compra */
    data object Launching : PurchaseState()
    
    /** Esperando respuesta de Google Play */
    data object Pending : PurchaseState()
    
    /** Validando con backend */
    data object Validating : PurchaseState()
    
    /** Compra exitosa */
    data class Success(val plan: SubscriptionPlan) : PurchaseState()
    
    /** Compra cancelada por usuario */
    data object Cancelled : PurchaseState()
    
    /** Error en la compra */
    data class Error(val message: String, val code: Int? = null) : PurchaseState()
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

/**
 * Eventos de suscripción para analytics
 */
sealed class SubscriptionEvent {
    data class PaywallViewed(val source: String) : SubscriptionEvent()
    data class PlanSelected(val plan: SubscriptionPlan) : SubscriptionEvent()
    data class PurchaseStarted(val productId: String) : SubscriptionEvent()
    data class PurchaseCompleted(val productId: String, val isUpgrade: Boolean) : SubscriptionEvent()
    data class PurchaseFailed(val productId: String, val errorCode: Int) : SubscriptionEvent()
    data class TrialStarted(val plan: SubscriptionPlan) : SubscriptionEvent()
    data class SubscriptionCancelled(val plan: SubscriptionPlan) : SubscriptionEvent()
    data class SubscriptionRestored(val plan: SubscriptionPlan) : SubscriptionEvent()
}

/**
 * Extensiones para UserSubscription
 */
fun UserSubscription.toUiState(availablePlans: List<PlanPricing>): SubscriptionUiState.Loaded {
    return SubscriptionUiState.Loaded(
        currentPlan = getPlanEnum(),
        status = getStatusEnum(),
        expiryDate = expiryTime?.toDate()?.time,
        isAutoRenewing = autoRenewing,
        availablePlans = availablePlans,
        canStartTrial = !trialUsed && plan == SubscriptionPlan.FREE.name
    )
}

/**
 * Razones por las que se muestra el paywall
 */
enum class PaywallTrigger {
    /** Usuario tocó botón "Pro" en navegación */
    MANUAL,
    
    /** Intentó agregar más mascotas del límite */
    PET_LIMIT_REACHED,
    
    /** Intentó agregar más recordatorios del límite mensual */
    REMINDER_LIMIT_REACHED,
    
    /** Intentó acceder a historial de vacunas */
    VACCINE_ACCESS,
    
    /** Intentó subir archivos/fotos */
    FILE_UPLOAD,
    
    /** Intentó compartir ficha con veterinario */
    SHARE_WITH_VET,
    
    /** Intentó usar recomendaciones IA */
    AI_RECOMMENDATIONS
}

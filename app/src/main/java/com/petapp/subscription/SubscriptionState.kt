package com.petapp.subscription

import com.petapp.billing.PlanPricing
import com.petapp.billing.SubscriptionPlan
import com.petapp.billing.SubscriptionStatus
import com.petapp.data.UserSubscription

/**
 * Estado de UI para la pantalla de suscripcion
 */
sealed class SubscriptionUiState {
    data object Loading : SubscriptionUiState()
    
    data class Loaded(
        val currentPlan: SubscriptionPlan,
        val status: SubscriptionStatus,
        val expiryDate: Long?,
        val isAutoRenewing: Boolean,
        val availablePlans: List<PlanPricing>,
        val canStartTrial: Boolean
    ) : SubscriptionUiState()
    
    data class Error(val message: String) : SubscriptionUiState()
}

/**
 * Eventos de suscripcion para analytics
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

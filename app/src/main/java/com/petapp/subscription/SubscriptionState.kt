package com.petapp.subscription

import com.petapp.billing.SubscriptionPlan
import com.petapp.billing.SubscriptionStatus
import com.petapp.data.UserSubscription

sealed class SubscriptionUiState {
    data object Loading : SubscriptionUiState()
    data class Loaded(
        val currentPlan: SubscriptionPlan,
        val status: SubscriptionStatus,
        val expiryDate: Long?,
        val isAutoRenewing: Boolean,
        val canStartTrial: Boolean
    ) : SubscriptionUiState()
    data class Error(val message: String) : SubscriptionUiState()
}

sealed class PurchaseState {
    data object Idle : PurchaseState()
    data object Launching : PurchaseState()
    data object Pending : PurchaseState()
    data object Validating : PurchaseState()
    data class Success(val plan: SubscriptionPlan) : PurchaseState()
    data object Cancelled : PurchaseState()
    data class Error(val message: String, val code: Int? = null) : PurchaseState()
}

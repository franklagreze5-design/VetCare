package com.petapp.billing

sealed class ValidationResult {
    data class Valid(
        val plan: SubscriptionPlan,
        val expiryTime: Long,
        val isInTrial: Boolean
    ) : ValidationResult()

    data class Invalid(val reason: String) : ValidationResult()

    data class Error(val exception: Exception) : ValidationResult()
}

package com.petapp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.petapp.billing.SubscriptionPlan
import com.petapp.billing.SubscriptionStatus

data class UserSubscription(
    @get:PropertyName("plan")
    @set:PropertyName("plan")
    var plan: String = SubscriptionPlan.FREE.name,

    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = SubscriptionStatus.ACTIVE.name,

    @get:PropertyName("productId")
    @set:PropertyName("productId")
    var productId: String? = null,

    @get:PropertyName("purchaseToken")
    @set:PropertyName("purchaseToken")
    var purchaseToken: String? = null,

    @get:PropertyName("originalPurchaseTime")
    @set:PropertyName("originalPurchaseTime")
    var originalPurchaseTime: Timestamp? = null,

    @get:PropertyName("expiryTime")
    @set:PropertyName("expiryTime")
    var expiryTime: Timestamp? = null,

    @get:PropertyName("autoRenewing")
    @set:PropertyName("autoRenewing")
    var autoRenewing: Boolean = false,

    @get:PropertyName("trialUsed")
    @set:PropertyName("trialUsed")
    var trialUsed: Boolean = false,

    @get:PropertyName("cancelReason")
    @set:PropertyName("cancelReason")
    var cancelReason: String? = null
) {
    constructor() : this(plan = SubscriptionPlan.FREE.name)

    fun isActive(): Boolean {
        if (status != SubscriptionStatus.ACTIVE.name) return false
        val expiry = expiryTime?.toDate()?.time ?: return plan == SubscriptionPlan.FREE.name
        return System.currentTimeMillis() < expiry
    }

    fun hasPremiumAccess(): Boolean {
        return isActive() && (plan == SubscriptionPlan.PREMIUM.name || plan == SubscriptionPlan.FAMILY.name)
    }

    fun getPlanEnum(): SubscriptionPlan {
        return try { SubscriptionPlan.valueOf(plan) } catch (e: IllegalArgumentException) { SubscriptionPlan.FREE }
    }

    fun getStatusEnum(): SubscriptionStatus {
        return try { SubscriptionStatus.valueOf(status) } catch (e: IllegalArgumentException) { SubscriptionStatus.EXPIRED }
    }
}

data class UserLimits(
    @get:PropertyName("petsCount") @set:PropertyName("petsCount") var petsCount: Int = 0,
    @get:PropertyName("remindersThisMonth") @set:PropertyName("remindersThisMonth") var remindersThisMonth: Int = 0,
    @get:PropertyName("lastReminderReset") @set:PropertyName("lastReminderReset") var lastReminderReset: Timestamp? = null
) {
    constructor() : this(petsCount = 0)
}

data class UserDocument(
    @get:PropertyName("email") @set:PropertyName("email") var email: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Timestamp? = null,
    @get:PropertyName("subscription") @set:PropertyName("subscription") var subscription: UserSubscription = UserSubscription(),
    @get:PropertyName("limits") @set:PropertyName("limits") var limits: UserLimits = UserLimits()
) {
    constructor() : this(email = "")
}

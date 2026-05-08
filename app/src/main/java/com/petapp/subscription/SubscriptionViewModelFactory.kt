package com.petapp.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.petapp.billing.BillingManager
import com.petapp.billing.PurchaseHelper

class SubscriptionViewModelFactory(
    private val repository: SubscriptionRepository,
    private val billingManager: BillingManager,
    private val purchaseHelper: PurchaseHelper
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubscriptionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SubscriptionViewModel(repository, billingManager, purchaseHelper) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

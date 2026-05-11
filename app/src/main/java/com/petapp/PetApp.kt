package com.petapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.petapp.ads.AdManager
import com.petapp.billing.BillingManager
import com.petapp.billing.PurchaseHelper
import com.petapp.subscription.SubscriptionRepository

class PetApp : Application() {

    lateinit var subscriptionRepository: SubscriptionRepository
        private set
    lateinit var billingManager: BillingManager
        private set
    lateinit var purchaseHelper: PurchaseHelper
        private set
    lateinit var adManager: AdManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)
        subscriptionRepository = SubscriptionRepository()
        billingManager = BillingManager(
            context = this,
            onPurchaseValidation = { purchase ->
                subscriptionRepository.validatePurchaseWithBackend(purchase)
            }
        )
        purchaseHelper = PurchaseHelper(billingManager)
        adManager = AdManager(this)
        adManager.initialize()
        billingManager.startConnection()
    }

    override fun onTerminate() {
        super.onTerminate()
        billingManager.endConnection()
        adManager.destroy()
    }

    companion object {
        lateinit var instance: PetApp
            private set
    }
}

val android.content.Context.petApp: PetApp get() = applicationContext as PetApp
val android.content.Context.subscriptionRepository: SubscriptionRepository get() = petApp.subscriptionRepository
val android.content.Context.billingManager: BillingManager get() = petApp.billingManager
val android.content.Context.purchaseHelper: PurchaseHelper get() = petApp.purchaseHelper
val android.content.Context.adManager: AdManager get() = petApp.adManager

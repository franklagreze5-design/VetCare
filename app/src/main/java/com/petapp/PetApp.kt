package com.petapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.petapp.ads.AdManager
import com.petapp.billing.BillingManager
import com.petapp.billing.PurchaseHelper
import com.petapp.billing.ValidationResult
import com.petapp.subscription.SubscriptionRepository

/**
 * Application class principal
 * 
 * Inicializa los componentes principales del sistema de suscripción:
 * - Firebase
 * - Google Play Billing
 * - AdMob
 */
class PetApp : Application() {
    
    // Instancias singleton (en producción usar DI como Hilt)
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
        
        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        
        // Inicializar repositorio de suscripciones
        subscriptionRepository = SubscriptionRepository()
        
        // Inicializar Billing Manager con callback de validación
        billingManager = BillingManager(
            context = this,
            onPurchaseValidation = { purchase ->
                // Validar con backend
                subscriptionRepository.validatePurchaseWithBackend(purchase)
            }
        )
        
        // Inicializar Purchase Helper
        purchaseHelper = PurchaseHelper(billingManager)
        
        // Inicializar AdManager
        adManager = AdManager(this)
        adManager.initialize()
        
        // Conectar con Google Play Billing
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

/**
 * Extensiones para acceder a los componentes desde cualquier Activity/Fragment
 */
val android.content.Context.petApp: PetApp
    get() = applicationContext as PetApp

val android.content.Context.subscriptionRepository: SubscriptionRepository
    get() = petApp.subscriptionRepository

val android.content.Context.billingManager: BillingManager
    get() = petApp.billingManager

val android.content.Context.purchaseHelper: PurchaseHelper
    get() = petApp.purchaseHelper

val android.content.Context.adManager: AdManager
    get() = petApp.adManager

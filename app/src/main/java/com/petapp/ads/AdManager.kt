package com.petapp.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.petapp.billing.SubscriptionPlan
import com.petapp.data.UserSubscription
import com.petapp.features.FeatureGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AdManager - Gestiona la publicidad de la app
 * 
 * REGLA PRINCIPAL: Solo mostrar anuncios a usuarios FREE
 * Los usuarios Premium y Family no ven publicidad.
 */
class AdManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AdManager"
        
        // IDs de test - REEMPLAZAR en producción
        const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111" // Test
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // Test
        
        // Configuración de frecuencia de interstitials
        const val INTERSTITIAL_FREQUENCY = 5 // Cada N acciones
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var actionCount = 0
    
    private val _isAdEnabled = MutableStateFlow(true)
    val isAdEnabled: StateFlow<Boolean> = _isAdEnabled.asStateFlow()
    
    private val _isInterstitialReady = MutableStateFlow(false)
    val isInterstitialReady: StateFlow<Boolean> = _isInterstitialReady.asStateFlow()
    
    /**
     * Inicializa AdMob
     * Llamar una vez al iniciar la app
     */
    fun initialize() {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob inicializado: $initializationStatus")
        }
    }
    
    /**
     * Actualiza el estado de ads según la suscripción del usuario
     * 
     * IMPORTANTE: Esta es la función que determina si mostrar ads.
     * Solo los usuarios FREE ven publicidad.
     */
    fun updateAdState(subscription: UserSubscription) {
        val shouldShowAds = FeatureGate.shouldShowAds(subscription)
        _isAdEnabled.value = shouldShowAds
        
        Log.d(TAG, "Ads enabled: $shouldShowAds (plan: ${subscription.plan})")
        
        if (shouldShowAds) {
            preloadInterstitial()
        } else {
            // Limpiar ad cargado si el usuario ya no debe ver ads
            interstitialAd = null
            _isInterstitialReady.value = false
        }
    }
    
    /**
     * Verifica si se deben mostrar ads para el plan actual
     */
    fun shouldShowAds(plan: SubscriptionPlan): Boolean {
        return plan.hasAds
    }
    
    /**
     * Carga un banner ad en el AdView proporcionado
     * Solo carga si el usuario es FREE
     */
    fun loadBannerAd(adView: AdView, subscription: UserSubscription) {
        if (!FeatureGate.shouldShowAds(subscription)) {
            Log.d(TAG, "Saltando banner ad - usuario premium")
            adView.visibility = android.view.View.GONE
            return
        }
        
        adView.visibility = android.view.View.VISIBLE
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }
    
    /**
     * Precarga un interstitial ad
     */
    private fun preloadInterstitial() {
        if (!_isAdEnabled.value) return
        if (interstitialAd != null) return // Ya hay uno cargado
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial cargado")
                    interstitialAd = ad
                    _isInterstitialReady.value = true
                    setupInterstitialCallbacks()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Error cargando interstitial: ${error.message}")
                    interstitialAd = null
                    _isInterstitialReady.value = false
                }
            }
        )
    }
    
    /**
     * Configura callbacks del interstitial
     */
    private fun setupInterstitialCallbacks() {
        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial cerrado")
                interstitialAd = null
                _isInterstitialReady.value = false
                // Precargar el siguiente
                preloadInterstitial()
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Error mostrando interstitial: ${error.message}")
                interstitialAd = null
                _isInterstitialReady.value = false
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial mostrado")
            }
        }
    }
    
    /**
     * Muestra un interstitial si está disponible y corresponde
     * Usa un sistema de frecuencia para no molestar demasiado
     * 
     * @param activity Activity desde donde mostrar
     * @param subscription Suscripción actual del usuario
     * @param force Si es true, ignora el contador de frecuencia
     */
    fun showInterstitialIfReady(
        activity: Activity,
        subscription: UserSubscription,
        force: Boolean = false
    ): Boolean {
        // No mostrar si es premium
        if (!FeatureGate.shouldShowAds(subscription)) {
            Log.d(TAG, "Saltando interstitial - usuario premium")
            return false
        }
        
        // Verificar frecuencia
        actionCount++
        if (!force && actionCount < INTERSTITIAL_FREQUENCY) {
            return false
        }
        
        // Resetear contador
        actionCount = 0
        
        // Mostrar si está listo
        val ad = interstitialAd
        return if (ad != null) {
            ad.show(activity)
            true
        } else {
            preloadInterstitial()
            false
        }
    }
    
    /**
     * Registra una acción del usuario para el contador de interstitials
     */
    fun registerUserAction() {
        actionCount++
    }
    
    /**
     * Resetea el contador de acciones
     */
    fun resetActionCount() {
        actionCount = 0
    }
    
    /**
     * Libera recursos
     */
    fun destroy() {
        interstitialAd = null
        _isInterstitialReady.value = false
    }
}

/**
 * Composable helper para mostrar/ocultar ads según suscripción
 */
object AdVisibility {
    /**
     * Determina la visibilidad del contenedor de ads
     */
    fun getVisibility(subscription: UserSubscription): Int {
        return if (FeatureGate.shouldShowAds(subscription)) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
}

package com.petapp.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petapp.billing.BillingManager
import com.petapp.billing.PlanInfo
import com.petapp.billing.PurchaseHelper
import com.petapp.billing.SubscriptionPlan
import com.petapp.features.PaywallTrigger
import com.petapp.features.FeatureConfig
import com.petapp.features.UpgradePrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para la pantalla de suscripcion/paywall
 */
class SubscriptionViewModel(
    private val repository: SubscriptionRepository,
    private val billingManager: BillingManager,
    private val purchaseHelper: PurchaseHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()
    
    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()
    
    init {
        observeSubscription()
        observeBillingEvents()
    }
    
    /**
     * Observa cambios en la suscripcion del usuario
     */
    private fun observeSubscription() {
        viewModelScope.launch {
            repository.observeSubscription().collectLatest { subscription ->
                subscription?.let {
                    _uiState.update { state ->
                        state.copy(
                            currentPlan = subscription.getPlanEnum(),
                            canStartTrial = !subscription.trialUsed && 
                                subscription.plan == SubscriptionPlan.FREE.name,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Observa eventos del billing manager
     */
    private fun observeBillingEvents() {
        viewModelScope.launch {
            billingManager.purchaseEvents.collectLatest { event ->
                when (event) {
                    is BillingManager.PurchaseEvent.PurchaseCompleted -> {
                        _purchaseState.value = PurchaseState.Validating
                    }
                    is BillingManager.PurchaseEvent.ValidationSuccess -> {
                        _purchaseState.value = PurchaseState.Success(event.plan)
                    }
                    is BillingManager.PurchaseEvent.ValidationFailed -> {
                        _purchaseState.value = PurchaseState.Error(event.reason)
                    }
                    is BillingManager.PurchaseEvent.PurchaseCancelled -> {
                        _purchaseState.value = PurchaseState.Cancelled
                    }
                    is BillingManager.PurchaseEvent.PurchaseError -> {
                        _purchaseState.value = PurchaseState.Error(event.message, event.code)
                    }
                    is BillingManager.PurchaseEvent.PurchasePending -> {
                        _purchaseState.value = PurchaseState.Pending
                    }
                }
            }
        }
    }
    
    /**
     * Carga planes disponibles desde Google Play
     */
    fun loadAvailablePlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            billingManager.connectionState.collectLatest { state ->
                if (state is BillingManager.ConnectionState.Connected) {
                    val plans = purchaseHelper.getAvailablePlans()
                    _uiState.update { 
                        it.copy(
                            availablePlans = plans,
                            isLoading = false
                        )
                    }
                    return@collectLatest
                }
            }
        }
    }
    
    /**
     * Configura el paywall segun el trigger
     */
    fun setPaywallTrigger(trigger: PaywallTrigger) {
        val prompt = FeatureConfig.getUpgradePrompt(trigger)
        _uiState.update { 
            it.copy(
                trigger = trigger,
                upgradePrompt = prompt
            )
        }
    }
    
    /**
     * Selecciona un plan para comprar
     */
    fun selectPlan(plan: SubscriptionPlan) {
        _uiState.update { it.copy(selectedPlan = plan) }
    }
    
    /**
     * Selecciona el periodo de facturacion
     */
    fun selectBillingPeriod(isYearly: Boolean) {
        _uiState.update { it.copy(isYearlySelected = isYearly) }
    }
    
    /**
     * Inicia el flujo de compra
     */
    fun purchaseSelectedPlan(activity: Activity) {
        val state = _uiState.value
        val plan = state.selectedPlan ?: return
        
        viewModelScope.launch {
            _purchaseState.value = PurchaseState.Launching
            
            val result = when (plan) {
                SubscriptionPlan.PREMIUM -> {
                    if (state.isYearlySelected) {
                        purchaseHelper.purchasePremiumYearly(activity)
                    } else {
                        purchaseHelper.purchasePremiumMonthly(activity)
                    }
                }
                SubscriptionPlan.FAMILY -> {
                    purchaseHelper.purchaseFamilyPlan(activity)
                }
                else -> return@launch
            }
            
            when (result) {
                is PurchaseHelper.PurchaseResult.Success -> {
                    // El evento de compra se manejara en observeBillingEvents
                }
                is PurchaseHelper.PurchaseResult.Cancelled -> {
                    _purchaseState.value = PurchaseState.Cancelled
                }
                is PurchaseHelper.PurchaseResult.Error -> {
                    _purchaseState.value = PurchaseState.Error(result.message, result.code)
                }
                is PurchaseHelper.PurchaseResult.NotConnected -> {
                    _purchaseState.value = PurchaseState.Error("No conectado a Google Play")
                }
                is PurchaseHelper.PurchaseResult.ProductNotFound -> {
                    _purchaseState.value = PurchaseState.Error("Producto no disponible")
                }
            }
        }
    }
    
    /**
     * Restaura compras anteriores
     */
    fun restorePurchases() {
        viewModelScope.launch {
            _purchaseState.value = PurchaseState.Validating
            
            val restored = purchaseHelper.restorePurchases()
            
            if (restored) {
                _purchaseState.value = PurchaseState.Idle
            } else {
                _purchaseState.value = PurchaseState.Error("No se encontraron compras anteriores")
            }
        }
    }
    
    /**
     * Resetea el estado de compra
     */
    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }
    
    /**
     * Obtiene el plan mensual para comparar precios
     */
    fun getMonthlyPlan(): PlanInfo? {
        return _uiState.value.availablePlans.find { it.productId == "premium_monthly" }
    }
    
    /**
     * Calcula el ahorro anual
     */
    fun calculateYearlySavings(): String? {
        val yearly = _uiState.value.availablePlans.find { it.productId == "premium_yearly" }
        val monthly = getMonthlyPlan()
        
        if (yearly == null || monthly == null) return null
        
        val percent = yearly.calculateYearlyDiscountPercent(monthly)
        return if (percent > 0) "Ahorra $percent%" else null
    }
}

/**
 * Estado de UI del paywall
 */
data class PaywallUiState(
    val isLoading: Boolean = true,
    val currentPlan: SubscriptionPlan = SubscriptionPlan.FREE,
    val availablePlans: List<PlanInfo> = emptyList(),
    val selectedPlan: SubscriptionPlan? = null,
    val isYearlySelected: Boolean = false,
    val canStartTrial: Boolean = true,
    val trigger: PaywallTrigger = PaywallTrigger.MANUAL,
    val upgradePrompt: UpgradePrompt = UpgradePrompt(
        title = "Mejora tu experiencia",
        subtitle = "Desbloquea todas las funcionalidades premium",
        ctaText = "Ver planes"
    )
)

/**
 * Estado del proceso de compra
 */
sealed class PurchaseState {
    data object Idle : PurchaseState()
    data object Launching : PurchaseState()
    data object Pending : PurchaseState()
    data object Validating : PurchaseState()
    data class Success(val plan: SubscriptionPlan) : PurchaseState()
    data object Cancelled : PurchaseState()
    data class Error(val message: String, val code: Int? = null) : PurchaseState()
}

package com.petapp.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petapp.billing.BillingManager
import com.petapp.billing.PlanInfo
import com.petapp.billing.PurchaseHelper
import com.petapp.billing.SubscriptionPlan
import com.petapp.features.FeatureConfig
import com.petapp.features.PaywallTrigger
import com.petapp.features.UpgradePrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private fun observeSubscription() {
        viewModelScope.launch {
            repository.observeSubscription().collectLatest { subscription ->
                subscription?.let {
                    _uiState.update { state ->
                        state.copy(
                            currentPlan = subscription.getPlanEnum(),
                            canStartTrial = !subscription.trialUsed && subscription.plan == SubscriptionPlan.FREE.name,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun observeBillingEvents() {
        viewModelScope.launch {
            billingManager.purchaseEvents.collectLatest { event ->
                when (event) {
                    is BillingManager.PurchaseEvent.PurchaseCompleted -> _purchaseState.value = PurchaseState.Validating
                    is BillingManager.PurchaseEvent.ValidationSuccess -> _purchaseState.value = PurchaseState.Success(event.plan)
                    is BillingManager.PurchaseEvent.ValidationFailed -> _purchaseState.value = PurchaseState.Error(event.reason)
                    is BillingManager.PurchaseEvent.PurchaseCancelled -> _purchaseState.value = PurchaseState.Cancelled
                    is BillingManager.PurchaseEvent.PurchaseError -> _purchaseState.value = PurchaseState.Error(event.message, event.code)
                    is BillingManager.PurchaseEvent.PurchasePending -> _purchaseState.value = PurchaseState.Pending
                }
            }
        }
    }

    fun loadAvailablePlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            billingManager.connectionState.collectLatest { state ->
                if (state is BillingManager.ConnectionState.Connected) {
                    _uiState.update { it.copy(availablePlans = purchaseHelper.getAvailablePlans(), isLoading = false) }
                    return@collectLatest
                }
            }
        }
    }

    fun setPaywallTrigger(trigger: PaywallTrigger) {
        _uiState.update { it.copy(trigger = trigger, upgradePrompt = FeatureConfig.getUpgradePrompt(trigger)) }
    }

    fun selectPlan(plan: SubscriptionPlan) { _uiState.update { it.copy(selectedPlan = plan) } }
    fun selectBillingPeriod(isYearly: Boolean) { _uiState.update { it.copy(isYearlySelected = isYearly) } }

    fun purchaseSelectedPlan(activity: Activity) {
        val state = _uiState.value
        val plan = state.selectedPlan ?: return
        viewModelScope.launch {
            _purchaseState.value = PurchaseState.Launching
            val result = when (plan) {
                SubscriptionPlan.PREMIUM -> if (state.isYearlySelected) purchaseHelper.purchasePremiumYearly(activity) else purchaseHelper.purchasePremiumMonthly(activity)
                SubscriptionPlan.FAMILY -> purchaseHelper.purchaseFamilyPlan(activity)
                else -> return@launch
            }
            when (result) {
                is PurchaseHelper.PurchaseResult.Cancelled -> _purchaseState.value = PurchaseState.Cancelled
                is PurchaseHelper.PurchaseResult.Error -> _purchaseState.value = PurchaseState.Error(result.message, result.code)
                is PurchaseHelper.PurchaseResult.NotConnected -> _purchaseState.value = PurchaseState.Error("No conectado a Google Play")
                is PurchaseHelper.PurchaseResult.ProductNotFound -> _purchaseState.value = PurchaseState.Error("Producto no disponible")
                else -> {}
            }
        }
    }

    fun subscribe(activity: Activity) = purchaseSelectedPlan(activity)

    fun restorePurchases() {
        viewModelScope.launch {
            _purchaseState.value = PurchaseState.Validating
            _purchaseState.value = if (purchaseHelper.restorePurchases()) PurchaseState.Idle
            else PurchaseState.Error("No se encontraron compras anteriores")
        }
    }

    fun getMonthlyPlan(): PlanInfo? = _uiState.value.availablePlans.find { it.productId == "premium_monthly" }

    fun calculateYearlySavings(): String? {
        val yearly = _uiState.value.availablePlans.find { it.productId == "premium_yearly" }
        val monthly = getMonthlyPlan()
        if (yearly == null || monthly == null) return null
        val percent = yearly.calculateYearlyDiscountPercent(monthly)
        return if (percent > 0) "Ahorra $percent%" else null
    }
}

data class PaywallUiState(
    val isLoading: Boolean = true,
    val currentPlan: SubscriptionPlan = SubscriptionPlan.FREE,
    val availablePlans: List<PlanInfo> = emptyList(),
    val selectedPlan: SubscriptionPlan? = null,
    val isYearlySelected: Boolean = false,
    val canStartTrial: Boolean = true,
    val trigger: PaywallTrigger = PaywallTrigger.MANUAL,
    val upgradePrompt: UpgradePrompt = UpgradePrompt("Mejora tu experiencia", "Desbloquea todas las funcionalidades premium", "Ver planes")
)

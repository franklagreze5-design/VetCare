package com.petapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petapp.data.Pet
import com.petapp.data.PetRepository
import com.petapp.data.Reminder
import com.petapp.data.ReminderRepository
import com.petapp.data.ReminderStatus
import com.petapp.data.UserSubscription
import com.petapp.subscription.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val pets: List<Pet> = emptyList(),
    val upcomingReminders: List<Reminder> = emptyList(),
    val subscription: UserSubscription = UserSubscription(),
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val petRepository: PetRepository = PetRepository(),
    private val reminderRepository: ReminderRepository = ReminderRepository(),
    private val subscriptionRepository: SubscriptionRepository = SubscriptionRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadPets()
        loadReminders()
        observeSubscription()
    }
    
    private fun loadPets() {
        viewModelScope.launch {
            petRepository.observePets().collectLatest { pets ->
                _uiState.update { it.copy(pets = pets, isLoading = false) }
            }
        }
    }
    
    private fun loadReminders() {
        viewModelScope.launch {
            reminderRepository.observePendingReminders().collectLatest { reminders ->
                // Filtrar solo los proximos (no vencidos y pendientes)
                val upcoming = reminders
                    .filter { it.status == ReminderStatus.PENDING.name }
                    .sortedBy { it.dueDate?.toDate()?.time ?: Long.MAX_VALUE }
                
                _uiState.update { it.copy(upcomingReminders = upcoming) }
            }
        }
    }
    
    private fun observeSubscription() {
        viewModelScope.launch {
            subscriptionRepository.observeSubscription().collectLatest { subscription ->
                subscription?.let {
                    _uiState.update { state -> 
                        state.copy(subscription = subscription) 
                    }
                }
            }
        }
    }
}

class HomeViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

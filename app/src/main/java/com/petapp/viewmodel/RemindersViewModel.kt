package com.petapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petapp.data.Pet
import com.petapp.data.PetRepository
import com.petapp.data.Reminder
import com.petapp.data.ReminderRepository
import com.petapp.data.UserSubscription
import com.petapp.features.FeatureAccessResult
import com.petapp.features.FeatureGate
import com.petapp.subscription.SubscriptionRepository
import com.petapp.subscription.UserLimitsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemindersUiState(
    val reminders: List<Reminder> = emptyList(),
    val pets: List<Pet> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val subscription: UserSubscription = UserSubscription(),
    val showAddDialog: Boolean = false,
    val showUpgradePrompt: Boolean = false,
    val editingReminder: Reminder? = null,
    val selectedFilter: ReminderFilter = ReminderFilter.ALL
)

enum class ReminderFilter(val displayName: String) {
    ALL("Todos"),
    PENDING("Pendientes"),
    COMPLETED("Completados"),
    OVERDUE("Vencidos")
}

class RemindersViewModel(
    private val reminderRepository: ReminderRepository = ReminderRepository(),
    private val petRepository: PetRepository = PetRepository(),
    private val subscriptionRepository: SubscriptionRepository = SubscriptionRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()
    
    init {
        loadReminders()
        loadPets()
        observeSubscription()
    }
    
    private fun loadReminders() {
        viewModelScope.launch {
            reminderRepository.observeReminders().collectLatest { reminders ->
                _uiState.update { 
                    it.copy(reminders = reminders, isLoading = false) 
                }
            }
        }
    }
    
    private fun loadPets() {
        viewModelScope.launch {
            petRepository.observePets().collectLatest { pets ->
                _uiState.update { it.copy(pets = pets) }
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
    
    fun onAddReminderClick() {
        viewModelScope.launch {
            val remindersThisMonth = reminderRepository.getRemindersThisMonthCount()
            val limits = UserLimitsData(remindersThisMonth = remindersThisMonth)
            val accessResult = FeatureGate.canAddReminder(_uiState.value.subscription, limits)
            
            when (accessResult) {
                is FeatureAccessResult.Allowed -> {
                    _uiState.update { it.copy(showAddDialog = true, editingReminder = null) }
                }
                is FeatureAccessResult.Blocked -> {
                    _uiState.update { it.copy(showUpgradePrompt = true) }
                }
            }
        }
    }
    
    fun onEditReminder(reminder: Reminder) {
        _uiState.update { it.copy(showAddDialog = true, editingReminder = reminder) }
    }
    
    fun onDismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingReminder = null) }
    }
    
    fun onDismissUpgradePrompt() {
        _uiState.update { it.copy(showUpgradePrompt = false) }
    }
    
    fun setFilter(filter: ReminderFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }
    
    fun getFilteredReminders(): List<Reminder> {
        val reminders = _uiState.value.reminders
        return when (_uiState.value.selectedFilter) {
            ReminderFilter.ALL -> reminders
            ReminderFilter.PENDING -> reminders.filter { 
                it.status == com.petapp.data.ReminderStatus.PENDING.name && !it.isOverdue() 
            }
            ReminderFilter.COMPLETED -> reminders.filter { 
                it.status == com.petapp.data.ReminderStatus.COMPLETED.name 
            }
            ReminderFilter.OVERDUE -> reminders.filter { it.isOverdue() }
        }
    }
    
    fun saveReminder(reminder: Reminder) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val result = if (reminder.id.isEmpty()) {
                reminderRepository.addReminder(reminder)
            } else {
                reminderRepository.updateReminder(reminder).map { reminder.id }
            }
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            showAddDialog = false, 
                            editingReminder = null,
                            isLoading = false,
                            error = null
                        ) 
                    }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = e.message ?: "Error al guardar"
                        ) 
                    }
                }
            )
        }
    }
    
    fun completeReminder(reminderId: String) {
        viewModelScope.launch {
            reminderRepository.completeReminder(reminderId)
        }
    }
    
    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            reminderRepository.deleteReminder(reminderId).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, error = null) }
                },
                onFailure = { e ->
                    _uiState.update { 
                        it.copy(isLoading = false, error = e.message ?: "Error al eliminar") 
                    }
                }
            )
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

class RemindersViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RemindersViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RemindersViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

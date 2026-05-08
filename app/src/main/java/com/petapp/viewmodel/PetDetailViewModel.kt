package com.petapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petapp.data.*
import com.petapp.features.FeatureAccessResult
import com.petapp.features.FeatureGate
import com.petapp.subscription.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetDetailUiState(
    val pet: Pet? = null,
    val medicalRecords: List<MedicalRecord> = emptyList(),
    val vaccines: List<VaccineRecord> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val subscription: UserSubscription = UserSubscription(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showUpgradePrompt: Boolean = false,
    val selectedTab: Int = 0
)

class PetDetailViewModel(
    private val petId: String,
    private val petRepository: PetRepository = PetRepository(),
    private val reminderRepository: ReminderRepository = ReminderRepository(),
    private val subscriptionRepository: SubscriptionRepository = SubscriptionRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PetDetailUiState())
    val uiState: StateFlow<PetDetailUiState> = _uiState.asStateFlow()
    
    init {
        loadPet()
        loadMedicalRecords()
        loadVaccines()
        loadReminders()
        observeSubscription()
    }
    
    private fun loadPet() {
        viewModelScope.launch {
            val pet = petRepository.getPetById(petId)
            _uiState.update { it.copy(pet = pet, isLoading = false) }
        }
    }
    
    private fun loadMedicalRecords() {
        viewModelScope.launch {
            petRepository.observeMedicalRecords(petId).collectLatest { records ->
                _uiState.update { it.copy(medicalRecords = records) }
            }
        }
    }
    
    private fun loadVaccines() {
        viewModelScope.launch {
            petRepository.observeVaccines(petId).collectLatest { vaccines ->
                _uiState.update { it.copy(vaccines = vaccines) }
            }
        }
    }
    
    private fun loadReminders() {
        viewModelScope.launch {
            reminderRepository.observeRemindersForPet(petId).collectLatest { reminders ->
                _uiState.update { it.copy(reminders = reminders) }
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
    
    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }
    
    fun canAccessMedicalHistory(): Boolean {
        val result = FeatureGate.canAccessFullMedicalHistory(_uiState.value.subscription)
        return result is FeatureAccessResult.Allowed
    }
    
    fun canAccessVaccines(): Boolean {
        val result = FeatureGate.canAccessVaccines(_uiState.value.subscription)
        return result is FeatureAccessResult.Allowed
    }
    
    fun onPremiumFeatureClick() {
        _uiState.update { it.copy(showUpgradePrompt = true) }
    }
    
    fun onDismissUpgradePrompt() {
        _uiState.update { it.copy(showUpgradePrompt = false) }
    }
    
    fun addMedicalRecord(record: MedicalRecord) {
        viewModelScope.launch {
            petRepository.addMedicalRecord(petId, record)
        }
    }
    
    fun addVaccine(vaccine: VaccineRecord) {
        viewModelScope.launch {
            petRepository.addVaccine(petId, vaccine)
        }
    }
}

class PetDetailViewModelFactory(
    private val petId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PetDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PetDetailViewModel(petId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

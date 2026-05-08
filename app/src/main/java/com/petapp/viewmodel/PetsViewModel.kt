package com.petapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petapp.data.Pet
import com.petapp.data.PetRepository
import com.petapp.data.UserSubscription
import com.petapp.features.FeatureAccessResult
import com.petapp.features.FeatureGate
import com.petapp.subscription.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetsUiState(
    val pets: List<Pet> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val subscription: UserSubscription = UserSubscription(),
    val showAddPetDialog: Boolean = false,
    val showUpgradePrompt: Boolean = false,
    val editingPet: Pet? = null
)

class PetsViewModel(
    private val petRepository: PetRepository = PetRepository(),
    private val subscriptionRepository: SubscriptionRepository = SubscriptionRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PetsUiState())
    val uiState: StateFlow<PetsUiState> = _uiState.asStateFlow()
    
    init {
        loadPets()
        observeSubscription()
    }
    
    private fun loadPets() {
        viewModelScope.launch {
            petRepository.observePets().collectLatest { pets ->
                _uiState.update { 
                    it.copy(pets = pets, isLoading = false) 
                }
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
    
    fun onAddPetClick() {
        val state = _uiState.value
        val currentPetsCount = state.pets.size
        
        // Verificar si puede agregar mas mascotas
        val accessResult = FeatureGate.canAddPet(state.subscription, currentPetsCount)
        
        when (accessResult) {
            is FeatureAccessResult.Allowed -> {
                _uiState.update { it.copy(showAddPetDialog = true, editingPet = null) }
            }
            is FeatureAccessResult.Blocked -> {
                _uiState.update { it.copy(showUpgradePrompt = true) }
            }
        }
    }
    
    fun onEditPet(pet: Pet) {
        _uiState.update { it.copy(showAddPetDialog = true, editingPet = pet) }
    }
    
    fun onDismissDialog() {
        _uiState.update { it.copy(showAddPetDialog = false, editingPet = null) }
    }
    
    fun onDismissUpgradePrompt() {
        _uiState.update { it.copy(showUpgradePrompt = false) }
    }
    
    fun savePet(pet: Pet) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val result = if (pet.id.isEmpty()) {
                petRepository.addPet(pet)
            } else {
                petRepository.updatePet(pet).map { pet.id }
            }
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            showAddPetDialog = false, 
                            editingPet = null,
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
    
    fun deletePet(petId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            petRepository.deletePet(petId).fold(
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

class PetsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PetsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PetsViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

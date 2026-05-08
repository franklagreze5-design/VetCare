package com.petapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petapp.ads.AdBanner
import com.petapp.data.Pet
import com.petapp.data.PetGender
import com.petapp.data.PetType
import com.petapp.viewmodel.PetsViewModel
import com.petapp.viewmodel.PetsViewModelFactory
import com.google.firebase.Timestamp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetsScreen(
    onNavigateToPaywall: () -> Unit,
    onNavigateToPetDetail: (String) -> Unit = {},
    viewModel: PetsViewModel = viewModel(factory = PetsViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Manejar prompt de upgrade
    LaunchedEffect(uiState.showUpgradePrompt) {
        if (uiState.showUpgradePrompt) {
            onNavigateToPaywall()
            viewModel.onDismissUpgradePrompt()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Mascotas", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToPaywall) {
                        Icon(Icons.Default.Star, contentDescription = "Premium")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onAddPetClick() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar mascota")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Banner de ads para usuarios FREE
            AdBanner(
                subscription = uiState.subscription,
                modifier = Modifier.fillMaxWidth()
            )
            
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.pets.isEmpty() -> {
                    EmptyPetsState()
                }
                else -> {
                    PetsList(
                        pets = uiState.pets,
                        onPetClick = { pet -> onNavigateToPetDetail(pet.id) },
                        onEditPet = { pet -> viewModel.onEditPet(pet) },
                        onDeletePet = { pet -> viewModel.deletePet(pet.id) }
                    )
                }
            }
        }
    }
    
    // Dialog para agregar/editar mascota
    if (uiState.showAddPetDialog) {
        AddEditPetDialog(
            pet = uiState.editingPet,
            onDismiss = { viewModel.onDismissDialog() },
            onSave = { pet -> viewModel.savePet(pet) }
        )
    }
    
    // Mostrar error si hay
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Auto-dismiss after showing
            viewModel.clearError()
        }
    }
}

@Composable
private fun EmptyPetsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Pets,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tienes mascotas aun",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Agrega tu primera mascota con el boton +",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PetsList(
    pets: List<Pet>,
    onPetClick: (Pet) -> Unit,
    onEditPet: (Pet) -> Unit,
    onDeletePet: (Pet) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pets, key = { it.id }) { pet ->
            PetCard(
                pet = pet,
                onClick = { onPetClick(pet) },
                onEdit = { onEditPet(pet) },
                onDelete = { onDeletePet(pet) }
            )
        }
    }
}

@Composable
private fun PetCard(
    pet: Pet,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar de la mascota
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(getPetTypeColor(pet.getPetType())),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getPetTypeIcon(pet.getPetType()),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pet.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pet.getPetType().displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pet.breed.isNotEmpty()) {
                        Text(
                            text = " - ${pet.breed}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = pet.getAgeText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.Delete, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            ) 
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditPetDialog(
    pet: Pet?,
    onDismiss: () -> Unit,
    onSave: (Pet) -> Unit
) {
    var name by remember { mutableStateOf(pet?.name ?: "") }
    var selectedType by remember { mutableStateOf(pet?.getPetType() ?: PetType.DOG) }
    var breed by remember { mutableStateOf(pet?.breed ?: "") }
    var selectedGender by remember { mutableStateOf(pet?.getPetGender() ?: PetGender.UNKNOWN) }
    var weight by remember { mutableStateOf(pet?.weight?.toString() ?: "") }
    var notes by remember { mutableStateOf(pet?.notes ?: "") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var genderMenuExpanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (pet == null) "Agregar Mascota" else "Editar Mascota",
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Tipo de mascota
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = !typeMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        PetType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = breed,
                    onValueChange = { breed = it },
                    label = { Text("Raza (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Genero
                ExposedDropdownMenuBox(
                    expanded = genderMenuExpanded,
                    onExpandedChange = { genderMenuExpanded = !genderMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedGender.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Genero") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = genderMenuExpanded,
                        onDismissRequest = { genderMenuExpanded = false }
                    ) {
                        PetGender.entries.forEach { gender ->
                            DropdownMenuItem(
                                text = { Text(gender.displayName) },
                                onClick = {
                                    selectedGender = gender
                                    genderMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Peso (kg)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newPet = Pet(
                        id = pet?.id ?: "",
                        name = name.trim(),
                        type = selectedType.name,
                        breed = breed.trim(),
                        gender = selectedGender.name,
                        weight = weight.toDoubleOrNull() ?: 0.0,
                        notes = notes.trim(),
                        birthDate = pet?.birthDate,
                        createdAt = pet?.createdAt,
                        updatedAt = pet?.updatedAt
                    )
                    onSave(newPet)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun getPetTypeIcon(type: PetType) = when (type) {
    PetType.DOG -> Icons.Default.Pets
    PetType.CAT -> Icons.Default.Pets
    PetType.BIRD -> Icons.Default.Air
    PetType.RABBIT -> Icons.Default.Pets
    PetType.HAMSTER -> Icons.Default.Pets
    PetType.FISH -> Icons.Default.Water
    PetType.TURTLE -> Icons.Default.Pets
    PetType.OTHER -> Icons.Default.Pets
}

private fun getPetTypeColor(type: PetType) = when (type) {
    PetType.DOG -> Color(0xFF8B4513)
    PetType.CAT -> Color(0xFFFF8C00)
    PetType.BIRD -> Color(0xFF1E90FF)
    PetType.RABBIT -> Color(0xFFDDA0DD)
    PetType.HAMSTER -> Color(0xFFD2691E)
    PetType.FISH -> Color(0xFF00CED1)
    PetType.TURTLE -> Color(0xFF228B22)
    PetType.OTHER -> Color(0xFF808080)
}

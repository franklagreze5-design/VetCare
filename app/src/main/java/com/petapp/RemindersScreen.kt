package com.petapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petapp.ads.AdBanner
import com.petapp.data.Pet
import com.petapp.data.Reminder
import com.petapp.data.ReminderFrequency
import com.petapp.data.ReminderStatus
import com.petapp.data.ReminderType
import com.petapp.viewmodel.ReminderFilter
import com.petapp.viewmodel.RemindersViewModel
import com.petapp.viewmodel.RemindersViewModelFactory
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onNavigateToPaywall: () -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredReminders = viewModel.getFilteredReminders()
    
    LaunchedEffect(uiState.showUpgradePrompt) {
        if (uiState.showUpgradePrompt) {
            onNavigateToPaywall()
            viewModel.onDismissUpgradePrompt()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordatorios", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToPaywall) {
                        Icon(Icons.Default.Star, contentDescription = "Premium")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onAddReminderClick() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar recordatorio")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AdBanner(
                subscription = uiState.subscription,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Filtros
            FilterChips(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.setFilter(it) }
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
                filteredReminders.isEmpty() -> {
                    EmptyRemindersState(filter = uiState.selectedFilter)
                }
                else -> {
                    RemindersList(
                        reminders = filteredReminders,
                        onComplete = { viewModel.completeReminder(it.id) },
                        onEdit = { viewModel.onEditReminder(it) },
                        onDelete = { viewModel.deleteReminder(it.id) }
                    )
                }
            }
        }
    }
    
    if (uiState.showAddDialog) {
        AddEditReminderDialog(
            reminder = uiState.editingReminder,
            pets = uiState.pets,
            onDismiss = { viewModel.onDismissDialog() },
            onSave = { reminder -> viewModel.saveReminder(reminder) }
        )
    }
}

@Composable
private fun FilterChips(
    selectedFilter: ReminderFilter,
    onFilterSelected: (ReminderFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ReminderFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.displayName) }
            )
        }
    }
}

@Composable
private fun EmptyRemindersState(filter: ReminderFilter) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when (filter) {
                ReminderFilter.ALL -> "Sin recordatorios aun"
                ReminderFilter.PENDING -> "Sin recordatorios pendientes"
                ReminderFilter.COMPLETED -> "Sin recordatorios completados"
                ReminderFilter.OVERDUE -> "Sin recordatorios vencidos"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Crea recordatorios para vacunas, citas y mas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RemindersList(
    reminders: List<Reminder>,
    onComplete: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reminders, key = { it.id }) { reminder ->
            ReminderCard(
                reminder = reminder,
                onComplete = { onComplete(reminder) },
                onEdit = { onEdit(reminder) },
                onDelete = { onDelete(reminder) }
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = reminder.status == ReminderStatus.COMPLETED.name
    val isOverdue = reminder.isOverdue()
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isOverdue -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox para completar
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { if (!isCompleted) onComplete() },
                enabled = !isCompleted
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Icono del tipo
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(getReminderTypeColor(reminder.getReminderType())),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getReminderTypeIcon(reminder.getReminderType()),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                )
                
                if (reminder.petName.isNotEmpty()) {
                    Text(
                        text = reminder.petName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isOverdue) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reminder.getRelativeDateText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (!isCompleted) {
                        DropdownMenuItem(
                            text = { Text("Completar") },
                            onClick = {
                                showMenu = false
                                onComplete()
                            },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                        )
                    }
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
private fun AddEditReminderDialog(
    reminder: Reminder?,
    pets: List<Pet>,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    var title by remember { mutableStateOf(reminder?.title ?: "") }
    var description by remember { mutableStateOf(reminder?.description ?: "") }
    var selectedType by remember { mutableStateOf(reminder?.getReminderType() ?: ReminderType.OTHER) }
    var selectedPet by remember { mutableStateOf<Pet?>(pets.find { it.id == reminder?.petId }) }
    var selectedFrequency by remember { mutableStateOf(reminder?.getReminderFrequency() ?: ReminderFrequency.ONCE) }
    
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var petMenuExpanded by remember { mutableStateOf(false) }
    var frequencyMenuExpanded by remember { mutableStateOf(false) }
    
    // Date picker state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = reminder?.dueDate?.toDate()?.time ?: System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (reminder == null) "Nuevo Recordatorio" else "Editar Recordatorio",
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titulo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Tipo de recordatorio
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
                        ReminderType.entries.forEach { type ->
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
                
                // Mascota (opcional)
                if (pets.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = petMenuExpanded,
                        onExpandedChange = { petMenuExpanded = !petMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedPet?.name ?: "Sin mascota",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Mascota") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = petMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = petMenuExpanded,
                            onDismissRequest = { petMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sin mascota") },
                                onClick = {
                                    selectedPet = null
                                    petMenuExpanded = false
                                }
                            )
                            pets.forEach { pet ->
                                DropdownMenuItem(
                                    text = { Text(pet.name) },
                                    onClick = {
                                        selectedPet = pet
                                        petMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Fecha
                OutlinedTextField(
                    value = datePickerState.selectedDateMillis?.let { 
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("es"))
                            .format(Date(it))
                    } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )
                
                // Frecuencia
                ExposedDropdownMenuBox(
                    expanded = frequencyMenuExpanded,
                    onExpandedChange = { frequencyMenuExpanded = !frequencyMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedFrequency.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repetir") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = frequencyMenuExpanded,
                        onDismissRequest = { frequencyMenuExpanded = false }
                    ) {
                        ReminderFrequency.entries.forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq.displayName) },
                                onClick = {
                                    selectedFrequency = freq
                                    frequencyMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
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
                    val selectedDate = datePickerState.selectedDateMillis?.let {
                        Timestamp(Date(it))
                    }
                    
                    val newReminder = Reminder(
                        id = reminder?.id ?: "",
                        title = title.trim(),
                        description = description.trim(),
                        type = selectedType.name,
                        petId = selectedPet?.id ?: "",
                        petName = selectedPet?.name ?: "",
                        dueDate = selectedDate,
                        frequency = selectedFrequency.name,
                        status = reminder?.status ?: ReminderStatus.PENDING.name,
                        createdAt = reminder?.createdAt
                    )
                    onSave(newReminder)
                },
                enabled = title.isNotBlank()
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
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun getReminderTypeIcon(type: ReminderType) = when (type) {
    ReminderType.VACCINE -> Icons.Default.Vaccines
    ReminderType.MEDICINE -> Icons.Default.Medication
    ReminderType.VET_APPOINTMENT -> Icons.Default.CalendarMonth
    ReminderType.GROOMING -> Icons.Default.Shower
    ReminderType.DEWORMING -> Icons.Default.BugReport
    ReminderType.FOOD -> Icons.Default.Restaurant
    ReminderType.OTHER -> Icons.Default.Notifications
}

private fun getReminderTypeColor(type: ReminderType) = when (type) {
    ReminderType.VACCINE -> Color(0xFF2196F3)
    ReminderType.MEDICINE -> Color(0xFF9C27B0)
    ReminderType.VET_APPOINTMENT -> Color(0xFF4CAF50)
    ReminderType.GROOMING -> Color(0xFF00BCD4)
    ReminderType.DEWORMING -> Color(0xFFFF9800)
    ReminderType.FOOD -> Color(0xFFE91E63)
    ReminderType.OTHER -> Color(0xFF607D8B)
}

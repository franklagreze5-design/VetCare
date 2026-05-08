package com.petapp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Tipos de recordatorio
 */
enum class ReminderType(val displayName: String, val icon: String) {
    VACCINE("Vacuna", "vaccine"),
    MEDICINE("Medicamento", "medication"),
    VET_APPOINTMENT("Cita veterinaria", "calendar"),
    GROOMING("Grooming/Bano", "shower"),
    DEWORMING("Desparasitacion", "bug"),
    FOOD("Comida", "restaurant"),
    OTHER("Otro", "notifications")
}

/**
 * Estado del recordatorio
 */
enum class ReminderStatus {
    PENDING,
    COMPLETED,
    OVERDUE,
    CANCELLED
}

/**
 * Frecuencia de repeticion
 */
enum class ReminderFrequency(val displayName: String) {
    ONCE("Una vez"),
    DAILY("Diario"),
    WEEKLY("Semanal"),
    BIWEEKLY("Cada 2 semanas"),
    MONTHLY("Mensual"),
    QUARTERLY("Trimestral"),
    YEARLY("Anual")
}

/**
 * Modelo de recordatorio almacenado en Firestore
 * 
 * Estructura: users/{userId}/reminders/{reminderId}
 */
data class Reminder(
    @DocumentId
    var id: String = "",
    
    @get:PropertyName("title")
    @set:PropertyName("title")
    var title: String = "",
    
    @get:PropertyName("description")
    @set:PropertyName("description")
    var description: String = "",
    
    @get:PropertyName("type")
    @set:PropertyName("type")
    var type: String = ReminderType.OTHER.name,
    
    @get:PropertyName("petId")
    @set:PropertyName("petId")
    var petId: String = "",
    
    @get:PropertyName("petName")
    @set:PropertyName("petName")
    var petName: String = "",
    
    @get:PropertyName("dueDate")
    @set:PropertyName("dueDate")
    var dueDate: Timestamp? = null,
    
    @get:PropertyName("frequency")
    @set:PropertyName("frequency")
    var frequency: String = ReminderFrequency.ONCE.name,
    
    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = ReminderStatus.PENDING.name,
    
    @get:PropertyName("notifyBefore")
    @set:PropertyName("notifyBefore")
    var notifyBefore: Int = 1, // Dias antes para notificar
    
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Timestamp? = null,
    
    @get:PropertyName("completedAt")
    @set:PropertyName("completedAt")
    var completedAt: Timestamp? = null
) {
    constructor() : this(id = "")
    
    fun getReminderType(): ReminderType {
        return try {
            ReminderType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            ReminderType.OTHER
        }
    }
    
    fun getReminderStatus(): ReminderStatus {
        return try {
            ReminderStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            ReminderStatus.PENDING
        }
    }
    
    fun getReminderFrequency(): ReminderFrequency {
        return try {
            ReminderFrequency.valueOf(frequency)
        } catch (e: IllegalArgumentException) {
            ReminderFrequency.ONCE
        }
    }
    
    /**
     * Verifica si el recordatorio esta vencido
     */
    fun isOverdue(): Boolean {
        val due = dueDate?.toDate() ?: return false
        return System.currentTimeMillis() > due.time && status == ReminderStatus.PENDING.name
    }
    
    /**
     * Obtiene texto de fecha formateada
     */
    fun getDueDateText(): String {
        val due = dueDate?.toDate() ?: return "Sin fecha"
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("es"))
        return formatter.format(due)
    }
    
    /**
     * Obtiene texto relativo de la fecha
     */
    fun getRelativeDateText(): String {
        val due = dueDate?.toDate() ?: return "Sin fecha"
        val now = System.currentTimeMillis()
        val dueTime = due.time
        val diffDays = ((dueTime - now) / (1000 * 60 * 60 * 24)).toInt()
        
        return when {
            diffDays < 0 -> "Vencido hace ${-diffDays} dias"
            diffDays == 0 -> "Hoy"
            diffDays == 1 -> "Manana"
            diffDays < 7 -> "En $diffDays dias"
            diffDays < 30 -> "En ${diffDays / 7} semanas"
            else -> getDueDateText()
        }
    }
}

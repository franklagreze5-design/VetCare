package com.petapp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Tipo de mascota
 */
enum class PetType(val displayName: String, val icon: String) {
    DOG("Perro", "dog"),
    CAT("Gato", "cat"),
    BIRD("Ave", "bird"),
    RABBIT("Conejo", "rabbit"),
    HAMSTER("Hamster", "hamster"),
    FISH("Pez", "fish"),
    TURTLE("Tortuga", "turtle"),
    OTHER("Otro", "pets")
}

/**
 * Genero de la mascota
 */
enum class PetGender(val displayName: String) {
    MALE("Macho"),
    FEMALE("Hembra"),
    UNKNOWN("No especificado")
}

/**
 * Modelo de mascota almacenado en Firestore
 * 
 * Estructura: users/{userId}/pets/{petId}
 */
data class Pet(
    @DocumentId
    var id: String = "",
    
    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",
    
    @get:PropertyName("type")
    @set:PropertyName("type")
    var type: String = PetType.DOG.name,
    
    @get:PropertyName("breed")
    @set:PropertyName("breed")
    var breed: String = "",
    
    @get:PropertyName("gender")
    @set:PropertyName("gender")
    var gender: String = PetGender.UNKNOWN.name,
    
    @get:PropertyName("birthDate")
    @set:PropertyName("birthDate")
    var birthDate: Timestamp? = null,
    
    @get:PropertyName("weight")
    @set:PropertyName("weight")
    var weight: Double = 0.0,
    
    @get:PropertyName("photoUrl")
    @set:PropertyName("photoUrl")
    var photoUrl: String? = null,
    
    @get:PropertyName("microchipId")
    @set:PropertyName("microchipId")
    var microchipId: String? = null,
    
    @get:PropertyName("notes")
    @set:PropertyName("notes")
    var notes: String = "",
    
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Timestamp? = null,
    
    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    var updatedAt: Timestamp? = null
) {
    constructor() : this(id = "")
    
    fun getPetType(): PetType {
        return try {
            PetType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            PetType.OTHER
        }
    }
    
    fun getPetGender(): PetGender {
        return try {
            PetGender.valueOf(gender)
        } catch (e: IllegalArgumentException) {
            PetGender.UNKNOWN
        }
    }
    
    /**
     * Calcula la edad en texto legible
     */
    fun getAgeText(): String {
        val birth = birthDate?.toDate() ?: return "Edad desconocida"
        val now = java.util.Date()
        val diffMs = now.time - birth.time
        val days = (diffMs / (1000 * 60 * 60 * 24)).toInt()
        
        return when {
            days < 30 -> "$days dias"
            days < 365 -> "${days / 30} meses"
            else -> {
                val years = days / 365
                val months = (days % 365) / 30
                if (months > 0) "$years años y $months meses" else "$years años"
            }
        }
    }
}

/**
 * Registro de vacuna
 */
data class VaccineRecord(
    @DocumentId
    var id: String = "",
    
    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",
    
    @get:PropertyName("dateApplied")
    @set:PropertyName("dateApplied")
    var dateApplied: Timestamp? = null,
    
    @get:PropertyName("nextDueDate")
    @set:PropertyName("nextDueDate")
    var nextDueDate: Timestamp? = null,
    
    @get:PropertyName("veterinarian")
    @set:PropertyName("veterinarian")
    var veterinarian: String = "",
    
    @get:PropertyName("notes")
    @set:PropertyName("notes")
    var notes: String = ""
) {
    constructor() : this(id = "")
}

/**
 * Registro medico
 */
data class MedicalRecord(
    @DocumentId
    var id: String = "",
    
    @get:PropertyName("title")
    @set:PropertyName("title")
    var title: String = "",
    
    @get:PropertyName("description")
    @set:PropertyName("description")
    var description: String = "",
    
    @get:PropertyName("date")
    @set:PropertyName("date")
    var date: Timestamp? = null,
    
    @get:PropertyName("veterinarian")
    @set:PropertyName("veterinarian")
    var veterinarian: String = "",
    
    @get:PropertyName("diagnosis")
    @set:PropertyName("diagnosis")
    var diagnosis: String = "",
    
    @get:PropertyName("treatment")
    @set:PropertyName("treatment")
    var treatment: String = "",
    
    @get:PropertyName("attachmentUrls")
    @set:PropertyName("attachmentUrls")
    var attachmentUrls: List<String> = emptyList()
) {
    constructor() : this(id = "")
}

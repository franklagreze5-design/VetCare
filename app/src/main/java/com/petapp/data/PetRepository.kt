package com.petapp.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio para gestionar mascotas en Firestore
 */
class PetRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    private val userId: String?
        get() = auth.currentUser?.uid
    
    private fun petsCollection() = userId?.let {
        firestore.collection("users").document(it).collection("pets")
    }
    
    /**
     * Observa la lista de mascotas en tiempo real
     */
    fun observePets(): Flow<List<Pet>> = callbackFlow {
        val collection = petsCollection()
        
        if (collection == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = collection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val pets = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Pet::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(pets)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Obtiene una mascota por ID
     */
    suspend fun getPetById(petId: String): Pet? {
        val collection = petsCollection() ?: return null
        
        return try {
            val doc = collection.document(petId).get().await()
            doc.toObject(Pet::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Obtiene el conteo de mascotas
     */
    suspend fun getPetsCount(): Int {
        val collection = petsCollection() ?: return 0
        
        return try {
            val snapshot = collection.get().await()
            snapshot.size()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Agrega una nueva mascota
     */
    suspend fun addPet(pet: Pet): Result<String> {
        val collection = petsCollection() 
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            val petData = pet.copy(
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            
            val docRef = collection.add(petData).await()
            
            // Actualizar contador de mascotas
            updatePetsCount(1)
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza una mascota existente
     */
    suspend fun updatePet(pet: Pet): Result<Unit> {
        val collection = petsCollection() 
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            val petData = pet.copy(updatedAt = Timestamp.now())
            collection.document(pet.id).set(petData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Elimina una mascota
     */
    suspend fun deletePet(petId: String): Result<Unit> {
        val collection = petsCollection() 
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            collection.document(petId).delete().await()
            updatePetsCount(-1)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza el contador de mascotas en el documento del usuario
     */
    private suspend fun updatePetsCount(delta: Int) {
        val uid = userId ?: return
        
        try {
            firestore.runTransaction { transaction ->
                val userRef = firestore.collection("users").document(uid)
                val snapshot = transaction.get(userRef)
                val currentCount = snapshot.getLong("petsCount")?.toInt() ?: 0
                val newCount = maxOf(0, currentCount + delta)
                transaction.update(userRef, "petsCount", newCount)
            }.await()
        } catch (e: Exception) {
            // Silently fail - the count will be eventually consistent
        }
    }
    
    // ============================================
    // REGISTROS MEDICOS (funciones premium)
    // ============================================
    
    private fun medicalRecordsCollection(petId: String) = userId?.let {
        firestore.collection("users").document(it)
            .collection("pets").document(petId)
            .collection("medicalRecords")
    }
    
    /**
     * Observa los registros medicos de una mascota
     */
    fun observeMedicalRecords(petId: String): Flow<List<MedicalRecord>> = callbackFlow {
        val collection = medicalRecordsCollection(petId)
        
        if (collection == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = collection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(MedicalRecord::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(records)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Agrega un registro medico
     */
    suspend fun addMedicalRecord(petId: String, record: MedicalRecord): Result<String> {
        val collection = medicalRecordsCollection(petId)
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            val docRef = collection.add(record).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // VACUNAS (funciones premium)
    // ============================================
    
    private fun vaccinesCollection(petId: String) = userId?.let {
        firestore.collection("users").document(it)
            .collection("pets").document(petId)
            .collection("vaccines")
    }
    
    /**
     * Observa las vacunas de una mascota
     */
    fun observeVaccines(petId: String): Flow<List<VaccineRecord>> = callbackFlow {
        val collection = vaccinesCollection(petId)
        
        if (collection == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = collection
            .orderBy("dateApplied", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val vaccines = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(VaccineRecord::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(vaccines)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Agrega una vacuna
     */
    suspend fun addVaccine(petId: String, vaccine: VaccineRecord): Result<String> {
        val collection = vaccinesCollection(petId)
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            val docRef = collection.add(vaccine).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

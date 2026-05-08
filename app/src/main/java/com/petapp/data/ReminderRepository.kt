package com.petapp.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Repositorio para gestionar recordatorios en Firestore
 */
class ReminderRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    private val userId: String?
        get() = auth.currentUser?.uid
    
    private fun remindersCollection() = userId?.let {
        firestore.collection("users").document(it).collection("reminders")
    }
    
    /**
     * Observa todos los recordatorios en tiempo real
     */
    fun observeReminders(): Flow<List<Reminder>> = callbackFlow {
        val collection = remindersCollection()
        
        if (collection == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = collection
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val reminders = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Reminder::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(reminders)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Observa recordatorios pendientes
     */
    fun observePendingReminders(): Flow<List<Reminder>> = callbackFlow {
        val collection = remindersCollection()
        
        if (collection == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = collection
            .whereEqualTo("status", ReminderStatus.PENDING.name)
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val reminders = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Reminder::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(reminders)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Obtiene el conteo de recordatorios creados este mes
     */
    suspend fun getRemindersThisMonthCount(): Int {
        val collection = remindersCollection() ?: return 0
        
        return try {
            // Obtener inicio del mes actual
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val startOfMonth = Timestamp(calendar.time)
            
            val snapshot = collection
                .whereGreaterThanOrEqualTo("createdAt", startOfMonth)
                .get()
                .await()
            
            snapshot.size()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Agrega un nuevo recordatorio
     */
    suspend fun addReminder(reminder: Reminder): Result<String> {
        val collection = remindersCollection()
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            val reminderData = reminder.copy(
                createdAt = Timestamp.now()
            )
            
            val docRef = collection.add(reminderData).await()
            
            // Actualizar contador de recordatorios del mes
            updateRemindersCount(1)
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza un recordatorio existente
     */
    suspend fun updateReminder(reminder: Reminder): Result<Unit> {
        val collection = remindersCollection()
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            collection.document(reminder.id).set(reminder).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Marca un recordatorio como completado
     */
    suspend fun completeReminder(reminderId: String): Result<Unit> {
        val collection = remindersCollection()
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            collection.document(reminderId).update(
                mapOf(
                    "status" to ReminderStatus.COMPLETED.name,
                    "completedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Elimina un recordatorio
     */
    suspend fun deleteReminder(reminderId: String): Result<Unit> {
        val collection = remindersCollection()
            ?: return Result.failure(Exception("Usuario no autenticado"))
        
        return try {
            collection.document(reminderId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza el contador de recordatorios del mes en el documento del usuario
     */
    private suspend fun updateRemindersCount(delta: Int) {
        val uid = userId ?: return
        
        try {
            firestore.runTransaction { transaction ->
                val userRef = firestore.collection("users").document(uid)
                val snapshot = transaction.get(userRef)
                val currentCount = snapshot.getLong("remindersThisMonth")?.toInt() ?: 0
                val newCount = maxOf(0, currentCount + delta)
                transaction.update(userRef, "remindersThisMonth", newCount)
            }.await()
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    /**
     * Obtiene recordatorios para una mascota especifica
     */
    fun observeRemindersForPet(petId: String): Flow<List<Reminder>> = callbackFlow {
        val collection = remindersCollection()
        
        if (collection == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = collection
            .whereEqualTo("petId", petId)
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val reminders = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Reminder::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(reminders)
            }
        
        awaitClose { listener.remove() }
    }
}

package com.petapp.subscription

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.petapp.billing.BillingManager
import com.petapp.data.UserSubscription
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Datos de límites del usuario
 */
data class UserLimitsData(
    val petsCount: Int = 0,
    val remindersThisMonth: Int = 0
)

class SubscriptionRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val billingManager: BillingManager
) {

    /**
     * Observa la suscripción del usuario en tiempo real desde Firestore
     */
    fun observeSubscription(): Flow<UserSubscription?> = callbackFlow {
        val userId = auth.currentUser?.uid ?: run {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = firestore
            .collection("users")
            .document(userId)
            .collection("subscription")
            .document("current")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val subscription = snapshot?.toObject(UserSubscription::class.java)
                trySend(subscription ?: UserSubscription())
            }

        awaitClose { listener.remove() }
    }

    /**
     * Obtiene la suscripción actual una sola vez
     */
    suspend fun getCurrentSubscription(): UserSubscription {
        val userId = auth.currentUser?.uid ?: return UserSubscription()
        return try {
            val doc = firestore
                .collection("users")
                .document(userId)
                .collection("subscription")
                .document("current")
                .get()
                .await()
            doc.toObject(UserSubscription::class.java) ?: UserSubscription()
        } catch (e: Exception) {
            UserSubscription()
        }
    }

    /**
     * Obtiene los límites de uso del usuario
     */
    suspend fun getUserLimits(): UserLimitsData {
        val userId = auth.currentUser?.uid ?: return UserLimitsData()
        return try {
            val doc = firestore
                .collection("users")
                .document(userId)
                .get()
                .await()
            UserLimitsData(
                petsCount = doc.getLong("petsCount")?.toInt() ?: 0,
                remindersThisMonth = doc.getLong("remindersThisMonth")?.toInt() ?: 0
            )
        } catch (e: Exception) {
            UserLimitsData()
        }
    }

    /**
     * Valida una compra con el backend (Cloud Function)
     */
    suspend fun validatePurchase(purchaseToken: String, productId: String): Boolean {
        return try {
            val data = hashMapOf(
                "purchaseToken" to purchaseToken,
                "productId" to productId
            )
            functions
                .getHttpsCallable("validatePurchase")
                .call(data)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}

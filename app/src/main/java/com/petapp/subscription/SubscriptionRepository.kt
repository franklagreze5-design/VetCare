package com.petapp.subscription

import com.android.billingclient.api.Purchase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.petapp.billing.SubscriptionPlan
import com.petapp.billing.ValidationResult
import com.petapp.data.UserSubscription
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Datos de limites del usuario
 */
data class UserLimitsData(
    val petsCount: Int = 0,
    val remindersThisMonth: Int = 0
)

class SubscriptionRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {

    /**
     * Observa la suscripcion del usuario en tiempo real desde Firestore
     */
    fun observeSubscription(): Flow<UserSubscription?> = callbackFlow {
        val userId = auth.currentUser?.uid ?: run {
            trySend(UserSubscription()) // Return free subscription for non-authenticated users
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
                    trySend(UserSubscription())
                    return@addSnapshotListener
                }
                val subscription = snapshot?.toObject(UserSubscription::class.java)
                trySend(subscription ?: UserSubscription())
            }

        awaitClose { listener.remove() }
    }

    /**
     * Obtiene la suscripcion actual una sola vez
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
     * Obtiene los limites de uso del usuario
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

    /**
     * Valida una compra con el backend usando el objeto Purchase
     */
    suspend fun validatePurchaseWithBackend(purchase: Purchase): ValidationResult {
        return try {
            val productId = purchase.products.firstOrNull() ?: return ValidationResult.Invalid("No product ID")
            val success = validatePurchase(purchase.purchaseToken, productId)
            
            if (success) {
                val plan = SubscriptionPlan.fromProductId(productId)
                ValidationResult.Valid(
                    plan = plan,
                    expiryTime = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000), // 30 days
                    isInTrial = false
                )
            } else {
                ValidationResult.Invalid("Validation failed")
            }
        } catch (e: Exception) {
            ValidationResult.Error(e)
        }
    }
}

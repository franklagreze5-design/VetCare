package com.petapp.subscription

import android.util.Log
import com.android.billingclient.api.Purchase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.petapp.billing.SubscriptionPlan
import com.petapp.billing.SubscriptionStatus
import com.petapp.billing.ValidationResult
import com.petapp.data.UserSubscription
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class SubscriptionRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    companion object {
        private const val TAG = "SubscriptionRepo"
        private const val USERS_COLLECTION = "users"
    }

    fun observeSubscription(): Flow<UserSubscription?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) { trySend(null); close(); return@callbackFlow }

        val reg: ListenerRegistration = firestore.collection(USERS_COLLECTION).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.e(TAG, "Error", error); trySend(null); return@addSnapshotListener }
                trySend(if (snapshot != null && snapshot.exists())
                    parseSubscription(snapshot.get("subscription") as? Map<*, *>)
                else UserSubscription())
            }
        awaitClose { reg.remove() }
    }

    suspend fun getCurrentSubscription(): UserSubscription {
        val userId = auth.currentUser?.uid ?: return UserSubscription()
        return try {
            val snap = firestore.collection(USERS_COLLECTION).document(userId).get().await()
            if (snap.exists()) parseSubscription(snap.get("subscription") as? Map<*, *>) ?: UserSubscription()
            else UserSubscription()
        } catch (e: Exception) { Log.e(TAG, "Error", e); UserSubscription() }
    }

    suspend fun validatePurchaseWithBackend(purchase: Purchase): ValidationResult {
        val userId = auth.currentUser?.uid ?: return ValidationResult.Error(Exception("No autenticado"))
        val productId = purchase.products.firstOrNull() ?: return ValidationResult.Error(Exception("Sin producto"))
        return try {
            val data = hashMapOf("userId" to userId, "productId" to productId, "purchaseToken" to purchase.purchaseToken)
            val result = functions.getHttpsCallable("validatePurchase").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val response = result.data as? Map<String, Any> ?: return ValidationResult.Invalid("Respuesta inválida")
            if (response["success"] as? Boolean == true) {
                ValidationResult.Valid(
                    plan = SubscriptionPlan.valueOf(response["plan"] as? String ?: "FREE"),
                    expiryTime = (response["expiryTime"] as? Long) ?: 0L,
                    isInTrial = response["isInTrial"] as? Boolean ?: false
                )
            } else ValidationResult.Invalid(response["error"] as? String ?: "Validación fallida")
        } catch (e: Exception) { Log.e(TAG, "Error", e); ValidationResult.Error(e) }
    }

    suspend fun getUserLimits(): UserLimitsData {
        val userId = auth.currentUser?.uid ?: return UserLimitsData()
        return try {
            val snap = firestore.collection(USERS_COLLECTION).document(userId).get().await()
            if (snap.exists()) {
                val l = snap.get("limits") as? Map<*, *>
                UserLimitsData((l?.get("petsCount") as? Long)?.toInt() ?: 0, (l?.get("remindersThisMonth") as? Long)?.toInt() ?: 0)
            } else UserLimitsData()
        } catch (e: Exception) { Log.e(TAG, "Error", e); UserLimitsData() }
    }

    suspend fun incrementPetsCount(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection(USERS_COLLECTION).document(userId)
                .update("limits.petsCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun incrementRemindersCount(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection(USERS_COLLECTION).document(userId)
                .update("limits.remindersThisMonth", com.google.firebase.firestore.FieldValue.increment(1)).await()
            true
        } catch (e: Exception) { false }
    }

    private fun parseSubscription(data: Map<*, *>?): UserSubscription? {
        if (data == null) return null
        return UserSubscription(
            plan = data["plan"] as? String ?: SubscriptionPlan.FREE.name,
            status = data["status"] as? String ?: SubscriptionStatus.ACTIVE.name,
            productId = data["productId"] as? String,
            purchaseToken = data["purchaseToken"] as? String,
            autoRenewing = data["autoRenewing"] as? Boolean ?: false,
            trialUsed = data["trialUsed"] as? Boolean ?: false
        )
    }
}

sealed class SubscriptionCheckResult {
    data class Success(val plan: SubscriptionPlan, val status: SubscriptionStatus, val expiryTime: Long?, val canStartTrial: Boolean) : SubscriptionCheckResult()
    data class Error(val message: String) : SubscriptionCheckResult()
    data object NotAuthenticated : SubscriptionCheckResult()
}

data class UserLimitsData(val petsCount: Int = 0, val remindersThisMonth: Int = 0)

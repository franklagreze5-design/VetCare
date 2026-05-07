package com.petapp.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.petapp.ads.AdManager
import com.petapp.billing.BillingManager
import com.petapp.features.FeatureGate
import com.petapp.subscription.SubscriptionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ==========================================
    // Firebase
    // ==========================================

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions {
        return FirebaseFunctions.getInstance()
    }

    // ==========================================
    // Billing
    // ==========================================

    @Provides
    @Singleton
    fun provideBillingManager(
        @ApplicationContext context: Context
    ): BillingManager {
        return BillingManager(context)
    }

    // ==========================================
    // Subscription
    // ==========================================

    @Provides
    @Singleton
    fun provideSubscriptionRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        functions: FirebaseFunctions,
        billingManager: BillingManager
    ): SubscriptionRepository {
        return SubscriptionRepository(
            auth = auth,
            firestore = firestore,
            functions = functions,
            billingManager = billingManager
        )
    }

    // ==========================================
    // Features
    // ==========================================

    @Provides
    @Singleton
    fun provideFeatureGate(
        subscriptionRepository: SubscriptionRepository,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): FeatureGate {
        return FeatureGate(
            subscriptionRepository = subscriptionRepository,
            firestore = firestore,
            auth = auth
        )
    }

    // ==========================================
    // Ads
    // ==========================================

    @Provides
    @Singleton
    fun provideAdManager(
        @ApplicationContext context: Context,
        subscriptionRepository: SubscriptionRepository
    ): AdManager {
        return AdManager(context, subscriptionRepository)
    }
}

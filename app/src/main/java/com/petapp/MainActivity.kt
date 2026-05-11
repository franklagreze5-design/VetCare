package com.petapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.petapp.subscription.SubscriptionViewModel
import com.petapp.subscription.SubscriptionViewModelFactory
import com.petapp.ui.paywall.PaywallScreen
import com.petapp.ui.theme.PetAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PetAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val app = application as PetApp

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onNavigateToPaywall = { navController.navigate("paywall") },
                                onNavigateToPets = { navController.navigate("pets") }
                            )
                        }

                        composable("paywall") {
                            val viewModel: SubscriptionViewModel = viewModel(
                                factory = SubscriptionViewModelFactory(
                                    app.subscriptionRepository,
                                    app.billingManager,
                                    app.purchaseHelper
                                )
                            )
                            val uiState by viewModel.uiState.collectAsState()
                            PaywallScreen(
                                uiState = uiState,
                                onSelectPlan = viewModel::selectPlan,
                                onSubscribe = { viewModel.subscribe(this@MainActivity) },
                                onRestorePurchases = viewModel::restorePurchases,
                                onDismiss = { navController.popBackStack() }
                            )
                        }

                        composable("pets") {
                            PetsScreen(onNavigateToPaywall = { navController.navigate("paywall") })
                        }

                        composable("pet/{petId}") { backStackEntry ->
                            val petId = backStackEntry.arguments?.getString("petId") ?: ""
                            PetDetailScreen(petId = petId, onNavigateToPaywall = { navController.navigate("paywall") })
                        }

                        composable("reminders") {
                            RemindersScreen(onNavigateToPaywall = { navController.navigate("paywall") })
                        }
                    }
                }
            }
        }
    }
}

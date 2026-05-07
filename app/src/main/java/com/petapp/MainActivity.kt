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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.petapp.subscription.SubscriptionViewModel
import com.petapp.ui.paywall.PaywallScreen
import com.petapp.ui.theme.PetAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        // Pantalla principal
                        composable("home") {
                            HomeScreen(
                                onNavigateToPaywall = {
                                    navController.navigate("paywall")
                                },
                                onNavigateToPets = {
                                    navController.navigate("pets")
                                }
                            )
                        }

                        // Paywall / Suscripciones
                        composable("paywall") {
                            val viewModel: SubscriptionViewModel = hiltViewModel()
                            val uiState by viewModel.uiState.collectAsState()

                            PaywallScreen(
                                uiState = uiState,
                                onSelectPlan = viewModel::selectPlan,
                                onSubscribe = { viewModel.subscribe(this@MainActivity) },
                                onRestorePurchases = viewModel::restorePurchases,
                                onDismiss = { navController.popBackStack() }
                            )
                        }

                        // Lista de mascotas
                        composable("pets") {
                            PetsScreen(
                                onNavigateToPaywall = {
                                    navController.navigate("paywall")
                                }
                            )
                        }

                        // Detalle de mascota
                        composable("pet/{petId}") { backStackEntry ->
                            val petId = backStackEntry.arguments?.getString("petId") ?: ""
                            PetDetailScreen(
                                petId = petId,
                                onNavigateToPaywall = {
                                    navController.navigate("paywall")
                                }
                            )
                        }

                        // Recordatorios
                        composable("reminders") {
                            RemindersScreen(
                                onNavigateToPaywall = {
                                    navController.navigate("paywall")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

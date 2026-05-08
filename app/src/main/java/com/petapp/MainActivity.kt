package com.petapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.petapp.subscription.SubscriptionViewModel
import com.petapp.subscription.SubscriptionViewModelFactory
import com.petapp.ui.paywall.PaywallScreen
import com.petapp.ui.theme.PetAppTheme

/**
 * Destinos de navegacion principal
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        title = "Inicio",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    data object Pets : Screen(
        route = "pets",
        title = "Mascotas",
        selectedIcon = Icons.Filled.Pets,
        unselectedIcon = Icons.Outlined.Pets
    )
    data object Reminders : Screen(
        route = "reminders",
        title = "Recordatorios",
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.Notifications
    )
    data object Premium : Screen(
        route = "paywall",
        title = "Premium",
        selectedIcon = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.Star
    )
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Pets,
    Screen.Reminders,
    Screen.Premium
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PetAppTheme {
                val navController = rememberNavController()
                val app = application as PetApp
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                // Determinar si mostrar bottom nav
                val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                bottomNavItems.forEach { screen ->
                                    val selected = currentDestination?.hierarchy?.any { 
                                        it.route == screen.route 
                                    } == true
                                    
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                                contentDescription = screen.title
                                            )
                                        },
                                        label = { Text(screen.title) },
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(padding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                onNavigateToPaywall = {
                                    navController.navigate("paywall")
                                },
                                onNavigateToPets = {
                                    navController.navigate("pets")
                                },
                                onNavigateToReminders = {
                                    navController.navigate("reminders")
                                }
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
                                onSubscribe = { viewModel.purchaseSelectedPlan(this@MainActivity) },
                                onRestorePurchases = viewModel::restorePurchases,
                                onDismiss = { navController.popBackStack() }
                            )
                        }

                        composable("pets") {
                            PetsScreen(
                                onNavigateToPaywall = {
                                    navController.navigate("paywall")
                                },
                                onNavigateToPetDetail = { petId ->
                                    navController.navigate("pet/$petId")
                                }
                            )
                        }

                        composable("pet/{petId}") { backStackEntry ->
                            val petId = backStackEntry.arguments?.getString("petId") ?: ""
                            PetDetailScreen(
                                petId = petId,
                                onNavigateToPaywall = {
                                    navController.navigate("paywall")
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

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

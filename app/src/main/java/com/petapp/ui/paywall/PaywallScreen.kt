package com.petapp.ui.paywall

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petapp.billing.SubscriptionPlan
import com.petapp.subscription.PaywallUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    uiState: PaywallUiState,
    onSelectPlan: (SubscriptionPlan) -> Unit,
    onSubscribe: () -> Unit,
    onRestorePurchases: () -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planes Premium", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.upgradePrompt.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.upgradePrompt.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Plan PREMIUM
            PlanCard(
                title = "Premium",
                price = "\$3.990/mes",
                features = listOf(
                    "Mascotas ilimitadas",
                    "Recordatorios ilimitados",
                    "Historial médico completo",
                    "Control de vacunas",
                    "Sin publicidad"
                ),
                isSelected = uiState.selectedPlan == SubscriptionPlan.PREMIUM,
                onClick = { onSelectPlan(SubscriptionPlan.PREMIUM) }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Plan FAMILY
            PlanCard(
                title = "Family",
                price = "\$5.490/mes",
                features = listOf(
                    "Hasta 5 mascotas",
                    "Todo lo de Premium",
                    "Recomendaciones IA",
                    "Compartir con veterinario"
                ),
                isSelected = uiState.selectedPlan == SubscriptionPlan.FAMILY,
                onClick = { onSelectPlan(SubscriptionPlan.FAMILY) }
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.selectedPlan != null
            ) {
                Text(uiState.upgradePrompt.ctaText)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRestorePurchases) {
                Text("Restaurar compras")
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    features: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null,
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            features.forEach { feature ->
                Text("✓ $feature", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

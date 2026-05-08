package com.petapp.ui.paywall

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.petapp.billing.SubscriptionPlan
import com.petapp.features.FeatureConfig
import com.petapp.features.FeatureItem
import com.petapp.subscription.PaywallUiState
import com.petapp.subscription.PurchaseState

@Composable
fun PaywallScreen(
    uiState: PaywallUiState,
    onSelectPlan: (SubscriptionPlan) -> Unit,
    onSubscribe: () -> Unit,
    onRestorePurchases: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                PaywallHeader(
                    prompt = uiState.upgradePrompt,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.selectedPlan == SubscriptionPlan.PREMIUM) {
                    BillingPeriodToggle(
                        isYearlySelected = uiState.isYearlySelected,
                        yearlySavings = null,
                        onToggle = { }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                PlanCards(
                    uiState = uiState,
                    onSelectPlan = onSelectPlan
                )

                Spacer(modifier = Modifier.height(24.dp))

                FeatureComparison(
                    selectedPlan = uiState.selectedPlan ?: SubscriptionPlan.PREMIUM
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.canStartTrial) {
                    TrialBadge()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = onSubscribe,
                    enabled = uiState.selectedPlan != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB800)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (uiState.canStartTrial) "Comenzar prueba gratis" else "Suscribirse ahora",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onRestorePurchases,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Restaurar compras",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Al suscribirte aceptas los Terminos de servicio y Politica de privacidad. " +
                            "La suscripcion se renueva automaticamente a menos que la canceles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PaywallHeader(
    prompt: com.petapp.features.UpgradePrompt,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(40.dp))

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFB800), Color(0xFFFF8A00))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = prompt.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = prompt.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BillingPeriodToggle(
    isYearlySelected: Boolean,
    yearlySavings: String?,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Plan anual", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                yearlySavings?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
            Switch(checked = isYearlySelected, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PlanCards(
    uiState: PaywallUiState,
    onSelectPlan: (SubscriptionPlan) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanCard(
            plan = SubscriptionPlan.PREMIUM,
            isSelected = uiState.selectedPlan == SubscriptionPlan.PREMIUM,
            isRecommended = true,
            priceText = uiState.availablePlans
                .find { it.productId == if (uiState.isYearlySelected) "premium_yearly" else "premium_monthly" }
                ?.formattedPrice ?: "$3.990/mes",
            periodText = if (uiState.isYearlySelected) "/año" else "/mes",
            onSelect = { onSelectPlan(SubscriptionPlan.PREMIUM) }
        )

        PlanCard(
            plan = SubscriptionPlan.FAMILY,
            isSelected = uiState.selectedPlan == SubscriptionPlan.FAMILY,
            isRecommended = false,
            priceText = uiState.availablePlans
                .find { it.productId == "family_plan" }
                ?.formattedPrice ?: "$5.490/mes",
            periodText = "/mes",
            onSelect = { onSelectPlan(SubscriptionPlan.FAMILY) }
        )
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    isSelected: Boolean,
    isRecommended: Boolean,
    priceText: String,
    periodText: String,
    onSelect: () -> Unit
) {
    val config = FeatureConfig.getPlanConfig(plan)
    val borderColor = if (isSelected) Color(0xFFFFB800) else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFFFFBE6) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(config.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (isRecommended) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFFB800))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Mas popular", style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(config.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(priceText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFFB800))
                    Text(periodText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FeatureComparison(selectedPlan: SubscriptionPlan) {
    val config = FeatureConfig.getPlanConfig(selectedPlan)
    Column {
        Text("Incluido en ${config.name}:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        config.features.forEach { feature -> FeatureRow(feature = feature) }
    }
}

@Composable
private fun FeatureRow(feature: FeatureItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (feature.isIncluded) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (feature.isIncluded) Color(0xFF4CAF50) else Color(0xFFE0E0E0),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = feature.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (feature.isIncluded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (!feature.isIncluded) TextDecoration.LineThrough else null
        )
        feature.note?.let { note ->
            Spacer(modifier = Modifier.width(4.dp))
            Text("($note)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrialBadge() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("7 dias de prueba gratis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        }
    }
}

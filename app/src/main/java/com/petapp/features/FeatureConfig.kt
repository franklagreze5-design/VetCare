package com.petapp.features

import com.petapp.billing.SubscriptionPlan

object FeatureConfig {

    fun getPlanConfig(plan: SubscriptionPlan): PlanConfiguration {
        return when (plan) {
            SubscriptionPlan.FREE -> PlanConfiguration(
                name = "Gratis",
                tagline = "Perfecto para empezar",
                features = listOf(
                    FeatureItem("1 mascota", true),
                    FeatureItem("Historial médico básico", true),
                    FeatureItem("3 recordatorios/mes", true),
                    FeatureItem("Veterinarias cercanas", true, "con publicidad"),
                    FeatureItem("Vacunas y archivos", false),
                    FeatureItem("Compartir con veterinario", false),
                    FeatureItem("Sin publicidad", false),
                    FeatureItem("Recomendaciones IA", false)
                ),
                isRecommended = false,
                badgeText = null
            )
            SubscriptionPlan.PREMIUM -> PlanConfiguration(
                name = "Premium",
                tagline = "Todo lo que necesitas",
                features = listOf(
                    FeatureItem("Mascotas ilimitadas", true),
                    FeatureItem("Historial médico completo", true),
                    FeatureItem("Recordatorios ilimitados", true),
                    FeatureItem("Veterinarias cercanas", true, "sin publicidad"),
                    FeatureItem("Vacunas y archivos", true),
                    FeatureItem("Compartir con veterinario", true),
                    FeatureItem("Sin publicidad", true),
                    FeatureItem("Recomendaciones IA", true)
                ),
                isRecommended = true,
                badgeText = "Mas popular"
            )
            SubscriptionPlan.FAMILY -> PlanConfiguration(
                name = "Family",
                tagline = "Para toda la familia",
                features = listOf(
                    FeatureItem("Hasta 5 mascotas", true),
                    FeatureItem("Historial médico completo", true),
                    FeatureItem("Recordatorios ilimitados", true),
                    FeatureItem("Veterinarias cercanas", true, "sin publicidad"),
                    FeatureItem("Vacunas y archivos", true),
                    FeatureItem("Compartir con veterinario", true),
                    FeatureItem("Sin publicidad", true),
                    FeatureItem("Recomendaciones IA", true)
                ),
                isRecommended = false,
                badgeText = "Ideal familias"
            )
        }
    }

    fun getUpgradePrompt(trigger: PaywallTrigger): UpgradePrompt {
        return when (trigger) {
            PaywallTrigger.MANUAL -> UpgradePrompt(
                title = "Mejora tu experiencia",
                subtitle = "Desbloquea todas las funcionalidades premium",
                ctaText = "Ver planes"
            )
            PaywallTrigger.PET_LIMIT_REACHED -> UpgradePrompt(
                title = "Has alcanzado el limite",
                subtitle = "Actualiza a Premium para agregar mas mascotas",
                ctaText = "Desbloquear mascotas"
            )
            PaywallTrigger.REMINDER_LIMIT_REACHED -> UpgradePrompt(
                title = "Limite de recordatorios alcanzado",
                subtitle = "Obtén recordatorios ilimitados con Premium",
                ctaText = "Obtener ilimitados"
            )
            PaywallTrigger.VACCINE_ACCESS -> UpgradePrompt(
                title = "Control de vacunas",
                subtitle = "Lleva el registro de vacunas de tu mascota",
                ctaText = "Activar vacunas"
            )
            PaywallTrigger.FILE_UPLOAD -> UpgradePrompt(
                title = "Guarda archivos y fotos",
                subtitle = "Almacena documentos y fotos médicas",
                ctaText = "Activar archivos"
            )
            PaywallTrigger.SHARE_WITH_VET -> UpgradePrompt(
                title = "Comparte con tu veterinario",
                subtitle = "Envía la ficha completa de tu mascota",
                ctaText = "Activar compartir"
            )
            PaywallTrigger.AI_RECOMMENDATIONS -> UpgradePrompt(
                title = "Recomendaciones inteligentes",
                subtitle = "Obtén consejos personalizados con IA",
                ctaText = "Activar IA"
            )
        }
    }
}

data class PlanConfiguration(
    val name: String,
    val tagline: String,
    val features: List<FeatureItem>,
    val isRecommended: Boolean,
    val badgeText: String?
)

data class FeatureItem(
    val text: String,
    val isIncluded: Boolean,
    val note: String? = null
)

data class UpgradePrompt(
    val title: String,
    val subtitle: String,
    val ctaText: String
)

enum class PaywallTrigger {
    MANUAL,
    PET_LIMIT_REACHED,
    REMINDER_LIMIT_REACHED,
    VACCINE_ACCESS,
    FILE_UPLOAD,
    SHARE_WITH_VET,
    AI_RECOMMENDATIONS
}

package com.petapp.features

import com.petapp.billing.SubscriptionPlan

/**
 * Prompt de upgrade para mostrar en el paywall
 */
data class UpgradePrompt(
    val title: String,
    val subtitle: String,
    val ctaText: String
)

/**
 * Item de feature para mostrar en la lista de comparacion
 */
data class FeatureItem(
    val text: String,
    val isIncluded: Boolean,
    val note: String? = null
)

/**
 * Configuracion de un plan de suscripcion
 */
data class PlanConfig(
    val name: String,
    val tagline: String,
    val features: List<FeatureItem>
)

/**
 * Razones por las que se muestra el paywall
 */
enum class PaywallTrigger {
    MANUAL,
    PET_LIMIT_REACHED,
    REMINDER_LIMIT_REACHED,
    VACCINE_ACCESS,
    FILE_UPLOAD,
    SHARE_WITH_VET,
    AI_RECOMMENDATIONS
}

/**
 * Configuracion de mensajes de upgrade segun el trigger
 */
object FeatureConfig {

    fun getUpgradePrompt(trigger: PaywallTrigger): UpgradePrompt {
        return when (trigger) {
            PaywallTrigger.PET_LIMIT_REACHED -> UpgradePrompt(
                title = "Tienes mas mascotas!",
                subtitle = "Con Premium puedes agregar mascotas ilimitadas",
                ctaText = "Agregar mas mascotas"
            )
            PaywallTrigger.REMINDER_LIMIT_REACHED -> UpgradePrompt(
                title = "Limite de recordatorios alcanzado",
                subtitle = "Con Premium crea recordatorios sin limite",
                ctaText = "Desbloquear recordatorios"
            )
            PaywallTrigger.VACCINE_ACCESS -> UpgradePrompt(
                title = "Control de vacunas",
                subtitle = "Registra y programa las vacunas de tus mascotas",
                ctaText = "Ver planes"
            )
            PaywallTrigger.FILE_UPLOAD -> UpgradePrompt(
                title = "Guarda documentos medicos",
                subtitle = "Sube fotos, recetas y resultados de examenes",
                ctaText = "Desbloquear almacenamiento"
            )
            PaywallTrigger.SHARE_WITH_VET -> UpgradePrompt(
                title = "Comparte con tu veterinario",
                subtitle = "Envia la ficha completa de tu mascota al instante",
                ctaText = "Compartir con veterinario"
            )
            PaywallTrigger.AI_RECOMMENDATIONS -> UpgradePrompt(
                title = "Recomendaciones inteligentes",
                subtitle = "Recibe consejos personalizados con IA para tus mascotas",
                ctaText = "Activar IA"
            )
            PaywallTrigger.MANUAL -> UpgradePrompt(
                title = "Mejora tu experiencia",
                subtitle = "Desbloquea todas las funcionalidades premium",
                ctaText = "Ver planes"
            )
        }
    }

    /**
     * Obtiene la configuracion del plan para mostrar en el paywall
     */
    fun getPlanConfig(plan: SubscriptionPlan): PlanConfig {
        return when (plan) {
            SubscriptionPlan.PREMIUM -> PlanConfig(
                name = "Premium",
                tagline = "Todo lo que necesitas para cuidar a tu mascota",
                features = listOf(
                    FeatureItem("Mascotas ilimitadas", true),
                    FeatureItem("Recordatorios ilimitados", true),
                    FeatureItem("Historial medico completo", true),
                    FeatureItem("Control de vacunas", true),
                    FeatureItem("Guardar documentos y fotos", true),
                    FeatureItem("Compartir con veterinario", true),
                    FeatureItem("Recomendaciones con IA", true),
                    FeatureItem("Sin publicidad", true)
                )
            )
            SubscriptionPlan.FAMILY -> PlanConfig(
                name = "Familia",
                tagline = "Ideal para hogares con varias mascotas",
                features = listOf(
                    FeatureItem("Hasta 5 mascotas", true, "perfecto para familias"),
                    FeatureItem("Recordatorios ilimitados", true),
                    FeatureItem("Historial medico completo", true),
                    FeatureItem("Control de vacunas", true),
                    FeatureItem("Guardar documentos y fotos", true),
                    FeatureItem("Compartir con veterinario", true),
                    FeatureItem("Recomendaciones con IA", true),
                    FeatureItem("Sin publicidad", true)
                )
            )
            SubscriptionPlan.FREE -> PlanConfig(
                name = "Gratis",
                tagline = "Funciones basicas para empezar",
                features = listOf(
                    FeatureItem("1 mascota", true),
                    FeatureItem("3 recordatorios por mes", true),
                    FeatureItem("Historial medico completo", false),
                    FeatureItem("Control de vacunas", false),
                    FeatureItem("Guardar documentos y fotos", false),
                    FeatureItem("Compartir con veterinario", false),
                    FeatureItem("Recomendaciones con IA", false),
                    FeatureItem("Con publicidad", true)
                )
            )
        }
    }
}

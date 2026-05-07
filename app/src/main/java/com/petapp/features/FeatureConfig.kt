package com.petapp.features

/**
 * Prompt de upgrade para mostrar en el paywall
 */
data class UpgradePrompt(
    val title: String,
    val subtitle: String,
    val ctaText: String
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
 * Configuración de mensajes de upgrade según el trigger
 */
object FeatureConfig {

    fun getUpgradePrompt(trigger: PaywallTrigger): UpgradePrompt {
        return when (trigger) {
            PaywallTrigger.PET_LIMIT_REACHED -> UpgradePrompt(
                title = "¡Tienes más mascotas!",
                subtitle = "Con Premium puedes agregar mascotas ilimitadas",
                ctaText = "Agregar más mascotas"
            )
            PaywallTrigger.REMINDER_LIMIT_REACHED -> UpgradePrompt(
                title = "Límite de recordatorios alcanzado",
                subtitle = "Con Premium crea recordatorios sin límite",
                ctaText = "Desbloquear recordatorios"
            )
            PaywallTrigger.VACCINE_ACCESS -> UpgradePrompt(
                title = "Control de vacunas",
                subtitle = "Registra y programa las vacunas de tus mascotas",
                ctaText = "Ver planes"
            )
            PaywallTrigger.FILE_UPLOAD -> UpgradePrompt(
                title = "Guarda documentos médicos",
                subtitle = "Sube fotos, recetas y resultados de exámenes",
                ctaText = "Desbloquear almacenamiento"
            )
            PaywallTrigger.SHARE_WITH_VET -> UpgradePrompt(
                title = "Comparte con tu veterinario",
                subtitle = "Envía la ficha completa de tu mascota al instante",
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
}

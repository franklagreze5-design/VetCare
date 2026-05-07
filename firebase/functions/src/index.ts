import * as admin from "firebase-admin";

// Inicializar Firebase Admin
admin.initializeApp();

// Exportar Cloud Functions
export {
  validatePurchase,
  checkSubscriptionStatus,
  syncSubscriptionFromPlayStore,
} from "./validatePurchase";

export { playBillingWebhook } from "./webhooks";

/**
 * Cloud Functions disponibles:
 *
 * 1. validatePurchase (Callable)
 *    - Valida una compra con Google Play Developer API
 *    - Actualiza el estado del usuario en Firestore
 *    - Parámetros: { userId, productId, purchaseToken }
 *
 * 2. checkSubscriptionStatus (Callable)
 *    - Verifica el estado actual de la suscripción
 *    - Crea usuario con plan FREE si no existe
 *    - Parámetros: { userId }
 *
 * 3. syncSubscriptionFromPlayStore (Callable)
 *    - Sincroniza estado desde Google Play (para restaurar compras)
 *    - Parámetros: { productId, purchaseToken }
 *
 * 4. playBillingWebhook (HTTP)
 *    - Recibe RTDN de Google Play via Pub/Sub
 *    - Actualiza automáticamente el estado de suscripciones
 *
 * Configuración requerida:
 * - Service Account con acceso a Google Play Developer API
 * - Pub/Sub topic configurado en Google Play Console
 * - Firestore Security Rules configuradas
 */

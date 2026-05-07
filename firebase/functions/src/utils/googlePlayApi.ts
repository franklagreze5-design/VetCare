import { google } from "googleapis";

/**
 * Cliente para Google Play Developer API
 * Usado para validar compras de suscripciones
 */

// Configuración - Estas variables deben estar en Firebase Config
const PACKAGE_NAME = process.env.ANDROID_PACKAGE_NAME || "com.petapp";

// Service Account credentials (configurar en Firebase Functions config)
// firebase functions:config:set googleplay.client_email="..." googleplay.private_key="..."

interface SubscriptionPurchase {
  kind: string;
  startTimeMillis: string;
  expiryTimeMillis: string;
  autoRenewing: boolean;
  priceCurrencyCode: string;
  priceAmountMicros: string;
  countryCode: string;
  developerPayload: string;
  paymentState?: number;
  cancelReason?: number;
  userCancellationTimeMillis?: string;
  orderId: string;
  linkedPurchaseToken?: string;
  purchaseType?: number;
  acknowledgementState: number;
}

interface ValidationResult {
  isValid: boolean;
  expiryTimeMillis?: number;
  autoRenewing?: boolean;
  paymentState?: number;
  cancelReason?: number;
  isInTrial?: boolean;
  linkedPurchaseToken?: string;
  error?: string;
}

/**
 * Crea un cliente autenticado de Google Play Developer API
 */
async function getPlayDeveloperClient() {
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });

  const authClient = await auth.getClient();

  return google.androidpublisher({
    version: "v3",
    auth: authClient as Parameters<typeof google.androidpublisher>[0]["auth"],
  });
}

/**
 * Valida una compra de suscripción con Google Play Developer API
 *
 * @param productId - ID del producto (premium_monthly, premium_yearly, family_plan)
 * @param purchaseToken - Token de la compra obtenido del cliente
 * @returns Resultado de la validación
 */
export async function validateSubscriptionPurchase(
  productId: string,
  purchaseToken: string
): Promise<ValidationResult> {
  try {
    const playDeveloper = await getPlayDeveloperClient();

    const response = await playDeveloper.purchases.subscriptions.get({
      packageName: PACKAGE_NAME,
      subscriptionId: productId,
      token: purchaseToken,
    });

    const subscription = response.data as SubscriptionPurchase;

    // Verificar si la suscripción está expirada
    const expiryTimeMillis = parseInt(subscription.expiryTimeMillis, 10);
    const isExpired = expiryTimeMillis < Date.now();

    // Verificar estado de pago
    // 0 = Pago pendiente, 1 = Pago recibido, 2 = Trial gratuito, 3 = Upgrade/downgrade pendiente
    const paymentState = subscription.paymentState;
    const isInTrial = paymentState === 2;

    // Verificar si fue cancelada
    const cancelReason = subscription.cancelReason;

    return {
      isValid: !isExpired && (paymentState === 1 || paymentState === 2),
      expiryTimeMillis,
      autoRenewing: subscription.autoRenewing,
      paymentState,
      cancelReason,
      isInTrial,
      linkedPurchaseToken: subscription.linkedPurchaseToken,
    };
  } catch (error) {
    console.error("Error validando suscripción:", error);

    // Manejar errores específicos de la API
    const errorMessage = error instanceof Error ? error.message : "Error desconocido";

    return {
      isValid: false,
      error: errorMessage,
    };
  }
}

/**
 * Acknowledges (confirma) una suscripción
 * Debe llamarse después de entregar el contenido al usuario
 */
export async function acknowledgeSubscription(
  productId: string,
  purchaseToken: string
): Promise<boolean> {
  try {
    const playDeveloper = await getPlayDeveloperClient();

    await playDeveloper.purchases.subscriptions.acknowledge({
      packageName: PACKAGE_NAME,
      subscriptionId: productId,
      token: purchaseToken,
    });

    return true;
  } catch (error) {
    console.error("Error acknowledging suscripción:", error);
    return false;
  }
}

/**
 * Cancela una suscripción (defer billing)
 * Útil para casos de soporte al cliente
 */
export async function cancelSubscription(
  productId: string,
  purchaseToken: string
): Promise<boolean> {
  try {
    const playDeveloper = await getPlayDeveloperClient();

    await playDeveloper.purchases.subscriptions.cancel({
      packageName: PACKAGE_NAME,
      subscriptionId: productId,
      token: purchaseToken,
    });

    return true;
  } catch (error) {
    console.error("Error cancelando suscripción:", error);
    return false;
  }
}

/**
 * Mapea el productId al plan correspondiente
 */
export function mapProductIdToPlan(
  productId: string
): "FREE" | "PREMIUM" | "FAMILY" {
  switch (productId) {
    case "premium_monthly":
    case "premium_yearly":
      return "PREMIUM";
    case "family_plan":
      return "FAMILY";
    default:
      return "FREE";
  }
}

/**
 * Mapea el estado de pago a un estado de suscripción
 */
export function mapPaymentStateToStatus(
  paymentState: number | undefined,
  cancelReason: number | undefined,
  expiryTimeMillis: number
): "ACTIVE" | "CANCELED" | "EXPIRED" | "GRACE_PERIOD" | "ON_HOLD" | "IN_TRIAL" {
  const isExpired = expiryTimeMillis < Date.now();

  if (isExpired) {
    return "EXPIRED";
  }

  if (paymentState === 2) {
    return "IN_TRIAL";
  }

  if (cancelReason !== undefined) {
    // 0 = Usuario canceló, 1 = Problema de pago, 2 = Sistema canceló
    if (cancelReason === 1) {
      return "GRACE_PERIOD";
    }
    return "CANCELED";
  }

  if (paymentState === 0) {
    return "ON_HOLD";
  }

  return "ACTIVE";
}

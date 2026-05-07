import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import {
  validateSubscriptionPurchase,
  mapProductIdToPlan,
  mapPaymentStateToStatus,
} from "./utils/googlePlayApi";

/**
 * Google Play Real-time Developer Notifications (RTDN) Webhook
 *
 * Este endpoint recibe notificaciones push de Google Play cuando:
 * - Una suscripción es comprada, renovada, cancelada, pausada, etc.
 * - Hay cambios en el estado de pago
 *
 * Configuración en Google Play Console:
 * 1. Ir a Monetization > Monetization setup
 * 2. En "Real-time developer notifications" configurar:
 *    Topic name: projects/YOUR_PROJECT/topics/play-billing
 *    O usar Cloud Pub/Sub directamente
 */

// Tipos de notificación de suscripción
enum SubscriptionNotificationType {
  SUBSCRIPTION_RECOVERED = 1,
  SUBSCRIPTION_RENEWED = 2,
  SUBSCRIPTION_CANCELED = 3,
  SUBSCRIPTION_PURCHASED = 4,
  SUBSCRIPTION_ON_HOLD = 5,
  SUBSCRIPTION_IN_GRACE_PERIOD = 6,
  SUBSCRIPTION_RESTARTED = 7,
  SUBSCRIPTION_PRICE_CHANGE_CONFIRMED = 8,
  SUBSCRIPTION_DEFERRED = 9,
  SUBSCRIPTION_PAUSED = 10,
  SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED = 11,
  SUBSCRIPTION_REVOKED = 12,
  SUBSCRIPTION_EXPIRED = 13,
}

interface DeveloperNotification {
  version: string;
  packageName: string;
  eventTimeMillis: string;
  subscriptionNotification?: {
    version: string;
    notificationType: number;
    purchaseToken: string;
    subscriptionId: string;
  };
  testNotification?: {
    version: string;
  };
}

/**
 * HTTP Endpoint para recibir RTDN via Pub/Sub push
 */
export const playBillingWebhook = functions.https.onRequest(
  async (req, res) => {
    // Verificar método
    if (req.method !== "POST") {
      res.status(405).send("Method not allowed");
      return;
    }

    try {
      // Decodificar mensaje de Pub/Sub
      const message = req.body.message;
      if (!message || !message.data) {
        res.status(400).send("Invalid message format");
        return;
      }

      const data = Buffer.from(message.data, "base64").toString();
      const notification: DeveloperNotification = JSON.parse(data);

      functions.logger.info("RTDN recibido", {
        type: notification.subscriptionNotification?.notificationType,
        subscriptionId: notification.subscriptionNotification?.subscriptionId,
      });

      // Manejar notificación de test
      if (notification.testNotification) {
        functions.logger.info("Notificación de test recibida");
        res.status(200).send("Test notification received");
        return;
      }

      // Procesar notificación de suscripción
      if (notification.subscriptionNotification) {
        await handleSubscriptionNotification(
          notification.subscriptionNotification
        );
      }

      res.status(200).send("OK");
    } catch (error) {
      functions.logger.error("Error procesando webhook", { error });
      res.status(500).send("Internal error");
    }
  }
);

/**
 * Procesa una notificación de suscripción
 */
async function handleSubscriptionNotification(notification: {
  notificationType: number;
  purchaseToken: string;
  subscriptionId: string;
}): Promise<void> {
  const { notificationType, purchaseToken, subscriptionId } = notification;

  // Buscar usuario por purchaseToken
  const userId = await findUserByPurchaseToken(purchaseToken);

  if (!userId) {
    functions.logger.warn("Usuario no encontrado para purchaseToken", {
      subscriptionId,
    });
    return;
  }

  functions.logger.info("Procesando notificación", {
    userId,
    type: notificationType,
    subscriptionId,
  });

  switch (notificationType) {
    case SubscriptionNotificationType.SUBSCRIPTION_PURCHASED:
    case SubscriptionNotificationType.SUBSCRIPTION_RENEWED:
    case SubscriptionNotificationType.SUBSCRIPTION_RECOVERED:
    case SubscriptionNotificationType.SUBSCRIPTION_RESTARTED:
      await handleSubscriptionActive(userId, subscriptionId, purchaseToken);
      break;

    case SubscriptionNotificationType.SUBSCRIPTION_CANCELED:
      await handleSubscriptionCanceled(userId);
      break;

    case SubscriptionNotificationType.SUBSCRIPTION_EXPIRED:
    case SubscriptionNotificationType.SUBSCRIPTION_REVOKED:
      await handleSubscriptionExpired(userId);
      break;

    case SubscriptionNotificationType.SUBSCRIPTION_ON_HOLD:
      await handleSubscriptionOnHold(userId);
      break;

    case SubscriptionNotificationType.SUBSCRIPTION_IN_GRACE_PERIOD:
      await handleSubscriptionGracePeriod(userId);
      break;

    case SubscriptionNotificationType.SUBSCRIPTION_PAUSED:
      await handleSubscriptionPaused(userId);
      break;

    default:
      functions.logger.info("Tipo de notificación no manejado", {
        notificationType,
      });
  }
}

/**
 * Busca un usuario por su purchaseToken
 */
async function findUserByPurchaseToken(
  purchaseToken: string
): Promise<string | null> {
  const db = admin.firestore();
  const snapshot = await db
    .collection("users")
    .where("subscription.purchaseToken", "==", purchaseToken)
    .limit(1)
    .get();

  if (snapshot.empty) return null;
  return snapshot.docs[0].id;
}

/**
 * Maneja suscripción activa (comprada, renovada, recuperada)
 */
async function handleSubscriptionActive(
  userId: string,
  subscriptionId: string,
  purchaseToken: string
): Promise<void> {
  // Validar con Google Play para obtener detalles actualizados
  const validation = await validateSubscriptionPurchase(
    subscriptionId,
    purchaseToken
  );

  if (!validation.isValid) {
    functions.logger.warn("Validación fallida en webhook", { userId });
    return;
  }

  const plan = mapProductIdToPlan(subscriptionId);
  const status = mapPaymentStateToStatus(
    validation.paymentState,
    validation.cancelReason,
    validation.expiryTimeMillis!
  );

  const db = admin.firestore();
  await db.collection("users").doc(userId).update({
    "subscription.plan": plan,
    "subscription.status": status,
    "subscription.expiryTime": admin.firestore.Timestamp.fromMillis(
      validation.expiryTimeMillis!
    ),
    "subscription.autoRenewing": validation.autoRenewing,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  functions.logger.info("Suscripción activada via webhook", {
    userId,
    plan,
    status,
  });
}

/**
 * Maneja suscripción cancelada (pero aún activa hasta expiración)
 */
async function handleSubscriptionCanceled(userId: string): Promise<void> {
  const db = admin.firestore();
  await db.collection("users").doc(userId).update({
    "subscription.status": "CANCELED",
    "subscription.autoRenewing": false,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  functions.logger.info("Suscripción cancelada", { userId });
}

/**
 * Maneja suscripción expirada
 */
async function handleSubscriptionExpired(userId: string): Promise<void> {
  const db = admin.firestore();
  await db.collection("users").doc(userId).update({
    "subscription.plan": "FREE",
    "subscription.status": "EXPIRED",
    "subscription.autoRenewing": false,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  functions.logger.info("Suscripción expirada", { userId });
}

/**
 * Maneja suscripción en espera (problema de pago)
 */
async function handleSubscriptionOnHold(userId: string): Promise<void> {
  const db = admin.firestore();
  await db.collection("users").doc(userId).update({
    "subscription.status": "ON_HOLD",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  functions.logger.info("Suscripción en espera", { userId });
}

/**
 * Maneja suscripción en período de gracia
 */
async function handleSubscriptionGracePeriod(userId: string): Promise<void> {
  const db = admin.firestore();
  await db.collection("users").doc(userId).update({
    "subscription.status": "GRACE_PERIOD",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  functions.logger.info("Suscripción en período de gracia", { userId });
}

/**
 * Maneja suscripción pausada
 */
async function handleSubscriptionPaused(userId: string): Promise<void> {
  const db = admin.firestore();
  await db.collection("users").doc(userId).update({
    "subscription.status": "ON_HOLD",
    "subscription.autoRenewing": false,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  functions.logger.info("Suscripción pausada", { userId });
}

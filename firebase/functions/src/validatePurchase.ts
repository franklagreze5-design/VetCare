import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import {
  validateSubscriptionPurchase,
  mapProductIdToPlan,
  mapPaymentStateToStatus,
} from "./utils/googlePlayApi";

/**
 * Cloud Function: validatePurchase
 *
 * Valida una compra de suscripción con Google Play Developer API
 * y actualiza el estado del usuario en Firestore.
 *
 * IMPORTANTE: Esta es la única fuente de verdad para el estado de suscripción.
 * El cliente NUNCA debe determinar su propio estado premium.
 */

interface ValidatePurchaseRequest {
  userId: string;
  productId: string;
  purchaseToken: string;
}

interface ValidatePurchaseResponse {
  success: boolean;
  plan?: string;
  status?: string;
  expiryTime?: number;
  isInTrial?: boolean;
  error?: string;
}

export const validatePurchase = functions.https.onCall(
  async (
    request: functions.https.CallableRequest<ValidatePurchaseRequest>
  ): Promise<ValidatePurchaseResponse> => {
    const { userId, productId, purchaseToken } = request.data;

    // Verificar autenticación
    if (!request.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Usuario no autenticado"
      );
    }

    // Verificar que el userId coincide con el usuario autenticado
    if (request.auth.uid !== userId) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "No tienes permiso para validar esta compra"
      );
    }

    // Validar parámetros
    if (!productId || !purchaseToken) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "productId y purchaseToken son requeridos"
      );
    }

    try {
      // Validar con Google Play Developer API
      const validationResult = await validateSubscriptionPurchase(
        productId,
        purchaseToken
      );

      if (!validationResult.isValid) {
        // Compra inválida - actualizar usuario a FREE
        await updateUserSubscription(userId, {
          plan: "FREE",
          status: "EXPIRED",
          productId: null,
          purchaseToken: null,
          expiryTime: null,
          autoRenewing: false,
        });

        return {
          success: false,
          error: validationResult.error || "Compra inválida",
        };
      }

      // Compra válida - mapear a plan y estado
      const plan = mapProductIdToPlan(productId);
      const status = mapPaymentStateToStatus(
        validationResult.paymentState,
        validationResult.cancelReason,
        validationResult.expiryTimeMillis!
      );

      // Actualizar usuario en Firestore
      await updateUserSubscription(userId, {
        plan,
        status,
        productId,
        purchaseToken,
        expiryTime: admin.firestore.Timestamp.fromMillis(
          validationResult.expiryTimeMillis!
        ),
        autoRenewing: validationResult.autoRenewing || false,
        originalPurchaseTime: admin.firestore.FieldValue.serverTimestamp(),
        trialUsed:
          validationResult.isInTrial ||
          (await hasUserUsedTrial(userId)),
      });

      functions.logger.info("Suscripción validada exitosamente", {
        userId,
        plan,
        status,
        productId,
      });

      return {
        success: true,
        plan,
        status,
        expiryTime: validationResult.expiryTimeMillis,
        isInTrial: validationResult.isInTrial,
      };
    } catch (error) {
      functions.logger.error("Error validando compra", { error, userId });

      throw new functions.https.HttpsError(
        "internal",
        "Error interno al validar la compra"
      );
    }
  }
);

/**
 * Actualiza la suscripción del usuario en Firestore
 */
async function updateUserSubscription(
  userId: string,
  subscriptionData: Record<string, unknown>
): Promise<void> {
  const db = admin.firestore();
  const userRef = db.collection("users").doc(userId);

  await userRef.set(
    {
      subscription: subscriptionData,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
}

/**
 * Verifica si el usuario ya usó su trial gratuito
 */
async function hasUserUsedTrial(userId: string): Promise<boolean> {
  const db = admin.firestore();
  const userDoc = await db.collection("users").doc(userId).get();

  if (!userDoc.exists) return false;

  const userData = userDoc.data();
  return userData?.subscription?.trialUsed || false;
}

/**
 * Cloud Function: checkSubscriptionStatus
 *
 * Verifica el estado actual de la suscripción de un usuario.
 * Útil para verificar al iniciar la app.
 */
export const checkSubscriptionStatus = functions.https.onCall(
  async (
    request: functions.https.CallableRequest<{ userId: string }>
  ): Promise<{
    plan: string;
    status: string;
    expiryTime: number | null;
    canStartTrial: boolean;
  }> => {
    if (!request.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Usuario no autenticado"
      );
    }

    const userId = request.data.userId || request.auth.uid;

    if (request.auth.uid !== userId) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "No tienes permiso para ver esta suscripción"
      );
    }

    const db = admin.firestore();
    const userDoc = await db.collection("users").doc(userId).get();

    if (!userDoc.exists) {
      // Usuario nuevo - crear con plan FREE
      await db.collection("users").doc(userId).set({
        subscription: {
          plan: "FREE",
          status: "ACTIVE",
          trialUsed: false,
        },
        limits: {
          petsCount: 0,
          remindersThisMonth: 0,
        },
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return {
        plan: "FREE",
        status: "ACTIVE",
        expiryTime: null,
        canStartTrial: true,
      };
    }

    const userData = userDoc.data()!;
    const subscription = userData.subscription || {};

    // Verificar si la suscripción expiró
    if (subscription.expiryTime && subscription.plan !== "FREE") {
      const expiryTime = subscription.expiryTime.toMillis();
      if (expiryTime < Date.now()) {
        // Suscripción expirada - actualizar a FREE
        await db.collection("users").doc(userId).update({
          "subscription.plan": "FREE",
          "subscription.status": "EXPIRED",
        });

        return {
          plan: "FREE",
          status: "EXPIRED",
          expiryTime: null,
          canStartTrial: !subscription.trialUsed,
        };
      }
    }

    return {
      plan: subscription.plan || "FREE",
      status: subscription.status || "ACTIVE",
      expiryTime: subscription.expiryTime?.toMillis() || null,
      canStartTrial: !subscription.trialUsed,
    };
  }
);

/**
 * Cloud Function: syncSubscriptionFromPlayStore
 *
 * Sincroniza el estado de la suscripción desde Google Play.
 * Útil para restaurar compras o verificar estado después de reinstalar.
 */
export const syncSubscriptionFromPlayStore = functions.https.onCall(
  async (
    request: functions.https.CallableRequest<{
      productId: string;
      purchaseToken: string;
    }>
  ): Promise<ValidatePurchaseResponse> => {
    if (!request.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Usuario no autenticado"
      );
    }

    const { productId, purchaseToken } = request.data;
    const userId = request.auth.uid;

    // Reutilizar lógica de validatePurchase
    return validatePurchase.run(
      { data: { userId, productId, purchaseToken }, auth: request.auth } as functions.https.CallableRequest<ValidatePurchaseRequest>,
      {}
    );
  }
);

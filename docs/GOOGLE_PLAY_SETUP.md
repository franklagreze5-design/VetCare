# Configuracion de Google Play Console para Suscripciones

## 1. Requisitos Previos

- Cuenta de desarrollador de Google Play ($25 USD, pago unico)
- App publicada en Google Play (puede ser en prueba interna)
- Cuenta de Google Cloud Platform vinculada

## 2. Crear Productos de Suscripcion

### Acceder a Monetizacion

1. Ir a [Google Play Console](https://play.google.com/console)
2. Seleccionar tu app
3. Ir a **Monetization > Subscriptions**

### Crear Premium Mensual

1. Click en **Create subscription**
2. Configurar:
   - **Product ID**: `premium_monthly`
   - **Name**: Premium Mensual
   - **Description**: Acceso completo a todas las funcionalidades

3. **Base plan**:
   - **ID**: `premium-monthly-base`
   - **Billing period**: Monthly
   - **Price**: $3,990 CLP (o equivalente)
   - **Renewal type**: Auto-renewing

4. **Offer** (Trial gratuito):
   - Click en **Add offer**
   - **Offer ID**: `premium-monthly-trial`
   - **Eligibility**: New customer acquisition
   - **Phases**:
     - Phase 1: Free for 7 days
     - Phase 2: $3,990 CLP/month

5. Click **Save** y luego **Activate**

### Crear Premium Anual

1. Click en **Create subscription**
2. Configurar:
   - **Product ID**: `premium_yearly`
   - **Name**: Premium Anual
   - **Description**: Acceso completo con descuento anual

3. **Base plan**:
   - **ID**: `premium-yearly-base`
   - **Billing period**: Yearly
   - **Price**: $39,990 CLP (equivalente a ~$3,332/mes = 16% descuento)
   - **Renewal type**: Auto-renewing

4. **Offer** (Trial gratuito):
   - **Offer ID**: `premium-yearly-trial`
   - **Eligibility**: New customer acquisition
   - **Phases**:
     - Phase 1: Free for 7 days
     - Phase 2: $39,990 CLP/year

5. Click **Save** y luego **Activate**

### Crear Family Plan

1. Click en **Create subscription**
2. Configurar:
   - **Product ID**: `family_plan`
   - **Name**: Plan Familiar
   - **Description**: Para familias con multiples mascotas

3. **Base plan**:
   - **ID**: `family-monthly-base`
   - **Billing period**: Monthly
   - **Price**: $5,490 CLP
   - **Renewal type**: Auto-renewing

4. **Offer**:
   - **Offer ID**: `family-trial`
   - **Phases**:
     - Phase 1: Free for 7 days
     - Phase 2: $5,490 CLP/month

5. Click **Save** y luego **Activate**

## 3. Configurar Real-time Developer Notifications (RTDN)

Las RTDN permiten recibir notificaciones push cuando hay cambios en suscripciones.

### Crear Topic en Cloud Pub/Sub

1. Ir a [Google Cloud Console](https://console.cloud.google.com)
2. Seleccionar el proyecto vinculado a tu app
3. Ir a **Pub/Sub > Topics**
4. Click **Create Topic**
5. Configurar:
   - **Topic ID**: `play-billing-notifications`
   - Dejar otras opciones por defecto
6. Click **Create**

### Crear Suscripcion Push

1. En el topic creado, click **Create Subscription**
2. Configurar:
   - **Subscription ID**: `play-billing-push`
   - **Delivery type**: Push
   - **Endpoint URL**: `https://[TU_REGION]-[TU_PROYECTO].cloudfunctions.net/playBillingWebhook`
3. Click **Create**

### Vincular en Google Play Console

1. Ir a Google Play Console
2. Ir a **Monetization setup**
3. En **Real-time developer notifications**:
   - **Topic name**: `projects/[TU_PROYECTO]/topics/play-billing-notifications`
4. Click **Save**
5. Click **Send test notification** para verificar

## 4. Configurar Licencias de Prueba

Para probar sin cargos reales:

1. Ir a **Settings > License testing**
2. Agregar emails de testers
3. Los testers veran "[Test purchase]" y no se les cobrara

## 5. Configurar Service Account para API

Necesario para validar compras desde el backend.

### Crear Service Account

1. Ir a Google Cloud Console
2. Ir a **IAM & Admin > Service Accounts**
3. Click **Create Service Account**
4. Configurar:
   - **Name**: `play-billing-validator`
   - **ID**: `play-billing-validator`
5. Click **Create**

### Otorgar Permisos

1. En el service account, ir a **Keys**
2. Click **Add Key > Create new key**
3. Seleccionar **JSON**
4. Guardar el archivo de forma segura

### Vincular en Google Play Console

1. Ir a **Settings > API access**
2. En **Service accounts**, click **Link existing service account**
3. Buscar `play-billing-validator`
4. Otorgar permisos:
   - **View financial data**
   - **Manage orders and subscriptions**
5. Click **Link**

## 6. Configurar Firebase

### Agregar Credenciales

1. En Firebase Console, ir a tu proyecto
2. Ir a **Project settings > Service accounts**
3. El JSON descargado de Google Cloud debe configurarse:

```bash
# Usando Firebase CLI
firebase functions:config:set \
  googleplay.service_account="$(cat path/to/service-account.json | base64)"
```

O usar secretos de Firebase:

```bash
firebase functions:secrets:set GOOGLE_PLAY_SERVICE_ACCOUNT
# Pegar el contenido del JSON
```

## 7. Verificacion

### Checklist

- [ ] Producto `premium_monthly` activo
- [ ] Producto `premium_yearly` activo
- [ ] Producto `family_plan` activo
- [ ] Trials de 7 dias configurados
- [ ] RTDN configurado y funcionando
- [ ] Service account con permisos correctos
- [ ] Credenciales en Firebase configuradas
- [ ] Licencias de prueba agregadas

### Probar Flujo Completo

1. Instalar app desde Play Store (prueba interna)
2. Iniciar sesion con cuenta de tester
3. Intentar suscribirse
4. Verificar que la compra se valida en backend
5. Verificar que el estado se actualiza en Firestore
6. Cancelar suscripcion y verificar que RTDN funciona

## 8. Precios por Region

Google Play maneja precios localizados automaticamente. Los precios que configures
se convertiran a otras monedas segun las tasas de cambio de Google.

Para Chile (CLP):
- Premium Mensual: $3,990 CLP (~$4.50 USD)
- Premium Anual: $39,990 CLP (~$45 USD)
- Family: $5,490 CLP (~$6.20 USD)

## 9. Mejores Practicas

1. **Siempre validar en backend** - Nunca confiar solo en el cliente
2. **Manejar grace periods** - Dar tiempo para resolver problemas de pago
3. **Implementar restaurar compras** - Para reinstalaciones y cambios de dispositivo
4. **Monitorear metricas** - Conversion, churn, LTV en Play Console
5. **A/B testing de precios** - Probar diferentes puntos de precio

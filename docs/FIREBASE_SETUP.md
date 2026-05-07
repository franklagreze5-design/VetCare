# Configuracion de Firebase para Sistema Freemium

## 1. Crear Proyecto Firebase

1. Ir a [Firebase Console](https://console.firebase.google.com)
2. Click **Add project**
3. Nombre: `petapp` (o tu nombre preferido)
4. Habilitar Google Analytics (opcional pero recomendado)
5. Click **Create project**

## 2. Agregar App Android

1. En el proyecto, click en el icono de Android
2. Configurar:
   - **Package name**: `com.petapp` (debe coincidir con tu app)
   - **App nickname**: PetApp Android
   - **Debug signing certificate SHA-1**: Obtener con:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
3. Descargar `google-services.json`
4. Colocar en `app/google-services.json`

## 3. Configurar Authentication

### Habilitar Email/Password

1. Ir a **Authentication > Sign-in method**
2. Habilitar **Email/Password**
3. Click **Save**

### Habilitar Google Sign-In (Opcional)

1. En Sign-in method, habilitar **Google**
2. Configurar el email de soporte
3. Agregar el SHA-1 de tu keystore de release

## 4. Configurar Firestore

### Crear Base de Datos

1. Ir a **Firestore Database**
2. Click **Create database**
3. Seleccionar modo de inicio:
   - **Start in production mode** (recomendado)
4. Seleccionar ubicacion mas cercana a tus usuarios
   - Para Chile: `southamerica-east1` (Sao Paulo)

### Desplegar Security Rules

1. En la raiz del proyecto, crear `firebase.json`:
```json
{
  "firestore": {
    "rules": "firebase/firestore.rules",
    "indexes": "firebase/firestore.indexes.json"
  },
  "functions": {
    "source": "firebase/functions"
  }
}
```

2. Crear `firebase/firestore.indexes.json`:
```json
{
  "indexes": [],
  "fieldOverrides": []
}
```

3. Desplegar reglas:
```bash
firebase deploy --only firestore:rules
```

## 5. Configurar Cloud Functions

### Instalar Firebase CLI

```bash
npm install -g firebase-tools
firebase login
```

### Inicializar Functions

```bash
cd android-pet-app
firebase init functions
```

Seleccionar:
- Usar proyecto existente
- TypeScript
- ESLint: Yes
- Instalar dependencias: Yes

### Instalar Dependencias

```bash
cd firebase/functions
npm install googleapis firebase-admin firebase-functions
```

### Configurar Variables de Entorno

Para desarrollo local:
```bash
cd firebase/functions
# Crear .env
echo "ANDROID_PACKAGE_NAME=com.petapp" > .env
```

Para produccion (usando secrets):
```bash
# Configurar service account de Google Play
firebase functions:secrets:set GOOGLE_PLAY_SERVICE_ACCOUNT

# Pegar el contenido del JSON del service account
```

### Desplegar Functions

```bash
firebase deploy --only functions
```

## 6. Estructura de Firestore

### Coleccion: users

```javascript
// Documento: users/{userId}
{
  email: "usuario@email.com",
  createdAt: Timestamp,
  updatedAt: Timestamp,
  
  subscription: {
    plan: "FREE" | "PREMIUM" | "FAMILY",
    status: "ACTIVE" | "CANCELED" | "EXPIRED" | "GRACE_PERIOD" | "ON_HOLD",
    productId: "premium_monthly" | "premium_yearly" | "family_plan" | null,
    purchaseToken: "token_string",
    originalPurchaseTime: Timestamp,
    expiryTime: Timestamp,
    autoRenewing: true,
    trialUsed: false,
    cancelReason: null
  },
  
  limits: {
    petsCount: 0,
    remindersThisMonth: 0,
    lastReminderReset: Timestamp
  }
}
```

### Coleccion: pets (subcoleccion de users)

```javascript
// Documento: users/{userId}/pets/{petId}
{
  name: "Max",
  species: "dog" | "cat" | "bird" | "other",
  breed: "Labrador",
  birthDate: Timestamp,
  weight: 25.5,
  photoUrl: "https://...",
  createdAt: Timestamp,
  updatedAt: Timestamp
}
```

### Coleccion: reminders (subcoleccion de users)

```javascript
// Documento: users/{userId}/reminders/{reminderId}
{
  petId: "pet_id_ref",
  title: "Vacuna anual",
  description: "Llevar a Max al veterinario",
  dueDate: Timestamp,
  reminderTime: Timestamp,
  isCompleted: false,
  type: "vaccine" | "medication" | "appointment" | "other",
  createdAt: Timestamp
}
```

## 7. Indices de Firestore

Si necesitas consultas complejas, agregar en `firestore.indexes.json`:

```json
{
  "indexes": [
    {
      "collectionGroup": "reminders",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "dueDate", "order": "ASCENDING" },
        { "fieldPath": "isCompleted", "order": "ASCENDING" }
      ]
    }
  ],
  "fieldOverrides": []
}
```

Desplegar:
```bash
firebase deploy --only firestore:indexes
```

## 8. Emuladores Locales

Para desarrollo local sin afectar produccion:

### Configurar Emuladores

```bash
firebase init emulators
```

Seleccionar:
- Firestore Emulator
- Functions Emulator
- Authentication Emulator

### Iniciar Emuladores

```bash
firebase emulators:start
```

### Conectar App a Emuladores

En tu codigo Android:

```kotlin
// Solo en debug
if (BuildConfig.DEBUG) {
    Firebase.firestore.useEmulator("10.0.2.2", 8080)
    Firebase.auth.useEmulator("10.0.2.2", 9099)
    Firebase.functions.useEmulator("10.0.2.2", 5001)
}
```

## 9. Monitoreo y Analytics

### Habilitar Crashlytics

1. En Firebase Console, ir a **Crashlytics**
2. Seguir instrucciones de setup
3. Agregar dependencias en `build.gradle`

### Configurar Analytics Events

Eventos recomendados para tracking de suscripciones:

```kotlin
Firebase.analytics.logEvent("paywall_viewed") {
    param("trigger", "pet_limit")
}

Firebase.analytics.logEvent("subscription_started") {
    param("plan", "premium_monthly")
    param("has_trial", true)
}

Firebase.analytics.logEvent("subscription_cancelled") {
    param("plan", "premium_monthly")
    param("reason", "user_cancelled")
}
```

## 10. Backup y Recuperacion

### Configurar Backups Automaticos

1. Ir a **Firestore > Backups**
2. Configurar schedule (diario recomendado)
3. Seleccionar bucket de Cloud Storage

### Exportar Datos Manualmente

```bash
gcloud firestore export gs://[BUCKET_NAME]/[EXPORT_PREFIX]
```

## 11. Checklist de Seguridad

- [ ] Security Rules desplegadas y probadas
- [ ] Indices necesarios creados
- [ ] Service account con permisos minimos
- [ ] Variables de entorno/secrets configurados
- [ ] Backups automaticos habilitados
- [ ] Monitoreo de errores configurado
- [ ] Rate limiting en Cloud Functions (si necesario)

## 12. Costos Estimados

Firebase tiene un plan gratuito generoso (Spark Plan):
- Firestore: 50K lecturas/dia, 20K escrituras/dia
- Functions: 2M invocaciones/mes
- Auth: Ilimitado

Para apps en crecimiento, considera el plan Blaze (pago por uso):
- Firestore: ~$0.06/100K lecturas
- Functions: ~$0.40/millon de invocaciones

Estima costos en: https://firebase.google.com/pricing

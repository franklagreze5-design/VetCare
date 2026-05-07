# Guía para Generar el .aab (Android App Bundle)

## Requisitos Previos

1. **Android Studio** Hedgehog o superior
2. **JDK 17** instalado
3. **Cuenta de Google Play Console**
4. **Proyecto Firebase** configurado

---

## Paso 1: Importar el Proyecto

```bash
# Clonar o copiar la carpeta android-pet-app/
# Abrir Android Studio
# File > Open > Seleccionar la carpeta android-pet-app/
```

---

## Paso 2: Configurar Firebase

1. Ve a [Firebase Console](https://console.firebase.google.com)
2. Crea un nuevo proyecto o usa uno existente
3. Agrega una app Android:
   - Package name: `com.petapp`
   - Nickname: `PetApp`
4. Descarga `google-services.json`
5. Colócalo en `app/google-services.json`

---

## Paso 3: Crear Keystore para Firma

```bash
# En terminal, navega a android-pet-app/
mkdir -p keystore

# Generar keystore
keytool -genkey -v -keystore keystore/release.keystore \
  -alias petapp \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Te pedirá:
# - Contraseña del keystore
# - Nombre y apellido
# - Unidad organizacional
# - Organización
# - Ciudad
# - Estado/Provincia
# - Código de país (CL para Chile)
```

**IMPORTANTE**: Guarda el keystore y las contraseñas de forma segura. Si los pierdes, no podrás actualizar tu app.

---

## Paso 4: Configurar Firma en Gradle

Edita `gradle.properties` y agrega tus credenciales:

```properties
RELEASE_STORE_FILE=keystore/release.keystore
RELEASE_STORE_PASSWORD=tu_password_keystore
RELEASE_KEY_ALIAS=petapp
RELEASE_KEY_PASSWORD=tu_password_key
```

Luego, actualiza `app/build.gradle.kts`:

```kotlin
android {
    // ... existing config ...

    signingConfigs {
        create("release") {
            storeFile = file(project.property("RELEASE_STORE_FILE") as String)
            storePassword = project.property("RELEASE_STORE_PASSWORD") as String
            keyAlias = project.property("RELEASE_KEY_ALIAS") as String
            keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

---

## Paso 5: Generar el .aab

### Opción A: Desde Android Studio (Recomendado)

1. **Build** > **Generate Signed Bundle / APK**
2. Selecciona **Android App Bundle**
3. Click **Next**
4. Selecciona tu keystore:
   - Key store path: `keystore/release.keystore`
   - Key store password: tu contraseña
   - Key alias: `petapp`
   - Key password: tu contraseña
5. Click **Next**
6. Selecciona **release** como build variant
7. Click **Create**

El .aab se generará en:
```
app/release/app-release.aab
```

### Opción B: Desde Terminal

```bash
# En la raíz del proyecto
./gradlew bundleRelease

# El .aab estará en:
# app/build/outputs/bundle/release/app-release.aab
```

---

## Paso 6: Verificar el Bundle

```bash
# Instalar bundletool (si no lo tienes)
# https://github.com/google/bundletool/releases

# Verificar el bundle
java -jar bundletool.jar validate --bundle=app/release/app-release.aab

# Ver contenido
java -jar bundletool.jar build-apks \
  --bundle=app/release/app-release.aab \
  --output=app-release.apks \
  --mode=universal
```

---

## Paso 7: Subir a Google Play Console

1. Ve a [Google Play Console](https://play.google.com/console)
2. Selecciona tu app o crea una nueva
3. **Production** > **Releases** > **Create new release**
4. Sube el archivo `.aab`
5. Completa la información de la versión
6. **Review** > **Start rollout to Production**

---

## Configuración de Productos In-App

Antes de publicar, configura los productos de suscripción en Google Play Console:

### En Google Play Console > Monetize > Subscriptions

| Product ID | Nombre | Precio | Periodo |
|------------|--------|--------|---------|
| `premium_monthly` | Premium Mensual | CLP $3,990 | 1 mes |
| `premium_yearly` | Premium Anual | CLP $39,990 | 1 año |
| `family_monthly` | Family Mensual | CLP $5,490 | 1 mes |

Para cada producto:
1. Click "Create subscription"
2. Product ID: usar exactamente los IDs de arriba
3. Name: nombre visible para usuarios
4. Description: descripción de beneficios
5. Add base plan:
   - Price: configurar precio en CLP
   - Renewal type: Auto-renewing
   - Grace period: 7 days (recomendado)
   - Free trial: 7 days (solo para premium_monthly)

---

## Checklist Final

- [ ] `google-services.json` en `app/`
- [ ] Keystore creado y guardado de forma segura
- [ ] `gradle.properties` con credenciales de firma
- [ ] Build exitoso sin errores
- [ ] .aab generado
- [ ] Productos de suscripción en Google Play Console
- [ ] Firebase Cloud Functions desplegadas
- [ ] API de Google Play Developer habilitada
- [ ] Service account JSON en Firebase (para validación server-side)

---

## Troubleshooting

### Error: "Cannot find google-services.json"
- Asegúrate de que el archivo está en `app/google-services.json`
- Verifica que el package name coincide con Firebase

### Error: "Keystore was tampered with"
- Verifica que la contraseña del keystore es correcta
- Regenera el keystore si es necesario

### Error de firma
- Verifica que `gradle.properties` tiene las rutas correctas
- Las rutas pueden ser absolutas o relativas al proyecto

### Build lento
```bash
# Limpiar cache
./gradlew clean
./gradlew --stop

# Build con más memoria
./gradlew bundleRelease -Dorg.gradle.jvmargs="-Xmx4g"
```

---

## Siguiente Paso

Después de subir el .aab, configura:
1. Real-Time Developer Notifications (RTDN) en Google Play Console
2. Webhook endpoint apuntando a tu Firebase Function
3. Testing con licencias de prueba

Ver `GOOGLE_PLAY_SETUP.md` para detalles de configuración de suscripciones.

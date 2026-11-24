# Configurar tu propia copia de FITNEX

La app compila sin configurar nada más que el SDK, pero para **iniciar sesión**, la **copia en la nube** y
**FITNEX AI** necesitas tus propios servicios. Nunca subas a GitHub ninguno de los archivos de este paso: ya están
en `.gitignore`.

## 1. Firebase (cuentas)

1. Crea un proyecto gratuito en <https://console.firebase.google.com>.
2. Añade una app Android con el paquete **`com.fitnex.app`** (o el tuyo si cambias `applicationId`).
3. Activa **Authentication → Correo electrónico/contraseña**.
4. Descarga `google-services.json` y guárdalo en `app/google-services.json`.

Sin este archivo el complemento de Google Services detiene la compilación.

## 2. Servidor (FITNEX AI y copia en la nube) — opcional

El servidor está en `backend/worker` y se ejecuta gratis en Cloudflare Workers.

```powershell
cd backend\worker
npm install
npx wrangler login
npx wrangler d1 create fitnex-sync          # copia el database_id en wrangler.toml
npx wrangler d1 migrations apply fitnex-sync --remote
npx wrangler secret put GEMINI_API_KEY      # tu clave de Google AI Studio
npm test
npx wrangler deploy
```

En `wrangler.toml` cambia `FIREBASE_PROJECT_ID` por el id de tu proyecto de Firebase: el servidor solo acepta
sesiones de ese proyecto.

Después indica la dirección de tu servidor al compilar la app:

```powershell
.\gradlew.bat assembleDebug "-Pfitnex.backendUrl=https://tu-worker.tu-cuenta.workers.dev/"
```

o guárdala en `~/.gradle/gradle.properties` como `fitnex.backendUrl=...`.

Hay una alternativa con Firebase Cloud Functions en `backend/functions` (requiere el plan Blaze).

## 3. Firma para publicar — opcional

Para generar tu propia clave de subida a Google Play:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\create-upload-key.ps1
.\gradlew.bat bundleRelease
```

El script crea `fitnex-upload.jks` y `keystore.properties`. Guarda la clave y las contraseñas en un lugar seguro:
si las pierdes no podrás actualizar la app en la tienda.

## 4. Regenerar el catálogo de ejercicios

El catálogo (`core/data/src/main/assets/catalog/catalog.json`) se genera desde `tools/catalog`:

```powershell
node tools\catalog\build-catalog.mjs
```

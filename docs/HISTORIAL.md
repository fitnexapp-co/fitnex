# FITNEX — BUILD YOUR BEST SELF

Aplicación Android de fitness, nutrición e IA (Kotlin + Jetpack Compose, Glassmorphism, Dark Mode).
Desarrollo por fases según el *Master Development Prompt V2.0 / V2.1*.

## Estado por fases

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Arquitectura y configuración del proyecto | ✅ Implementada (ver abajo) |
| 2 | Sistema visual y navegación | ✅ Implementada (ver "Fase 2") |
| 3 | Autenticación y perfiles (Firebase Auth, onboarding) | ✅ Implementada (falta prueba real con Firebase, ver "Fase 3") |
| 4 | Biblioteca de ejercicios y rutinas | ✅ Implementada (ver "Fase 4") |
| 5 | Registro de series, modo entrenamiento, progreso, calendario | ✅ Implementada (ver "Fase 5") |
| 6 | Mapas musculares 2D y 3D | ✅ Implementada (ver "Fase 6") |
| 7 | Nutrición y análisis de fotografías | ✅ Implementada; el análisis de fotos con IA se activa en la Fase 8 (ver "Fase 7") |
| 8 | Backend + Gemini (FITNEX AI) | ✅ Desplegado en Cloudflare Workers (gratis); chat verificado con Gemini (ver "Fase 8") |
| 9 | Sincronización, seguridad, offline | ✅ Copia en la nube (Cloudflare D1), restauración, exportación, cuota global de IA (ver "Fase 9") |
| 10 | Pruebas, optimización y compilación Android | ✅ Rutinas con FITNEX AI, contador por cámara, versión 1.0.0 optimizada con R8 (ver "Fase 10") |

> Las pantallas de Progreso, Nutrición, Entrenamiento y FITNEX AI muestran hoy estados vacíos
> honestos, **sin botones falsos**. Ninguna funcionalidad se marca como terminada si sólo existe su interfaz.

## Requisitos

- Android Studio (incluye JBR / JDK 25) y Android SDK con plataforma **android-37**.
- Gradle 9.9.1 (wrapper incluido, verificado por SHA-256).

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug testDebugUnitTest
```

## Arquitectura

Clean Architecture + MVVM, multimódulo con *convention plugins* (`build-logic/`).

```
app/                    Application, MainActivity, splash, NavHost, barra inferior, Hilt root
core/common             Dispatchers, ApplicationScope, AppResult/AppError/LoadState, Clock, IdGenerator (UUID)
core/model              Enums de dominio + AgePolicy (reglas para menores de edad)
core/designsystem       Tokens (color, tipografía, espaciado, radios, elevación), Glass, botones, campos,
                        anillo de progreso, estados, barra inferior, botón FITNEX AI, iconos propios
core/anatomy            Mapas musculares: atlas 2D (frente/espalda), cuerpo 3D procedural, rasterizador, tarjeta
core/database           Room: 16 tablas, DAOs, conversores, metadatos de sync, esquema exportado
core/datastore          Preferencias locales (tema, descanso, sonido, mapa 3D…)
core/network            Retrofit/OkHttp, AuthInterceptor, safeApiCall → AppError tipado, NetworkMonitor
feature/progress        Pestaña PROGRESO
feature/nutrition       Pestaña NUTRICIÓN
feature/workout         Pestaña ENTRENAMIENTO
feature/ai              Chat FITNEX AI (ruta con contexto de pantalla)
feature/calendar        Calendario (accesible desde Progreso y Entrenamiento)
feature/profile         Perfil y ajustes (ajustes reales en DataStore)
```

## Fase 2 — Sistema visual y navegación

**Sistema de diseño (`core/designsystem`)**
- Tokens: colores (oscuro y claro), tipografía, espaciados, radios, elevaciones.
- Glass: `GlassCard`, `glass()`, `FitnexBackground`.
- Controles: botones primario y secundario, campo de texto, `GlassIconButton`, `FitnexSegmentedControl`,
  `FitnexSwitchRow`, `FitnexSliderRow`, `StatTile`, `FitnexProgressRing`, estados de carga, vacío y error.
- Gráficas: `FitnexLineChart` (con selección por toque) y `FitnexBarChart`; la lógica de ejes está en `ChartMath`.
- Modales: `FitnexBottomSheet` y `FitnexConfirmDialog` (con variante destructiva).
- Calendario: `FitnexMonthCalendar` + `CalendarLegend`. *Sin marca* = sin actividad registrada,
  que es distinto de *descanso programado*.
- `rememberAppLocale()`: las fechas usan el idioma de la app (español), no el del dispositivo.

**Navegación**
- Exactamente 3 pestañas. Cabecera con **Calendario** (Progreso y Entrenamiento) y **Perfil y ajustes** (las 3).
- FITNEX AI en las pestañas y en el calendario, con su contexto (`AiScreenContext`). Se oculta en el
  chat y en los ajustes.
- Transiciones entre pantallas; cada pestaña conserva su pila y su scroll.

**Pantallas funcionales**
- *Perfil y ajustes*: tema (oscuro, claro o sistema; se aplica al instante), descanso predeterminado
  (30 s–5 min en pasos de 15 s), sonido y notificaciones del temporizador, preferencia del mapa 3D. Todo persiste.
  La sección de cuenta indica que el registro llega en la Fase 3; no tiene botones falsos.
- *Calendario*: navegación por meses, botón "Hoy", selección de día y estado que sobrevive al cierre del proceso.
  **Aún no lee actividad**: se conectará a `WorkoutCalendarRepository` en la Fase 5. Hoy es correcto
  que muestre "sin actividad registrada" porque todavía no se puede registrar nada.

**Pendiente o no verificado**
- Las gráficas, la hoja inferior, el diálogo y `StatTile` están implementados y su lógica tiene pruebas,
  pero **ninguna pantalla los usa todavía**, así que no se han revisado visualmente en el dispositivo.
  Se validarán al usarlos en las Fases 4 y 5.
- No hay icono de notificaciones en la cabecera porque aún no hay nada que notificar.
- `createComposeRule` (API v1) está obsoleta; la migración a v2 queda pendiente.

Reglas: los features dependen sólo de `core/*`, nunca entre sí. Los composables no contienen
lógica de negocio; el estado se expone con `StateFlow` inmutable desde ViewModels.

### Base de datos (Room, v1)

`users, muscles, exercises, exercise_muscles, favorite_exercises, workouts, workout_exercises,
workout_sessions, exercise_sets, scheduled_sessions, planned_rest_days, meals, food_items,
water_logs, ai_conversations, ai_messages`.

Decisiones clave:
- **IDs UUID generados en el dispositivo** → registros offline sin colisiones; reenviar es idempotente.
- **`SyncMeta`** en cada fila del usuario: `updated_at`, `sync_status`, `deleted_at`
  (borrado lógico hasta que el backend lo confirma; last-writer-wins por `updated_at`).
- **El historial sobrevive a las rutinas**: `workout_sessions.workout_id` es `SET_NULL` y guarda
  `workout_name_snapshot`; `exercise_sets` guarda `exercise_name_snapshot`.
- **Recuperación de sesiones**: `status IN_PROGRESS/PAUSED` + `current_exercise_index`.
- **Calendario**: `planned_rest_days` separa *descanso programado* de *día sin registros*.
- **Mapa muscular**: `muscles.model_2d_id` / `model_3d_id` enlazan el mismo músculo en SVG y GLB;
  `exercise_muscles.involvement` (PRIMARY 1.0 / SECONDARY 0.5 / STABILIZER 0.25) pondera las estadísticas.
- Sin `fallbackToDestructiveMigration`: cada cambio de esquema exige una migración (`FitnexMigrations`).
- Orden de sincronización (fase 9): catálogo → rutinas → sesiones → series (respeta las FKs).

### Seguridad desde la fase 1

- **La clave de Gemini nunca está en el APK.** El cliente sólo conoce `fitnex.backendUrl`
  (`gradle.properties`, obligatoriamente HTTPS). Flujo: App → backend autenticado → Gemini → validación → App.
- Logging HTTP sólo en debug, nivel BASIC (sin cuerpos), cabecera `Authorization` redactada.
- `allowBackup=false` + `data_extraction_rules`: los datos de salud no salen por la copia de Android.
- `AgePolicy`: edad desconocida o < 18 ⇒ sin objetivos de déficit ni estimaciones calóricas.

## Configuración pendiente (se documenta en cada fase)

| Recurso | Fase | Dónde |
|---|---|---|
| Proyecto Firebase + `app/google-services.json` | 3 | Consola Firebase |
| URL del backend (Cloud Run) | 8 | `fitnex.backendUrl` en `~/.gradle/gradle.properties` |
| Clave Gemini | 8 | **Sólo** Google Secret Manager (servidor) |
| Modelo anatómico GLB segmentado por músculo | 6 | `app/src/main/assets/models/` (requiere licencia) |

## Fase 3 — Autenticación y perfiles

**Módulos nuevos:** `core/auth` (interfaz `AuthRepository` + `FirebaseAuthRepository`), `core/data`
(`UserProfileRepository`, `SessionRepository`, `AccountManager`), `core/testing` (fakes para pruebas),
`feature/auth` (bienvenida, registro, inicio de sesión, recuperación, onboarding y edición de perfil).

**Flujo:** la app muestra una pantalla u otra solo según `SessionState`:
`SignedOut` → bienvenida · `NeedsOnboarding` → configuración inicial · `Ready` → las 3 pestañas.
Al registrarte, completar el onboarding, cerrar sesión o eliminar la cuenta, la pantalla cambia sola
y no queda ninguna pantalla de la cuenta anterior en el historial de navegación.

**Decisiones**
- **Edad mínima: 14 años** (`AgePolicy.MIN_REGISTRATION_AGE`), la edad de consentimiento digital en España.
  La edad se comprueba **antes** de crear la cuenta; un menor de 14 años no llega a enviar nada.
  De 14 a 17 años: experiencia protegida, con un aviso explícito en el onboarding.
- **Contraseña:** 8 o más caracteres, con letras y números (Firebase solo exige 6).
- **Sin enumeración de cuentas:** el inicio de sesión y la recuperación no revelan si un correo está registrado.
- **Verificación de correo:** se envía al registrarse. No bloquea el uso offline; se puede reenviar y
  comprobar desde Ajustes. En la Fase 8, el backend podrá exigirla para FITNEX AI.
- **Consentimiento de IA:** desactivado por defecto; se activa en el onboarding y se puede retirar en Ajustes.
- **Eliminar cuenta:** pide la contraseña. Primero se borra la cuenta en Firebase y, solo si eso funciona,
  se borran los datos locales de ese usuario (el catálogo compartido se conserva).
- **Cerrar sesión:** conserva los datos locales, separados por usuario; desde la Fase 9 también están en la nube.
- **Room v2:** columna `onboarding_completed_at`, con migración 1→2 probada con `MigrationTestHelper`.
- **Selectores de ruedas** (`core/designsystem/.../picker`): la fecha de nacimiento, la altura y el peso
  se eligen deslizando ruedas en un panel inferior, sin teclado. `FitnexDatePickerField` (día, mes por
  nombre, año; el día se ajusta en meses cortos), `FitnexNumberPickerField` (entero y, si se quiere, un
  decimal; "Quitar" en los opcionales) y `FitnexWheelPicker`, reutilizable para series y pesos.
- Firebase envía los correos en español (`setLanguageCode("es")`). El token de Firebase se
  adjunta a las llamadas al backend (`AuthTokenProvider`).

**Pendiente o no verificado**
- ✅ Registro real con Firebase verificado: el propietario creó una cuenta y completó el onboarding en
  el emulador. El perfil se guardó (nombre, fecha, altura y peso desde las ruedas), la sesión se mantiene
  tras dos reinicios en frío y Ajustes muestra la cuenta.
- La verificación del correo real y la eliminación de una cuenta real no se han probado a mano;
  con el Firebase simulado (`FakeAuthRepository`) sí están probadas.
- Exportar datos: hecho en la Fase 9 (Ajustes → Copia en la nube → Exportar mis datos).
- Iniciar sesión con Google: no se ha incluido (decidido en esta fase); necesita la huella SHA-1 en Firebase.
- Política de privacidad y términos: deben redactarse antes de publicar en Play Store.

## Fase 4 — Biblioteca de ejercicios y rutinas

**Catálogo** (`core/data/src/main/assets/catalog/catalog.json`, versión 1): 38 ejercicios y 20 músculos,
con instrucciones, técnica, errores frecuentes, equipamiento, tipo y nivel, y músculos principales,
secundarios y estabilizadores. `CatalogSeeder` lo carga en Room la primera vez y cuando sube `version`.
Los ids son estables (`bench_press`, `pectoralis_major`…), así que las rutinas y series que los usan
nunca se rompen. Una prueba comprueba la coherencia: ningún ejercicio sin músculo principal, ninguna
referencia rota y ningún músculo sin ejercicio. Las flexiones y la sentadilla son los únicos ejercicios
marcados como compatibles con el contador por cámara (Fase 5).

**Repositorios:** `ExerciseRepository` (biblioteca, detalle, músculos, favoritos por usuario) y
`RoutineRepository` (crear, editar y reordenar; el borrado es lógico y conserva el historial).

**Pantallas (pestaña Entrenamiento)**
- *Inicio*: acceso a la biblioteca y lista de rutinas (días, número de ejercicios, plantilla).
- *Biblioteca*: buscador que ignora tildes y busca también por músculo; chips de Favoritos,
  **Mi equipamiento** (solo ejercicios que puedes hacer con el material de tu perfil) y grupo muscular;
  hoja de filtros por equipamiento, tipo y nivel.
- *Detalle*: descripción, nivel, equipamiento, músculos (con la función de los principales), pasos,
  técnica, errores y favorito. FITNEX AI disponible con el contexto "Detalle de ejercicio".
- *Editor de rutinas*: nombre, días, plantilla, añadir ejercicios desde la biblioteca (selección múltiple),
  subir, bajar y quitar, y **series × repeticiones × descanso × peso objetivo con ruedas**. El descanso
  inicial sale de tu ajuste "Descanso predeterminado". Al eliminar se pide confirmación.

**Errores reales encontrados por las pruebas y corregidos**
- Los ejercicios elegidos en la biblioteca no llegaban al editor: se usaba el `SavedStateHandle` del
  ViewModel en lugar del de la entrada de navegación.
- Error de orden de inicialización en el editor (`loaded` declarado después de `init`): habría cerrado la app.
- Se podían añadir ejercicios duplicados si llegaban repetidos en la misma selección.

**Pendiente o no verificado**
- **Imágenes y animaciones** de los ejercicios: se necesitan recursos con licencia. Hasta entonces, el
  detalle lo indica claramente en lugar de mostrar una imagen falsa.
- Rutinas creadas por Gemini: en la Fase 8. Ejercicios personalizados: la base de datos ya los admite,
  pero aún no hay pantalla para crearlos.
- El editor y su panel de ruedas están probados de principio a fin con pruebas de interfaz (Robolectric);
  en el emulador revisé la pestaña, la biblioteca y el detalle, pero no el editor, para no interferir
  mientras el propietario usaba la app.
- Al reordenar o editar, los ejercicios quitados de una rutina se borran físicamente en local;
  la Fase 9 los convertirá en borrados lógicos para sincronizarlos.

## Fase 5 — Modo entrenamiento, progreso y calendario

**Room v3**: `workout_sessions.plan_json` (copia del plan al empezar, para poder retomar la sesión
aunque la rutina cambie) y `paused_at`. La migración 2→3 está probada y se verificó en el emulador
sobre datos v2 reales.

**Modo entrenamiento en vivo** (`feature/workout/live`), a pantalla completa, sin barra inferior ni IA:
- Se lanza con ▶ en cada rutina. Muestra el tiempo activo (sin pausas), el ejercicio actual, sus músculos,
  la **última sesión** de ese ejercicio y el progreso de series.
- **Registro de una serie con un toque**. Los valores propuestos vienen, por este orden, de la serie anterior
  de hoy, de la misma serie de tu última sesión o del objetivo de la rutina. Se cambian con ruedas.
  También hay "Repetir anterior", "+ Serie", saltar o cambiar de ejercicio, y editar o borrar series.
- **Descanso** basado en la hora de fin (no en ticks): −15 s, +15 s, pausar y saltar; pitido y vibración
  con la app abierta, notificación si está en segundo plano (`AlarmManager`; sin pedir el permiso de
  alarmas exactas, que Play reserva a las apps de reloj). El permiso de notificaciones se pide en contexto.
- Pantalla siempre encendida durante el entrenamiento.
- **Todo se guarda al instante en Room.** Si sales o la app se cierra, Entrenamiento muestra
  "Entrenamiento en curso · Continuar". Terminar sin series descarta la sesión.
- **Resumen**: duración, series, repeticiones, volumen (solo con carga), ejercicios, **récords personales**
  (peso máximo; 1RM estimado con Epley entre 1 y 12 repeticiones; repeticiones máximas en ejercicios con
  peso corporal) y músculos trabajados. La primera vez que haces un ejercicio nunca cuenta como récord.

**Progreso** (`feature/progress`): selector Semana / Mes / 3 meses / Año / Todo; entrenamientos, series,
repeticiones, tiempo, días activos y volumen; gráfica de frecuencia (con los periodos vacíos visibles);
grupos musculares por series ponderadas (principal 1, secundario 0,5, estabilizador 0,25; en cada grupo una
serie cuenta una sola vez, con su implicación más fuerte); ejercicios e
historial. Una cuenta nueva ve el panel completo con los valores reales en 0 y, arriba, el aviso "Aún no has
hecho ningún ejercicio" con el botón "Ir a entrenar"; el aviso desaparece tras el primer entrenamiento terminado. **Página por ejercicio** con evolución (peso máximo, 1RM estimado, volumen o repeticiones) e
historial de series. Todo sale de sesiones terminadas; no se muestran pasos, calorías ni datos de sensores.

**Calendario**: marca los entrenamientos realizados, las rutinas programadas, las no realizadas y los
descansos programados. El detalle del día muestra cada sesión serie a serie. Se puede programar una
rutina (hoy o en el futuro), cancelarla y marcar o quitar descanso. Al terminar un entrenamiento, la
rutina programada para ese día se marca como completada. **Un día sin datos nunca se trata como descanso.**

**Pendiente o no verificado**
- "ANALIZAR MI PROGRESO" (§20) llegará con Gemini en la Fase 8; no se ha añadido un botón sin función.
- Estadísticas de nutrición en el panel: en la Fase 7.
- El contador de repeticiones por cámara (§7) se hizo en la Fase 10.
- El temporizador de descanso no sobrevive a que Android cierre la app (la sesión sí).
- Comprobado a mano en el emulador (20/11/2026) con la cuenta del propietario: rutina "Prueba FITNEX",
  entrenamiento en vivo con ruedas de peso, descanso, resumen, Progreso y Calendario. Esa prueba destapó dos
  fallos ya corregidos: los grupos musculares contaban una serie varias veces (3 series → "Brazos 5") y las
  series sin carga ponían "peso corporal" también en ejercicios con barra (ahora: "sin peso").
- El permiso de notificaciones se rechazó en esa prueba; con él concedido, el aviso de fin de descanso aún
  no se ha visto en un dispositivo.

## Fase 6 — Mapas musculares 2D y 3D

Módulo nuevo `core/anatomy`, usado por Progreso y por el detalle de ejercicio. Los dos mapas comparten
datos y selección. El identificador de cada músculo en los mapas es su `Muscle.id` del catálogo, así que
`model2DIdentifier` y `model3DIdentifier` coinciden con él. Una prueba verifica que los 20 músculos del
catálogo existen en ambos mapas y que los mapas no dibujan músculos desconocidos.

**Mapa 2D** (`BodyAtlas2D`, `MuscleMap2D`): vista frontal y posterior dibujadas a mano en vectores, con estilo
de lámina de anatomía. Las proporciones son realistas (unas 8 cabezas) y los músculos se tocan entre sí,
separados por líneas finas, con líneas de detalle: abdomen, fibras del pectoral, serrato, cabezas del
cuádriceps, escápula y pliegue glúteo. Cada músculo es una forma independiente, izquierda y derecha. Al tocar se selecciona el músculo; los músculos profundos
(romboides bajo el trapecio) ganan sobre el que los rodea. La selección se anima con un relleno y un pulso.
Código de colores del §5: blanco = sin trabajar, rojo = trabajado o seleccionado (más rojo = más trabajo),
gris oscuro = resto del cuerpo.

**Mapa 3D** (`HumanBody`, `AtlasTexture`, `MuscleSegmentation`, `BodyMesh`, `MuscleMap3D`): cuerpo humano
realista esculpido, unos 7.000 vértices.
- **Origen:** modelo aportado por el propietario (`hy3d-model.glb`, 20/11/2026; por su nombre, generado con
  Hunyuan3D). El GLB traía tres figuras (frente, perfil, espalda); solo se usa la que mira de frente,
  normalizada a 180 cm. Está en `core/anatomy/src/main/assets/anatomy/body.obj`, con cabecera de origen.
  Las articulaciones (hombro, codo, muñeca, cadera, rodilla, tobillo, pelvis, cuello) se leyeron de un
  render frontal con cuadrícula de 10 cm; la profundidad se mide de la malla.
- **Músculos separados como en el 2D:** el atlas 2D se rasteriza en dos imágenes de etiquetas, frente y
  espalda (`AtlasTexture`), con las mismas curvas y líneas divisorias. Cada vértice recibe su posición en el
  atlas, alineada con las articulaciones del modelo:
  - brazos: a lo largo de hombro → codo → muñeca, con la distancia medida también en el plano frontal
    para que el tríceps de un brazo grueso no caiga en el dorsal;
  - piernas: enderezadas desde la cadera;
  - tronco: altura por puntos de referencia y anchura suavizada;
  - entre tronco y piernas hay una banda de mezcla, para que no quede costura.
  Cada píxel interpola esa posición y lee el músculo y la línea divisoria de la imagen, así que cada
  músculo tiene el contorno exacto del 2D sobre el cuerpo realista. En los costados, si una vista no tiene
  músculo se usa la otra. Los triángulos que cruzan una costura (axila, muñeca, cuello) usan el músculo de su
  vértice más cercano.
- **Dibujo:** rasterizador por software con búfer de profundidad y sombreado suave, sin dependencias nuevas.
- **Selección:** rayo contra triángulo, que lee el mismo píxel del atlas.
- **Carga:** el modelo se lee una sola vez por proceso, en segundo plano, cuando se abre la vista 3D. Si no
  se puede cargar, la tarjeta pasa sola a 2D.
- **Gestos:** se gira deslizando de lado, se acerca pellizcando y se mueve la cámara con dos dedos. Los
  deslizamientos verticales siguen desplazando la página. Frente y Espalda giran con una transición suave, y
  "Centrar" restablece la vista. En dispositivos con poca RAM (`isLowRamDevice`) se muestra solo el 2D.

**Licencia del modelo 3D:** el modelo lo aportó el propietario. Si se generó con Hunyuan3D, antes de
publicar hay que revisar la licencia de Tencent Hunyuan 3D, que tiene restricciones de territorio y de uso.
El código no depende de este modelo concreto: `BodyAssetBuilderTest` (herramienta de desarrollo, con las
variables `FITNEX_GLB` y `FITNEX_ASSET_OUT`) genera el asset desde cualquier GLB de una figura de pie.
`GlbPreviewTest` sirve para previsualizarlo y leer las articulaciones con cuadrícula.

**Decisión técnica:** el §5 recomienda glTF/GLB con Filament/SceneView. Se carga el GLB con un lector propio
y se dibuja sin Filament, para no añadir una dependencia nativa grande. Los músculos son regiones de la
superficie con los contornos del atlas, no mallas anatómicas internas separadas.

**Tarjeta "Mapa muscular"** (Progreso y detalle de ejercicio):
- Selector 2D/3D y Frente/Espalda, y leyenda de colores.
- Información del músculo tocado: nombre, función y una línea de contexto. En Progreso son sus series
  ponderadas y sesiones del periodo; en el ejercicio, su papel (principal, secundario o estabilizador).
- Botón "Ver detalle".
- En Progreso el color sale de `GlobalStats.muscles` del periodo elegido, así que se actualiza solo al
  terminar entrenamientos. En el detalle de ejercicio, el principal se pinta en rojo pleno y el secundario
  más suave.

**Página de músculo** (`MuscleDetailRoute`, en `feature/progress`):
- Mapa con el músculo resaltado y su función.
- Sesiones, series ponderadas, última vez y frecuencia (sesiones por semana en las últimas 4 semanas).
- Evolución de las últimas 12 semanas, en series ponderadas o volumen ponderado (kg × participación).
- Ejercicios principales y secundarios, que abren el detalle de ese ejercicio.
- Historial de sesiones con solo las series que lo trabajaron.
- Accesos a otros músculos del mismo grupo.
- FITNEX AI con el contexto nuevo "Detalle muscular".
- Todo sale de series reales (`MuscleStatsCalculator`); un músculo sin entrenar muestra ceros y lo dice.

Se llega a la página desde el mapa de Progreso, desde el mapa o la lista de músculos del detalle de ejercicio
y desde otra página de músculo.

**Pendiente o no verificado**
- Probado en el emulador (tema oscuro): 2D frente y espalda, selección, cambio a 3D, giro, Espalda, selección
  en 3D, detalle de ejercicio con músculos resaltados y página de músculo. El pellizco y el movimiento con dos
  dedos no se pueden simular bien con adb: no se han probado a mano.
- El tema claro del mapa no se ha visto todavía en pantalla.
- Ni el rendimiento del 3D en un móvil real de gama baja ni la versión solo 2D (`isLowRamDevice`) se han
  medido o visto en un dispositivo.
- El 3D con el modelo del propietario y los contornos del atlas se comprobó en las imágenes de prueba de frente
  y de espalda (`HumanBodyTest` genera PNG con `FITNEX_RENDER_DIR`, con el mismo dibujo que la app). En el
  emulador aún no se ha visto: estaba cerrado.
- Pequeños defectos conocidos en el 3D: alguna mancha gris donde el brazo toca el tronco y en la nuca, y el
  recto abdominal sale algo más ancho en el centro que en el 2D.

## Fase 7 — Nutrición

**Base de datos de alimentos** (`core/data/src/main/assets/nutrition/foods.json`): 212 alimentos habituales con
nombre en español, categoría (18), alérgenos (con las mismas claves que el perfil), una ración típica y 18
nutrientes por 100 g: energía, proteínas, carbohidratos, grasas, fibra, azúcares, grasa saturada, sodio,
potasio, calcio, hierro, magnesio, zinc, vitaminas A, C, D y B12, y folato.
- **Origen:** USDA FoodData Central, SR Legacy (abril de 2018), dominio público (CC0 1.0). Se descargó con
  permiso del propietario el 20/11/2026.
- **Cómo se construyó:** cada alimento se enlazó por su descripción exacta de USDA (se guarda `fdc_id`).
  Se descartaron los que no tenían equivalente fiable (bebida de avena, paella…) en lugar de inventarlos.
- **Uso:** se carga en memoria una sola vez. La app indica la fuente en el buscador.

**Diario** (`feature/nutrition`):
- **Navegación por días**, con desayuno, almuerzo, cena, meriendas y bebidas (§6).
- **Resumen del día:** proteínas, carbohidratos, grasas, fibra y alimentos distintos.
  - Adultos: también la energía, marcada como "≈ … kcal · estimación".
  - Menores (`AgeProfile.showCalorieEstimates = false`): nunca ven calorías ni objetivos; ven un mensaje
    sobre variedad, nutrientes e hidratación. No hay objetivos de calorías para nadie.
- **Hidratación:** +250 ml y +500 ml, deshacer el último y total del día.
- **Buscador:** sin acentos ni mayúsculas, con filtro por categoría y alimentos recientes.
- **Raciones:** en unidades domésticas ("1 × unidad mediana") o en gramos, con ruedas, nutrientes en vivo
  y aviso si el alimento contiene un alérgeno marcado en el perfil.
- **Entradas:** se pueden editar y eliminar. También hay alimentos personalizados, con los valores que
  introduce el usuario, guardados como estimación suya.
- **Recetas:** se crean con ingredientes del catálogo y su número de raciones. Muestran nutrientes y
  alérgenos por ración y se añaden a una comida eligiendo las raciones.
- **Resumen de la semana** (§20, solo descriptivo):
  - días con registros, alimentos distintos, días con desayuno y grupos de alimentos;
  - gráficas de agua, proteínas y fibra;
  - medias por día registrado de macronutrientes y algunos minerales y vitaminas;
  - lo más comido.

**Fotos de comidas:** cámara (`TakePicture` + `FileProvider`, sin permiso de cámara) o galería (selector del
sistema, sin permisos). La foto se copia al almacenamiento privado de la app, se queda solo en el móvil y se
asocia a la comida elegida. **El análisis automático (alimentos, raciones, nutrientes, confianza) llega con
FITNEX AI en la Fase 8**, porque necesita el servidor seguro con Gemini. Mientras tanto, la app lo dice
claramente y el usuario añade los alimentos a mano. No hay ningún botón "Analizar" que no funcione.

**Base de datos v4** (migración probada): `food_items.food_id` enlaza con el catálogo, y hay tablas nuevas
`recipes` y `recipe_items` (que se borran en cascada). Las recetas también se borran al eliminar la cuenta.

**Pendiente o no verificado**
- El análisis de fotos con Gemini (§6), en la Fase 8.
- Las preferencias alimentarias (vegetariano, vegano…) se guardan en el perfil, pero el buscador todavía
  no filtra por ellas; las alergias sí se avisan.
- En el emulador se vieron el diario (cuenta de menor: sin calorías), el buscador ("platano" encuentra
  "Plátano"), el editor de raciones (1 plátano = 118 g → 27,0 g de carbohidratos, igual que USDA) y el
  resumen semanal vacío. No se guardaron datos en la cuenta del propietario: un toque accidental registró
  500 ml de agua y se deshizo al momento.
- La cámara y la galería no se han probado a mano en el emulador.

## Fase 8 — Servidor seguro y FITNEX AI

**Servidor** (`backend/`, Firebase Cloud Functions v2, Node 22, región `us-central1`): una función HTTPS `api`.
- **Endpoints:** `GET /v1/health`, `POST /v1/ai/chat` y `POST /v1/ai/analyze-food`.
- **Cada petición:**
  - verifica el token de Firebase del usuario;
  - limita el uso (10 por minuto y 150 al día por usuario y por instancia);
  - valida la entrada (longitud, tipo y tamaño de imagen de 4 MB como máximo);
  - llama a Gemini.
- **Clave:** la de Gemini está en Secret Manager (`GEMINI_API_KEY`) y nunca va en la app. El modelo es un
  parámetro del servidor (`GEMINI_MODEL`, por defecto `gemini-2.5-flash`).
- **Reglas de las instrucciones:** solo en el servidor.
  - En español, orientación general y sin diagnósticos.
  - Para menores (y edad desconocida), sin calorías, déficit, pérdida de peso ni comentarios corporales.
  - No inventar datos.
- **Fotos:** se piden con un esquema JSON y la respuesta se limpia: valores acotados, kcal a 0 para menores
  y, si no se identifica la comida, una pregunta en lugar de alimentos inventados. No se guardan en el
  servidor y no se registran cuerpos de petición en los logs.
- **Pruebas:** `cd backend/functions && npm test` (5 pruebas: validación, reglas para menores, limpieza de
  la salida del modelo, límite de uso).

**App:**
- **Chat real** (`feature/ai`):
  - pide consentimiento explícito antes del primer uso (`aiConsentAt` del perfil);
  - envía solo los datos de la pantalla de origen: estadísticas del mes desde Progreso, Estadísticas o
    Calendario; el diario de hoy desde Nutrición; nada personal desde Entrenamiento o Biblioteca;
  - guarda la conversación en Room, con sugerencias por pantalla, "Nueva conversación", errores claros
    (sin conexión, límite, sin desplegar) y reintento sin duplicar la pregunta.
- **"Analizar mi progreso"** en Progreso (§20) abre FITNEX AI con ese contexto.
- **Fotos de comida:**
  - el botón "Analizar con FITNEX AI" envía la foto (reducida a 1280 px como máximo, JPEG);
  - el editor muestra cada alimento con su confianza, permite quitarlo o cambiar los gramos (los
    nutrientes se recalculan) y lo guarda como estimación con su confianza;
  - si no se identifica, muestra la pregunta de la IA.
- La URL del servidor está en `gradle.properties` (`fitnex.backendUrl`).

**Despliegue en uso: Cloudflare Workers (gratis, sin tarjeta)** — `backend/worker`, desplegado el
2026-11-20 en `https://fitnex-api.fitnex-api-worker.workers.dev` (es la URL de `fitnex.backendUrl`).
- Mismo contrato y la misma lógica (`backend/functions/src/logic.js`). El token de Firebase se verifica con
  `jose` contra las claves públicas de Google (emisor y audiencia `fitnex-fd961`).
- **Modelo:** `GEMINI_MODEL = "gemini-flash-latest"` en `wrangler.toml` (alias que Google mantiene; el
  nombre fijo `gemini-2.5-flash` ya devolvía 404). Si Gemini responde 500/503 (sobrecarga, frecuente en el
  plan gratis), el servidor reintenta una vez y luego usa `gemini-flash-lite-latest`.
- Los logs registran solo el estado y el motivo de error de Google (p. ej. `API_KEY_INVALID`), nunca
  mensajes ni fotos.
- **Redesplegar:** `cd backend\worker; npm install; npx wrangler deploy`.
- **Cambiar la clave:** `npx wrangler secret put GEMINI_API_KEY` y pegarla **una sola vez** (empieza por
  `AIza`). Logs en vivo: `npx wrangler tail`.
- **Privacidad (plan gratis de Gemini):** Google puede usar las peticiones del plan gratuito para mejorar
  sus productos. Antes de publicar la app hay que pasar a un plan de pago de Gemini o avisarlo en la
  política de privacidad y en el consentimiento.

**Alternativa: Firebase Functions** (`backend/functions`, requiere el plan Blaze):
`firebase functions:secrets:set GEMINI_API_KEY` y `firebase deploy --only functions`; después, cambiar
`fitnex.backendUrl` a `https://us-central1-fitnex-fd961.cloudfunctions.net/api/`.

**Verificado el 2026-11-20 en el emulador** con la cuenta del propietario: el chat responde de verdad,
usa el contexto de Progreso y respeta las reglas para menores. Las respuestas en Markdown ligero se muestran
con negritas y viñetas (`lightMarkdown`).

**Pendiente o no verificado**
- El análisis de fotos con Gemini aún no se ha probado con una foto real.
- Límite de uso: ráfagas por instancia y, desde la Fase 9, cuota diaria global por usuario en D1.

## Fase 9 — Sincronización, seguridad y modo sin conexión

**Modelo:** la app es *local-first*: Room sigue siendo la fuente de verdad y todo funciona sin conexión.
La nube es una copia por usuario en **Cloudflare D1** (gratis, sin tarjeta), servida por el mismo Worker
que FITNEX AI y protegida con el token de Firebase (cada usuario solo lee y escribe bajo su `uid` verificado).

**App** (`core/database/sync`, `core/data/sync`):
- **Qué se sincroniza:** perfil, rutinas (con sus ejercicios), sesiones y series, calendario y descansos,
  comidas y alimentos, agua, recetas (con ingredientes) y favoritos (`SyncSchema`).
- **Qué no:** el catálogo (va en el APK), las conversaciones con FITNEX AI y los archivos de fotos de comidas
  (se quedan en el dispositivo; sí van en la exportación).
- **Cambios pendientes:** cada fila tiene `SyncMeta`; una fila está pendiente mientras `sync_status != 'SYNCED'`.
- **Borrados:** los borrados lógicos viajan como borrado. Los borrados físicos los registran **triggers de
  SQLite** en `sync_tombstones`, así ningún DAO puede olvidarlo. Al aplicar cambios de la nube, una fila en
  `sync_apply_lock` silencia los triggers para no devolver el eco.
- **Conflictos:** gana la última escritura (`updated_at`); en empate gana el borrado. Las mismas reglas en el
  móvil y en el servidor. Una referencia opcional a una rutina que no existe se pone a `NULL` (el historial
  conserva el nombre). Filas imposibles de guardar se cuentan y se omiten, sin romper la sincronización.
- **Cuándo:** al iniciar sesión, al abrir la app y cuando vuelve la conexión, 5 s después de cada cambio
  local y cada 15 min mientras está abierta. "Sincronizar ahora" en Ajustes. Sin WorkManager: todo cambio se
  hace dentro de la app y lo pendiente se sube la próxima vez que se abra con conexión.
- **Móvil nuevo:** si inicias sesión en un dispositivo sin tus datos, la app muestra "Recuperando tus datos
  de la nube…" (`SessionState.Restoring`, máx. 25 s) antes de decidir si hace falta el onboarding.
- **Eliminar cuenta:** primero se borra la copia en la nube (después el token ya no serviría), luego la
  cuenta de Firebase y por último los datos locales. Si Firebase falla (contraseña), todo se vuelve a subir.
- **Exportar mis datos:** JSON con todo lo del usuario en el dispositivo, guardado donde elija (selector
  del sistema).
- **Room v5:** tablas `sync_tombstones`, `sync_state` (cursor y última copia) y `sync_apply_lock`, más los
  triggers (migración 4→5 probada).

**Servidor** (`backend/worker/src/sync.js`, D1 `fitnex-sync`, esquema en `backend/worker/migrations/`):
- `POST /v1/sync/push` (máx. 200 registros y 1,8 MB), `POST /v1/sync/pull` (páginas de 500 por número de
  secuencia) y `POST /v1/sync/erase`.
- Cada operación son 1 o 2 sentencias SQL (límite del plan gratis); los registros viajan como un array JSON
  expandido con `json_each`, y el envío es atómico (`batch`).
- **Cuota global de IA:** 150 peticiones por usuario y día en la tabla `ai_usage`, compartida por todas las
  instancias, además del límite de ráfagas.
- Pruebas: `cd backend/worker && npm test` ejecuta el SQL real contra SQLite (`node:sqlite`).
- Crear o actualizar el esquema: `npx wrangler d1 migrations apply fitnex-sync --remote`.

**Seguridad:** clave de Gemini solo en el servidor; HTTPS obligatorio (tráfico en claro bloqueado por
defecto con `targetSdk` 36); sin copia de Android de los datos de salud (`allowBackup=false`); los logs del
servidor nunca guardan cuerpos de petición.

**Verificado el 2026-11-20 en el emulador** con la cuenta del propietario: la migración 4→5 conservó los
datos, la copia inicial subió perfil, rutinas y agua a D1, y Ajustes muestra "Última copia: hace un momento
· Todo está guardado en la nube".

**Pendiente o no verificado**
- La restauración en un segundo dispositivo real no se ha probado a mano (sí en `CloudSyncTest`, con dos
  bases de datos y un servidor en memoria con las mismas reglas).
- "Exportar mis datos" no se ha probado a mano con el selector de archivos.
- Los relojes de los dispositivos deciden los conflictos: un móvil con la hora muy adelantada ganaría.
- Datos sensibles de salud en un proveedor de nube: hay que mencionarlo en la política de privacidad
  antes de publicar (Fase 10).
## Rediseño visual y personalización (entre las Fases 9 y 10)

Rediseño completo de la estética, sin tocar lógica, rutas, base de datos ni APIs. Todo pasa por
`core/designsystem`, así que las pantallas heredan el estilo sin reescribirse.

- **Identidad:** fondo `#0B0B0D`, tarjetas grafito con bordes finos, texto blanco suave y gris, acento violeta
  `#8B5CF6` por defecto. Modo claro con fondo gris suave y tarjetas blancas. Textos en tipo oración (sin
  mayúsculas forzadas). Iconos redondeados.
- **Tipografía:** Inter (SIL OFL 1.1, `core/designsystem/src/main/res/font/inter_variable.ttf`; licencia en
  `assets/licenses/inter-OFL.txt`): una escala corta, títulos grandes en negrita, números tabulares.
- **Color de acento** (Perfil → Apariencia): 8 colores predefinidos, color personalizado con tono,
  saturación y brillo (vista previa en vivo en toda la app, se guarda al pulsar "Aplicar") y "Restaurar
  violeta FITNEX".
  - `fitnexColors(dark, accent)` deriva todos los roles del acento; fondo, superficies y texto no cambian.
  - En el tema claro el acento se oscurece si hace falta para un contraste ≥ 3:1.
  - El texto sobre el acento elige blanco o tinta según cuál se lea mejor.
  - El mapa muscular usa el acento como color de "trabajado".
- **Glass (predeterminado):** tarjetas sólidas, sin desenfoques; lo más eficiente.
- **Liquid Glass (opcional, apagado por defecto):**
  - **Barra inferior y botón de FITNEX AI:** desenfoque real del contenido que tienen detrás
    (`GraphicsLayer` + `BlurEffect`, Android 12+ y dispositivos sin poca RAM).
  - **Controles, menús, modales y tarjetas destacadas:** cristal translúcido con brillo y borde luminoso,
    sin desenfoque.
  - **Seguridad del dibujo:** el contenido de la pantalla es la fuente del desenfoque y nunca muestrea su
    propio fondo; hacerlo provocaba un bucle de dibujo, detectado y corregido en el emulador.
- **Reducir animaciones:**
  - Quita las entradas, los desplazamientos, el crecimiento de las gráficas, los rebotes y las transiciones
    largas. Las pulsaciones pasan a un simple atenuado.
  - Se activa sola si Android tiene la escala de animación a 0.
  - Todas las animaciones pasan por `FitnexMotion`, `pressFeedback` y `enterAnimation`.
- **Perfil:** tarjeta con foto, nombre, correo y "Editar perfil".
  - Debajo, "Configuración": Apariencia, Cuenta, Copia en la nube, Entrenamiento y Mapa muscular.
  - Se conservan todas las opciones anteriores.
- **Foto de perfil opcional:**
  - Se elige con el selector de fotos del sistema (sin permisos), con vista previa circular antes de
    aplicarla. Se puede cambiar o quitar.
  - Se corrige la orientación EXIF y se reduce a 1024 px.
  - Se guarda solo en este dispositivo (no se sincroniza) y se borra al eliminar la cuenta.
  - Aparece en la cabecera de las pestañas. Sin foto, se muestra el icono de persona de siempre.
- **Gráficas:** líneas finas y cuadrícula discontinua, barras redondeadas (la máxima en acento pleno),
  punto seleccionado con guía; el color sigue al acento.
- **Preferencias nuevas** (DataStore, sin tocar Room): `accent_argb`, `custom_accent_argb`,
  `surface_style`, `reduce_motion`, `profile_photo_<uid>`.

**Verificado en el emulador el 2026-11-20:**
- Progreso, Nutrición, Entrenamiento y Perfil en oscuro y violeta.
- Cambio a azul y tema claro en vivo.
- Liquid Glass con el desenfoque real de la barra.
- La configuración del propietario se dejó como estaba (oscuro, violeta, Glass).

**Pendiente o no verificado:**
- El selector de fotos no se ha probado a mano: el emulador no tiene fotos.
- El selector de color personalizado no se ha arrastrado a mano. Sus cálculos de color sí están probados
  en `AccentPaletteTest`.
- Algunas frases de cabecera heredadas siguen en inglés ("Build your best self").
## Fase 10 — Funciones pendientes, pruebas, optimización y compilación

**Rutinas con FITNEX AI** (Entrenamiento → "Crear con FITNEX AI"):
- **Qué se envía:** objetivo, duración y una nota opcional. Solo van los ejercicios de la biblioteca que
  permite el equipamiento del perfil.
- **Servidor** (`backend/worker/src/routine.js`, `POST /v1/ai/routine`):
  - Esquema JSON estricto.
  - Descarta ids inventados y repetidos, y limita series a 1-6, repeticiones a 1-30 y descansos a 30-300 s.
  - Reglas para menores: técnica primero, máximo 4 series, sin fallo ni objetivos estéticos.
  - Cuenta para la cuota diaria.
  - Usa Gemini Flash-Lite; con Flash se agotaban los 60 s de espera de la app.
- **En la app:** la propuesta se revisa y solo se guarda con "Guardar rutina". Queda como una rutina
  normal y editable, con la etiqueta "FITNEX AI" y la sección "Por qué esta rutina" en el editor.
- **Verificado en el emulador:** propuesta de 5 ejercicios del catálogo, adaptada a menor de edad. No se
  guardó.

**Contador de repeticiones por cámara** (§7):
- **Cómo funciona:** CameraX y ML Kit Pose Detection funcionan en el propio móvil (no se graba ni se envía
  nada). El ángulo de una articulación pasa por un contador con histéresis que exige el recorrido
  completo.
- **9 ejercicios:** flexiones, sentadilla, sentadilla goblet, zancada, curl con barra, curl martillo,
  press de hombros con mancuernas, elevaciones laterales y puente de glúteo. Catálogo v2; una prueba
  verifica que coincide.
- **En el entrenamiento en vivo:** "Contar con cámara".
  - Pide el permiso solo al abrirlo.
  - "Usar N repeticiones" pasa el número a la serie, que sigue siendo editable; se guarda con
    `SetSource.CAMERA`.
- **No verificado:** los umbrales no se han validado con personas reales (las pruebas usan señales
  sintéticas). El emulador tiene cámara virtual: hay que probarlo en un móvil.

**Compilación de versión** (1.0.0, `versionCode` 10):
- **R8:** minificación y reducción de recursos. Probada en el emulador sin cierres: arranque, pestañas,
  biblioteca, IA, calendario, Perfil y sincronización.
- **Tamaño:** AAB 34,7 MB; APK universal 84 MB (ML Kit incluye una librería de 15-20 MB por
  arquitectura); APK arm64 30,6 MB (`.\gradlew.bat :app:assembleRelease "-Pfitnex.abi=arm64-v8a"`).
- **Firma:** a partir de `keystore.properties` (ignorado por git). `scripts\create-upload-key.ps1` crea la
  clave de subida y la contraseña la escribe el propietario.
- **Antes de publicar:** crear la clave, compilar `:app:bundleRelease`, revisar `PRIVACIDAD.md`
  (borrador) y rellenar la ficha de seguridad de datos de Google Play.
## Mejoras de uso (después de la Fase 10)

- **Progreso:**
  - Tarjeta de bienvenida compacta ("Tu progreso empieza hoy").
  - Estadísticas con el número como protagonista y selector de periodo compacto.
  - "Analizar mi progreso" como botón tonal secundario.
  - Estados vacíos discretos.
  - Botón de FITNEX AI de 52 dp, con un único destello al aparecer.
- **Mapa muscular:**
  - Figura blanca tipo estatua y más humana: cabeza con orejas y mandíbula, manos con dedos.
  - Los músculos trabajados se pintan en el color de acento con un halo; el seleccionado, con un anillo.
  - Mapa de 440 dp con selectores compactos.
  - El 3D usa la misma paleta.
- **Objetivo semanal, racha y 9 logros** (`AchievementsCalculator`):
  - Solo con entrenamientos reales; nada de peso ni calorías, así que sirve también para menores.
  - Objetivo editable (1-7), guardado en `weekly_goal`.
- **63 ejercicios con fotos**, de inicio y final:
  - Fotos de free-exercise-db (dominio público, Unlicense), en `core/data/src/main/assets/exercise_media`.
    236 ejercicios con foto (catálogo v5): 63 originales + 173 emparejados por nombre en inglés y revisados a mano;
    las nuevas van a 600 px, JPEG calidad 78. Se ven en la biblioteca, el detalle, el editor de rutinas y el
    entrenamiento en vivo.
  - Catálogo v6: otros 265 ejercicios muestran la foto de una variante muy parecida (`"mediaFrom"` en
    catalog.json → `mediaUri` terminado en `#ref`), con la etiqueta «Foto de referencia». En total 501 de 611
    tienen foto; los 110 restantes (natación, burpees, boxeo…) no tienen ninguna parecida y muestran su icono.
    `ExerciseMediaConsistencyTest` comprueba que todas las fotos enlazadas existen.
  - Widgets de inicio: «FITNEX» (objetivo semanal), «Racha» (2×2) y «Racha y estadísticas» (4×2).
  - En la biblioteca aparecen como miniaturas; en el detalle, alternan como una animación (con "Reducir
    animaciones", lado a lado).
  - 25 ejercicios nuevos redactados en español. Catálogo versión 3.
- **Ejercicios personalizados:** "+" en la biblioteca.
  - Nombre, músculos, equipamiento y tipo.
  - Se sincronizan con la nube (Room v6, trigger nuevo) y se pueden ocultar.
- **Hoy toca:** en Entrenamiento, la rutina de hoy (calendario o días de la rutina) con un botón
  "Empezar" (`TodayPlanRepository`).
- **Recordatorios de entrenamiento:**
  - Perfil → Entrenamiento: activar y elegir hora.
  - WorkManager con Hilt avisa solo los días con rutina, si aún no se ha entrenado y no es descanso.
- **Widget de inicio** (Glance): objetivo de la semana, racha y entrenamiento de hoy o el próximo.
- **Código de barras** (Nutrición → Añadir → "Escanear código de barras"):
  - CameraX + ML Kit en el móvil, y después Open Food Facts.
  - Solo se envía el código, con un cliente sin el token de FITNEX.
  - Si el producto no existe, se ofrece crear un alimento personalizado.
- **Plan semanal con FITNEX AI:** el modo "Plan semanal" (`POST /v1/ai/plan`) crea una sesión distinta
  por día de entrenamiento.
  - Se ajusta a un resumen de las últimas 4 semanas reales.
  - Se guarda como varias rutinas, una por cada día del perfil.
- **Health Connect** (Perfil):
  - Lee pasos, pulso medio y peso. El peso no se pide ni se muestra a menores.
  - Escribe cada entrenamiento terminado como sesión de ejercicio.
  - Manifiesto con permisos y pantalla de privacidad para Android 13/14.

**Pendiente o no verificado:**
- El escáner (en el emulador la cámara es virtual), los recordatorios reales, el widget en el escritorio,
  Health Connect con permisos concedidos y el plan semanal con Gemini no se han probado a mano.
- Wear OS queda para más adelante.

## Versión 1.6.0: nuevas pantallas de Nutrición y Entrenamiento

- **Nutrición** (siempre en verde, también la barra inferior): cabecera con el logo, anillo de calorías del día contra tu objetivo (con «Restantes»), anillos de proteínas, carbohidratos y grasas contra objetivos derivados (25/45/30 %), «Consejo del día» (uno distinto cada día), herramientas (Calculadora de calorías, Nueva receta, Registrar comida, Mis estadísticas), agua y comidas como antes.
- **Calculadora de calorías** (solo adultos): Mifflin-St Jeor × actividad, con objetivo mantener / perder (−15 %) / ganar (+10 %), entre 1200 y 5000 kcal; o escribes tu objetivo. Se guarda por usuario/perfil en el dispositivo. El sexo solo se usa para el cálculo y no se guarda.
- **Menores y edad desconocida**: no ven calorías, objetivo ni calculadora; el anillo grande muestra la variedad de alimentos.
- **Entrenamiento**: tarjeta «Plan actual» (la rutina de hoy o la primera) con músculos principales, nº de ejercicios y duración estimada; «Tus entrenamientos» con la última vez que hiciste cada rutina (Hoy / Ayer / fecha • minutos), check verde si la hiciste hoy, botón de empezar y «Ver historial» (calendario); tarjeta «Constancia que inspira» con la meta semanal (la misma de Progreso).
- **Plan de comidas** (Nutrición › Herramientas): plan semanal (lunes a domingo) por comida con alimentos del catálogo o recetas, totales de macros del día (kcal solo adultos) y «Añadir el plan del … al diario de hoy» con confirmación. Se repite cada semana. Tabla Room `meal_plan_items` (BD v8, migración 7→8 que solo añade la tabla) sincronizada con la nube (el servidor necesita la versión con `meal_plan_items` en `SYNC_TABLES`). Alimentos personalizados y del escáner no entran en el plan.
- Pruebas nuevas: `CalorieMathTest`, `RoutineEstimateTest`, `MealPlanTest`, migración 7→8 y el flujo del plan en `NutritionFlowTest`.
### Revisión de usabilidad (1.6.0)

- **Cabecera común** en Progreso, Nutrición y Entrenamiento: el logo (la «F» con la mancuerna del icono) en lugar del texto FITNEX, frase que dice para qué sirve la sección, y los mismos tamaños.
- **Nutrición con la misma estética que el resto**: ya no tiene su propio color verde. Orden por uso: resumen del día, agua, comidas, herramientas (Plan de comidas, Resumen semanal, Calculadora solo para adultos) y consejo del día al final.
- **Duplicados eliminados**: el botón de resumen semanal de la cabecera de Nutrición, la herramienta «Registrar comida» (repetía el «+ Añadir» de cada comida), «Ver historial» en Entrenamiento (el calendario ya está en la cabecera), la tarjeta de objetivo semanal repetida en Progreso, la tarjeta de «ritmo» y el gráfico de series por sesión (el historial muestra las series de cada entrenamiento) y la cifra de kcal repetida bajo el anillo.
- **Progreso**: 4 cifras en lugar de 6 (las repeticiones y los días activos pasan a ser el texto pequeño de su tarjeta).
- **Modo entrenamiento**: botón «Cómo se hace» con pasos, consejos y errores frecuentes sin salir de la sesión; icono de pausa real; dos acciones claras (Repetir anterior / + Serie), porque «Saltar» repetía la flecha →; el descanso ya no muestra el tiempo dos veces.
- **Mensajes**: pasarse del objetivo de calorías ya no sale en color de alerta y se dice de forma neutra.
### Más en 1.6.0

- **Cardio con tiempo y distancia** en el resumen del entrenamiento y en Progreso (tarjetas «Cardio y tiempo» y «Distancia», solo si hay datos). Un entrenamiento solo de cardio ya no muestra «0 repeticiones».
- **Récord personal al momento**: al completar una serie que supera tu mejor peso (o repeticiones) aparece «¡Nuevo récord personal!» con la marca anterior. La primera vez que haces un ejercicio no cuenta como récord.
- **Propuesta de subir peso**: si la última vez completaste todas las series con las repeticiones previstas, la primera serie propone +2,5 kg (+1 kg por debajo de 10 kg). Tú decides: es solo la propuesta de la serie. Nunca en ejercicios sin peso ni por tiempo.
- **Editor de rutinas**: arrastra ≡ para reordenar. Subir y bajar siguen disponibles como acciones de TalkBack.
- **Medidas corporales** (Progreso, solo adultos, nunca el peso): cintura, pecho, cadera, brazo y muslo, todas opcionales, con el cambio desde el primer registro. Tabla Room `body_measurements` (BD v9, migración 8→9 que solo añade la tabla) sincronizada; el servidor necesita `body_measurements` en `SYNC_TABLES`.
- **Ajustes**: la fila «Nube» dice si la copia está guardada («Copia guardada hace 5 min») o si hay cambios pendientes. Nueva página **Privacidad y datos**, que explica qué se guarda y dónde, y enlaza a dónde gestionarlo.
- **Letra grande**: las etiquetas de la barra inferior ya no se cortan con la letra del sistema al 130 %.
## Versión 1.5.1: ejercicios propios, textos cruzados y reiniciar estadísticas

- **Borrar ejercicios personalizados**: papelera roja en la ficha del ejercicio (solo en los tuyos), con
  confirmación. Se oculta de la biblioteca; las series ya hechas se quedan en el historial.
- **"Crear ejercicio" salía como "Alimento personalizado"**: Nutrición y Entrenamiento usaban los mismos nombres
  de texto (`custom_title`…) y Android se quedaba con los de comida. Renombrados (`exercise_custom_*`), y de paso
  otros choques: el resumen del entrenamiento decía "Resumen del día" (ahora "¡Entrenamiento completado!"), la
  descripción de la foto de perfil y los nombres de material del onboarding. Nueva prueba
  `StringResourceClashTest` que falla si dos módulos repiten un nombre con distinto texto.
- **Hoja "Crear ejercicio" rediseñada** en tarjetas: tipo, cómo se registra (peso × reps, solo reps, tiempo,
  tiempo y distancia; automático según tipo y material), músculos agrupados por zona (contador n/3) y material.
- **Ajustes › Cuenta › Reiniciar estadísticas**: borra (con confirmación) el historial de entrenamientos de la
  cuenta y de todos los perfiles —entrenamientos, series, récords, minutos, logros, racha y días completados—
  y se sincroniza. Se quedan rutinas, ejercicios propios, nutrición, peso y ajustes.
- **Sincronización**: el servidor (`backend/worker/src/sync.js`) no aceptaba la tabla `exercises`, así que en
  cuanto había un ejercicio personalizado pendiente la subida fallaba con 400. Añadida a la lista; nueva prueba
  `serverAcceptsEveryTableTheAppUploads` compara la lista de la app con la del servidor. **Hay que desplegar el
  worker** (`cd backend/worker; npx wrangler deploy`) para que llegue al servidor.
## Versión 1.5.0: catálogo ampliado a 611 ejercicios

- **611 ejercicios** (los 63 de antes, con los mismos ids, textos y fotos, + 548 nuevos): máquinas, poleas,
  barra, barra Z, mancuernas, peso corporal, bandas, kettlebells, balón medicinal, trineo, saco, cuerda,
  paralelas, máquinas de cardio, cardio sin máquinas, movilidad y estiramientos.
  Por tipo: 66 fuerza, 296 hipertrofia, 73 calistenia, 39 funcional, 48 cardio, 44 movilidad, 45 estiramientos.
- Cada ejercicio trae nombre en español, nombre en inglés (para buscar), músculos principales, secundarios y
  estabilizadores (los mismos ids del mapa 2D/3D), tipo, material, dificultad, descripción, pasos, consejos,
  errores comunes y etiquetas. Sin duplicados: el generador falla si se repite un id o un nombre.
- **Generador**: `node tools/catalog/build-catalog.mjs` (familias en `tools/catalog/families-*.mjs`) escribe
  `core/data/src/main/assets/catalog/catalog.json` (versión 4). Conserva siempre los originales.
- **Cómo se registra cada ejercicio** (`Tracking`): kg × reps (336), solo reps (138), tiempo (103: planchas,
  movilidad, estiramientos…) y tiempo + distancia (34: cinta, bici, remo, carrera…). En la rutina el objetivo
  pasa a ser segundos o minutos según el caso; en el entrenamiento la hoja de la serie muestra min/seg (y kg si
  lleva carga) o min + km. El cardio se añade con 1 serie por defecto.
- **Biblioteca**: chips rápidos por grupo muscular (pecho, espalda, hombros, bíceps, tríceps, antebrazos,
  cuádriceps, isquios, glúteos, aductores, abductores, gemelos, abdomen, trapecios, lumbar) y por tipo
  (cuerpo completo, cardio, movilidad, estiramientos); filtros de material con los 8 materiales nuevos (también
  en el onboarding). Búsqueda por varias palabras a la vez: nombre, nombre en inglés, músculo, material,
  etiquetas ("press inclinado mancuerna", "treadmill", "polea triceps"); primero los que lo tienen en el nombre.
- **Base de datos v7** (migración 6→7 solo añade columnas: `tracking`, `search_terms`, `duration_seconds`,
  `distance_m`). No se borra nada: rutinas, historial y ejercicios propios se mantienen.
- FITNEX AI sigue recibiendo como máximo 80 ejercicios de fuerza variados (para no gastar de más).
- **Rendimiento**: el catálogo se lee una sola vez por arranque y la lista es perezosa.
- **Sin foto**: los 548 nuevos muestran el icono en lugar de imagen (solo los 63 originales tienen foto).
- Verificado en el emulador: 611 en la biblioteca, búsqueda en inglés, rutina con cinta y plancha, registro
  "20 min · 400 m" y "30 s", resumen y datos anteriores intactos tras la migración. En el móvil quedó
  instalada pero no se pudo abrir (pantalla bloqueada).
## Versión 1.4.0: estilo violeta, comparativas, gráficas nuevas y cuerpo 3D musculoso

- **Copia de seguridad** de la 1.3.2 en una carpeta local (`fitnex-copia-v1.3.2`) (código + APK).
- **Logo:** el palito rojo tras la "F" es ahora una pesa pequeña morada (`ic_fitnex_mark.xml`).
- **Estilo:**
  - Tarjetas con borde y luz violeta.
  - Botones con degradado violeta y brillo.
  - Tarjeta "Tu experiencia FITNEX" de cristal violeta con un haz de luz.
  - Raya morada bajo la pestaña elegida.
  - Botones redondos separados en la cabecera.
- **Progreso:**
  - Cada mosaico tiene icono y comparación con el periodo anterior (`GlobalStats.previous`, "↑ +2").
  - Frecuencia por Días/Semanas/Meses (`sessionDates`).
  - "Tu ritmo" con anillos concéntricos.
  - "Grupos musculares" con gráfica circular y el grupo principal.
  - "Series por entrenamiento" en barras.
- **Cuerpo 3D:**
  - Modelo: «Male base muscular anatomy» de CharacterZone (Sketchfab, **CC BY 4.0**), descargado con la cuenta del dueño.
  - Cambios: brazos bajados de T a A con `BodyAssetBuilderTest` (`FITNEX_ARM_DOWN`, `FITNEX_JOINTS`, `FITNEX_SINGLE`) y ojos quitados.
  - Se ve como estatua gris (`rememberStatuePalette`) con los músculos trabajados en violeta.
  - Crédito en Ajustes y en la cabecera de `body.obj`.
  - El modelo anterior (Hunyuan3D) se puede recuperar de la copia de seguridad.
## Versión 1.3.1: Nutrición arreglada y más simple

- **Cierre al tocar "Cámara" arreglado.**
  - Antes de abrir la cámara del sistema se pide el permiso de cámara.
  - Si no hay app de cámara o se niega el permiso, se muestra un aviso en la comida y la app no se cierra.
  - Verificado en el emulador: aparece el permiso y luego se abre la cámara.
- **La foto va dentro de cada comida**: icono de cámara junto a "+ Añadir", con dos opciones,
  "Hacer foto" y "Elegir de la galería". Desaparecen la tarjeta aparte y el selector de comida.
- **Diario más corto:**
  - Resumen del día en una fila: proteínas, hidratos, grasas y fibra.
  - Agua en una fila con +250 y +500.
  - "Resumen de la semana" pasa a ser un icono en la cápsula de la cabecera.
- **Buscador de alimentos:** el escáner de código de barras es un icono junto a la búsqueda.
  - En el emulador abre la cámara y carga ML Kit sin errores.
  - No se ha escaneado un código real.
- **Editar una ración** registrada en unidades vuelve a abrirse en unidades, sin redondear a gramos.
- **La barra de entrenamiento en curso** muestra horas (h:mm:ss) a partir de 60 minutos.

Sigue sin probar en un móvil real: el escaneo de un código real y el análisis de foto con FITNEX AI.
## Versión 1.3.0: estilo de la foto de referencia y transiciones naturales

- **Fondo negro puro** (#000) y tarjetas de grafito suave que flotan sobre él.
- **Cabecera estilo iOS:**
  - Botón "<" de cristal redondo a la izquierda.
  - Acciones (calendario, avatar) agrupadas en una sola **cápsula de cristal** a la derecha.
  - Título grande (36 sp, negrita) en su propia línea.
- **Tarjetas "Tu experiencia FITNEX"** al principio de Progreso, con aspecto de grafito cepillado:
  - Un número protagonista con degradado metálico y el nombre en mayúsculas espaciadas.
  - Tarjetas: minutos entrenados con "Miembro desde", constancia (semanas seguidas, último entreno)
    y esta semana.
  - Se deslizan con efecto imán y la siguiente asoma por la derecha.
- **Entrenamiento:** Biblioteca, Crear con FITNEX AI y Calendario pasan a ser filas limpias con icono
  y flecha, en lugar de tarjetas grandes.
- **Barra de entrenamiento en curso:** se parece al mini reproductor de una app de música.
  - Barra de cristal flotante sobre la barra inferior mientras hay un entrenamiento abierto.
  - Muestra la rutina, el tiempo en vivo y las series. Un toque vuelve al entrenamiento.
- **Transiciones naturales:**
  - La pantalla nueva entra deslizándose desde la derecha y la anterior se aparta un poco.
  - "Atrás" invierte el movimiento con una curva que frena suave, estilo iPhone.
  - El gesto de atrás predictivo de Android 14+ está activado.
  - Entre pestañas, un fundido rápido.
- **Menú de cuentas** más opaco y con el fondo más oscuro, para que se lea bien.
## Versión 1.2.0: perfiles "Entrenar juntos", Ajustes nuevos y Liquid Glass real

- **Menú de cuentas.** Tocar el avatar redondo de cualquier cabecera abre un menú que sale del propio avatar:
  - tus perfiles con una marca en el activo;
  - "Añadir perfil" y "Administrar perfiles";
  - "Ajustes".
  Si hay un amigo en pantalla, el avatar muestra su inicial.
- **Entrenar juntos.** Perfiles locales dentro de tu cuenta: no son cuentas nuevas.
  - Solo piden nombre, edad, altura y peso. El peso solo se pide a mayores de 18.
  - Cada perfil tiene sus propias rutinas, entrenamientos, progreso, calendario y diario.
  - Funcionamiento interno:
    - El id de datos de un perfil es `<uid>~<id>`.
    - `SessionRepository.currentUserId` es el perfil en pantalla y `accountUserId` la cuenta.
    - El onboarding y la nube usan la cuenta.
  - La sincronización sube los datos de todos los perfiles con el token de la cuenta (`SyncStore.scopeIds`).
  - Al borrar un perfil, sus borrados pendientes se conservan para que también se eliminen de la nube
    (`AccountDao.deleteProfileData`).
  - Borrar la cuenta borra también sus perfiles.
- **Ajustes** (antes "Perfil"):
  - Lista con buscador y grupos con iconos.
  - Una página propia para cada apartado: Cuenta e integraciones, Entrenar juntos, Entrenamiento,
    Apariencia, Health Connect y Copia en la nube.
- **Apariencia:**
  - Tema (Sistema/Claro/Oscuro) y color de acento.
  - Reducir animaciones.
  - **Reducir desenfoque dinámico** (nuevo).
  - Liquid Glass.
- **Liquid Glass real** (Android 13+, `LiquidLens.kt`):
  - Un shader AGSL curva el contenido en el borde redondeado, como el canto de un cristal grueso.
  - Añade una ligera dispersión de color, más saturación y un esmerilado suave.
  - Se calcula en cada fotograma con el contenido real, así que las listas fluyen a través del cristal.
  - En Android 12 queda el desenfoque anterior.
  - Sin Liquid Glass se usa el cristal ligero de antes.
- **Borde inferior que se desvanece:** en las pestañas, la parte de abajo se desenfoca de forma
  progresiva y se oscurece bajo la barra.
- **Barra inferior y selectores deslizables:**
  - Se puede deslizar el dedo por la barra o por cualquier selector de pastillas, estilo iPhone.
  - La pastilla sigue al dedo y da un toque háptico en cada opción. Al soltar se elige esa opción.
  - En la barra, con Liquid Glass, la pastilla se convierte en una lupa de cristal mientras se arrastra.
  - Tocar sigue funcionando igual.
- **El botón de FITNEX AI** va junto a la barra en las pestañas, así que ya no tapa contenido.

Verificado en el emulador:
- el menú, Ajustes, Apariencia;
- Liquid Glass con refracción;
- la barra deslizable (al soltar abre la pestaña);
- el desvanecido inferior.

En el emulador no se crearon perfiles en la cuenta del dueño; eso lo cubre `TrainingProfilesTest`. Queda
probar en un móvil real el rendimiento del Liquid Glass y la sensación háptica.

## Por hacer

- Probar en un móvil real el escaneo de un código de barras y el análisis de foto con FITNEX AI.
- Wear OS.
- Fotos o ilustraciones para los 548 ejercicios nuevos del catálogo.
- Mostrar tiempo y distancia totales en el resumen y en las estadísticas (hoy cuentan series y repeticiones).

## Notas de versiones

- **Compose BOM 2026.09.00**: la 2026.11.x (Compose 1.13) exige `compileSdk` 37.1; el SDK instalado
  trae `android-37.0`. Al instalar la plataforma 37.1 se puede subir el BOM y `COMPILE_SDK`
  (`build-logic/.../FitnexBuild.kt`).
- `targetSdk` 36, `compileSdk` 37, `minSdk` 26, JVM 17. AGP 9 con Kotlin integrado (sin plugin `kotlin-android`).
- Wrapper con `networkTimeout=120000` y 3 reintentos (el valor por defecto de 10 s fallaba en esta red).

## Pruebas actuales

Última ejecución (`assembleDebug testDebugUnitTest`): **BUILD SUCCESSFUL, 203 pruebas, 0 fallos** (más 5 de `backend/functions` y 11 de `backend/worker` con `npm test`)
(3 son herramientas de desarrollo que no hacen nada sin sus variables de entorno).

- Fase 7: `NutritionMathTest` (búsqueda sin acentos, escalado, alérgenos, totales y variedad),
  `NutritionStatsTest` (semana con medias solo de días registrados, hábitos, recetas por ración),
  `NutritionDataTest` (catálogo completo y coherente con USDA, diario, edición, alimento personalizado,
  agua, fotos, recetas), `MigrationTest` 3→4 y `NutritionFlowTest` (buscar → ración → diario con valores
  de USDA, agua con deshacer, aviso de alergia, menores sin calorías, receta creada y añadida, resumen
  semanal).
- Fase 6: `AnatomyGeometryTest` (polígonos, cada músculo tocable en su centro, prioridad de músculos profundos,
  vista preferida), `HumanBodyTest` (el asset carga de pie a 180 cm con sus articulaciones, los 20 músculos del
  2D están en el 3D con superficie suficiente y simétrica, selección de frente y de espalda, rasterizador),
  `MuscleStatsCalculatorTest` (sesiones y series por músculo, 12 semanas con huecos, volumen
  ponderado, intensidad del mapa), coherencia catálogo ↔ mapas en `TrainingDataTest`, y en
  `ProgressCalendarFlowTest` un flujo que toca el pectoral en su posición real del mapa → información →
  página de músculo → ejercicio → músculo secundario, con contexto de FITNEX AI.

- Fase 5: `TrainingMathTest` (Epley, volumen, récords), `StatsCalculatorTest` (periodos, frecuencia, carga
  muscular, prioridades del calendario), `WorkoutSessionRepositoryTest` (recuperación tras cerrar la app, pausas,
  descarte, resumen), `CalendarRepositoryTest` (programar → entrenar → completado; no realizado; descansos),
  `MigrationTest` 2→3, `LiveLogicTest`, `CalendarViewModelTest`, `LiveWorkoutFlowTest` y `ProgressCalendarFlowTest`.

- Fase 4: `TrainingDataTest` (coherencia del catálogo, carga única, favoritos por usuario, rutinas),
  `ExerciseFilterTest`, `RoutineEditorViewModelTest`, `WorkoutFlowTest` (biblioteca → detalle → favorito → IA;
  crear rutina → elegir ejercicios → ruedas → reordenar → guardar → eliminar).

- Fase 3: `AccountAndSessionTest` (estados de sesión, eliminación por usuario, fallo remoto que conserva los datos),
  `MigrationTest` (v1→v2), `FormRulesTest`, `SignUpViewModelTest` (bloqueo por edad sin llamar al servidor),
  `AuthFlowTest` (registro + onboarding de principio a fin, menor de 14, menor protegido, contraseña
  incorrecta + recuperación, eliminación de cuenta).
Verificada en el emulador `Medium_Phone_API_37.0` (capturas en `docs/screenshots/`). En este equipo el
emulador necesita `-gpu swiftshader_indirect`, porque el modo GPU por defecto se cierra con Android 37.

- `core/designsystem` — `ChartMathTest` (ejes legibles, selección por toque, etiquetas) y `CalendarMathTest`
  (rejilla mensual, años bisiestos, L M X J V S D, "Noviembre 2026").
- `feature/calendar` — `CalendarViewModelTest` (sin actividad inventada, cambio de año, restauración tras cerrar el proceso).
- `feature/profile` — `SettingsViewModelTest` (persistencia del tema, ajuste y límites del descanso, interruptores).
- `app` — `FitnexNavigationTest` con Hilt: 3 pestañas, IA con contexto, calendario desde Progreso y
  Entrenamiento, ajustes que persisten el tema y ocultan la IA.

- `core/common` — `AppResultTest` (no se traga la cancelación de corrutinas, estados de carga/error).
- `core/datastore` — `UserPreferencesDataSourceTest` (modo oscuro por defecto, persistencia, rango de descanso).

- `core/model` — `AgePolicyTest` (límites de edad, edad desconocida, fecha futura).
- `core/database` — `FitnexDatabaseTest` (historial tras borrar rutina, borrado lógico, sesión activa,
  asociación ejercicio–músculo, historial por ejercicio) y `ConvertersTest`.
- `core/network` — `SafeApiCallTest` con MockWebServer (token Bearer, 401, 429 + Retry-After, cuerpo inválido, sin conexión).
- `app` — `FitnexNavigationTest` (exactamente 3 pestañas, cambio de pestaña, FITNEX AI con contexto y volver).

## 1.6.1 y 1.6.2 (21-11-2026)

**1.6.1** (instalada en el móvil)
- Cristal líquido (`LiquidGlassSurface`, `liquidRim`, `liquidInteraction`): borde especular diagonal, resorte al pulsar (0,965) y franja de luz en tarjetas, botones, selectores, hojas y diálogos. El dock usa desenfoque real (se graba el contenido salvo con «Reducir desenfoque»). La franja termina y se desvanece al soltar.
- Biblioteca de ejercicios rediseñada: buscador fijo, grupos musculares con su número de ejercicios, lista compacta agrupada por letra e icono según el tipo de ejercicio.

**1.6.2**
- Tema claro revisado: logo con «F» oscura (`ic_fitnex_mark_ink`), logros bloqueados y días de la semana visibles, icono de FITNEX AI con color.
- Accesibilidad: anillos, días y logros se leen una sola vez; el buscador de la biblioteca se lee primero; la tarjeta «Tu experiencia» es una sola parada de TalkBack.
- Ajustes por categorías (Cuenta, Entrenamiento, Personalización, Datos y privacidad, Acerca de) con la versión de la app.
- Ahorro de batería del sistema = sin desenfoque ni refracción. Medido en el emulador (renderizado por software): el desenfoque costaba entre un 30 % y un 45 % del tiempo de fotograma al hacer scroll.
- Traducción completa de la interfaz al inglés (`values-en` en los 10 módulos, 1105 textos) y selector de idioma por app de Android 13+ (`locales_config.xml`). Los datos del catálogo (nombres de ejercicios, músculos y alimentos) y las respuestas de FITNEX AI siguen en español.
- Ficha de Google Play en `store/FICHA-GOOGLE-PLAY.md` y capturas en `store/screenshots/`.
- Corregido: ejercicios del catálogo sin foto ya no dicen «Ejercicio personalizado».
- Las pruebas de interfaz se ejecutan en español (`qualifiers=es` en Robolectric); `EnglishTranslationTest` comprueba el inglés. 220 pruebas.

## Racha diaria y reglas de peso (22-11-2026)
- Modo entrenamiento: la próxima serie se ajusta con dos reglas horizontales (peso 0–300 kg de 0,5 en 0,5; reps 0–100) con vibración por paso y acción de accesibilidad (`FitnexRulerPicker`). Los demás tipos (tiempo, cardio) siguen con ruedas.
- Junto al ejercicio: mejor peso, 1RM estimado (Epley) y máximo de repeticiones de sesiones anteriores (`ExerciseBests`).
- Racha diaria (`DayStreakCalculator`): cada día con entrenamiento suma; un día de descanso entre entrenos no la rompe, dos seguidos sí. Pantalla de racha (`StreakDialog`, llama dibujada en código) al terminar un entrenamiento y desde la cabecera de Progreso (`StreakChip`). La racha semanal del objetivo sigue igual.
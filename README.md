<p align="center">
  <img src="docs/play-store/logo-fitnex.png" width="120" alt="Logo de FITNEX">
</p>

<h1 align="center">FITNEX</h1>
<p align="center"><b>Entrenamiento, nutrición y progreso en una sola app, con IA.</b><br>
Android · Hecha en español</p>

<p align="center">
  <img src="docs/play-store/screenshots/es-ES/01-progreso.png" width="200" alt="Progreso">
  <img src="docs/play-store/screenshots/es-ES/02-entreno-en-vivo.png" width="200" alt="Entrenamiento en vivo">
  <img src="docs/play-store/screenshots/es-ES/05-nutricion.png" width="200" alt="Nutrición">
  <img src="docs/play-store/screenshots/es-ES/06-fitnex-ai.png" width="200" alt="FITNEX AI">
</p>

> **Estado:** versión de prueba. Todavía no está en Google Play: la publicación está prevista para
> **enero de 2027**. Mientras tanto puedes instalar el APK de la sección **Releases** y contarnos qué te parece.

## Qué hace

- **Entrenamiento en vivo:** eliges peso y repeticiones deslizando una regla, ves tu última marca y tus récords,
  temporizador de descanso con aviso y contador de repeticiones con la cámara en algunos ejercicios.
- **611 ejercicios** con técnica, músculos y errores frecuentes; 501 con fotos.
- **Mapa muscular 2D y 3D:** qué músculos has trabajado y cuánto.
- **FITNEX AI:** asistente que conoce tus datos (solo si le das permiso) y crea rutinas o planes semanales con tu
  equipo y tu tiempo.
- **Nutrición:** diario por comidas con 212 alimentos, agua, porciones, escáner de código de barras, recetas, plan
  de comidas y foto de la comida analizada por la IA.
- **Progreso:** estadísticas, gráficas, récords, calendario, racha diaria y logros.
- **Widgets** de racha y estadísticas para la pantalla de inicio.
- **Personalización:** tema claro u oscuro, color de acento, Liquid Glass, «Reducir animaciones».
- **Entrenar juntos:** varios perfiles en el mismo móvil para entrenar con amigos o familia.
- **Menores protegidos:** de 14 a 17 años la app no muestra calorías, peso ni objetivos estéticos.
- **Privacidad:** todo se guarda en el móvil; la copia en la nube es opcional y solo tuya. Ver
  [docs/PRIVACIDAD.md](docs/PRIVACIDAD.md).

## Instalar la versión de prueba

1. Descarga el APK más reciente desde **[Releases](https://github.com/fitnexapp-co/fitnex/releases/latest)**.
2. En el móvil, permite instalar apps de origen desconocido para tu navegador o gestor de archivos.
3. Ábrelo e instálalo. Necesitas Android 8.0 o superior y un móvil de 64 bits.

Cuando salga una versión nueva, la propia app te avisará para descargarla: se instala encima y no pierdes tus
datos.

## Cómo está hecho

| Parte | Tecnología |
|---|---|
| App | Kotlin, Jetpack Compose, Hilt, Room, DataStore, WorkManager, Glance (widgets), CameraX + ML Kit |
| Arquitectura | Multimódulo: `app`, `core/*` (datos, base de datos, diseño, red, anatomía…) y `feature/*` (una por sección) |
| Servidor | Cloudflare Workers + D1 (`backend/worker`): FITNEX AI con Google Gemini y la copia en la nube |
| Cuentas | Firebase Authentication (correo y contraseña) |
| Pruebas | 227 pruebas unitarias y de interfaz (JUnit + Robolectric) y 14 del servidor |

La clave de Gemini **nunca** está en la app: vive solo en el servidor. El historial completo del desarrollo,
fase por fase, está en [docs/HISTORIAL.md](docs/HISTORIAL.md).

## Ayúdanos

FITNEX se está construyendo escuchando a quienes la usan. Puedes:

- **Probarla y opinar:** abre un *issue* con lo que te gusta, lo que no o lo que le falta.
- **Proponer funciones** o **votar** las que ya están propuestas. Más detalles en [CONTRIBUTING.md](CONTRIBUTING.md).

## Licencia

© 2026 FITNEX. **Todos los derechos reservados.** El código es visible para que puedas ver cómo está hecha la app,
pero no está permitido copiarlo, modificarlo, reutilizarlo ni redistribuirlo sin permiso. Puedes instalar los APK
de **Releases** para uso personal. Condiciones completas en [LICENSE](LICENSE).

Los recursos de terceros (fotos, alimentos, modelo 3D, tipografía) tienen sus propias licencias: ver
[CREDITOS.md](CREDITOS.md).

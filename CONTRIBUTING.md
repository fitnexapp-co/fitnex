# Cómo contribuir a FITNEX

¡Gracias por querer ayudar! FITNEX se construye con la comunidad.

## Opinar o informar de un error

Abre un *issue* y cuenta:
- qué hiciste, qué esperabas y qué pasó;
- tu móvil y versión de Android, y la versión de FITNEX (Ajustes → Acerca de);
- una captura si ayuda (sin datos personales).

## Proponer una función

Abre un *issue* con la etiqueta **idea**. Explica el problema que resuelve antes que la solución. Dale 👍 a las
ideas de otros que también quieras: las más votadas se priorizan.

## Enviar código

1. Haz un *fork* y crea una rama (`git checkout -b mejora-temporizador`).
2. Configura el proyecto siguiendo [docs/CONFIGURAR.md](docs/CONFIGURAR.md).
3. Antes de enviar, comprueba que compila y pasan las pruebas:
   ```powershell
   .\gradlew.bat assembleDebug testDebugUnitTest
   ```
4. Abre un *pull request* explicando qué cambia y por qué.

### Reglas del proyecto

- **Nada de botones falsos ni datos inventados:** una función no se marca como terminada si solo existe su
  pantalla.
- **Privacidad primero:** nada de datos personales sin necesidad, y nunca claves ni secretos en la app.
- **Menores protegidos:** de 14 a 17 años no se muestran calorías, peso corporal ni objetivos estéticos.
- **Textos:** la app está en español y en inglés. Todo texto nuevo va en `values/strings.xml` **y** en
  `values-en/strings.xml`, con un nombre único entre módulos.
- **Accesibilidad:** descripciones para TalkBack y respeto a «Reducir animaciones».
- **Estilo:** Kotlin oficial; Compose con el sistema de diseño de `core/designsystem`.

## Licencia de tus aportaciones

Al contribuir aceptas que tu código se publique bajo la misma licencia del proyecto, **GNU GPL v3.0**.

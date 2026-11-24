# FITNEX — Política de privacidad (BORRADOR)

> Borrador técnico redactado a partir de lo que la app hace realmente. Antes de publicar, debe
> revisarlo una persona con conocimientos legales y completar el responsable, la dirección de contacto
> y la jurisdicción.

**Responsable:** _[nombre / razón social]_ · **Contacto:** _[correo de privacidad]_

## Qué datos se tratan y para qué

| Dato | Para qué | Dónde se guarda |
|---|---|---|
| Correo y contraseña | Crear la cuenta e iniciar sesión | Firebase Authentication (Google). La contraseña nunca llega a FITNEX. |
| Perfil: nombre, fecha de nacimiento, altura, peso opcional, nivel, días, equipamiento, preferencias y alergias | Adaptar entrenamientos, nutrición y FITNEX AI; aplicar las protecciones para menores | En el móvil y en tu copia en la nube |
| Entrenamientos, series, rutinas, calendario, comidas, agua y recetas | Funciones de la app y estadísticas | En el móvil y en tu copia en la nube |
| Conversaciones con FITNEX AI | Historial del chat | Solo en el móvil (se incluyen en "Exportar mis datos") |
| Fotos de comidas | Diario de comidas | Solo en el móvil. Se envían a FITNEX AI únicamente si pulsas "Analizar". |
| Foto de perfil | Mostrar tu avatar | Solo en el móvil |
| Imágenes de la cámara (contador de repeticiones) | Contar repeticiones | Se analizan en el móvil, fotograma a fotograma. No se graban ni se envían. |

## FITNEX AI (Google Gemini)
- Solo se usa si das tu consentimiento explícito (Ajustes → Cuenta e integraciones), y puedes retirarlo
  cuando quieras.
- Se envía únicamente lo necesario para la petición:
  - **Chat:** tu mensaje y un resumen de la pantalla desde la que preguntas.
  - **Análisis de comida:** la foto.
  - **Rutinas:** tu nivel, tu equipamiento, tus días y la lista de ejercicios posibles.
- Las peticiones pasan por el servidor de FITNEX, que comprueba tu sesión. La clave de Gemini nunca
  está en la app.
- **Mientras se use el plan gratuito de Gemini, Google puede usar estas peticiones para mejorar sus
  productos.** _[Actualizar al pasar a un plan de pago.]_
- Las respuestas son orientación general, no consejo médico.

## Copia en la nube
- Se guarda en Cloudflare (base de datos D1), asociada a tu identificador de usuario. Solo tu sesión
  puede leerla o modificarla.
- Sirve para recuperar tus datos al cambiar de móvil.
- Al eliminar la cuenta se borra primero la copia en la nube y después los datos del móvil.

## Avisos de nuevas versiones
- Si instalaste FITNEX desde un APK (no desde Google Play), al abrirse consulta la lista pública de
  versiones del proyecto en GitHub para avisarte cuando haya una nueva.
- No se envía ningún dato tuyo: es la misma página pública que cualquiera puede abrir. GitHub ve la
  dirección IP de la conexión, como en cualquier web.

## Menores
- La edad mínima es de 14 años.
- Entre los 14 y los 17 años, y si la edad no consta, la app no muestra calorías ni objetivos de peso,
  y FITNEX AI aplica reglas específicas.

## Tus derechos
- **Exportar:** Ajustes → Copia en la nube → Exportar mis datos (archivo JSON).
- **Rectificar:** Ajustes → Cuenta e integraciones → Editar perfil.
- **Eliminar:** Ajustes → Cuenta e integraciones → Eliminar cuenta (cuenta, copia en la nube y datos
  del móvil).
- **Retirar el consentimiento de IA:** Ajustes → Cuenta e integraciones.

Estas opciones solo aparecen en el perfil del titular de la cuenta, no en los perfiles de
«Entrenar juntos».

## Seguridad
- Conexiones solo por HTTPS.
- Los datos de salud no se incluyen en las copias de seguridad de Android.
- Los registros del servidor no guardan el contenido de las peticiones.

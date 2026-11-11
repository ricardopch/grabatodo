# Grabatodo — grabador de llamadas para Android

App (Kotlin, sin dependencias externas) que graba llamadas telefónicas y tiene un widget.

## Funciones
- **Escucha automática**: al activarla, empieza a grabar cuando descuelgas/contestas y para al colgar.
- **Grabar ahora / Parar** manual.
- **Widget** de pantalla de inicio con dos botones: `Escucha ON/OFF` y `Grabar/Parar`.
- Las grabaciones (`.m4a`) se guardan en `Música/Grabaciones de llamadas`; la app las lista, reproduce y borra.

## Compilar el APK
**Opción A – GitHub Actions (sin instalar nada):** pestaña *Actions* → *Build APK* → *Run workflow*;
al terminar, descarga el artefacto `grabatodo-apk` (contiene `app-debug.apk`).

**Opción B – Android Studio:** *Open* esta carpeta → *Build > Build APK(s)*.

## Instalar
Copia `app-debug.apk` al móvil y ábrelo (permite "instalar apps desconocidas"). Concede micrófono,
estado del teléfono y notificaciones. Añade el widget desde la pantalla de inicio.

## Limitaciones importantes (Android)
Desde Android 10 Google bloquea que las apps normales capten el audio de la llamada.
La app prueba `VOICE_CALL` → `VOICE_RECOGNITION` → `MIC`; en la mayoría de móviles acaba usando el
micrófono, así que **activa el altavoz** durante la llamada para captar a ambos interlocutores.
Si tu móvil tiene grabación de llamadas nativa (Google Teléfono, Xiaomi, Samsung en algunos países),
será de mayor calidad. En Xiaomi/Samsung/Huawei desactiva el ahorro de batería para la app.

## Legalidad
Avisa siempre a la otra persona. En España grabar una conversación en la que participas es lícito
para uso propio, pero difundirla puede ser delito/ilícito civil.

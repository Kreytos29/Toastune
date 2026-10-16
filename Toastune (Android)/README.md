# Toastune 🐾

Reproductor de música gratuito y de código abierto para Android (y Linux después), nombrado por Tostada, una gatita muy querida.

## Estructura

- `core/` — modelos e interfaz `MusicProvider` (Kotlin puro, se reutiliza en Linux)
- `provider-youtube/` — proveedor de YouTube Music con [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
- `app/` — app Android (Compose + Media3)
  - `playback/` — servicio de reproducción, cola y controles
  - `data/` — biblioteca local (favoritos, historial, playlists) en SQLite
  - `ui/` — diseño de cristal (Haze), aurora de colores, dock con mini-reproductor, Inicio, Buscar, Biblioteca, Ajustes y reproductor completo con letras

## Probar

1. Abrir la carpeta en Android Studio con **Gradle JDK 21** (Settings → Build Tools → Gradle) y sincronizar.
2. Conectar el celular con Depuración USB (`adb devices`).
3. Para rendimiento real, elegir la variante **release** (Build → Select Build Variant → `release`): está optimizada con R8 y firmada con la llave de depuración, así que se instala igual.

## Cambiar de fuente de música

La app solo conoce `MusicProvider`. Para usar otra fuente (por ejemplo Navidrome) basta con
implementarla y cambiar una línea en `ToastuneApp.provider`.

## Roadmap

- [x] Búsqueda y reproducción (YouTube Music), sugerencias, paginación
- [x] Cola editable, aleatorio, repetir, temporizador de sueño, reproducción automática
- [x] Favoritos, historial y playlists locales
- [x] Descargas y caché sin conexión
- [x] Letras sincronizadas (LRCLIB)
- [x] Diseño sólido con colores de la portada (cristal opcional)
- [x] Copia de seguridad de la biblioteca (JSON)
- [x] Inicio de sesión con YouTube Music (me gusta, historial, playlists) estilo Metrolist
- [x] Sincronización con Google Drive
- [ ] Port a Linux (Compose Desktop + MPRIS)
- [ ] Backend y sincronización de cuentas

## Aviso

Proyecto no afiliado a Google ni YouTube. Licencia: GPL-3.0 (NewPipeExtractor es GPL-3.0).
Donaciones opcionales vía Ko-fi / PayPal.

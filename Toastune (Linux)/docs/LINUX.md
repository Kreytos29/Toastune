# Toastune para Linux

Requisitos (Arch): `sudo pacman -S jdk17-openjdk mpv`

Instalar como programa normal:

    ./install-linux.sh

Después se abre desde el menú de aplicaciones o con `toastune`.

Probar sin instalar: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :desktop:run`

Datos: `~/.local/share/toastune/` (library.json y carpeta music/ con las descargas).

Atajos: Ctrl+Espacio pausa, Ctrl+→ siguiente, Ctrl+← anterior.

Desinstalar: `./uninstall-linux.sh` (conserva tus datos) o `./uninstall-linux.sh --todo` (borra también favoritos, listas y descargas).

## Multimedia y segundo plano (0.6.0)
- Toastune se anuncia por MPRIS (D-Bus): aparece en el widget de multimedia de tu escritorio, `playerctl`, teclas multimedia, etc. Requiere `python` + `python-gobject`.
- Al cerrar la ventana sigue sonando y deja un icono en la bandeja (StatusNotifierItem, como Steam/Discord). Clic = abrir; menú = pausar/siguiente/anterior/salir. Ejecutar `toastune` de nuevo muestra la ventana de la instancia ya abierta.
- Desactivable en Ajustes → «Seguir sonando al cerrar la ventana».

## Mezcla y listas (0.7.0)
- Ajustes → Reproducción → «Mezcla entre canciones» (o el botón de ecualizador en la pantalla de la canción): crossfade de 3–12 s con dos mpv; la siguiente canción entra mientras la actual baja.
- Listas: botón «Editar» para quitar canciones; la duración total (≈ si faltan datos) y el tiempo que queda aparecen en la cabecera; los álbumes también muestran su duración total.

## 0.7.1
- Pantalla de la canción reorganizada (la barra de tiempo siempre visible), tooltips en todos los botones.
- Mezcla premium: estilo Suave/DJ, vista de la curva, mezcla al saltar de canción, recorte de silencio inicial, indicador «Mezclando» y portada que se funde a la vez que el audio.

## 0.8.0
- Precarga de la siguiente canción en un segundo mpv: cambios instantáneos y sin pausa (gapless), con o sin mezcla.
- Mezcla: saltar manualmente ahora mezcla siempre (encadena mezclas), 4 estilos (Suave, DJ, Ambiental, Directa) y botón «Probar mezcla ahora».

## 0.9.0
- Arreglos: barra de tiempo (los globos de ayuda ya no tapan), canciones dobles al mezclar, «anterior» con un clic, mover la barra con la cola restaurada.
- Solo canciones: los relacionados/radio/autoplay descartan videos oficiales, lyric videos, mixes y directos, y empiezan por el mismo artista.
- Letras: búsqueda más precisa, caché en disco, barrido letra a letra, puntos en partes instrumentales, ajuste de sincronía y tamaño, «Reintentar».

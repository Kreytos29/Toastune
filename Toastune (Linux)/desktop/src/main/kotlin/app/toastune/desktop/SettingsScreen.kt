package app.toastune.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.FileDialog
import java.nio.file.Path

@Composable
private fun Group(title: String, index: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.enter(index).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.blue)
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Palette.muted, fontSize = 13.sp)
        }
        Switch(checked, onChange)
    }
}

private fun pick(title: String, save: Boolean): Path? {
    val fd = FileDialog(null as java.awt.Frame?, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
    if (save) fd.file = "toastune-biblioteca.json"
    fd.isVisible = true
    val f = fd.file ?: return null
    return Path.of(fd.directory, f)
}

@Composable
fun SettingsScreen(app: AppState) {
    val s by app.settings.data.collectAsState()
    val lib by app.library.data.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }

    SmoothList(spacing = 16.dp) {
        item { Text("Ajustes", fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.enter(0)) }
        item {
            Group("Reproducción", 1) {
                SwitchRow("Reproducción automática", "Al acabar la cola, sigue con canciones parecidas.", s.autoplay) { app.setAutoplay(it) }
                Text("Mezcla entre canciones", fontWeight = FontWeight.Medium)
                MixPanel(app)
                Text("Calidad de audio", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = s.highQuality, onClick = { app.setQuality(true) }, label = { Text("Alta") })
                    FilterChip(selected = !s.highQuality, onClick = { app.setQuality(false) }, label = { Text("Ahorro de datos") })
                }
            }
        }
        item {
            Group("Apariencia", 2) {
                SwitchRow("Colores de la portada", "Tiñe el fondo y los acentos con la canción que suena.", s.coverColors) { v -> app.setCoverColors(v) }
                SwitchRow("Seguir sonando al cerrar la ventana", "Como Steam o Discord: la música sigue y Toastune queda en la bandeja. Si no ves el icono, vuelve a ejecutar «toastune» para abrir la ventana.", s.closeToTray) { v -> app.settings.update { it.copy(closeToTray = v) } }
                SwitchRow("Modo para dislexia", "Fuente OpenDyslexic y más espacio entre letras y líneas.", s.dyslexic) { v -> app.settings.update { it.copy(dyslexic = v) } }
            }
        }
        item {
            Group("Ecualizador", 2) {
                SwitchRow("Activar ecualizador", "Ajusta graves y agudos (usa los filtros de mpv).", s.eqOn) { app.setEq(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqPresets.presets.forEach { (name, levels) ->
                        FilterChip(selected = s.eqOn && s.eqLevels == levels, onClick = { app.setEq(true, levels) }, label = { Text(name) })
                    }
                }
                EqPresets.bands.forEachIndexed { i, hz ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (hz >= 1000) "${hz / 1000.0} kHz".replace(".0", "") else "$hz Hz", Modifier.width(70.dp), color = Palette.muted, fontSize = 13.sp)
                        SlimSlider(
                            value = (s.eqLevels[i] + 12) / 24f,
                            onChange = { v -> app.setEq(true, s.eqLevels.toMutableList().also { it[i] = (v * 24f).toInt() - 12 }) },
                            modifier = Modifier.weight(1f),
                        )
                        Text("${s.eqLevels[i]} dB", Modifier.width(52.dp), color = Palette.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Group("Descargas", 3) {
                Text("Calidad de las descargas", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = s.downloadHigh, onClick = { app.setDownloadQuality(true) }, label = { Text("Alta") })
                    FilterChip(selected = !s.downloadHigh, onClick = { app.setDownloadQuality(false) }, label = { Text("Ahorro de espacio") })
                }
                var bytes by remember(lib.downloads.size) { mutableStateOf(app.downloads.sizeBytes()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Espacio usado", fontWeight = FontWeight.Medium)
                        Text(formatBytes(bytes) + " · ~/.local/share/toastune/music", color = Palette.muted, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { app.downloads.removeAll(lib.downloads); bytes = app.downloads.sizeBytes() }, enabled = lib.downloads.isNotEmpty()) { Text("Eliminar todo") }
                }
            }
        }
        item {
            Group("Tus gustos", 3) {
                val summary = (lib.taste + lib.tasteArtists).take(8).joinToString(", ").ifBlank { "Aún no elegiste nada" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Géneros y artistas", fontWeight = FontWeight.Medium)
                        Text(summary, color = Palette.muted, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { app.settings.update { it.copy(onboarded = false) } }) { Text("Editar") }
                }
            }
        }
        item {
            Group("Biblioteca", 3) {
                Text("${lib.favorites.size} favoritos · ${lib.albums.size} álbumes · ${lib.playlists.size} listas · ${lib.downloads.size} descargas", color = Palette.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pick("Exportar biblioteca", true)?.let { p -> msg = try { app.library.exportTo(p); "Biblioteca exportada." } catch (_: Exception) { "No se pudo exportar." } }
                    }) { Text("Exportar copia") }
                    OutlinedButton(onClick = {
                        pick("Importar biblioteca", false)?.let { p -> msg = if (app.library.importFrom(p)) "Biblioteca importada." else "No se importó nada." }
                    }) { Text("Importar") }
                    OutlinedButton(onClick = { app.library.clearHistory(); msg = "Historial borrado." }) { Text("Borrar historial") }
                }
                msg?.let { Text(it, color = Palette.blue, fontSize = 13.sp) }
            }
        }
        item {
            Group("Atajos (fuera de un campo de texto)", 4) {
                listOf(
                    "Espacio" to "Pausar / reanudar",
                    "← / →" to "Retroceder / adelantar 5 s",
                    "↑ / ↓" to "Subir / bajar volumen",
                    "N / P" to "Siguiente / anterior",
                    "L" to "Abrir o cerrar la pantalla de la canción",
                    "Ctrl + Espacio, Ctrl + ← / →" to "Funcionan también al escribir",
                ).forEach { (k, v) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(k, Modifier.width(220.dp), fontWeight = FontWeight.Medium)
                        Text(v, color = Palette.muted)
                    }
                }
            }
        }
        item {
            Group("Rendimiento", 4) {
                var mem by remember { mutableStateOf(usedMb()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Memoria que usa la app ahora", fontWeight = FontWeight.Medium)
                        Text("$mem MB", color = Palette.muted, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { System.gc(); mem = usedMb() }) { Text("Liberar y actualizar") }
                }
            }
        }
        item {
            Group("Acerca de", 5) {
                Text("Toastune para Linux 0.9.0", fontWeight = FontWeight.Medium)
                Text("Libre y gratuita · GPL-3.0 · sin anuncios ni rastreo · hecha con cariño para Tostada.", color = Palette.muted, fontSize = 13.sp)
                Text("Tipografías: Outfit (Outfit Project Authors) y OpenDyslexic (Abbie Gonzalez), ambas con licencia SIL Open Font License 1.1.", color = Palette.muted, fontSize = 13.sp)
                Text("Fuente de música: YouTube Music, mediante NewPipeExtractor · Letras: LRCLIB · Audio: mpv.", color = Palette.muted, fontSize = 13.sp)
            }
        }
    }
}

private fun usedMb(): Long = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%d KB".format(b / 1024)
}


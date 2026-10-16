package app.toastune.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.toastune.MainViewModel
import app.toastune.core.AudioQuality
import app.toastune.data.ThemeMode

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%d KB".format(b / 1024)
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).frost(RoundedCornerShape(24.dp)).padding(vertical = 6.dp)) { content() }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
        )
    }
}

@Composable
private fun <T> ChipsRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 18.dp))
        Spacer(Modifier.size(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { (label, value) -> GlassChip(label, value == selected, { onSelect(value) }) }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) GlassChip(action, false, onAction)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, bottomPad: androidx.compose.ui.unit.Dp, onLogin: () -> Unit, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    var memory by remember { mutableIntStateOf(vm.memoryMb()) }
    var cacheBytes by remember { mutableLongStateOf(vm.cacheBytes()) }
    val downloadsBytes = remember(s) { vm.downloadsBytes() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.onDriveConsent(r.data)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val diagnosticsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.exportDiagnostics(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onBack)
            Spacer(Modifier.width(14.dp))
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
            item {
                Group("Apariencia") {
                    ChipsRow(
                        "Tema",
                        listOf("Oscuro" to ThemeMode.DARK, "Claro" to ThemeMode.LIGHT, "Sistema" to ThemeMode.SYSTEM),
                        s.theme,
                    ) { v -> vm.updateSettings { it.copy(theme = v) } }
                    SwitchRow("Cristal con desenfoque", "Más bonito; si notas tirones, apágalo.", s.glass) { v -> vm.updateSettings { it.copy(glass = v) } }
                    SwitchRow("Modo para dislexia", "Fuente OpenDyslexic y más espacio entre letras y líneas.", s.dyslexic) { v -> vm.updateSettings { it.copy(dyslexic = v) } }
                    SwitchRow("Colores de la portada", "Tiñe el fondo con la canción que suena.", s.coverColors) { v -> vm.updateSettings { it.copy(coverColors = v) } }
                }
                Group("Reproducción") {
                    ChipsRow(
                        "Calidad al reproducir",
                        listOf("Alta" to AudioQuality.HIGH, "Ahorro de datos" to AudioQuality.LOW),
                        s.streamQuality,
                    ) { v -> vm.updateSettings { it.copy(streamQuality = v) } }
                    SwitchRow("Reproducción automática", "Al acabar la cola, sigue con canciones parecidas.", s.autoplay) { v -> vm.updateSettings { it.copy(autoplay = v) } }
                }
                Group("Ecualizador") {
                    SwitchRow("Activar ecualizador", "Ajusta graves, voces y agudos.", s.eqEnabled) { v -> vm.updateSettings { it.copy(eqEnabled = v) } }
                    if (s.eqEnabled) {
                        val levels = List(5) { s.eqLevels.getOrElse(it) { 0 } }
                        ChipsRow(
                            "Ajustes rápidos",
                            app.toastune.playback.EqualizerController.PRESETS.map { (name, values) -> name to values },
                            levels,
                        ) { v -> vm.updateSettings { it.copy(eqLevels = v) } }
                        app.toastune.playback.EqualizerController.LABELS.forEachIndexed { i, label ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                                androidx.compose.material3.Slider(
                                    value = levels[i].toFloat(),
                                    onValueChange = { nv ->
                                        vm.updateSettings { st ->
                                            st.copy(eqLevels = List(5) { j -> if (j == i) nv.toInt() else st.eqLevels.getOrElse(j) { 0 } })
                                        }
                                    },
                                    valueRange = -12f..12f,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${levels[i]} dB", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
                            }
                        }
                        Spacer(Modifier.size(6.dp))
                    }
                }
                Group("Descargas") {
                    ChipsRow(
                        "Calidad de descarga",
                        listOf("Alta" to AudioQuality.HIGH, "Ahorro de espacio" to AudioQuality.LOW),
                        s.downloadQuality,
                    ) { v -> vm.updateSettings { it.copy(downloadQuality = v) } }
                    SwitchRow("Solo con Wi‑Fi", "No gasta tus datos móviles.", s.wifiOnlyDownloads) { v -> vm.updateSettings { it.copy(wifiOnlyDownloads = v) } }
                    InfoRow("Espacio usado", formatBytes(downloadsBytes), "Eliminar todo") { vm.removeAllDownloads() }
                }
                Group("Caché") {
                    ChipsRow(
                        "Tamaño máximo (se aplica al reiniciar)",
                        listOf(128, 256, 512, 1024, 2048).map { (if (it >= 1024) "${it / 1024} GB" else "$it MB") to it },
                        s.cacheMb,
                    ) { v -> vm.updateSettings { it.copy(cacheMb = v) } }
                    InfoRow("Música guardada al escuchar", formatBytes(cacheBytes), "Vaciar") {
                        vm.clearStreamCache()
                        cacheBytes = 0L
                    }
                }
                Group("Tus gustos") {
                    val summary = (s.genres + s.artists).joinToString(", ").ifBlank { "Aún no elegiste nada" }
                    InfoRow("Géneros y artistas", summary, "Editar") { vm.updateSettings { it.copy(onboarded = false) } }
                }
                Group("YouTube Music") {
                    val yt by vm.ytAccount.collectAsState()
                    val acc = yt
                    if (acc == null) {
                        InfoRow(
                            "Cuenta de YouTube Music",
                            "Entra con tu cuenta para ver tus me gusta, historial y playlists, y traerlos a Toastune.",
                            "Iniciar sesión",
                            onLogin,
                        )
                    } else {
                        InfoRow("Sesión iniciada", listOfNotNull(acc.name, acc.email).joinToString(" · "), "Cerrar sesión") { vm.ytLogout() }
                    }
                }
                Group("Tu cuenta y copia de seguridad") {
                    if (!s.driveSync) {
                        InfoRow(
                            "Cuenta de Google",
                            "Sincroniza tus favoritos, historial y playlists en tu propio Google Drive (carpeta oculta de la app). Gratis y sin servidor.",
                            "Conectar",
                        ) { vm.connectDrive { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) } }
                    } else {
                        val last = if (s.lastSyncMs == 0L) "todavía no" else
                            android.text.format.DateUtils.getRelativeTimeSpanString(s.lastSyncMs).toString()
                        InfoRow("Sincronizado con Google Drive", "Última vez: $last", "Sincronizar") { vm.syncNow() }
                        InfoRow("Dejar de sincronizar", "Tus datos en Drive no se borran.", "Desconectar") { vm.disconnectDrive() }
                    }
                    InfoRow("Guardar copia", "Favoritos, historial, playlists y búsquedas. Puedes guardarla en Drive.", "Exportar") {
                        exportLauncher.launch("toastune-copia.json")
                    }
                    InfoRow("Restaurar copia", "Se une a lo que ya tienes, no borra nada.", "Importar") {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                }
                Group("Si algo falla") {
                    InfoRow(
                        "Batería sin restricciones",
                        "Algunos teléfonos (Tecno, Xiaomi, Huawei…) congelan las apps en segundo plano y la música deja de responder. Busca Toastune y elige «Sin restricciones».",
                        "Abrir",
                    ) {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }
                    InfoRow(
                        "Informe de errores",
                        "Guarda un archivo con los últimos cierres de la app. Mándalo si la app se traba.",
                        "Guardar",
                    ) { diagnosticsLauncher.launch("toastune-informe.txt") }
                }
                Group("Rendimiento") {
                    InfoRow("Memoria que usa la app ahora", "$memory MB", "Actualizar") {
                        memory = vm.memoryMb()
                        cacheBytes = vm.cacheBytes()
                    }
                }
                Group("Acerca de") {
                    InfoRow("Toastune 0.7.3", "Libre y gratuita · GPL‑3.0 · hecha con cariño para Tostada 🐾")
                    InfoRow("Tipografías", "Outfit (Outfit Project Authors) y OpenDyslexic (Abbie Gonzalez), ambas con licencia SIL Open Font License 1.1")
                    InfoRow("Fuente de música", "YouTube Music, mediante NewPipeExtractor · Letras: LRCLIB")
                }
            }
        }
    }
}

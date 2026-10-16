package app.toastune.desktop

import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState

/** Una sola instancia: si ya hay una abierta, le pide que muestre su ventana y sale. */
private fun singleInstance(): Boolean {
    return try {
        val dir = java.nio.file.Path.of(System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"), "toastune")
        java.nio.file.Files.createDirectories(dir)
        val ch = java.nio.channels.FileChannel.open(dir.resolve("app.lock"), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)
        val lock = try { ch.tryLock() } catch (_: Exception) { null }
        if (lock == null) {
            java.nio.file.Files.writeString(dir.resolve("show.flag"), "1")
            false
        } else {
            java.nio.file.Files.deleteIfExists(dir.resolve("show.flag"))
            true // el candado vive mientras el proceso
        }
    } catch (_: Exception) { true }
}

private fun showFlag(): java.nio.file.Path =
    java.nio.file.Path.of(System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"), "toastune", "show.flag")

fun main() {
    if (!singleInstance()) return
    runApp()
}

private fun runApp() = application {
    val state = remember { AppState() }
    val prefs by state.settings.data.collectAsState()
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { state.start() }

    val holder = remember { arrayOfNulls<Mpris>(1) }
    fun quit() { holder[0]?.stop(); state.shutdown(); exitApplication() }
    val mpris = remember { Mpris(state.scope, state, onRaise = { visible = true }, onQuit = { quit() }).also { holder[0] = it } }
    val canHide = prefs.closeToTray && (isTraySupported || mpris.trayOk || mpris.active)

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { mpris.start() }
        // Otra ejecución de «toastune» pide mostrar la ventana de esta instancia
        while (true) {
            kotlinx.coroutines.delay(700)
            if (java.nio.file.Files.deleteIfExists(showFlag())) visible = true
        }
    }

    // Con bandeja del sistema, cerrar la ventana deja la música sonando en segundo plano.
    // El icono lo pone el ayudante D-Bus (StatusNotifierItem, como Steam/Discord); si no, la bandeja clásica de Java.
    if (prefs.closeToTray && isTraySupported && !mpris.trayOk) {
        Tray(
            icon = painterResource("icon.png"),
            tooltip = "Toastune",
            onAction = { visible = true },
            menu = {
                Item("Abrir Toastune") { visible = true }
                Item("Pausar / reanudar") { state.player.togglePlay() }
                Item("Siguiente") { state.player.next() }
                Item("Anterior") { state.player.previous() }
                Separator()
                Item("Salir") { quit() }
            },
        )
    }

    Window(
        onCloseRequest = { if (canHide) visible = false else quit() },
        visible = visible,
        title = "Toastune",
        icon = painterResource("icon.png"),
        state = rememberWindowState(size = DpSize(1180.dp, 760.dp)),
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) return@Window false
            val s = state.mpv.state.value
            val vol = prefs.volume
            if (e.isCtrlPressed) {
                // Funcionan también mientras escribes
                when (e.key) {
                    Key.Spacebar -> { state.player.togglePlay(); true }
                    Key.DirectionRight -> { state.player.next(); true }
                    Key.DirectionLeft -> { state.player.previous(); true }
                    else -> false
                }
            } else if (!state.typing) {
                when (e.key) {
                    Key.Spacebar -> { state.player.togglePlay(); true }
                    Key.DirectionRight -> { state.mpv.seekTo((s.positionMs + 5000).coerceAtMost(s.durationMs)); true }
                    Key.DirectionLeft -> { state.mpv.seekTo((s.positionMs - 5000).coerceAtLeast(0)); true }
                    Key.DirectionUp -> { state.setVolume((vol + 5).coerceAtMost(100)); true }
                    Key.DirectionDown -> { state.setVolume((vol - 5).coerceAtLeast(0)); true }
                    Key.N -> { state.player.next(); true }
                    Key.P -> { state.player.previous(); true }
                    Key.L -> { state.openNowPlaying(0); true }
                    else -> false
                }
            } else false
        },
    ) {
        LaunchedEffect(visible) { if (visible) { window.isMinimized = false; window.toFront() } }
        ToastuneTheme(dyslexic = prefs.dyslexic) { Surface(color = Color.Transparent, contentColor = Color.White) { MainScreen(state) } }
    }
}

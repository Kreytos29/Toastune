package app.toastune.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.abs

/**
 * Publica Toastune en D-Bus como reproductor MPRIS (barra del escritorio, playerctl, teclas multimedia, KDE Connect…).
 * Usa un pequeño ayudante en Python (python + python-gobject). Si no está, simplemente no se activa.
 */
class Mpris(
    private val scope: CoroutineScope,
    private val app: AppState,
    private val onRaise: () -> Unit,
    private val onQuit: () -> Unit,
) {
    private var proc: Process? = null
    private var out: BufferedWriter? = null
    @Volatile var active = false; private set
    /** true si el icono de bandeja (StatusNotifierItem) quedó registrado */
    var trayOk by mutableStateOf(false); private set

    private var lastSent = ""
    private var lastPos = 0L
    private var lastAt = 0L

    fun start() {
        val script = try {
            val dir = Path.of(System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"), "toastune")
            Files.createDirectories(dir)
            val f = dir.resolve("mpris.py")
            Mpris::class.java.getResourceAsStream("/mpris.py")!!.use { Files.copy(it, f, StandardCopyOption.REPLACE_EXISTING) }
            f.toString()
        } catch (_: Exception) { return }

        for (py in listOf("python3", "python")) {
            val p = try { ProcessBuilder(py, script).redirectError(ProcessBuilder.Redirect.DISCARD).start() } catch (_: Exception) { continue }
            val first = try { p.inputStream.bufferedReader().readLine() } catch (_: Exception) { null }
            if (first != "Ready") { p.destroy(); continue }
            proc = p
            out = p.outputStream.bufferedWriter()
            active = true
            Thread({ readActions(p) }, "mpris-in").apply { isDaemon = true }.start()
            watch()
            return
        }
    }

    private fun readActions(p: Process) {
        try {
            p.inputStream.bufferedReader().forEachLine { line ->
                scope.launch(Dispatchers.Main) { handle(line.trim()) }
            }
        } catch (_: Exception) {}
        active = false
    }

    private fun handle(cmd: String) {
        if (cmd == "Tray") { trayOk = true; return }
        if (cmd == "NoTray") { trayOk = false; return }
        val pl = app.player
        val st = app.mpv.state.value
        when {
            cmd == "PlayPause" -> pl.togglePlay()
            cmd == "Play" -> if (!st.playing) pl.togglePlay()
            cmd == "Pause" -> if (st.playing) pl.togglePlay()
            cmd == "Stop" -> app.mpv.setPaused(true)
            cmd == "Next" -> pl.next()
            cmd == "Previous" -> pl.previous()
            cmd == "Raise" -> onRaise()
            cmd == "Quit" -> onQuit()
            cmd == "Shuffle" -> pl.toggleShuffle()
            cmd == "Loop" -> pl.cycleRepeat()
            cmd.startsWith("Seek:") -> cmd.removePrefix("Seek:").toLongOrNull()?.let {
                app.mpv.seekTo((st.positionMs + it / 1000).coerceIn(0, st.durationMs.takeIf { d -> d > 0 } ?: Long.MAX_VALUE))
            }
            cmd.startsWith("SetPos:") -> cmd.removePrefix("SetPos:").toLongOrNull()?.let { app.mpv.seekTo((it / 1000).coerceAtLeast(0)) }
            cmd.startsWith("Vol:") -> cmd.removePrefix("Vol:").toDoubleOrNull()?.let { app.setVolume((it * 100).toInt().coerceIn(0, 100)) }
        }
    }

    private fun watch() {
        scope.launch {
            combine(app.player.queue, app.mpv.state, app.settings.data) { q, s, set -> Triple(q, s, set) }.collect { (q, s, set) ->
                val cur = q.current
                val status = when {
                    cur == null -> "Stopped"
                    s.playing || s.loading -> "Playing"
                    else -> "Paused"
                }
                val now = System.currentTimeMillis()
                val expected = if (status == "Playing") lastPos + (now - lastAt) else lastPos
                val jumped = abs(s.positionMs - expected) > 1500
                val m = JSONObject()
                    .put("id", cur?.id ?: "none")
                    .put("title", cur?.title ?: "")
                    .put("artist", cur?.artist ?: "")
                    .put("art", cur?.thumbnailUrl?.let { hiRes(it, 512) } ?: "")
                    .put("len", (if (s.durationMs > 0) s.durationMs else (cur?.durationMs ?: 0L)) * 1000)
                    .put("status", status)
                    .put("volume", set.volume / 100.0)
                    .put("next", q.queue.isNotEmpty())
                    .put("prev", q.queue.isNotEmpty())
                    .put("shuffle", q.shuffle)
                    .put("loop", when (q.repeat) { Repeat.OFF -> "None"; Repeat.ALL -> "Playlist"; Repeat.ONE -> "Track" })
                val key = m.toString()
                if (key != lastSent || jumped) {
                    lastSent = key
                    lastPos = s.positionMs; lastAt = now
                    m.put("pos", s.positionMs * 1000)
                    send(m.toString())
                }
            }
        }
    }

    private fun send(line: String) {
        try { out?.apply { write(line); newLine(); flush() } } catch (_: Exception) { active = false }
    }

    fun stop() { try { proc?.destroy() } catch (_: Exception) {} }
}

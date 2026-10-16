package app.toastune.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class PlaybackState(
    val playing: Boolean = false,
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/**
 * Reproduce audio con mpv, controlado por su socket IPC (hace falta tener `mpv` instalado).
 * Usa dos "platos" (dos procesos mpv) para poder mezclar una canción con la siguiente (crossfade).
 */
class MpvPlayer {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    @Volatile var onEnded: () -> Unit = {}
    @Volatile var onError: () -> Unit = {}

    private val a = Deck("a")
    private val b = Deck("b")
    @Volatile private var active = a
    private val other: Deck get() = if (active === a) b else a

    @Volatile private var master = 100
    @Volatile private var eq: String = ""
    @Volatile private var eqLevels: List<Int>? = null
    @Volatile private var trim = false
    private val _fade = MutableStateFlow(-1f)
    /** Progreso de la mezcla actual (0..1), o -1 si no hay mezcla. */
    val fadeProgress: StateFlow<Float> = _fade
    @Volatile private var fadeToken = 0
    @Volatile var fading = false; private set

    val isIdle: Boolean get() = active.idle

    /** Devuelve null si todo bien, o un mensaje de error. */
    fun start(): String? = a.start()

    /** Arranca el segundo mpv con antelación para que la mezcla no tenga que esperarlo. */
    fun warmSecond() {
        if (b.started) return
        Thread({ b.start() }, "mpv-warm").apply { isDaemon = true }.start()
    }

    private inner class Deck(val name: String) {
        val state = MutableStateFlow(PlaybackState())
        @Volatile var paused = false
        @Volatile var idle = true
        @Volatile var started = false
        @Volatile var loaded: CountDownLatch? = null
        @Volatile var failed = false
        @Volatile var preKey: String? = null
        @Volatile var preReady = false
        private var process: Process? = null
        private var channel: SocketChannel? = null
        private val writeLock = Any()
        private val socketPath: Path = Path.of(
            System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"),
            "toastune-mpv-${ProcessHandle.current().pid()}-$name.sock",
        )

        @Synchronized
        fun start(): String? {
            if (started) return null
            return try {
                Files.deleteIfExists(socketPath)
                process = ProcessBuilder(
                    "mpv", "--idle=yes", "--no-video", "--no-terminal", "--force-window=no",
                    "--audio-display=no", "--cache=yes", "--demuxer-max-bytes=64MiB",
                    "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
                    "--input-ipc-server=$socketPath",
                ).redirectErrorStream(true).start()
                var waited = 0
                while (!Files.exists(socketPath) && waited < 5000) {
                    if (process?.isAlive != true) return "mpv se cerró al arrancar"
                    Thread.sleep(50); waited += 50
                }
                val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
                ch.connect(UnixDomainSocketAddress.of(socketPath))
                channel = ch
                Thread({ readLoop(ch) }, "mpv-reader-$name").apply { isDaemon = true }.start()
                observe(1, "time-pos"); observe(2, "duration"); observe(3, "pause"); observe(5, "idle-active")
                started = true
                send("set_property", "volume", master)
                if (eq.isNotEmpty()) send("set_property", "af", eq)
                null
            } catch (e: java.io.IOException) {
                "No se pudo iniciar mpv. Instálalo con: sudo pacman -S mpv"
            }
        }

        private fun observe(id: Int, n: String) = send("observe_property", id, n)

        fun send(vararg args: Any) {
            val ch = channel ?: return
            val line = JSONObject().put("command", JSONArray(args.toList())).toString() + "\n"
            synchronized(writeLock) {
                try { ch.write(ByteBuffer.wrap(line.toByteArray())) } catch (_: java.io.IOException) {}
            }
        }

        private fun publish() { if (active === this) _state.value = state.value }

        private fun readLoop(ch: SocketChannel) {
            try {
                Channels.newInputStream(ch).bufferedReader().forEachLine { line ->
                    val o = try { JSONObject(line) } catch (_: Exception) { return@forEachLine }
                    when (o.optString("event")) {
                        "property-change" -> onProperty(o.optString("name"), o.opt("data"))
                        "start-file" -> { state.value = state.value.copy(loading = true, positionMs = 0); publish() }
                        "file-loaded" -> { state.value = state.value.copy(loading = false); publish(); if (preKey != null) preReady = true; loaded?.countDown() }
                        "end-file" -> when (o.optString("reason")) {
                            "eof" -> if (active === this) onEnded()
                            "error" -> {
                                failed = true; loaded?.countDown()
                                state.value = state.value.copy(loading = false, playing = false); publish()
                                if (active === this) onError()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        private fun onProperty(n: String, data: Any?) {
            val s = state.value
            state.value = when (n) {
                "time-pos" -> if (data is Number) s.copy(positionMs = (data.toDouble() * 1000).toLong()) else s
                "duration" -> if (data is Number) s.copy(durationMs = (data.toDouble() * 1000).toLong()) else s
                "pause" -> { paused = data != false; s.copy(playing = !paused && !idle) }
                "idle-active" -> { idle = data == true; if (idle) s.copy(playing = false, positionMs = 0, durationMs = 0) else s.copy(playing = !paused) }
                else -> s
            }
            publish()
        }

        fun release() {
            send("quit")
            try { channel?.close() } catch (_: Exception) {}
            process?.destroy()
            try { Files.deleteIfExists(socketPath) } catch (_: Exception) {}
        }
    }

    // ---------- API pública (opera sobre el plato activo) ----------

    @Volatile var onFadeEnd: () -> Unit = {}

    /** ¿La canción [key] ya está cargada (en pausa) en el otro plato? */
    fun hasPreload(key: String): Boolean { val o = other; return o.started && o.preKey == key && o.preReady && !o.failed }

    /** Carga en silencio la siguiente canción en el otro plato: así el cambio es instantáneo y sin pausa. */
    fun preload(key: String, url: String) {
        if (fading) return
        Thread({
            val d = other
            if (fading || d.start() != null || d.preKey == key) return@Thread
            d.failed = false; d.preReady = false; d.preKey = key
            d.send("set_property", "volume", 0)
            d.send("set_property", "pause", true)
            d.send("loadfile", url, "replace")
        }, "mpv-preload").apply { isDaemon = true }.start()
    }

    /** Reproduce [url]; si [key] ya estaba precargada, cambia al instante. */
    fun play(url: String, key: String? = null): Boolean {
        cancelFade()
        if (key != null && hasPreload(key)) {
            val old = active
            val nw = other
            nw.preKey = null; nw.preReady = false
            old.send("stop")
            active = nw
            nw.send("set_property", "volume", master)
            nw.send("set_property", "pause", false)
            _state.value = nw.state.value
            return true
        }
        val d = active
        d.failed = false; d.preKey = null
        d.state.value = d.state.value.copy(loading = true, positionMs = 0, durationMs = 0)
        _state.value = d.state.value
        d.send("set_property", "volume", master)
        d.send("set_property", "pause", false)
        d.send("loadfile", url, "replace")
        return false
    }

    /**
     * Mezcla: la canción [key] entra en el otro plato (ya precargada si es posible) mientras la actual baja de volumen.
     * [onStarted] avisa cuando la nueva ya suena (true) o si no se pudo (false).
     */
    fun crossfadeTo(url: String, key: String, seconds: Int, style: Int = 0, valid: () -> Boolean = { true }, onStarted: (Boolean) -> Unit) {
        Thread({
            // Si había una mezcla en curso, se da por terminada para encadenar la nueva
            if (fading) {
                fadeToken++
                other.send("stop")
                active.send("set_property", "volume", master)
                fading = false
                _fade.value = -1f
                Thread.sleep(60)
            }
            if (!valid()) { onStarted(false); return@Thread }
            val out = active
            val inc = other
            if (inc.start() != null) { onStarted(false); return@Thread }
            val token = ++fadeToken
            if (inc.preKey == key && inc.preReady && !inc.failed) {
                inc.preKey = null; inc.preReady = false
                inc.send("set_property", "volume", 0)
                inc.send("set_property", "pause", false)
            } else {
                inc.preKey = null; inc.preReady = false
                inc.failed = false
                inc.loaded = CountDownLatch(1)
                inc.send("set_property", "volume", 0)
                inc.send("set_property", "pause", false)
                inc.send("loadfile", url, "replace")
                val ok = inc.loaded!!.await(8, TimeUnit.SECONDS) && !inc.failed
                if (!ok || token != fadeToken) { inc.send("stop"); onStarted(false); return@Thread }
            }
            if (token != fadeToken || !valid()) { inc.send("stop"); onStarted(false); return@Thread }
            fading = true
            active = inc
            _state.value = inc.state.value
            _fade.value = 0f
            onStarted(true)
            // No más larga que lo que le queda a la canción que sale
            val os = out.state.value
            val remain = os.durationMs - os.positionMs
            val total = if (os.durationMs > 0 && remain > 0) minOf(seconds * 1000L, maxOf(800L, remain - 150)) else seconds * 1000L
            val t0 = System.currentTimeMillis()
            while (token == fadeToken) {
                val t = ((System.currentTimeMillis() - t0).toDouble() / total).coerceIn(0.0, 1.0)
                val (go, gi) = mixGains(t, style)
                _fade.value = t.toFloat()
                out.send("set_property", "volume", (master * go).toInt())
                inc.send("set_property", "volume", (master * gi).toInt())
                if (t >= 1.0) break
                Thread.sleep(40)
            }
            if (token == fadeToken) {
                out.send("stop")
                inc.send("set_property", "volume", master)
                fading = false
                _fade.value = -1f
                onFadeEnd()
            }
        }, "mpv-crossfade").apply { isDaemon = true }.start()
    }

    private fun cancelFade() {
        fadeToken++
        if (fading) {
            other.send("stop")
            active.send("set_property", "volume", master)
            fading = false
        }
        _fade.value = -1f
    }

    fun setPaused(paused: Boolean) {
        active.send("set_property", "pause", paused)
        if (fading) other.send("set_property", "pause", paused)
    }
    fun toggle() = setPaused(_state.value.playing)
    fun seekTo(ms: Long) = active.send("seek", ms / 1000.0, "absolute")

    /** Ecualizador de 5 bandas con el filtro de audio de mpv; null lo desactiva. */
    fun setEqualizer(levels: List<Int>?) { eqLevels = levels; applyAf() }

    /** Recorta el silencio del principio de cada canción (útil para que las mezclas entren a tiempo). */
    fun setTrimSilence(on: Boolean) { if (trim != on) { trim = on; applyAf() } }

    private fun applyAf() {
        val parts = ArrayList<String>()
        if (trim) parts += "silenceremove=start_periods=1:start_threshold=-48dB"
        eqLevels?.takeIf { l -> l.any { it != 0 } }?.forEachIndexed { i, g -> parts += "equalizer=f=${EqPresets.bands[i]}:t=o:w=1.5:g=$g" }
        eq = if (parts.isEmpty()) "" else "lavfi=[" + parts.joinToString(",") + "]"
        a.send("set_property", "af", eq)
        if (b.started) b.send("set_property", "af", eq)
    }

    fun setVolume(percent: Int) {
        master = percent
        if (!fading) { a.send("set_property", "volume", percent); if (b.started) b.send("set_property", "volume", percent) }
    }

    fun stop() {
        cancelFade()
        a.preKey = null; b.preKey = null
        a.send("stop"); if (b.started) b.send("stop")
    }

    fun release() {
        fadeToken++
        a.release(); if (b.started) b.release()
    }
}

/** Volumen (saliente, entrante) en 0..1 según el avance t de la mezcla. */
fun mixGains(t: Double, style: Int): Pair<Double, Double> {
    when (style) {
        1 -> { // DJ: la nueva entra rápido, la vieja se mantiene más; potencia normalizada
            val gi = sin(minOf(1.0, t / 0.5) * PI / 2)
            val go = cos(maxOf(0.0, (t - 0.3) / 0.7) * PI / 2)
            val n = maxOf(1.0, Math.hypot(go, gi))
            return go / n to gi / n
        }
        2 -> { // Ambiental: la vieja se apaga pronto y la nueva llega tarde, con un respiro en medio
            val go = cos(minOf(1.0, t / 0.75) * PI / 2)
            val gi = sin(maxOf(0.0, (t - 0.25) / 0.75) * PI / 2)
            return go to gi
        }
        else -> return cos(t * PI / 2) to sin(t * PI / 2) // Suave: potencia constante
    }
}

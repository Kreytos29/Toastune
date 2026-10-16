package app.toastune.desktop

import app.toastune.core.MusicProvider
import app.toastune.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Repeat { OFF, ALL, ONE }

data class QueueState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.OFF,
    val error: String? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
}

class PlayerController(
    private val scope: CoroutineScope,
    private val provider: MusicProvider,
    val mpv: MpvPlayer,
    private val localPath: (String) -> String? = { null },
    private val onPlayed: (Track) -> Unit = {},
    private val quality: () -> app.toastune.core.AudioQuality = { app.toastune.core.AudioQuality.HIGH },
) {
    @Volatile var autoplay = true
    private val _q = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _q
    private var job: Job? = null
    private val urlCache = HashMap<String, Pair<String, Long>>()

    /** URL de audio con caché de 90 min, para que la siguiente canción empiece sin espera. */
    private suspend fun resolve(id: String): String {
        val now = System.currentTimeMillis()
        synchronized(urlCache) { urlCache[id]?.takeIf { now - it.second < 90 * 60_000 }?.let { return it.first } }
        val url = withContext(Dispatchers.IO) { provider.getStreamUrl(id, quality()) }
        synchronized(urlCache) { urlCache[id] = url to now }
        return url
    }

    private fun prefetchNext() {
        val s = _q.value
        for (t in s.queue.drop(s.index + 1).take(2)) {
            if (localPath(t.id) != null) continue
            scope.launch { try { resolve(t.id) } catch (_: Exception) {} }
        }
    }
    private var failures = 0

    /** Segundos de mezcla entre canciones (0 = sin mezcla). */
    @Volatile var crossfade = 0
        set(v) { field = v; if (v > 0) mpv.warmSecond() }
    @Volatile var mixStyle = 0
    @Volatile var mixOnSkip = true
    private var mixing = false
    private var mixSkip: String? = null
    private var mixGen = 0
    private var preloadJob: Job? = null
    private var planned: Int? = null

    /** El estilo "Directa" es un corte rápido; los demás usan la duración elegida. */
    private fun effSec(cf: Int) = if (mixStyle == 3) minOf(cf, 2) else cf

    /** Cuando la canción está por terminar, la mezcla con la siguiente (crossfade). */
    private fun maybeMix(st: PlaybackState) {
        val cf = crossfade
        if (cf <= 0 || mixing || mpv.fading || !st.playing) return
        val q = _q.value
        val cur = q.current ?: return
        if (q.repeat == Repeat.ONE || cur.id == mixSkip) return
        val sec = effSec(cf)
        if (st.durationMs < (sec * 2 + 10) * 1000L || st.durationMs - st.positionMs > sec * 1000L + 250) return
        val n = nextIndex(q)
        if (n < 0) return
        mixTo(n, sec) {
            mixSkip = cur.id
            // Si mientras tanto la canción terminó, sigue con la siguiente
            if (mpv.isIdle && _q.value.current?.id == cur.id) direct(n)
        }
    }

    /** Mezcla con la canción [n] de la cola; si no se puede, ejecuta [onFail]. */
    private fun mixTo(n: Int, sec: Int, onFail: () -> Unit) {
        val t = _q.value.queue.getOrNull(n) ?: return
        mixing = true
        val gen = mixGen
        scope.launch {
            try {
                val url = if (mpv.hasPreload(t.id)) "" else (localPath(t.id) ?: resolve(t.id))
                mpv.crossfadeTo(url, t.id, sec, mixStyle, valid = { gen == mixGen }) { ok ->
                    scope.launch {
                        mixing = false
                        if (gen != mixGen) return@launch // el usuario ya eligió otra cosa
                        if (ok) {
                            failures = 0; planned = null
                            _q.value = _q.value.copy(index = n, error = null)
                            onPlayed(t); prefetchNext()
                        } else onFail()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { mixing = false; onFail() }
        }
    }

    /** Cambio manual de canción: con mezcla activada se funde un momento en vez de cortar. */
    private fun go(n: Int) {
        val cf = crossfade
        if (cf > 0 && mixOnSkip && mpv.state.value.playing && !mpv.isIdle && !mixing && n != _q.value.index) {
            mixTo(n, minOf(effSec(cf), 3)) { direct(n) }
        } else direct(n)
    }

    private fun direct(n: Int) {
        _q.value = _q.value.copy(index = n); planned = null; playCurrent()
    }

    /** Salta a [ms]: si aún no hay audio cargado, lo hará en cuanto esté listo. */
    fun seekTo(ms: Long) {
        val st = mpv.state.value
        when {
            st.durationMs > 0 && !st.loading && !mpv.isIdle -> mpv.seekTo(ms)
            mpv.isIdle -> playCurrent(ms)
            else -> pendingSeek = ms
        }
    }

    /** Empieza la canción actual en el instante [ms] (p. ej. al mover la barra con la cola restaurada). */
    fun playAt(ms: Long) = playCurrent(ms)
    @Volatile private var pendingSeek: Long? = null

    /** Carga en segundo plano la siguiente canción para que el cambio sea instantáneo y sin pausa. */
    fun preloadNext() {
        preloadJob?.cancel()
        val s = _q.value
        val cur = s.current ?: return
        if (s.repeat == Repeat.ONE) return
        val n = nextIndex(s)
        val t = s.queue.getOrNull(n) ?: return
        if (t.id == cur.id) return
        preloadJob = scope.launch {
            try {
                kotlinx.coroutines.delay(1500)
                if (mpv.fading || mpv.hasPreload(t.id)) return@launch
                mpv.preload(t.id, localPath(t.id) ?: resolve(t.id))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    /** Salta al final de la canción para escuchar cómo suena la mezcla. */
    fun previewMix() {
        val st = mpv.state.value
        val sec = effSec(crossfade)
        if (sec <= 0 || st.durationMs <= 0) return
        mpv.seekTo(maxOf(0L, st.durationMs - (sec + 3) * 1000L))
    }

    private fun nextIndex(s: QueueState): Int = when {
        s.queue.isEmpty() -> -1
        s.shuffle && s.queue.size > 1 -> planned?.takeIf { it in s.queue.indices && it != s.index } ?: (s.queue.indices - s.index).random().also { planned = it }
        s.index + 1 < s.queue.size -> s.index + 1
        s.repeat == Repeat.ALL -> 0
        else -> -1
    }

    init {
        scope.launch {
            mpv.state.collect { st ->
                pendingSeek?.let { if (st.durationMs > 0 && !st.loading && st.playing) { mpv.seekTo(it); pendingSeek = null } }
                maybeMix(st)
            }
        }
        mpv.onFadeEnd = { scope.launch { preloadNext() } }
        mpv.onEnded = { scope.launch { onTrackEnded() } }
        mpv.onError = { scope.launch { onTrackFailed() } }
    }

    fun playList(tracks: List<Track>, start: Int = 0) {
        if (tracks.isEmpty()) return
        failures = 0
        _q.value = _q.value.copy(queue = tracks, index = start.coerceIn(tracks.indices), error = null)
        playCurrent()
    }

    fun playOne(track: Track) = playList(listOf(track))

    fun enqueue(track: Track) {
        val s = _q.value
        if (s.queue.isEmpty()) playOne(track) else { _q.value = s.copy(queue = s.queue + track); preloadNext() }
    }

    fun playNext(track: Track) {
        val s = _q.value
        if (s.queue.isEmpty()) return playOne(track)
        val q = s.queue.toMutableList().also { it.add(s.index + 1, track) }
        _q.value = s.copy(queue = q)
        planned = null; preloadNext()
    }

    fun jumpTo(i: Int) {
        if (i in _q.value.queue.indices) go(i)
    }

    fun next() {
        val s = _q.value
        if (s.queue.isEmpty()) return
        val n = nextIndex(s)
        if (n >= 0) go(n) else autoplayOrStop()
    }

    /** Siempre va a la canción anterior con un solo clic (en la primera, vuelve al inicio). */
    fun previous() {
        val s = _q.value
        if (s.queue.isEmpty()) return
        val n = if (s.index > 0) s.index - 1 else if (s.repeat == Repeat.ALL) s.queue.lastIndex else -1
        if (n < 0) mpv.seekTo(0) else go(n)
    }

    /** Pausa/reanuda; si no hay nada cargado (p. ej. cola restaurada) empieza la canción actual. */
    fun togglePlay() {
        if (mpv.isIdle && _q.value.current != null) playCurrent() else mpv.toggle()
    }

    /** Cola guardada de la sesión anterior: se muestra pero no suena hasta pulsar play. */
    fun restore(queue: List<Track>, index: Int) {
        if (queue.isEmpty()) return
        _q.value = _q.value.copy(queue = queue, index = index.coerceIn(queue.indices))
    }

    /** Cambia una canción de lugar en la cola; la que suena sigue sonando. */
    fun move(from: Int, to: Int) {
        val s = _q.value
        if (from !in s.queue.indices || to !in s.queue.indices || from == to) return
        val list = s.queue.toMutableList()
        list.add(to, list.removeAt(from))
        val idx = when {
            s.index == from -> to
            from < s.index && to >= s.index -> s.index - 1
            from > s.index && to <= s.index -> s.index + 1
            else -> s.index
        }
        autoIdx = emptySet()
        _q.value = s.copy(queue = list, index = idx)
        planned = null; preloadNext()
    }

    fun removeAt(i: Int) {
        val s = _q.value
        if (i !in s.queue.indices) return
        autoIdx = emptySet()
        if (s.queue.size == 1) return clear()
        val list = s.queue.toMutableList().also { it.removeAt(i) }
        when {
            i < s.index -> { _q.value = s.copy(queue = list, index = s.index - 1); planned = null; preloadNext() }
            i > s.index -> { _q.value = s.copy(queue = list); planned = null; preloadNext() }
            else -> { _q.value = s.copy(queue = list, index = minOf(i, list.lastIndex)); playCurrent() }
        }
    }

    fun clear() {
        job?.cancel()
        autoIdx = emptySet()
        mpv.stop()
        _q.value = _q.value.copy(queue = emptyList(), index = -1, error = null)
    }

    /** Baraja solo lo que viene después de la canción actual. */
    fun shuffleUpcoming() {
        val s = _q.value
        if (s.index < 0 || s.index >= s.queue.lastIndex) return
        autoIdx = emptySet()
        _q.value = s.copy(queue = s.queue.take(s.index + 1) + s.queue.drop(s.index + 1).shuffled())
    }

    fun toggleShuffle() { _q.value = _q.value.copy(shuffle = !_q.value.shuffle) }

    /** Al pasar a "repetir todo" se descartan las canciones que añadió la reproducción automática. */
    fun cycleRepeat() {
        val s = _q.value
        val r = when (s.repeat) { Repeat.OFF -> Repeat.ALL; Repeat.ALL -> Repeat.ONE; Repeat.ONE -> Repeat.OFF }
        _q.value = if (r == Repeat.ALL) {
            val cur = s.current
            val keep = s.queue.filterIndexed { i, t -> i !in autoIdx || i == s.index }
            autoIdx = emptySet()
            s.copy(repeat = r, queue = keep, index = keep.indexOf(cur).coerceAtLeast(0))
        } else s.copy(repeat = r)
    }

    private var autoIdx: Set<Int> = emptySet()

    private fun playCurrent(at: Long? = null) {
        val t = _q.value.current ?: return
        mixGen++
        pendingSeek = at
        job?.cancel()
        job = scope.launch {
            try {
                val url = if (mpv.hasPreload(t.id)) "" else (localPath(t.id) ?: resolve(t.id))
                mpv.play(url, t.id)
                onPlayed(t)
                prefetchNext()
                preloadNext()
                _q.value = _q.value.copy(error = null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onTrackFailed()
            }
        }
    }

    private fun onTrackFailed() {
        failures++
        if (failures >= 3) {
            _q.value = _q.value.copy(error = "No se pudo reproducir. Revisa tu conexión.")
            failures = 0
        } else next()
    }

    private fun onTrackEnded() {
        if (mixing) return // la mezcla en curso se encarga del cambio
        failures = 0
        if (_q.value.repeat == Repeat.ONE) playCurrent() else next()
    }

    /** Se acabó la cola: sin repetición, sigue con canciones parecidas. */
    private fun autoplayOrStop() {
        val s = _q.value
        val cur = s.current ?: return
        if (s.repeat != Repeat.OFF || !autoplay) return
        scope.launch {
            val more = try {
                withContext(Dispatchers.IO) { provider.related(cur.id) }.filter { r -> s.queue.none { it.id == r.id } }
            } catch (_: Exception) { emptyList() }
            if (more.isEmpty()) return@launch
            val start = _q.value.queue.size
            autoIdx = autoIdx + (start until start + more.size)
            _q.value = _q.value.copy(queue = _q.value.queue + more, index = start)
            playCurrent()
        }
    }
}

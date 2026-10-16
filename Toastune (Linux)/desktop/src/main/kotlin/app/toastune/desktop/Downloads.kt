package app.toastune.desktop

import app.toastune.core.MusicProvider
import app.toastune.core.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

enum class DlState { QUEUED, ACTIVE, FAILED }

data class DlTask(val track: Track, val state: DlState, val progress: Float = 0f, val error: String? = null)

/** Descargas para escuchar sin conexión, en ~/.local/share/toastune/music. */
class Downloads(
    private val scope: CoroutineScope,
    private val provider: MusicProvider,
    private val library: Library,
    private val quality: () -> app.toastune.core.AudioQuality = { app.toastune.core.AudioQuality.HIGH },
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val dir: Path = Path.of(
        System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"),
        "toastune", "music",
    ).also {
        Files.createDirectories(it)
        // Restos de descargas interrumpidas
        Files.list(it).use { s -> s.filter { p -> p.toString().endsWith(".part") }.forEach { p -> Files.deleteIfExists(p) } }
    }
    private val limit = Semaphore(2)
    private val jobs = HashMap<String, Job>()

    private val _tasks = MutableStateFlow<List<DlTask>>(emptyList())
    /** Cola de descargas en curso, en espera o fallidas. */
    val tasks: StateFlow<List<DlTask>> = _tasks

    private val _have = MutableStateFlow(scan())
    /** Ids que ya están en disco. */
    val have: StateFlow<Set<String>> = _have

    private fun file(id: String) = dir.resolve("$id.audio")
    private fun scan(): Set<String> = Files.list(dir).use { s ->
        s.map { it.fileName.toString() }.filter { it.endsWith(".audio") }.map { it.removeSuffix(".audio") }.toList().toSet()
    }

    /** Espacio que ocupan las descargas, en bytes. */
    fun sizeBytes(): Long = try {
        Files.list(dir).use { s -> s.mapToLong { runCatching { Files.size(it) }.getOrDefault(0L) }.sum() }
    } catch (_: Exception) { 0L }

    fun localPath(id: String): String? = file(id).takeIf { Files.exists(it) }?.toString()

    private fun setTask(id: String, f: (DlTask) -> DlTask) {
        _tasks.value = _tasks.value.map { if (it.track.id == id) f(it) else it }
    }

    fun download(track: Track) {
        if (track.id in _have.value) return
        val existing = _tasks.value.firstOrNull { it.track.id == track.id }
        if (existing != null && existing.state != DlState.FAILED) return
        _tasks.value = _tasks.value.filterNot { it.track.id == track.id } + DlTask(track, DlState.QUEUED)
        jobs[track.id] = scope.launch {
            try {
                limit.withPermit {
                    setTask(track.id) { it.copy(state = DlState.ACTIVE, progress = 0f) }
                    withContext(Dispatchers.IO) { fetch(track) }
                }
                library.addDownload(track)
                _have.value = _have.value + track.id
                _tasks.value = _tasks.value.filterNot { it.track.id == track.id }
            } catch (e: CancellationException) {
                _tasks.value = _tasks.value.filterNot { it.track.id == track.id }
                withContext(kotlinx.coroutines.NonCancellable) { Files.deleteIfExists(dir.resolve("${track.id}.part")) }
                throw e
            } catch (e: Exception) {
                setTask(track.id) { it.copy(state = DlState.FAILED, error = e.message ?: "Error de red") }
            } finally {
                jobs.remove(track.id)
            }
        }
    }

    /** Baja el audio en trozos de 1 MB con cabecera Range (así lo hace YouTube y evita que lo frene). */
    private suspend fun fetch(track: Track) {
        val url = provider.getStreamUrl(track.id, quality())
        val tmp = dir.resolve("${track.id}.part")
        Files.deleteIfExists(tmp)
        var start = 0L
        var total = -1L
        val chunk = 1_048_576L
        while (total < 0 || start < total) {
            currentCoroutineContext().ensureActive()
            var attempt = 0
            while (true) {
                try {
                    val req = Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
                        .header("Range", "bytes=$start-${start + chunk - 1}")
                        .build()
                    client.newCall(req).execute().use { r ->
                        if (r.code == 200) {
                            // El servidor ignora Range: se baja todo de una vez.
                            val body = r.body ?: error("Respuesta vacía")
                            val len = body.contentLength()
                            var done = 0L
                            Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
                                body.byteStream().use { input ->
                                    val buf = ByteArray(64 * 1024)
                                    while (true) {
                                        val n = input.read(buf); if (n < 0) break
                                        out.write(buf, 0, n); done += n
                                        if (len > 0) setTask(track.id) { it.copy(progress = done.toFloat() / len) }
                                    }
                                }
                            }
                            total = done; start = done
                        } else if (r.code == 206) {
                            val range = r.header("Content-Range") ?: error("Sin Content-Range")
                            total = range.substringAfter('/').toLongOrNull() ?: error("Tamaño desconocido")
                            val bytes = r.body?.bytes() ?: error("Respuesta vacía")
                            Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                            start += bytes.size
                            setTask(track.id) { it.copy(progress = (start.toFloat() / total).coerceIn(0f, 1f)) }
                        } else error("HTTP ${r.code}")
                    }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (++attempt >= 3) throw e
                    kotlinx.coroutines.delay(800L * attempt)
                }
            }
        }
        Files.move(tmp, file(track.id), StandardCopyOption.REPLACE_EXISTING)
    }

    fun downloadAll(tracks: List<Track>) = tracks.forEach(::download)

    fun cancel(id: String) {
        val j = jobs[id]
        if (j != null) j.cancel() else _tasks.value = _tasks.value.filterNot { it.track.id == id }
    }

    fun cancelAll() = _tasks.value.map { it.track.id }.forEach(::cancel)

    fun retryFailed() = _tasks.value.filter { it.state == DlState.FAILED }.forEach { download(it.track) }

    fun remove(id: String) {
        try { Files.deleteIfExists(file(id)) } catch (_: Exception) {}
        _have.value = _have.value - id
        library.removeDownload(id)
    }

    fun removeAll(tracks: List<Track>) = tracks.forEach { remove(it.id) }
}

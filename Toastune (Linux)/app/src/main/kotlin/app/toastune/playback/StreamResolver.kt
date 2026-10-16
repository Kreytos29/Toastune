package app.toastune.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import app.toastune.core.AudioQuality
import app.toastune.core.MusicProvider
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Esquema interno: la cola guarda "toastune://track/<id>" y la URL real se resuelve al reproducir. */
const val TRACK_SCHEME = "toastune"

/**
 * Convierte "toastune://track/<id>" en la URL real de audio. Se ejecuta en el hilo de carga del
 * reproductor, por eso puede bloquear. Guarda las URLs un rato para no repetir la extracción.
 */
@OptIn(UnstableApi::class)
class StreamResolver(
    private val provider: MusicProvider,
    private val quality: () -> AudioQuality,
) : ResolvingDataSource.Resolver {

    private class Entry(val url: String, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Any>()

    fun clear() = cache.clear()

    private fun cached(key: String): String? =
        cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.url

    /** Devuelve la URL real; si otro hilo ya la está pidiendo (precarga), espera en vez de repetir la petición. */
    fun resolve(id: String): String {
        val q = quality()
        val key = "$id|$q"
        cached(key)?.let { return it }
        synchronized(locks.getOrPut(key) { Any() }) {
            cached(key)?.let { return it }
            val url = runBlocking { provider.getStreamUrl(id, q) }
            cache[key] = Entry(url, System.currentTimeMillis() + CACHE_MS)
            return url
        }
    }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != TRACK_SCHEME) return dataSpec
        val id = uri.lastPathSegment ?: throw IOException("Identificador de canción inválido")
        val url = try {
            resolve(id)
        } catch (e: Exception) {
            throw IOException("No se pudo obtener el audio: ${e.message}", e)
        }
        return dataSpec.withUri(Uri.parse(url))
    }

    private companion object {
        const val CACHE_MS = 90 * 60 * 1000L
    }
}

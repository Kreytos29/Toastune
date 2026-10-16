package app.toastune.desktop

import app.toastune.core.LrcParser
import app.toastune.core.LyricLine
import app.toastune.core.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Letras desde LRCLIB (https://lrclib.net), con letras sincronizadas cuando existen.
 * Limpia el título y el artista (quita "(Official Video)", "feat.", "Artista - "…), prueba varias búsquedas,
 * descarta letras cuya duración no cuadra con la canción y guarda lo encontrado en disco.
 */
class LrclibLyrics {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val cache = HashMap<String, Lyrics?>()
    private val dir: Path = Path.of(
        System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"), "toastune", "lyrics",
    )

    suspend fun lyrics(title: String, artist: String, durationMs: Long?, force: Boolean = false): Lyrics? = withContext(Dispatchers.IO) {
        val a = primaryArtist(artist)
        val t = cleanTitle(title, a)
        val key = "$a|$t"
        if (force) { synchronized(cache) { cache.remove(key) }; runCatching { Files.deleteIfExists(file(key)) } }
        synchronized(cache) { if (cache.containsKey(key) && cache[key] != null) return@withContext cache[key] }
        readDisk(key)?.let { l -> synchronized(cache) { cache[key] = l }; return@withContext l }

        val secs = durationMs?.takeIf { it > 0 }?.div(1000)
        val result = runCatching {
            val found = get("get", mapOf("track_name" to t, "artist_name" to a) + (secs?.let { mapOf("duration" to "$it") } ?: emptyMap()))
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: best(get("search", mapOf("track_name" to t, "artist_name" to a)), secs, t)
                ?: best(get("search", mapOf("q" to "$t $a")), secs, t)
                ?: best(get("search", mapOf("q" to t)), secs, t)
            found?.also { writeDisk(key, it) }?.let(::toLyrics)
        }.getOrNull()
        synchronized(cache) { cache[key] = result }
        result
    }

    // ---------- Limpieza ----------

    private fun primaryArtist(raw: String): String =
        raw.replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
            .split(",", " & ", " x ", " X ", " feat", " ft.", " y ")
            .first().trim()

    private val noise = Regex(
        """\s*[(\[][^)\]]*(feat|ft\.?|with|official|oficial|video|vídeo|lyric|letra|audio|remix|remaster|live|en vivo|hd|4k|visualizer|explicit)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )

    private fun cleanTitle(raw: String, artist: String): String {
        var t = raw.replace(noise, "")
        // "Artista - Título" → "Título"
        Regex("""^\s*(.+?)\s+[-–—]\s+(.+)$""").find(t)?.let { m ->
            if (artist.isNotBlank() && m.groupValues[1].contains(artist, ignoreCase = true)) t = m.groupValues[2]
        }
        t = t.replace(Regex("""\s+[-–—]\s*(remaster(ed)?.*|single version|radio edit|\d{4}.*)$""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""\s*(ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
        return t.trim().ifBlank { raw.trim() }
    }

    // ---------- Selección ----------

    /** La mejor opción de una lista: con letra sincronizada y duración parecida; si la duración no cuadra, ninguna. */
    private fun best(body: String?, secs: Long?, title: String): JSONObject? {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        var top: JSONObject? = null
        var topScore = Int.MIN_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val synced = !o.isNull("syncedLyrics") && o.optString("syncedLyrics").isNotBlank()
            val plain = !o.isNull("plainLyrics") && o.optString("plainLyrics").isNotBlank()
            if (!synced && !plain && !o.optBoolean("instrumental")) continue
            val d = o.optDouble("duration", -1.0)
            val diff = if (secs != null && d > 0) abs(d - secs) else 0.0
            if (secs != null && d > 0 && diff > 8) continue
            var score = (if (synced) 100 else 0) - (diff * 6).toInt()
            if (o.optString("trackName").equals(title, ignoreCase = true)) score += 30
            if (score > topScore) { topScore = score; top = o }
        }
        return top
    }

    private fun get(path: String, params: Map<String, String>): String? {
        val url = "https://lrclib.net/api/$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url).header("User-Agent", "Toastune-Desktop/0.8 (open source music player)").build()
        return client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private fun toLyrics(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental")) return Lyrics(listOf(LyricLine(0, "♪ Instrumental")), false)
        val synced = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() }
        if (synced != null) {
            val lines = LrcParser.parse(synced)
            if (lines.isNotEmpty()) return Lyrics(lines, true)
        }
        val plain = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() } ?: return null
        return Lyrics(plain.lines().map { LyricLine(0, it) }, false)
    }

    // ---------- Caché en disco ----------

    private fun file(key: String): Path {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
        return dir.resolve("$h.json")
    }

    private fun readDisk(key: String): Lyrics? = runCatching {
        toLyrics(JSONObject(Files.readString(file(key))))
    }.getOrNull()

    private fun writeDisk(key: String, o: JSONObject) {
        runCatching { Files.createDirectories(dir); Files.writeString(file(key), o.toString()) }
    }
}

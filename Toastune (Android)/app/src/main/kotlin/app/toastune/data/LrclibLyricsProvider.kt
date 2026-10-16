package app.toastune.data

import app.toastune.core.LrcParser
import app.toastune.core.LyricLine
import app.toastune.core.Lyrics
import app.toastune.core.LyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/** Letras desde LRCLIB (https://lrclib.net), un servicio abierto y gratuito, con letras sincronizadas. */
class LrclibLyricsProvider(private val client: OkHttpClient) : LyricsProvider {

    private val cache: MutableMap<String, Lyrics?> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Lyrics?>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics?>?): Boolean = size > 30
        },
    )

    override suspend fun lyrics(title: String, artist: String, durationMs: Long?): Lyrics? =
        withContext(Dispatchers.IO) {
            val cleanTitle = clean(title)
            val cleanArtist = artist.replace(" - Topic", "").trim()
            val key = "$cleanArtist|$cleanTitle"
            if (cache.containsKey(key)) return@withContext cache[key]

            val result = runCatching {
                val exact = get("get", cleanTitle, cleanArtist, durationMs)?.let { runCatching { JSONObject(it) }.getOrNull() }
                val found = exact ?: searchFirst(cleanTitle, cleanArtist)
                found?.let { toLyrics(it) }
            }.getOrNull()

            cache[key] = result
            result
        }

    private fun searchFirst(title: String, artist: String): JSONObject? {
        val body = get("search", title, artist, null) ?: return null
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (!item.isNull("syncedLyrics") || !item.isNull("plainLyrics")) return item
        }
        return null
    }

    private fun get(path: String, title: String, artist: String, durationMs: Long?): String? {
        val url = "https://lrclib.net/api/$path".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply {
                if (durationMs != null && durationMs > 0) addQueryParameter("duration", (durationMs / 1000).toString())
            }
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "Toastune/0.3 (open source music player)").build()
        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) response.body?.string() else null
        }
    }

    private fun toLyrics(json: JSONObject): Lyrics? {
        val synced = if (json.isNull("syncedLyrics")) null else json.getString("syncedLyrics")
        if (!synced.isNullOrBlank()) {
            val lines = LrcParser.parse(synced)
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true)
        }
        val plain = if (json.isNull("plainLyrics")) null else json.getString("plainLyrics")
        if (!plain.isNullOrBlank()) {
            return Lyrics(plain.lines().map { LyricLine(0L, it.trim()) }, synced = false)
        }
        return null
    }

    /** Quita "(Official Video)", "[Letra]" y similares que despistan al buscar. */
    private fun clean(title: String): String =
        title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").replace(Regex("""\s+"""), " ").trim()
}

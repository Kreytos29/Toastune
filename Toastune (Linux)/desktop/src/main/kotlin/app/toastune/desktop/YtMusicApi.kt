package app.toastune.desktop

import app.toastune.core.Album
import app.toastune.core.ArtistPage
import app.toastune.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** La sesión no sirve (caducó o se cerró en otro lado): hay que volver a iniciar sesión. */
class YtAuthException(message: String) : IOException(message)

data class YtPlaylist(val id: String, val title: String, val subtitle: String, val thumbnailUrl: String?)

/**
 * Cliente mínimo de la API interna de YouTube Music con la sesión del usuario (como Metrolist).
 * Las respuestas son árboles JSON enormes y cambiantes, así que en vez de seguir rutas fijas se
 * buscan los elementos por nombre (musicResponsiveListItemRenderer, musicTwoRowItemRenderer).
 */
class YtMusicApi(private val client: OkHttpClient, private val store: YtAccountStore) {

    val loggedIn get() = store.state.value != null

    // --- Peticiones ---

    private suspend fun post(
        endpoint: String,
        browseId: String?,
        token: String?,
        anonymous: Boolean = false,
        extra: (JSONObject) -> Unit = {},
    ): JSONObject = withContext(Dispatchers.IO) {
        // Las páginas públicas (artistas) funcionan sin sesión; si hay sesión se usa igualmente.
        val auth = store.state.value
        if (auth == null && !anonymous) throw YtAuthException("No has iniciado sesión")
        val sapisid = auth?.let {
            cookieValue(it.cookie, "SAPISID") ?: cookieValue(it.cookie, "__Secure-3PAPISID")
                ?: throw YtAuthException("La sesión no es válida")
        }
        val version = "1." + SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date()) + ".01.00"
        val locale = Locale.getDefault()

        val clientJson = JSONObject()
            .put("clientName", "WEB_REMIX").put("clientVersion", version)
            .put("hl", locale.language.ifEmpty { "es" }).put("gl", locale.country.ifEmpty { "US" })
        if (auth != null && auth.visitorData.isNotEmpty()) clientJson.put("visitorData", auth.visitorData)
        val body = JSONObject().put("context", JSONObject().put("client", clientJson))
        if (browseId != null) body.put("browseId", browseId)
        extra(body)

        val url = StringBuilder("$BASE/$endpoint?prettyPrint=false&alt=json&key=$KEY")
        if (token != null) url.append("&ctoken=").append(token).append("&continuation=").append(token)

        val builder = Request.Builder()
            .url(url.toString())
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Origin", ORIGIN)
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", version)
        if (auth != null && sapisid != null) {
            val ts = System.currentTimeMillis() / 1000
            builder
                .header("X-Goog-AuthUser", auth.authUser)
                .header("Cookie", auth.cookie)
                .header("Authorization", "SAPISIDHASH ${ts}_${sha1("$ts $sapisid $ORIGIN")}")
            if (auth.visitorData.isNotEmpty()) builder.header("X-Goog-Visitor-Id", auth.visitorData)
        }
        val request = builder.post(body.toString().toRequestBody("application/json".toMediaType())).build()

        client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) {
                if (auth != null) throw YtAuthException("La sesión de YouTube caducó. Vuelve a iniciar sesión.")
                throw IOException("YouTube respondió ${r.code}")
            }
            if (!r.isSuccessful) throw IOException("YouTube respondió ${r.code}")
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    /** Pide varias páginas siguiendo los tokens de continuación. */
    private suspend fun <T> pages(
        browseId: String,
        maxPages: Int,
        parse: (JSONObject) -> List<T>,
        anonymous: Boolean = false,
    ): List<T> {
        val out = ArrayList<T>()
        var json = post("browse", browseId, null, anonymous)
        var page = 1
        while (true) {
            out += parse(json)
            val token = continuationToken(json)
            if (token == null || page >= maxPages) break
            json = post("browse", null, token, anonymous)
            page++
        }
        return out
    }

    // --- Datos de la cuenta ---

    /** Nombre y correo de la cuenta; sirve también para comprobar que la sesión funciona. */
    suspend fun accountInfo(): Pair<String, String?> {
        val json = post("account/account_menu", null, null)
        val header = collect(json, "activeAccountHeaderRenderer").firstOrNull()
            ?: throw YtAuthException("No se pudo leer la cuenta")
        val name = runs(header.optJSONObject("accountName")).ifBlank { "YouTube Music" }
        val email = runs(header.optJSONObject("email")).ifBlank { null }
        return name to email
    }

    /** "Canciones que me gustan" (la playlist LM de YouTube Music). */
    suspend fun likedSongs(): List<Track> = pages("VLLM", 12, ::tracksOf).distinctBy { it.id }

    suspend fun history(): List<Track> = pages("FEmusic_history", 3, ::tracksOf).distinctBy { it.id }

    /** Canciones guardadas en la biblioteca. */
    suspend fun librarySongs(): List<Track> = pages("FEmusic_liked_videos", 12, ::tracksOf).distinctBy { it.id }

    suspend fun playlists(): List<YtPlaylist> = pages("FEmusic_liked_playlists", 4, ::playlistsOf).distinctBy { it.id }

    suspend fun playlistTracks(playlistId: String): List<Track> =
        pages(if (playlistId.startsWith("VL")) playlistId else "VL$playlistId", 12, ::tracksOf).distinctBy { it.id }

    // --- Escritura: lo que haces en Toastune se refleja en tu cuenta ---

    suspend fun setSongLiked(videoId: String, liked: Boolean) {
        post(if (liked) "like/like" else "like/removelike", null, null) {
            it.put("target", JSONObject().put("videoId", videoId))
        }
    }

    /** Guardar un álbum en la biblioteca de YouTube Music es "dar me gusta" a su lista. */
    suspend fun setPlaylistSaved(playlistId: String, saved: Boolean) {
        post(if (saved) "like/like" else "like/removelike", null, null) {
            it.put("target", JSONObject().put("playlistId", playlistId.removePrefix("VL")))
        }
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String) {
        post("browse/edit_playlist", null, null) {
            it.put("playlistId", playlistId.removePrefix("VL"))
            it.put(
                "actions",
                JSONArray().put(JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)),
            )
        }
    }

    /** Crea una playlist privada con las canciones dadas y devuelve su identificador. */
    suspend fun createPlaylist(title: String, videoIds: List<String>): String? {
        val json = post("playlist/create", null, null) {
            it.put("title", title).put("privacyStatus", "PRIVATE")
            it.put("videoIds", JSONArray(videoIds))
        }
        return json.optString("playlistId").ifEmpty { null }
    }

    // --- Artistas (páginas públicas: no hace falta sesión) ---

    suspend fun artistPage(artistId: String, artistName: String): ArtistPage {
        val json = post("browse", artistId, null, anonymous = true)
        var songs = tracksOf(json).distinctBy { it.id }
        // El estante de canciones solo trae unas pocas; su título enlaza a la lista completa.
        val allId = collect(json, "musicShelfRenderer").firstOrNull()
            ?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
        if (!allId.isNullOrEmpty()) {
            val full = runCatching { pages(allId, 3, ::tracksOf, anonymous = true) }.getOrDefault(emptyList())
            if (full.isNotEmpty()) songs = full.distinctBy { it.id }
        }
        val albums = collect(json, "musicTwoRowItemRenderer").mapNotNull { r ->
            val browse = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if (!browse.startsWith("MPRE")) return@mapNotNull null
            val playlistId = findString(r, "playlistId") ?: return@mapNotNull null
            val title = runs(r.optJSONObject("title")).ifBlank { return@mapNotNull null }
            Album(playlistId, title, artistName, thumbnail(r))
        }.distinctBy { it.id }
        return ArtistPage(songs.take(40), albums)
    }

    // --- Lectura del JSON ---

    private fun tracksOf(json: JSONObject): List<Track> =
        collect(json, "musicResponsiveListItemRenderer").mapNotNull { r ->
            val id = r.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
                .ifEmpty { findString(r, "videoId").orEmpty() }
            if (id.isEmpty()) return@mapNotNull null
            val flex = r.optJSONArray("flexColumns")
            fun column(i: Int): JSONObject? = flex?.optJSONObject(i)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
            val title = runs(column(0)).ifBlank { return@mapNotNull null }
            val artistRuns = column(1)?.optJSONArray("runs")
            val artists = ArrayList<String>()
            if (artistRuns != null) {
                for (i in 0 until artistRuns.length()) {
                    val run = artistRuns.getJSONObject(i)
                    val browse = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                    if (browse.startsWith("UC")) artists += run.optString("text")
                }
                if (artists.isEmpty()) artists += artistRuns.optJSONObject(0)?.optString("text").orEmpty()
            }
            val duration = r.optJSONArray("fixedColumns")?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
                ?.let { runs(it) }?.let(::parseDuration)
            Track(
                id = id,
                title = title,
                artist = artists.filter { it.isNotBlank() }.joinToString(", "),
                durationMs = duration,
                thumbnailUrl = thumbnail(r),
            )
        }

    private fun playlistsOf(json: JSONObject): List<YtPlaylist> =
        collect(json, "musicTwoRowItemRenderer").mapNotNull { r ->
            val id = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            // Solo playlists (VL…); se ignoran artistas, álbumes y el botón "Nueva playlist".
            if (!id.startsWith("VL")) return@mapNotNull null
            val title = runs(r.optJSONObject("title")).ifBlank { return@mapNotNull null }
            YtPlaylist(id, title, runs(r.optJSONObject("subtitle")), thumbnail(r))
        }

    private fun thumbnail(node: JSONObject): String? {
        val list = collect(node, "musicThumbnailRenderer").firstOrNull()
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: return null
        return list.optJSONObject(list.length() - 1)?.optString("url")?.ifBlank { null }
    }

    private fun continuationToken(json: JSONObject): String? {
        collect(json, "nextContinuationData").firstNotNullOfOrNull { it.optString("continuation").ifBlank { null } }
            ?.let { return it }
        return collect(json, "continuationItemRenderer").firstNotNullOfOrNull {
            it.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.ifBlank { null }
        }
    }

    private fun runs(text: JSONObject?): String {
        val a = text?.optJSONArray("runs") ?: return ""
        return (0 until a.length()).joinToString("") { a.getJSONObject(it).optString("text") }
    }

    /** Busca (sin entrar dentro de lo encontrado) todos los objetos que cuelgan de la clave [key]. */
    private fun collect(node: Any?, key: String, out: MutableList<JSONObject> = ArrayList()): List<JSONObject> {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val v = node.opt(k)
                if (k == key && v is JSONObject) out += v else collect(v, key, out)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
        return out
    }

    private fun findString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val v = node.opt(k)
                if (k == key && v is String && v.isNotEmpty()) return v
                findString(v, key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findString(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun parseDuration(text: String): Long? {
        val parts = text.trim().split(":").map { it.toLongOrNull() ?: return null }
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
    }

    private fun cookieValue(cookie: String, name: String): String? =
        cookie.split(";").map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val BASE = "$ORIGIN/youtubei/v1"
        const val KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}

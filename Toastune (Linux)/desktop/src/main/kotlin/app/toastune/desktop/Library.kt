package app.toastune.desktop

import app.toastune.core.Album
import app.toastune.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

data class LibraryData(
    val favorites: List<Track> = emptyList(),
    val playlists: Map<String, List<Track>> = emptyMap(),
    val albums: List<Album> = emptyList(),
    val downloads: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val searches: List<String> = emptyList(),
    val taste: List<String> = emptyList(),
    val tasteArtists: List<String> = emptyList(),
)

/** Favoritos y listas, guardados en ~/.local/share/toastune/library.json. */
class Library {
    private val file: Path = Path.of(
        System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"),
        "toastune", "library.json",
    )
    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data

    fun isFavorite(id: String) = _data.value.favorites.any { it.id == id }

    fun toggleFavorite(t: Track) = update {
        if (isFavorite(t.id)) it.copy(favorites = it.favorites.filterNot { f -> f.id == t.id })
        else it.copy(favorites = listOf(t) + it.favorites)
    }

    fun isAlbumSaved(id: String) = _data.value.albums.any { it.id == id }

    fun toggleAlbum(a: Album) = update {
        if (isAlbumSaved(a.id)) it.copy(albums = it.albums.filterNot { x -> x.id == a.id })
        else it.copy(albums = listOf(a) + it.albums)
    }

    fun addDownload(t: Track) = update {
        if (it.downloads.any { x -> x.id == t.id }) it else it.copy(downloads = listOf(t) + it.downloads)
    }

    fun removeDownload(id: String) = update { it.copy(downloads = it.downloads.filterNot { x -> x.id == id }) }

    fun addRecent(t: Track) = update {
        it.copy(recent = (listOf(t) + it.recent.filterNot { x -> x.id == t.id }).take(200))
    }

    fun importFavorites(tracks: List<Track>): Int {
        val before = _data.value.favorites.size
        update { it.copy(favorites = (it.favorites + tracks).distinctBy { t -> t.id }) }
        return _data.value.favorites.size - before
    }

    fun clearHistory() = update { it.copy(recent = emptyList(), searches = emptyList()) }
    fun addSearch(q: String) = update { it.copy(searches = (listOf(q) + it.searches.filterNot { x -> x.equals(q, true) }).take(10)) }
    fun removeSearch(q: String) = update { it.copy(searches = it.searches - q) }
    fun toggleTaste(g: String) = update { it.copy(taste = if (g in it.taste) it.taste - g else it.taste + g) }

    /** Misma copia de seguridad que la app de Android: se pueden pasar datos de un lado a otro. */
    fun exportTo(path: Path) {
        val d = _data.value
        val root = JSONObject().put("app", "toastune").put("version", 1)
            .put("favorites", JSONArray(d.favorites.map(::trackJson)))
            .put("history", JSONArray(d.recent.map(::trackJson)))
            .put("playlists", JSONArray(d.playlists.map { (n, l) -> JSONObject().put("name", n).put("tracks", JSONArray(l.map(::trackJson))) }))
            .put("albums", JSONArray(d.albums.map(::albumJson)))
            .put("searches", JSONArray(d.searches))
        Files.writeString(path, root.toString())
    }

    fun setTaste(genres: List<String>, artists: List<String>) = update { it.copy(taste = genres, tasteArtists = artists) }

    fun importFrom(path: Path): Boolean = try {
        val loaded = parse(Files.readString(path))
        val cur = _data.value
        update {
            LibraryData(
                favorites = (it.favorites + loaded.favorites).distinctBy { t -> t.id },
                playlists = loaded.playlists.mapValues { (k, v) -> ((it.playlists[k] ?: emptyList()) + v).distinctBy { t -> t.id } } + it.playlists.filterKeys { k -> k !in loaded.playlists },
                albums = (it.albums + loaded.albums).distinctBy { a -> a.id },
                downloads = it.downloads,
                recent = (it.recent + loaded.recent).distinctBy { t -> t.id }.take(200),
                searches = (it.searches + loaded.searches).distinct().take(10),
                taste = (it.taste + loaded.taste).distinct(),
                tasteArtists = (it.tasteArtists + loaded.tasteArtists).distinct(),
            )
        }
        cur != _data.value
    } catch (_: Exception) { false }

    fun createPlaylist(name: String) = update {
        if (name.isBlank() || name in it.playlists) it else it.copy(playlists = it.playlists + (name to emptyList()))
    }

    fun deletePlaylist(name: String) = update { it.copy(playlists = it.playlists - name) }

    fun addToPlaylist(name: String, t: Track) = update {
        val cur = it.playlists[name] ?: return@update it
        if (cur.any { x -> x.id == t.id }) it else it.copy(playlists = it.playlists + (name to cur + t))
    }

    fun removeFromPlaylist(name: String, id: String) = update {
        val cur = it.playlists[name] ?: return@update it
        it.copy(playlists = it.playlists + (name to cur.filterNot { x -> x.id == id }))
    }

    private fun update(f: (LibraryData) -> LibraryData) {
        _data.value = f(_data.value)
        save()
    }

    private fun trackJson(t: Track) = JSONObject()
        .put("id", t.id).put("title", t.title).put("artist", t.artist)
        .put("duration", t.durationMs ?: JSONObject.NULL).put("thumb", t.thumbnailUrl ?: JSONObject.NULL)

    private fun trackFrom(o: JSONObject) = Track(
        id = o.getString("id"), title = o.getString("title"), artist = o.optString("artist"),
        durationMs = when {
            o.has("duration") && !o.isNull("duration") -> o.getLong("duration")
            o.has("durationMs") && !o.isNull("durationMs") -> o.getLong("durationMs")
            else -> null
        },
        thumbnailUrl = if (o.isNull("thumb")) null else o.getString("thumb"),
    )

    private fun albumJson(a: Album) = JSONObject()
        .put("id", a.id).put("title", a.title).put("artist", a.artist).put("thumb", a.thumbnailUrl ?: JSONObject.NULL)

    private fun albumFrom(o: JSONObject) = Album(
        o.getString("id"), o.getString("title"), o.optString("artist"),
        if (o.isNull("thumb")) null else o.getString("thumb"),
    )

    private fun tracksOf(root: JSONObject, key: String): List<Track> {
        val a = root.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { trackFrom(a.getJSONObject(it)) }
    }

    private fun load(): LibraryData = try { parse(Files.readString(file)) } catch (_: Exception) { LibraryData() }

    private fun parse(text: String): LibraryData = try {
        val root = JSONObject(text)
        val favs = root.optJSONArray("favorites") ?: JSONArray()
        val plsArr = root.optJSONArray("playlists")
        val pls = root.optJSONObject("playlists") ?: JSONObject()
        val histArr = root.optJSONArray("history")
        LibraryData(
            favorites = (0 until favs.length()).map { trackFrom(favs.getJSONObject(it)) },
            albums = root.optJSONArray("albums")?.let { a -> (0 until a.length()).map { albumFrom(a.getJSONObject(it)) } } ?: emptyList(),
            downloads = tracksOf(root, "downloads"),
            recent = if (histArr != null) tracksOf(root, "history") else tracksOf(root, "recent"),
            searches = root.optJSONArray("searches")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            taste = root.optJSONArray("taste")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            tasteArtists = root.optJSONArray("tasteArtists")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            playlists = if (plsArr != null) {
                (0 until plsArr.length()).associate { i ->
                    val p = plsArr.getJSONObject(i)
                    val a = p.optJSONArray("tracks") ?: JSONArray()
                    p.getString("name") to (0 until a.length()).map { trackFrom(a.getJSONObject(it)) }
                }
            } else pls.keys().asSequence().associateWith { k ->
                val a = pls.getJSONArray(k)
                (0 until a.length()).map { trackFrom(a.getJSONObject(it)) }
            },
        )
    } catch (_: Exception) {
        LibraryData()
    }

    private fun save() {
        try {
            val d = _data.value
            val root = JSONObject()
                .put("favorites", JSONArray(d.favorites.map(::trackJson)))
                .put("albums", JSONArray(d.albums.map(::albumJson)))
                .put("downloads", JSONArray(d.downloads.map(::trackJson)))
                .put("recent", JSONArray(d.recent.map(::trackJson)))
                .put("searches", JSONArray(d.searches))
                .put("taste", JSONArray(d.taste))
                .put("tasteArtists", JSONArray(d.tasteArtists))
                .put("playlists", JSONObject().also { o -> d.playlists.forEach { (k, v) -> o.put(k, JSONArray(v.map(::trackJson))) } })
            Files.createDirectories(file.parent)
            Files.writeString(file, root.toString())
        } catch (_: Exception) {}
    }
}

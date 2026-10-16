package app.toastune.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.contentValuesOf
import app.toastune.core.Album
import app.toastune.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: Long, val name: String, val trackCount: Int)

/**
 * Biblioteca local: favoritos, historial, playlists, descargas y búsquedas recientes en SQLite.
 * Los flujos se vuelven a consultar cada vez que algo cambia.
 */
class LibraryRepository(context: Context) {

    private val helper = Db(context.applicationContext)
    private val changes = MutableStateFlow(0)

    val favorites: Flow<List<Track>> = changes.map {
        io {
            queryTracks(
                "SELECT t.id, t.title, t.artist, t.duration, t.thumb FROM favorites f " +
                    "JOIN tracks t ON t.id = f.track_id ORDER BY f.added_at DESC",
            )
        }
    }

    val favoriteIds: Flow<Set<String>> = favorites.map { list -> list.mapTo(HashSet()) { it.id } }

    val history: Flow<List<Track>> = changes.map {
        io {
            queryTracks(
                "SELECT t.id, t.title, t.artist, t.duration, t.thumb FROM history h " +
                    "JOIN tracks t ON t.id = h.track_id ORDER BY h.played_at DESC LIMIT 100",
            )
        }
    }

    val downloaded: Flow<List<Track>> = changes.map {
        io {
            queryTracks(
                "SELECT t.id, t.title, t.artist, t.duration, t.thumb FROM downloads d " +
                    "JOIN tracks t ON t.id = d.track_id ORDER BY d.downloaded_at DESC",
            )
        }
    }

    val downloadedIds: Flow<Set<String>> = downloaded.map { list -> list.mapTo(HashSet()) { it.id } }

    val playlists: Flow<List<Playlist>> = changes.map {
        io {
            helper.readableDatabase.rawQuery(
                "SELECT p.id, p.name, COUNT(pt.track_id) FROM playlists p " +
                    "LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id " +
                    "GROUP BY p.id ORDER BY p.id DESC",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(Playlist(c.getLong(0), c.getString(1), c.getInt(2)))
                }
            }
        }
    }

    val albums: Flow<List<Album>> = changes.map {
        io {
            helper.readableDatabase.rawQuery(
                "SELECT id, title, artist, thumb FROM albums ORDER BY added_at DESC", null,
            ).use { c ->
                buildList { while (c.moveToNext()) add(Album(c.getString(0), c.getString(1), c.getString(2), c.getString(3))) }
            }
        }
    }

    /** Canciones guardadas de un álbum: sirven para abrirlo sin conexión. */
    suspend fun albumTracks(albumId: String): List<Track> = io {
        runCatching { queryTracks(
            "SELECT t.id, t.title, t.artist, t.duration, t.thumb FROM album_tracks atr " +
                "JOIN tracks t ON t.id = atr.track_id WHERE atr.album_id = ? ORDER BY atr.position",
            arrayOf(albumId),
        ) }.getOrDefault(emptyList())
    }

    /** Guarda o quita el álbum de la biblioteca. Devuelve true si quedó guardado. */
    suspend fun toggleAlbum(album: Album, tracks: List<Track>): Boolean {
        val saved = io {
            val db = helper.writableDatabase
            var nowSaved = false
            db.transaction {
                val exists = db.rawQuery("SELECT 1 FROM albums WHERE id = ?", arrayOf(album.id)).use { it.moveToFirst() }
                if (exists) {
                    db.delete("albums", "id = ?", arrayOf(album.id))
                    db.delete("album_tracks", "album_id = ?", arrayOf(album.id))
                } else {
                    saveAlbum(db, album, tracks, System.currentTimeMillis())
                    nowSaved = true
                }
            }
            nowSaved
        }
        notifyChanged()
        return saved
    }

    val recentSearches: Flow<List<String>> = changes.map {
        io {
            helper.readableDatabase.rawQuery(
                "SELECT term FROM searches ORDER BY searched_at DESC LIMIT 12",
                null,
            ).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        }
    }

    fun playlistTracks(playlistId: Long): Flow<List<Track>> = changes.map {
        io {
            queryTracks(
                "SELECT t.id, t.title, t.artist, t.duration, t.thumb FROM playlist_tracks pt " +
                    "JOIN tracks t ON t.id = pt.track_id WHERE pt.playlist_id = ? ORDER BY pt.position",
                arrayOf(playlistId.toString()),
            )
        }
    }

    suspend fun toggleFavorite(track: Track) {
        io {
            val db = helper.writableDatabase
            db.transaction {
                val exists = db.rawQuery("SELECT 1 FROM favorites WHERE track_id = ?", arrayOf(track.id))
                    .use { it.moveToFirst() }
                if (exists) {
                    db.delete("favorites", "track_id = ?", arrayOf(track.id))
                } else {
                    upsertTrack(db, track)
                    db.insertWithOnConflict(
                        "favorites", null,
                        contentValuesOf("track_id" to track.id, "added_at" to System.currentTimeMillis()),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
        }
        notifyChanged()
    }

    suspend fun recordPlay(track: Track) {
        io {
            val db = helper.writableDatabase
            db.transaction {
                upsertTrack(db, track)
                db.insertWithOnConflict(
                    "history", null,
                    contentValuesOf("track_id" to track.id, "played_at" to System.currentTimeMillis()),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        notifyChanged()
    }

    suspend fun clearHistory() {
        io { helper.writableDatabase.delete("history", null, null) }
        notifyChanged()
    }

    /** Guarda los datos de una canción (título, portada...) sin tocar favoritos ni historial. */
    suspend fun rememberTrack(track: Track) {
        io { upsertTrack(helper.writableDatabase, track) }
    }

    suspend fun markDownloaded(trackId: String) {
        io {
            helper.writableDatabase.insertWithOnConflict(
                "downloads", null,
                contentValuesOf("track_id" to trackId, "downloaded_at" to System.currentTimeMillis()),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        notifyChanged()
    }

    suspend fun removeDownloaded(trackId: String) {
        io { helper.writableDatabase.delete("downloads", "track_id = ?", arrayOf(trackId)) }
        notifyChanged()
    }

    suspend fun clearDownloaded() {
        io { helper.writableDatabase.delete("downloads", null, null) }
        notifyChanged()
    }

    suspend fun recordSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        io {
            helper.writableDatabase.insertWithOnConflict(
                "searches", null,
                contentValuesOf("term" to q, "searched_at" to System.currentTimeMillis()),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        notifyChanged()
    }

    suspend fun deleteSearch(query: String) {
        io { helper.writableDatabase.delete("searches", "term = ?", arrayOf(query)) }
        notifyChanged()
    }

    suspend fun clearSearches() {
        io { helper.writableDatabase.delete("searches", null, null) }
        notifyChanged()
    }

    suspend fun createPlaylist(name: String): Long {
        val id = io {
            helper.writableDatabase.insert("playlists", null, contentValuesOf("name" to name.trim()))
        }
        notifyChanged()
        return id
    }

    suspend fun deletePlaylist(playlistId: Long) {
        io {
            val db = helper.writableDatabase
            db.transaction {
                db.delete("playlist_tracks", "playlist_id = ?", arrayOf(playlistId.toString()))
                db.delete("playlists", "id = ?", arrayOf(playlistId.toString()))
            }
        }
        notifyChanged()
    }

    suspend fun addToPlaylist(playlistId: Long, track: Track) {
        io {
            val db = helper.writableDatabase
            db.transaction {
                upsertTrack(db, track)
                db.execSQL(
                    "INSERT OR IGNORE INTO playlist_tracks(playlist_id, track_id, position) " +
                        "VALUES (?, ?, (SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id = ?))",
                    arrayOf<Any?>(playlistId, track.id, playlistId),
                )
            }
        }
        notifyChanged()
    }

    suspend fun removeFromPlaylist(playlistId: Long, trackId: String) {
        io {
            helper.writableDatabase.delete(
                "playlist_tracks", "playlist_id = ? AND track_id = ?",
                arrayOf(playlistId.toString(), trackId),
            )
        }
        notifyChanged()
    }

    /** Añade muchas canciones a favoritos a la vez, conservando su orden. Devuelve cuántas eran nuevas. */
    suspend fun addFavorites(tracks: List<Track>): Int {
        val added = io {
            val db = helper.writableDatabase
            val now = System.currentTimeMillis()
            var n = 0
            db.transaction {
                tracks.forEachIndexed { i, t ->
                    upsertTrack(db, t)
                    val row = db.insertWithOnConflict(
                        "favorites", null,
                        contentValuesOf("track_id" to t.id, "added_at" to now - i),
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                    if (row != -1L) n++
                }
            }
            n
        }
        notifyChanged()
        return added
    }

    /** Crea una playlist local con todas esas canciones. */
    suspend fun importPlaylist(name: String, tracks: List<Track>) {
        io {
            val db = helper.writableDatabase
            db.transaction {
                val id = db.insert("playlists", null, contentValuesOf("name" to name.trim()))
                tracks.forEachIndexed { i, t ->
                    upsertTrack(db, t)
                    db.execSQL(
                        "INSERT OR IGNORE INTO playlist_tracks(playlist_id, track_id, position) VALUES (?, ?, ?)",
                        arrayOf<Any?>(id, t.id, i),
                    )
                }
            }
        }
        notifyChanged()
    }

    // --- Copia de seguridad ---

    private fun trackJson(t: Track) = JSONObject()
        .put("id", t.id).put("title", t.title).put("artist", t.artist)
        .put("duration", t.durationMs ?: JSONObject.NULL).put("thumb", t.thumbnailUrl ?: JSONObject.NULL)

    private fun trackFrom(o: JSONObject) = Track(
        id = o.getString("id"),
        title = o.optString("title"),
        artist = o.optString("artist"),
        durationMs = if (o.isNull("duration")) null else o.getLong("duration"),
        thumbnailUrl = if (o.isNull("thumb")) null else o.getString("thumb"),
    )

    /** Todo lo personal (favoritos, historial, playlists, búsquedas) en un JSON que se puede guardar donde quieras. */
    suspend fun exportJson(): String = io {
        val sel = "SELECT t.id, t.title, t.artist, t.duration, t.thumb FROM "
        fun arr(list: List<Track>) = JSONArray().also { a -> list.forEach { a.put(trackJson(it)) } }
        val playlists = JSONArray()
        helper.readableDatabase.rawQuery("SELECT id, name FROM playlists ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val tracks = queryTracks(
                    sel + "playlist_tracks pt JOIN tracks t ON t.id = pt.track_id WHERE pt.playlist_id = ? ORDER BY pt.position",
                    arrayOf(c.getLong(0).toString()),
                )
                playlists.put(JSONObject().put("name", c.getString(1)).put("tracks", arr(tracks)))
            }
        }
        val searches = JSONArray()
        helper.readableDatabase.rawQuery("SELECT term FROM searches ORDER BY searched_at DESC", null).use { c ->
            while (c.moveToNext()) searches.put(c.getString(0))
        }
        val albumsJson = JSONArray()
        helper.readableDatabase.rawQuery("SELECT id, title, artist, thumb FROM albums ORDER BY added_at DESC", null).use { c ->
            while (c.moveToNext()) {
                val tracks = queryTracks(
                    sel + "album_tracks atr JOIN tracks t ON t.id = atr.track_id WHERE atr.album_id = ? ORDER BY atr.position",
                    arrayOf(c.getString(0)),
                )
                albumsJson.put(
                    JSONObject().put("id", c.getString(0)).put("title", c.getString(1)).put("artist", c.getString(2))
                        .put("thumb", c.getString(3)).put("tracks", arr(tracks)),
                )
            }
        }
        JSONObject()
            .put("app", "toastune").put("version", 1)
            .put("albums", albumsJson)
            .put("favorites", arr(queryTracks(sel + "favorites f JOIN tracks t ON t.id = f.track_id ORDER BY f.added_at DESC")))
            .put("history", arr(queryTracks(sel + "history h JOIN tracks t ON t.id = h.track_id ORDER BY h.played_at DESC")))
            .put("playlists", playlists)
            .put("searches", searches)
            .toString()
    }

    /** Une la copia con lo que ya hay (no borra nada). Devuelve cuántas canciones se leyeron. */
    suspend fun importJson(json: String): Int {
        val n = io {
            val root = JSONObject(json)
            require(root.optString("app") == "toastune") { "Archivo no válido" }
            val db = helper.writableDatabase
            var count = 0
            val now = System.currentTimeMillis()
            db.transaction {
                val fav = root.optJSONArray("favorites") ?: JSONArray()
                for (i in 0 until fav.length()) {
                    val t = trackFrom(fav.getJSONObject(i)); count++
                    upsertTrack(db, t)
                    db.insertWithOnConflict("favorites", null, contentValuesOf("track_id" to t.id, "added_at" to now - i), SQLiteDatabase.CONFLICT_REPLACE)
                }
                val his = root.optJSONArray("history") ?: JSONArray()
                for (i in 0 until his.length()) {
                    val t = trackFrom(his.getJSONObject(i)); count++
                    upsertTrack(db, t)
                    db.insertWithOnConflict("history", null, contentValuesOf("track_id" to t.id, "played_at" to now - 1000L * (i + 1)), SQLiteDatabase.CONFLICT_IGNORE)
                }
                val pls = root.optJSONArray("playlists") ?: JSONArray()
                for (i in 0 until pls.length()) {
                    val p = pls.getJSONObject(i)
                    val name = p.getString("name")
                    val existing = db.rawQuery("SELECT id FROM playlists WHERE name = ?", arrayOf(name)).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                    val pid = existing ?: db.insert("playlists", null, contentValuesOf("name" to name))
                    val tracks = p.optJSONArray("tracks") ?: JSONArray()
                    for (j in 0 until tracks.length()) {
                        val t = trackFrom(tracks.getJSONObject(j)); count++
                        upsertTrack(db, t)
                        db.execSQL(
                            "INSERT OR IGNORE INTO playlist_tracks(playlist_id, track_id, position) " +
                                "VALUES (?, ?, (SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id = ?))",
                            arrayOf<Any?>(pid, t.id, pid),
                        )
                    }
                }
                val als = root.optJSONArray("albums") ?: JSONArray()
                for (i in 0 until als.length()) {
                    val a = als.getJSONObject(i)
                    val tr = a.optJSONArray("tracks") ?: JSONArray()
                    val tracks = List(tr.length()) { trackFrom(tr.getJSONObject(it)) }
                    count += tracks.size
                    saveAlbum(
                        db,
                        Album(a.getString("id"), a.getString("title"), a.optString("artist"), a.optString("thumb").ifEmpty { null }),
                        tracks, now - i,
                    )
                }
                val ss = root.optJSONArray("searches") ?: JSONArray()
                for (i in 0 until ss.length()) {
                    db.insertWithOnConflict("searches", null, contentValuesOf("term" to ss.getString(i), "searched_at" to now - i), SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            count
        }
        notifyChanged()
        return n
    }

    // --- internos ---

    private fun saveAlbum(db: SQLiteDatabase, album: Album, tracks: List<Track>, addedAt: Long) {
        db.insertWithOnConflict(
            "albums", null,
            contentValuesOf("id" to album.id, "title" to album.title, "artist" to album.artist, "thumb" to album.thumbnailUrl, "added_at" to addedAt),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        db.delete("album_tracks", "album_id = ?", arrayOf(album.id))
        tracks.forEachIndexed { i, t ->
            upsertTrack(db, t)
            db.insertWithOnConflict(
                "album_tracks", null,
                contentValuesOf("album_id" to album.id, "track_id" to t.id, "position" to i),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    private fun notifyChanged() {
        changes.value += 1
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun upsertTrack(db: SQLiteDatabase, t: Track) {
        db.insertWithOnConflict(
            "tracks", null,
            contentValuesOf(
                "id" to t.id,
                "title" to t.title,
                "artist" to t.artist,
                "duration" to t.durationMs,
                "thumb" to t.thumbnailUrl,
            ),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun queryTracks(sql: String, args: Array<String>? = null): List<Track> =
        helper.readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Track(
                            id = c.getString(0),
                            title = c.getString(1),
                            artist = c.getString(2),
                            durationMs = if (c.isNull(3)) null else c.getLong(3),
                            thumbnailUrl = c.getString(4),
                        ),
                    )
                }
            }
        }

    private inline fun SQLiteDatabase.transaction(body: () -> Unit) {
        beginTransaction()
        try {
            body()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private class Db(context: Context) : SQLiteOpenHelper(context, "toastune.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, duration INTEGER, thumb TEXT)")
            db.execSQL("CREATE TABLE favorites(track_id TEXT PRIMARY KEY, added_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE history(track_id TEXT PRIMARY KEY, played_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE playlist_tracks(playlist_id INTEGER NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id, track_id))")
            createV2Tables(db)
            createV3Tables(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createV2Tables(db)
            if (oldVersion < 3) createV3Tables(db)
        }

        private fun createV3Tables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS albums(id TEXT PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, thumb TEXT, added_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS album_tracks(album_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(album_id, track_id))")
        }

        private fun createV2Tables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS downloads(track_id TEXT PRIMARY KEY, downloaded_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS searches(term TEXT PRIMARY KEY, searched_at INTEGER NOT NULL)")
        }
    }
}

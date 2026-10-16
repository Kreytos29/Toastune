package app.toastune

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Debug
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.toastune.core.Album
import app.toastune.core.Artist
import app.toastune.core.ArtistPage
import app.toastune.core.Lyrics
import app.toastune.core.SearchFilter
import app.toastune.core.Track
import app.toastune.data.Diagnostics
import app.toastune.data.Settings
import app.toastune.data.Playlist
import app.toastune.data.Taste
import app.toastune.data.YtAuth
import app.toastune.data.YtAuthException
import app.toastune.data.YtPlaylist
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

data class TasteShelf(val title: String, val tracks: List<Track>)

sealed interface SearchUi {
    data object Idle : SearchUi
    data object Loading : SearchUi
    data class Error(val message: String) : SearchUi
    data class Albums(val albums: List<Album>) : SearchUi
    data class Artists(val artists: List<Artist>) : SearchUi
    data class Results(val tracks: List<Track>, val hasMore: Boolean, val loadingMore: Boolean = false) : SearchUi
}

sealed interface LyricsUi {
    data object Idle : LyricsUi
    data object Loading : LyricsUi
    data object None : LyricsUi
    data class Found(val lyrics: Lyrics) : LyricsUi
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ToastuneApp
    private val provider = app.provider
    private val library = app.library

    val player = app.player
    val settings: StateFlow<Settings> = app.settings.state
    val online: StateFlow<Boolean> = app.network.online
    val downloadStates = app.downloads.states

    private fun <T> Flow<T>.eager(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // --- Biblioteca ---
    val favorites = library.favorites.eager(emptyList())
    val favoriteIds = favorites.map { l -> l.mapTo(HashSet()) { it.id } as Set<String> }.eager(emptySet())
    val history = library.history.eager(emptyList())
    val downloaded = library.downloaded.eager(emptyList())
    val downloadedIds = downloaded.map { l -> l.mapTo(HashSet()) { it.id } as Set<String> }.eager(emptySet())
    val playlists = library.playlists.eager(emptyList<Playlist>())
    val recentSearches = library.recentSearches.eager(emptyList())
    val savedAlbums = library.albums.eager(emptyList<Album>())
    val savedAlbumIds = savedAlbums.map { l -> l.mapTo(HashSet()) { it.id } as Set<String> }.eager(emptySet())

    /** Canciones de un álbum: de la biblioteca si está guardado (sirve sin conexión), si no de YouTube. */
    suspend fun albumTracks(album: Album): List<Track> {
        val local = library.albumTracks(album.id)
        if (local.isNotEmpty() && !online.value) return local
        val remote = runCatching { withContext(Dispatchers.IO) { provider.albumTracks(album.id) } }.getOrDefault(emptyList())
        // Las canciones de un álbum traen miniaturas de video; se usa la portada del álbum.
        val tracks = remote.map { if (album.thumbnailUrl != null) it.copy(thumbnailUrl = album.thumbnailUrl) else it }
        return tracks.ifEmpty { local }
    }

    fun toggleAlbum(album: Album, tracks: List<Track>) = viewModelScope.launch {
        val saved = library.toggleAlbum(album, tracks)
        ytSync { setPlaylistSaved(album.id, saved) }
        Toast.makeText(app, if (saved) "Álbum guardado" else "Álbum quitado de tu biblioteca", Toast.LENGTH_SHORT).show()
    }

    fun downloadAlbum(album: Album, tracks: List<Track>) {
        app.downloads.downloadAll(tracks)
        Toast.makeText(app, "Descargando ${tracks.size} canciones de ${album.title}", Toast.LENGTH_SHORT).show()
    }

    fun removeDownloads(tracks: List<Track>) = tracks.forEach { app.downloads.remove(it.id) }

    fun playlistTracks(id: Long): Flow<List<Track>> = library.playlistTracks(id)

    /** "Para ti": canciones parecidas a la última escuchada al abrir la app. Fija mientras se navega. */
    val forYou: StateFlow<List<Track>> = online.flatMapLatest { on ->
        if (!on) {
            flowOf(emptyList())
        } else {
            flow {
                val seed = history.first { it.isNotEmpty() }.first()
                emit(
                    runCatching { provider.related(seed.id) }.getOrDefault(emptyList())
                        .filter { it.id != seed.id }
                        .distinctBy { it.id },
                )
            }
        }
    }.eager(emptyList())

    /** Estantes según lo que el usuario dijo que le gusta; se van llenando uno por uno. */
    val tasteShelves: StateFlow<List<TasteShelf>> = combine(
        settings.map { it.genres to it.artists }.distinctUntilChanged(),
        online,
    ) { taste, on -> taste to on }
        .flatMapLatest { (taste, on) ->
            val (genres, artists) = taste
            if (!on || (genres.isEmpty() && artists.isEmpty())) {
                flowOf(emptyList())
            } else {
                flow {
                    val jobs = ArrayList<Pair<String, String>>() // título, búsqueda
                    val g = genres.mapNotNull(Taste::genre).take(4).map { "${it.label} para ti" to it.query }
                    val a = artists.take(4).map { "Más de $it" to it }
                    for (i in 0 until maxOf(g.size, a.size)) {
                        g.getOrNull(i)?.let { jobs += it }
                        a.getOrNull(i)?.let { jobs += it }
                    }
                    val out = ArrayList<TasteShelf>()
                    for ((title, query) in jobs) {
                        val tracks = runCatching { provider.search(query).tracks }.getOrDefault(emptyList())
                            .distinctBy { it.id }.take(15)
                        if (tracks.isNotEmpty()) {
                            out += TasteShelf(title, tracks)
                            emit(out.toList())
                        }
                    }
                }
            }
        }.eager(emptyList())

    fun finishOnboarding(genres: List<String>, artists: List<String>) {
        app.settings.update { it.copy(onboarded = true, genres = genres, artists = artists) }
    }

    // --- Búsqueda ---
    val query = MutableStateFlow("")
    private val _filter = MutableStateFlow(SearchFilter.SONGS)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()
    private val _search = MutableStateFlow<SearchUi>(SearchUi.Idle)
    val search: StateFlow<SearchUi> = _search.asStateFlow()
    private var continuation: Any? = null
    private var searchJob: Job? = null
    private var moreJob: Job? = null

    val suggestions: StateFlow<List<String>> = query
        .debounce(250)
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.trim().length < 2 || !online.value) emptyList()
            else runCatching { provider.suggestions(q.trim()) }.getOrDefault(emptyList())
        }
        .eager(emptyList())

    fun onQueryChange(q: String) {
        query.value = q
        if (q.isBlank()) {
            searchJob?.cancel()
            moreJob?.cancel()
            _search.value = SearchUi.Idle
        }
    }

    fun submitSearch(q: String = query.value) {
        val term = q.trim()
        if (term.isEmpty()) return
        query.value = term
        searchJob?.cancel()
        moreJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!online.value) {
                _search.value = SearchUi.Error("Sin conexión")
                return@launch
            }
            _search.value = SearchUi.Loading
            library.recordSearch(term)
            _search.value = try {
                if (_filter.value == SearchFilter.ALBUMS) {
                    continuation = null
                    SearchUi.Albums(withContext(Dispatchers.IO) { provider.searchAlbums(term) })
                } else if (_filter.value == SearchFilter.ARTISTS) {
                    continuation = null
                    SearchUi.Artists(withContext(Dispatchers.IO) { provider.searchArtists(term) })
                } else {
                    val page = withContext(Dispatchers.IO) { provider.search(term, _filter.value) }
                    continuation = page.continuation
                    SearchUi.Results(page.tracks.distinctBy { it.id }, page.hasMore)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUi.Error(e.message ?: "No se pudo buscar")
            }
        }
    }

    fun setFilter(f: SearchFilter) {
        if (_filter.value == f) return
        _filter.value = f
        if (_search.value !is SearchUi.Idle) submitSearch()
    }

    fun loadMore() {
        val cur = _search.value as? SearchUi.Results ?: return
        val cont = continuation ?: return
        if (cur.loadingMore || !cur.hasMore) return
        _search.value = cur.copy(loadingMore = true)
        moreJob = viewModelScope.launch {
            _search.value = try {
                val page = withContext(Dispatchers.IO) { provider.loadMore(cont) }
                continuation = page.continuation
                val seen = cur.tracks.mapTo(HashSet()) { it.id }
                SearchUi.Results(cur.tracks + page.tracks.filter { it.id !in seen }, page.hasMore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cur.copy(hasMore = false, loadingMore = false)
            }
        }
    }

    fun deleteSearch(term: String) = viewModelScope.launch { library.deleteSearch(term) }
    fun clearSearches() = viewModelScope.launch { library.clearSearches() }

    // --- Letras ---
    private val _lyrics = MutableStateFlow<LyricsUi>(LyricsUi.Idle)
    val lyrics: StateFlow<LyricsUi> = _lyrics.asStateFlow()
    private var lyricsFor: String? = null
    private var lyricsJob: Job? = null

    fun loadLyrics() {
        val t = player.state.value.track ?: return
        if (lyricsFor == t.id) return
        lyricsFor = t.id
        lyricsJob?.cancel()
        _lyrics.value = LyricsUi.Loading
        lyricsJob = viewModelScope.launch {
            val found = runCatching { app.lyrics.lyrics(t.title, t.artist, t.durationMs) }.getOrNull()
            _lyrics.value = if (found == null || found.lines.isEmpty()) LyricsUi.None else LyricsUi.Found(found)
        }
    }

    // --- Acciones ---
    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)

    fun toggleFavorite(t: Track) = viewModelScope.launch {
        val wasFavorite = t.id in favoriteIds.value
        library.toggleFavorite(t)
        ytSync { setSongLiked(t.id, !wasFavorite) }
    }

    /** Refleja un cambio en la cuenta de YouTube Music, si hay sesión. Si falla no pasa nada: queda en local. */
    private fun ytSync(block: suspend app.toastune.data.YtMusicApi.() -> Unit) {
        if (!app.ytApi.loggedIn || !online.value) return
        viewModelScope.launch {
            runCatching { app.ytApi.block() }
            ytCache.clear()
            ytPlaylistsCache = null
        }
    }

    suspend fun artistPage(artist: Artist): ArtistPage? =
        runCatching { app.ytApi.artistPage(artist.id, artist.name) }.getOrNull()

    fun addToYtPlaylist(p: YtPlaylist, track: Track) = viewModelScope.launch {
        val ok = runCatching { app.ytApi.addToPlaylist(p.id, track.id) }.isSuccess
        if (ok) { ytCache.remove("pl:${p.id}"); ytCache.remove("pl:${p.id.removePrefix("VL")}") }
        Toast.makeText(app, if (ok) "Añadida a ${p.title}" else "No se pudo añadir a ${p.title}", Toast.LENGTH_SHORT).show()
    }

    fun createYtPlaylist(title: String, track: Track) = viewModelScope.launch {
        val id = runCatching { app.ytApi.createPlaylist(title.trim(), listOf(track.id)) }.getOrNull()
        ytPlaylistsCache = null
        Toast.makeText(app, if (id != null) "Playlist «$title» creada en YouTube Music" else "No se pudo crear la playlist", Toast.LENGTH_SHORT).show()
    }
    fun download(t: Track) = app.downloads.download(t)
    fun downloadAll(list: List<Track>) = app.downloads.downloadAll(list)
    fun removeDownload(id: String) = app.downloads.remove(id)
    fun removeAllDownloads() = app.downloads.removeAll()

    fun createPlaylist(name: String, then: Track? = null) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = library.createPlaylist(name)
        if (then != null) library.addToPlaylist(id, then)
    }

    fun deletePlaylist(id: Long) = viewModelScope.launch { library.deletePlaylist(id) }
    fun addToPlaylist(id: Long, t: Track) = viewModelScope.launch { library.addToPlaylist(id, t) }
    fun removeFromPlaylist(id: Long, trackId: String) = viewModelScope.launch { library.removeFromPlaylist(id, trackId) }
    fun clearHistory() = viewModelScope.launch { library.clearHistory() }

    fun updateSettings(transform: (Settings) -> Settings) = app.settings.update(transform)

    // --- Copia de seguridad ---
    private fun toast(msg: String) = Toast.makeText(app, msg, Toast.LENGTH_LONG).show()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = library.exportJson()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }.isSuccess
            toast(if (ok) "Copia de seguridad guardada" else "No se pudo guardar la copia")
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                library.importJson(text)
            }
            toast(
                result.fold(
                    { "Copia restaurada ($it canciones)" },
                    { "Ese archivo no es una copia de Toastune" },
                ),
            )
        }
    }

    // --- Cuenta de YouTube Music ---
    val ytAccount: StateFlow<YtAuth?> = app.ytStore.state
    private val ytCache = HashMap<String, List<Track>>()
    private var ytPlaylistsCache: List<YtPlaylist>? = null

    fun onYtLogin(cookie: String, visitorData: String, authUser: String) {
        viewModelScope.launch {
            val base = YtAuth(cookie, visitorData, authUser)
            app.ytStore.save(base)
            try {
                val (name, email) = app.ytApi.accountInfo()
                app.ytStore.save(base.copy(name = name, email = email))
                ytRefresh()
                toast("Sesión iniciada: $name")
            } catch (e: Exception) {
                app.ytStore.clear()
                toast("No se pudo iniciar sesión: ${e.message}")
            }
        }
    }

    fun ytLogout() {
        app.ytStore.clear()
        ytRefresh()
    }

    fun ytRefresh() {
        ytCache.clear()
        ytPlaylistsCache = null
    }

    /** Si YouTube rechaza la sesión se cierra para que la interfaz pida iniciar sesión otra vez. */
    private suspend fun <T> ytGuard(block: suspend () -> T): T = try {
        block()
    } catch (e: YtAuthException) {
        app.ytStore.clear()
        ytRefresh()
        throw e
    }

    /** Claves: "liked", "history", "library" o "pl:<id>". */
    suspend fun ytTracks(key: String): List<Track> {
        ytCache[key]?.let { return it }
        val result = ytGuard {
            when {
                key == "liked" -> app.ytApi.likedSongs()
                key == "history" -> app.ytApi.history()
                key == "library" -> app.ytApi.librarySongs()
                key.startsWith("pl:") -> app.ytApi.playlistTracks(key.removePrefix("pl:"))
                else -> emptyList()
            }
        }
        ytCache[key] = result
        return result
    }

    suspend fun ytPlaylists(): List<YtPlaylist> =
        ytPlaylistsCache ?: ytGuard { app.ytApi.playlists() }.also { ytPlaylistsCache = it }

    fun importYtLikes(tracks: List<Track>) {
        viewModelScope.launch {
            val n = library.addFavorites(tracks)
            toast("$n canciones nuevas en tus favoritos")
        }
    }

    fun importYtPlaylist(name: String, tracks: List<Track>) {
        viewModelScope.launch {
            library.importPlaylist(name, tracks)
            toast("Playlist \"$name\" guardada en Toastune")
        }
    }

    // --- Cuenta de Google (Drive) ---
    private var syncing = false

    fun connectDrive(launchConsent: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            try {
                val r = app.drive.authorize()
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) launchConsent(pending) else finishConnect(r)
            } catch (e: Exception) {
                toast("No se pudo conectar con Google: ${e.message}")
            }
        }
    }

    fun onDriveConsent(data: Intent?) {
        viewModelScope.launch {
            try {
                finishConnect(app.drive.resultFromIntent(data))
            } catch (e: Exception) {
                toast("No se pudo conectar con Google: ${e.message}")
            }
        }
    }

    private suspend fun finishConnect(r: AuthorizationResult) {
        if (r.accessToken == null) {
            toast("Google no entregó el permiso")
            return
        }
        app.settings.update { it.copy(driveSync = true) }
        trySync(manual = true)
    }

    fun syncNow() {
        viewModelScope.launch { trySync(manual = true) }
    }

    /** Se llama al abrir y al cerrar la app; no hace nada si no hay cuenta, no hay red o se sincronizó hace poco. */
    fun syncSilently() {
        val s = settings.value
        if (!s.driveSync || !online.value) return
        if (System.currentTimeMillis() - s.lastSyncMs < 120_000L) return
        viewModelScope.launch { trySync(manual = false) }
    }

    fun disconnectDrive() = app.settings.update { it.copy(driveSync = false) }

    private suspend fun trySync(manual: Boolean) {
        if (syncing) return
        syncing = true
        try {
            val r = app.drive.authorize()
            val token = r.accessToken
            if (r.hasResolution() || token == null) {
                if (manual) toast("Vuelve a conectar tu cuenta de Google")
                return
            }
            app.drive.sync(token, library)
            app.settings.update { it.copy(lastSyncMs = System.currentTimeMillis()) }
            if (manual) toast("Sincronizado con Google Drive")
        } catch (e: Exception) {
            if (manual) toast("No se pudo sincronizar: ${e.message}")
        } finally {
            syncing = false
        }
    }

    // --- Almacenamiento y memoria ---
    fun exportDiagnostics(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val text = Diagnostics.report(app)
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            Toast.makeText(app, if (ok) "Informe guardado" else "No se pudo guardar el informe", Toast.LENGTH_SHORT).show()
        }
    }

    fun cacheBytes(): Long = app.media.caches.streamCache.cacheSpace
    fun downloadsBytes(): Long = app.downloads.usedBytes()

    fun clearStreamCache() = viewModelScope.launch(Dispatchers.IO) {
        val cache = app.media.caches.streamCache
        cache.keys.toList().forEach { runCatching { cache.removeResource(it) } }
    }

    /** Memoria real que usa la app (PSS), en MB. */
    fun memoryMb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / 1024
    }
}

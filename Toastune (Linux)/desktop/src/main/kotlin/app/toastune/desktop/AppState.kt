package app.toastune.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.toastune.core.Album
import app.toastune.core.Artist
import app.toastune.core.AudioQuality
import app.toastune.core.SearchFilter
import app.toastune.core.SearchPage
import app.toastune.core.Track
import app.toastune.youtube.YouTubeMusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Albums : Screen
    data object Downloads : Screen
    data object NowPlaying : Screen
    data object Settings : Screen
    data class ArtistView(val name: String) : Screen
    data object History : Screen
    data object Favorites : Screen
    data object YouTube : Screen
    data class YtList(val title: String) : Screen
    data object Playlists : Screen
    data object Queue : Screen
    data class Playlist(val name: String) : Screen
    data class AlbumView(val album: Album) : Screen
}

val GENRES: List<String> = Taste.genres.map { it.label }

class AppState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val provider = YouTubeMusicProvider()
    val mpv = MpvPlayer()
    val http = OkHttpClient()
    val ytStore = YtAccountStore()
    val yt = YtMusicApi(http, ytStore)
    val library = Library()
    val settings = AppSettings()
    val downloads = Downloads(scope, provider, library) { if (settings.data.value.downloadHigh) AudioQuality.HIGH else AudioQuality.LOW }
    val lyrics = LrclibLyrics()
    /** Ajuste de sincronía de la letra por canción (ms), solo durante esta sesión. */
    val lyricOffsets = HashMap<String, Long>()
    val player = PlayerController(
        scope, provider, mpv, downloads::localPath, library::addRecent,
        quality = { if (settings.data.value.highQuality) AudioQuality.HIGH else AudioQuality.LOW },
    )

    var startError by mutableStateOf<String?>(null)

    // Navegación
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()
    fun open(s: Screen) { typing = false; if (screen != s) stack.add(s) }
    fun goTo(s: Screen) { typing = false; stack.clear(); stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    /** 0 = letra, 1 = cola */
    var npTab by mutableStateOf(0)
    fun openNowPlaying(tab: Int? = null) {
        if (tab != null) npTab = tab
        if (screen == Screen.NowPlaying) { if (tab == null) back() } else open(Screen.NowPlaying)
    }

    // Búsqueda
    var query by mutableStateOf("")
    var filter by mutableStateOf(SearchFilter.SONGS)
    var searching by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)
    var tracks by mutableStateOf<List<Track>>(emptyList())
    var albums by mutableStateOf<List<Album>>(emptyList())
    var artists by mutableStateOf<List<Artist>>(emptyList())
    private var continuation: SearchPage? = null
    val canLoadMore: Boolean get() = continuation?.hasMore == true

    // Álbum abierto
    var albumTracks by mutableStateOf<List<Track>>(emptyList())
    var albumLoading by mutableStateOf(false)

    fun start() {
        startError = mpv.start()
        val s = settings.data.value
        mpv.setVolume(s.volume)
        if (s.eqOn) mpv.setEqualizer(s.eqLevels)
        player.autoplay = s.autoplay
        mpv.warmSecond()
        applyMix(s)
        Session.load()?.let { (q, i) -> player.restore(q, i) }
        if (ytStore.state.value != null) loadYtPlaylists()
        scope.launch(Dispatchers.IO) { try { provider.warmUp() } catch (_: Exception) {} }
    }

    fun search(q: String = query, f: SearchFilter = filter) {
        query = q; filter = f
        if (q.isBlank()) return
        library.addSearch(q.trim())
        goTo(Screen.Search)
        searching = true; searched = true; searchError = null
        tracks = emptyList(); albums = emptyList(); artists = emptyList(); continuation = null
        scope.launch {
            try {
                when (f) {
                    SearchFilter.ALBUMS -> albums = provider.searchAlbums(q)
                    SearchFilter.ARTISTS -> artists = provider.searchArtists(q)
                    else -> { val p = provider.search(q, f); tracks = p.tracks; continuation = p }
                }
            } catch (e: Exception) {
                searchError = "No se pudo buscar. Revisa tu conexión."
            } finally { searching = false }
        }
    }

    fun loadMore() {
        val c = continuation?.continuation ?: return
        scope.launch {
            try {
                val p = provider.loadMore(c)
                tracks = tracks + p.tracks.filter { n -> tracks.none { it.id == n.id } }
                continuation = p
            } catch (_: Exception) { continuation = null }
        }
    }

    fun openAlbum(a: Album) {
        albumTracks = emptyList(); albumLoading = true
        open(Screen.AlbumView(a))
        scope.launch {
            try {
                albumTracks = provider.albumTracks(a.id).map { if (it.thumbnailUrl == null) it.copy(thumbnailUrl = a.thumbnailUrl) else it }
            } catch (_: Exception) {} finally { albumLoading = false }
        }
    }

    // Artista (canciones y álbumes que salen al buscar su nombre)
    var artistTracks by mutableStateOf<List<Track>>(emptyList())
    var artistAlbums by mutableStateOf<List<Album>>(emptyList())
    var artistLoading by mutableStateOf(false)

    fun openArtist(name: String, id: String? = null) {
        artistTracks = emptyList(); artistAlbums = emptyList(); artistLoading = true
        open(Screen.ArtistView(name))
        scope.launch {
            try {
                val aid = id ?: runCatching {
                    val found = provider.searchArtists(name)
                    (found.firstOrNull { it.name.equals(name, true) } ?: found.firstOrNull())?.id
                }.getOrNull()
                val page = aid?.let { runCatching { yt.artistPage(it, name) }.getOrNull() }
                if (page != null && (page.songs.isNotEmpty() || page.albums.isNotEmpty())) {
                    artistTracks = page.songs; artistAlbums = page.albums
                } else {
                    artistTracks = provider.search(name, SearchFilter.SONGS).tracks
                    artistAlbums = provider.searchAlbums(name)
                }
            } catch (_: Exception) {} finally { artistLoading = false }
        }
    }

    /** Radio: la canción y otras parecidas. */
    fun startRadio(t: Track) {
        player.playOne(t)
        scope.launch {
            val more = try { provider.related(t.id) } catch (_: Exception) { emptyList() }
            more.filter { it.id != t.id }.forEach { player.enqueue(it) }
        }
    }

    // Inicio: parecidas a lo último que escuchaste, o a tus géneros
    var forYou by mutableStateOf<List<Track>>(emptyList())
    var forYouLoading by mutableStateOf(false)
    private var forYouKey = ""

    fun loadForYou(force: Boolean = false) {
        val lib = library.data.value
        val seeds = (lib.recent.take(3) + lib.favorites.take(3)).distinctBy { it.id }.take(4)
        val genres = lib.taste.take(3)
        val artists = lib.tasteArtists.take(4)
        val key = seeds.joinToString { it.id } + "|" + genres.joinToString() + "|" + artists.joinToString()
        if (!force && key == forYouKey) return
        if (seeds.isEmpty() && genres.isEmpty() && artists.isEmpty()) { forYou = emptyList(); forYouKey = key; return }
        forYouKey = key; forYouLoading = true
        scope.launch {
            try {
                val known = (lib.recent + lib.favorites).map { it.id }.toSet()
                val picked = (if (force) lib.tasteArtists.shuffled() else lib.tasteArtists).take(3)
                val fromSeeds = seeds.flatMap { runCatching { provider.related(it.id) }.getOrDefault(emptyList()) }
                val fromArtists = picked.flatMap { a -> runCatching { provider.search(a, SearchFilter.SONGS).tracks.take(8) }.getOrDefault(emptyList()) }
                val fromGenres = genres.take(2).flatMap { g ->
                    val q = Taste.genre(g)?.query ?: "$g mix"
                    runCatching { provider.search(q, SearchFilter.SONGS).tracks.take(8) }.getOrDefault(emptyList())
                }
                forYou = (fromSeeds + fromArtists + fromGenres).distinctBy { it.id }.filter { it.id !in known }.shuffled().take(36)
            } finally { forYouLoading = false }
        }
    }

    // Diálogo "Nueva lista…" para una canción
    var newListFor by mutableStateOf<Track?>(null)

    /** El teclado va al campo de texto y no a los atajos. */
    var typing by mutableStateOf(false)

    fun finishOnboarding(genres: List<String>, artists: List<String>) {
        library.setTaste(genres, artists)
        settings.update { it.copy(onboarded = true) }
        loadForYou(force = true)
    }

    // Temporizador de sueño
    var sleepEndsAt by mutableStateOf<Long?>(null)
    private var sleepJob: Job? = null

    fun setSleep(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) { sleepEndsAt = null; return }
        sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            mpv.setPaused(true)
            sleepEndsAt = null
        }
    }

    // Ajustes
    fun setVolume(v: Int) { mpv.setVolume(v); settings.update { it.copy(volume = v) } }
    private fun applyMix(s: SettingsData) {
        player.mixStyle = s.mixStyle; player.mixOnSkip = s.mixOnSkip; player.crossfade = s.crossfade
        mpv.setTrimSilence(s.crossfade > 0 && s.mixTrim)
    }
    fun updateMix(f: (SettingsData) -> SettingsData) { settings.update(f); applyMix(settings.data.value) }
    fun setCrossfade(sec: Int) = updateMix { it.copy(crossfade = sec) }
    fun setAutoplay(on: Boolean) { player.autoplay = on; settings.update { it.copy(autoplay = on) } }
    fun setQuality(high: Boolean) = settings.update { it.copy(highQuality = high) }
    fun setDownloadQuality(high: Boolean) = settings.update { it.copy(downloadHigh = high) }
    fun setCoverColors(on: Boolean) = settings.update { it.copy(coverColors = on) }
    fun setEq(on: Boolean, levels: List<Int> = settings.data.value.eqLevels) {
        settings.update { it.copy(eqOn = on, eqLevels = levels) }
        mpv.setEqualizer(if (on) levels else null)
    }

    // Sugerencias al escribir
    var suggestions by mutableStateOf<List<String>>(emptyList())
    private var suggestJob: Job? = null

    fun onQuery(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) { suggestions = emptyList(); return }
        suggestJob = scope.launch {
            delay(250)
            suggestions = try { provider.suggestions(text).take(7) } catch (_: Exception) { emptyList() }
        }
    }

    // Favoritos y álbumes: si hay sesión de YouTube Music, se reflejan allá
    fun toggleFavorite(t: Track) {
        val was = library.isFavorite(t.id)
        library.toggleFavorite(t)
        if (ytStore.state.value != null) scope.launch(Dispatchers.IO) { runCatching { yt.setSongLiked(t.id, !was) } }
    }

    fun toggleAlbum(a: Album) {
        val was = library.isAlbumSaved(a.id)
        library.toggleAlbum(a)
        if (ytStore.state.value != null) scope.launch(Dispatchers.IO) { runCatching { yt.setPlaylistSaved(a.id, !was) } }
    }

    // YouTube Music
    var ytBusy by mutableStateOf(false)
    var ytMessage by mutableStateOf<String?>(null)
    var ytPlaylists by mutableStateOf<List<YtPlaylist>>(emptyList())
    var ytTracks by mutableStateOf<List<Track>>(emptyList())
    var ytLoading by mutableStateOf(false)

    fun connectYt(rawCookie: String) {
        val cookie = rawCookie.trim().removePrefix("Cookie:").trim()
        if ("SAPISID" !in cookie) { ytMessage = "Esa cookie no tiene SAPISID. Copia el valor completo de la cabecera «Cookie»."; return }
        ytBusy = true; ytMessage = null
        scope.launch {
            try {
                ytStore.save(YtAuth(cookie))
                val (name, email) = yt.accountInfo()
                ytStore.save(YtAuth(cookie, name = name, email = email))
                ytMessage = "Conectado como $name."
                loadYtPlaylists()
            } catch (e: Exception) {
                ytStore.clear()
                ytMessage = if (e is YtAuthException) "YouTube no aceptó la sesión. Revisa que copiaste la cookie completa." else "No se pudo conectar: ${e.message}"
            } finally { ytBusy = false }
        }
    }

    fun disconnectYt() {
        ytStore.clear(); ytPlaylists = emptyList(); ytTracks = emptyList(); ytMessage = "Sesión cerrada."
    }

    fun loadYtPlaylists() {
        scope.launch {
            try { ytPlaylists = yt.playlists() } catch (e: Exception) {
                if (e is YtAuthException) ytMessage = "La sesión de YouTube caducó. Vuelve a conectar."
            }
        }
    }

    /** key: "liked", "history" o el id de una lista. */
    fun openYtList(key: String, title: String) {
        ytTracks = emptyList(); ytLoading = true
        open(Screen.YtList(title))
        scope.launch {
            try {
                ytTracks = when (key) {
                    "liked" -> yt.likedSongs()
                    "history" -> yt.history()
                    else -> yt.playlistTracks(key)
                }
            } catch (e: Exception) {
                ytMessage = if (e is YtAuthException) "La sesión de YouTube caducó. Vuelve a conectar." else "No se pudo cargar la lista."
            } finally { ytLoading = false }
        }
    }

    fun importYtLikes() {
        ytBusy = true; ytMessage = null
        scope.launch {
            try {
                val liked = yt.likedSongs()
                val added = library.importFavorites(liked)
                ytMessage = "Importadas $added canciones nuevas a Favoritos (${liked.size} en tu cuenta)."
            } catch (e: Exception) {
                ytMessage = "No se pudieron importar tus «Me gusta»."
            } finally { ytBusy = false }
        }
    }

    fun addToYtPlaylist(playlistId: String, t: Track) {
        scope.launch(Dispatchers.IO) { runCatching { yt.addToPlaylist(playlistId, t.id) } }
    }

    fun shutdown() { Session.save(player.queue.value); mpv.release() }
}

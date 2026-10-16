package app.toastune.core

enum class AudioQuality { HIGH, LOW }

enum class SearchFilter { SONGS, ALBUMS, ARTISTS, VIDEOS }

/** Una página de resultados. [continuation] es opaco: se devuelve tal cual a [MusicProvider.loadMore]. */
class SearchPage(val tracks: List<Track>, val continuation: Any? = null) {
    val hasMore: Boolean get() = continuation != null
}

interface MusicProvider {
    val name: String

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS): SearchPage

    suspend fun loadMore(continuation: Any): SearchPage

    suspend fun suggestions(query: String): List<String>

    suspend fun getStreamUrl(trackId: String, quality: AudioQuality = AudioQuality.HIGH): String

    /** Álbumes que coinciden con [query]. */
    suspend fun searchAlbums(query: String): List<Album> = emptyList()

    /** Artistas que coinciden con [query]. */
    suspend fun searchArtists(query: String): List<Artist> = emptyList()

    /** Canciones de un álbum, en orden. */
    suspend fun albumTracks(albumId: String): List<Track> = emptyList()

    /** Calienta lo que hace lenta la primera reproducción (se llama una vez al abrir la app). */
    suspend fun warmUp() {}

    /** Canciones parecidas a [trackId]: sirven para "radio" y reproducción automática. */
    suspend fun related(trackId: String): List<Track>
}

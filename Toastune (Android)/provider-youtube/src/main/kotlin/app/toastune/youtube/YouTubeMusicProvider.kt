package app.toastune.youtube

import app.toastune.core.Album
import app.toastune.core.Artist
import app.toastune.core.AudioQuality
import app.toastune.core.MusicProvider
import app.toastune.core.SearchFilter
import app.toastune.core.SearchPage
import app.toastune.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Proveedor de YouTube Music basado en NewPipeExtractor (GPL-3.0), que mantiene
 * al día los cambios de YouTube (firmas, parámetro "n", etc.).
 *
 * Todo lo específico de YouTube vive en este módulo: si algo se rompe, solo se toca aquí.
 */
class YouTubeMusicProvider(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : MusicProvider {

    override val name = "YouTube Music"

    /** Para pedir la siguiente página de una búsqueda hace falta el mismo extractor. */
    private class Continuation(val extractor: SearchExtractor, val page: Page)

    /** Los relacionados llegan gratis con la información del video: se guardan para no repetir la petición. */
    private val relatedCache: MutableMap<String, List<Track>> = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<Track>>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Track>>?): Boolean =
                size > 60
        },
    )

    init {
        NewPipe.init(OkHttpDownloader(client))
    }

    override suspend fun search(query: String, filter: SearchFilter): SearchPage = withContext(Dispatchers.IO) {
        if (query.isBlank() || filter == SearchFilter.ALBUMS || filter == SearchFilter.ARTISTS) return@withContext SearchPage(emptyList())
        val contentFilter = when (filter) {
            SearchFilter.ALBUMS -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
            SearchFilter.ARTISTS -> YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
            SearchFilter.SONGS -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            SearchFilter.VIDEOS -> YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS
        }
        val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf(contentFilter), "")
        extractor.fetchPage()
        toPage(extractor, extractor.initialPage)
    }

    override suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), "",
        )
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
            val id = item.url.substringAfter("list=", "").substringBefore('&')
            if (id.isEmpty()) null
            else Album(
                id = id,
                title = item.name,
                artist = item.uploaderName.orEmpty(),
                thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url,
            )
        }.distinctBy { it.id }
    }

    override suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val extractor = ServiceList.YouTube.getSearchExtractor(
            query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS), "",
        )
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<ChannelInfoItem>().mapNotNull { item ->
            val id = item.url.substringAfterLast('/').substringBefore('?')
            if (!id.startsWith("UC")) null
            else Artist(id, item.name, item.thumbnails.maxByOrNull { it.height }?.url)
        }.distinctBy { it.id }
    }

    override suspend fun albumTracks(albumId: String): List<Track> = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/playlist?list=$albumId"
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val out = ArrayList<Track>()
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNullTo(out) { it.toTrack() }
        var page = info.nextPage
        var guard = 0
        while (page != null && guard++ < 4) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            more.items.filterIsInstance<StreamInfoItem>().mapNotNullTo(out) { it.toTrack() }
            page = more.nextPage
        }
        out.distinctBy { it.id }
    }

    /** Descarga una sola vez el código de firma de YouTube: la primera canción deja de tardar. */
    override suspend fun warmUp() {
        runCatching { getStreamUrl("jNQXAC9IVRw", AudioQuality.LOW) }
    }

    override suspend fun loadMore(continuation: Any): SearchPage = withContext(Dispatchers.IO) {
        val c = continuation as? Continuation ?: return@withContext SearchPage(emptyList())
        toPage(c.extractor, c.extractor.getPage(c.page))
    }

    override suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else ServiceList.YouTube.suggestionExtractor.suggestionList(query)
    }

    override suspend fun getStreamUrl(trackId: String, quality: AudioQuality): String =
        withContext(Dispatchers.IO) {
            // Solo el extractor de audio: más ligero que StreamInfo (que además trae relacionados y comentarios).
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$trackId")
            extractor.fetchPage()
            val candidates = extractor.audioStreams
                .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .filter { it.isOriginalTrack() }

            val chosen = when (quality) {
                AudioQuality.HIGH -> candidates.maxByOrNull { it.averageBitrate }
                AudioQuality.LOW -> candidates.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate }
                    ?: candidates.firstOrNull()
            }
            chosen?.content ?: error("No se encontró audio reproducible para este video")
        }

    override suspend fun related(trackId: String): List<Track> = withContext(Dispatchers.IO) {
        relatedCache[trackId] ?: run {
            fetchInfo(trackId)
            relatedCache[trackId].orEmpty()
        }
    }

    private fun fetchInfo(trackId: String): StreamInfo {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$trackId")
        relatedCache[trackId] = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrack() }
            .filter { it.id != trackId }
            .take(30)
        return info
    }

    private fun toPage(extractor: SearchExtractor, page: ListExtractor.InfoItemsPage<InfoItem>): SearchPage {
        val tracks = page.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() }
        val next = page.nextPage?.takeIf { page.hasNextPage() }?.let { Continuation(extractor, it) }
        return SearchPage(tracks, next)
    }

    private fun AudioStream.isOriginalTrack(): Boolean =
        audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL

    private fun StreamInfoItem.toTrack(): Track? {
        val id = runCatching { YoutubeStreamLinkHandlerFactory.getInstance().getId(url) }.getOrNull()
            ?: return null
        return Track(
            id = id,
            title = name,
            artist = uploaderName.orEmpty(),
            durationMs = duration.takeIf { it >= 0 }?.times(1000),
            thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
        )
    }
}

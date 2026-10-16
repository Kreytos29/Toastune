package app.toastune.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import app.toastune.core.MusicProvider
import app.toastune.data.SettingsRepository
import java.io.File
import java.util.concurrent.Executors

/**
 * Dos cachés independientes:
 *  - [streamCache]: lo que se reproduce; se limpia solo (las más antiguas primero) al pasar el límite.
 *  - [downloadCache]: descargas explícitas; nunca se borra sola.
 */
@OptIn(UnstableApi::class)
class MediaCaches(context: Context, streamCacheBytes: Long) {
    val databaseProvider = StandaloneDatabaseProvider(context)

    val streamCache = SimpleCache(
        File(context.cacheDir, "audio"),
        LeastRecentlyUsedCacheEvictor(streamCacheBytes),
        databaseProvider,
    )

    val downloadCache = SimpleCache(
        File(context.filesDir, "downloads"),
        NoOpCacheEvictor(),
        databaseProvider,
    )
}

/**
 * Cadena de lectura del reproductor, de más a menos barata:
 *  descargas → caché de reproducción → red (resolviendo "toastune://track/<id>" a la URL real).
 * Si la canción ya está en disco no se toca la red, así funciona sin conexión.
 */
@OptIn(UnstableApi::class)
class MediaGraph(
    context: Context,
    provider: MusicProvider,
    settings: SettingsRepository,
) {
    val caches = MediaCaches(context, settings.state.value.cacheMb * 1024L * 1024L)

    private val streamResolver = StreamResolver(provider) { settings.state.value.streamQuality }
    private val downloadResolver = StreamResolver(provider) { settings.state.value.downloadQuality }

    private fun network(resolver: StreamResolver): DataSource.Factory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000),
        resolver,
    )

    private fun streaming(resolver: StreamResolver): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(caches.streamCache)
        .setUpstreamDataSourceFactory(network(resolver))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val playbackFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(caches.downloadCache)
        .setUpstreamDataSourceFactory(streaming(streamResolver))
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            context,
            caches.databaseProvider,
            caches.downloadCache,
            streaming(downloadResolver),
            Executors.newFixedThreadPool(2),
        ).apply { maxParallelDownloads = 2 }
    }

    /** Resuelve la URL de una canción con antelación (la siguiente de la cola) para que empiece al instante. */
    fun prefetch(trackId: String) {
        runCatching { streamResolver.resolve(trackId) }
    }

    /** Olvida las URLs guardadas (por ejemplo tras un error de reproducción: pueden haber caducado). */
    fun invalidateStreams() {
        streamResolver.clear()
        downloadResolver.clear()
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}

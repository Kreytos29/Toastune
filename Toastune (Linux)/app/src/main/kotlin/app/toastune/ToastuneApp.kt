package app.toastune

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import app.toastune.core.MusicProvider
import app.toastune.data.DriveSync
import app.toastune.data.LibraryRepository
import app.toastune.data.LrclibLyricsProvider
import app.toastune.data.NetworkMonitor
import app.toastune.data.SettingsRepository
import app.toastune.data.YtAccountStore
import app.toastune.data.YtMusicApi
import app.toastune.playback.DownloadsController
import app.toastune.playback.MediaGraph
import app.toastune.playback.PlayerConnection
import app.toastune.youtube.YouTubeMusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ToastuneApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val network: NetworkMonitor by lazy { NetworkMonitor(this) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Para cambiar de fuente solo hay que reemplazar esta línea (p. ej. por un proveedor Navidrome).
    val provider: MusicProvider by lazy { YouTubeMusicProvider(httpClient) }

    val lyrics: LrclibLyricsProvider by lazy { LrclibLyricsProvider(httpClient) }

    val ytStore: YtAccountStore by lazy { YtAccountStore(this) }
    val ytApi: YtMusicApi by lazy { YtMusicApi(httpClient, ytStore) }

    val drive: DriveSync by lazy { DriveSync(this, httpClient) }

    val library: LibraryRepository by lazy { LibraryRepository(this) }

    val media: MediaGraph by lazy { MediaGraph(this, provider, settings) }

    val downloads: DownloadsController by lazy {
        DownloadsController(this, media, library, settings, appScope)
    }

    val player: PlayerConnection by lazy { PlayerConnection(this, appScope) }

    override fun onCreate() {
        super.onCreate()

        // El historial se guarda aunque la pantalla esté cerrada y la música siga sonando.
        appScope.launch {
            player.state
                .map { it.track }
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collect { library.recordPlay(it) }
        }

        // Abre las cachés de música en segundo plano (en teléfonos con almacenamiento lento tardan).
        appScope.launch(Dispatchers.IO) { runCatching { media } }

        // Calienta YouTube en segundo plano: la primera canción deja de tardar varios segundos.
        appScope.launch(Dispatchers.IO) {
            network.online.first { it }
            runCatching { provider.warmUp() }
        }

        // Resuelve de antemano las dos canciones siguientes de la cola.
        appScope.launch {
            player.state
                .map { s -> s.queue.drop(s.queueIndex + 1).take(2).map { it.id } }
                .distinctUntilChanged()
                .collect { ids ->
                    if (!network.online.value) return@collect
                    launch(Dispatchers.IO) { kotlinx.coroutines.delay(2_500); ids.forEach { media.prefetch(it) } }
                }
        }

        // Mantiene el widget de la pantalla de inicio al día.
        appScope.launch {
            player.state
                .map { Triple(it.track?.id, it.isPlaying, it.track) }
                .distinctUntilChangedBy { it.first to it.second }
                .collect { (_, playing, track) -> runCatching { PlayerWidget.refresh(this@ToastuneApp, track, playing) } }
        }

        // Reproducción automática: al acabar la cola se añaden canciones parecidas.
        appScope.launch {
            var lastSeed: String? = null
            player.state.collect { s ->
                val t = s.track ?: return@collect
                if (s.hasNext || s.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) return@collect
                if (!settings.state.value.autoplay || !network.online.value) return@collect
                if (lastSeed == t.id) return@collect
                lastSeed = t.id
                val inQueue = s.queue.mapTo(HashSet()) { it.id }
                val more = runCatching { provider.related(t.id) }.getOrDefault(emptyList())
                    .filter { it.id !in inQueue }
                    .take(10)
                player.enqueueAuto(more)
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(httpClient)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(200)
        .build()
}

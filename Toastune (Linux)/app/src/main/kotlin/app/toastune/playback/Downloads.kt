package app.toastune.playback

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import app.toastune.R
import app.toastune.ToastuneApp
import app.toastune.core.Track
import app.toastune.data.LibraryRepository
import app.toastune.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DownloadPhase { QUEUED, DOWNLOADING, DONE, FAILED, PAUSED }

/** Estado de una descarga; [progress] va de 0 a 1. */
data class DownloadStatus(val phase: DownloadPhase, val progress: Float = 0f)

/** Servicio en primer plano que mantiene vivas las descargas aunque se cierre la app. */
@OptIn(UnstableApi::class)
class ToastuneDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel,
    0,
) {
    override fun getDownloadManager(): DownloadManager = (application as ToastuneApp).media.downloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
        this,
        R.drawable.ic_stat_download,
        null,
        null,
        downloads,
        notMetRequirements,
    )

    private companion object {
        const val NOTIFICATION_ID = 7
        const val CHANNEL_ID = "toastune_downloads"
    }
}

/** Orquesta las descargas y expone su estado a la interfaz. */
@OptIn(UnstableApi::class)
class DownloadsController(
    private val context: Context,
    private val media: MediaGraph,
    private val library: LibraryRepository,
    settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val states: StateFlow<Map<String, DownloadStatus>> = _states.asStateFlow()

    private val manager: DownloadManager get() = media.downloadManager

    init {
        manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) = onDownload(download)

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                val id = download.request.id
                _states.update { it - id }
                scope.launch { library.removeDownloaded(id) }
            }
        })

        // Descargas que ya existían de otra sesión.
        scope.launch {
            val existing = withContext(Dispatchers.IO) {
                val map = HashMap<String, DownloadStatus>()
                manager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val d = cursor.download
                        map[d.request.id] = d.toStatus()
                    }
                }
                map
            }
            _states.update { existing + it }
        }

        scope.launch {
            settings.state.map { it.wifiOnlyDownloads }.distinctUntilChanged().collect { wifiOnly ->
                manager.requirements = Requirements(
                    if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK,
                )
            }
        }

        // El progreso no genera eventos: se consulta mientras haya descargas activas.
        scope.launch {
            while (true) {
                delay(1_000)
                val active = manager.currentDownloads
                if (active.isNotEmpty()) active.forEach { onDownload(it) }
            }
        }
    }

    fun download(track: Track) {
        scope.launch {
            library.rememberTrack(track)
            val request = DownloadRequest.Builder(track.id, Uri.parse("$TRACK_SCHEME://track/${track.id}")).build()
            DownloadService.sendAddDownload(context, ToastuneDownloadService::class.java, request, false)
        }
    }

    fun downloadAll(tracks: List<Track>) = tracks.forEach { download(it) }

    fun remove(trackId: String) {
        DownloadService.sendRemoveDownload(context, ToastuneDownloadService::class.java, trackId, false)
    }

    fun removeAll() {
        DownloadService.sendRemoveAllDownloads(context, ToastuneDownloadService::class.java, false)
        scope.launch { library.clearDownloaded() }
    }

    /** Espacio ocupado por las descargas, en bytes. */
    fun usedBytes(): Long = media.caches.downloadCache.cacheSpace

    private fun onDownload(download: Download) {
        val id = download.request.id
        _states.update { it + (id to download.toStatus()) }
        if (download.state == Download.STATE_COMPLETED) scope.launch { library.markDownloaded(id) }
    }

    private fun Download.toStatus(): DownloadStatus {
        val phase = when (state) {
            Download.STATE_COMPLETED -> DownloadPhase.DONE
            Download.STATE_DOWNLOADING -> DownloadPhase.DOWNLOADING
            Download.STATE_FAILED -> DownloadPhase.FAILED
            Download.STATE_STOPPED -> DownloadPhase.PAUSED
            else -> DownloadPhase.QUEUED
        }
        val progress = if (phase == DownloadPhase.DONE) 1f else (percentDownloaded.takeIf { it >= 0f } ?: 0f) / 100f
        return DownloadStatus(phase, progress)
    }
}

package app.toastune.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.toastune.R
import app.toastune.ToastuneApp
import android.os.Handler
import android.os.Looper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val equalizer = EqualizerController()

    override fun onCreate() {
        super.onCreate()
        val app = application as ToastuneApp

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(app.media.playbackFactory))
            // Buffers pequeños: es solo audio. Empieza a sonar antes y gasta menos memoria.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20_000, 60_000, 1_000, 2_500)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Fallos de reproducción: se reintenta dos veces (las URLs caducan) y, si sigue sin sonar y hay
        // conexión, se salta a la siguiente canción en vez de quedarse trabado.
        player.addListener(object : Player.Listener {
            private var failedId: String? = null
            private var attempts = 0

            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId ?: return
                if (id != failedId) { failedId = id; attempts = 0 }
                if (attempts < 2) {
                    attempts++
                    app.media.invalidateStreams()
                    handler.postDelayed({ if (player.playerError != null) player.prepare() }, 700L * attempts)
                } else if (app.network.online.value && player.hasNextMediaItem()) {
                    attempts = 0
                    player.seekToNextMediaItem()
                    player.prepare()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem?.mediaId != failedId) { failedId = null; attempts = 0 }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizer.attach(audioSessionId)
            }
        })
        equalizer.attach(player.audioSessionId)

        scope.launch {
            app.settings.state.map { it.eqEnabled to it.eqLevels }.distinctUntilChanged()
                .collect { equalizer.apply(app.settings.state.value) }
        }
        // Al volver la conexión, si la canción se había quedado con error, se reintenta sola.
        scope.launch {
            app.network.online.collect { online ->
                if (online && player.playerError != null) player.prepare()
            }
        }

        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map {
                        it.buildUpon().setUri("$TRACK_SCHEME://track/${it.mediaId}").build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_logo) },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Los botones de la notificación arrancan el servicio con startForegroundService: Android exige
        // llamar a startForeground en menos de 10 s o muestra "no responde". Si la canción tarda en
        // resolverse (red lenta) o no hay nada que reproducir, el aviso normal de Media3 no llega a
        // tiempo, así que se publica uno provisional de inmediato.
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) ensureForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun ensureForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        runCatching {
            nm.createNotificationChannel(NotificationChannel(BOOT_CHANNEL, "Reproducción", NotificationManager.IMPORTANCE_LOW))
            val n = Notification.Builder(this, BOOT_CHANNEL)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle("Toastune")
                .setContentText("Preparando la música…")
                .build()
            startForeground(BOOT_NOTIFICATION_ID, n)
        }
        handler.postDelayed({ runCatching { nm.cancel(BOOT_NOTIFICATION_ID) } }, 8_000)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Si se cierra la app desde recientes, la música sigue; solo se detiene el servicio si no suena nada. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        equalizer.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val BOOT_CHANNEL = "toastune_playback_boot"
        const val BOOT_NOTIFICATION_ID = 4242
    }
}

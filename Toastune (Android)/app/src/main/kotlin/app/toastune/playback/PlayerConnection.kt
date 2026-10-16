package app.toastune.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.toastune.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado del reproductor SIN la posición: esta cambia 4 veces por segundo y vive aparte. */
data class PlayerState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val sleepTimerEndsAt: Long? = null,
    val error: String? = null,
)

/** Puente entre la interfaz y el [PlaybackService]: expone el estado y los controles. */
class PlayerConnection(context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Solo cambia al cambiar de canción: las listas la leen sin recomponerse por cada evento. */
    val currentId: StateFlow<String?> = _state.map { it.track?.id }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)
    val isPlaying: StateFlow<Boolean> = _state.map { it.isPlaying }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var controller: MediaController? = null
    private val pending = ArrayDeque<MediaController.() -> Unit>()

    private val uiActive = MutableStateFlow(false)
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val rebuildQueue = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
            )
            refresh(rebuildQueue)
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess { c ->
                    controller = c
                    c.addListener(listener)
                    while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
                    refresh(true)
                }
                .onFailure {
                    _state.value = _state.value.copy(error = "No se pudo conectar con el reproductor")
                }
        }, ContextCompat.getMainExecutor(context))

        // La posición solo se consulta si la pantalla está visible y suena algo.
        scope.launch {
            var job: Job? = null
            uiActive.collect { active ->
                job?.cancel()
                if (active) {
                    job = launch {
                        while (true) {
                            val c = controller
                            if (c != null) {
                                _position.value = c.currentPosition.coerceAtLeast(0L)
                            }
                            delay(if (c?.isPlaying == true) 250 else 1000)
                        }
                    }
                }
            }
        }
    }

    fun setUiActive(active: Boolean) {
        uiActive.value = active
    }

    private var lastQueue: List<Track> = emptyList()

    private fun refresh(rebuildQueue: Boolean) {
        val c = controller ?: return
        if (rebuildQueue) {
            lastQueue = List(c.mediaItemCount) { c.getMediaItemAt(it).toTrack() }
        }
        _state.value = PlayerState(
            track = c.currentMediaItem?.toTrack(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.takeIf { it > 0L } ?: 0L,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = lastQueue,
            queueIndex = c.currentMediaItemIndex,
            sleepTimerEndsAt = _state.value.sleepTimerEndsAt,
            error = c.playerError?.let { "${it.errorCodeName}: ${it.cause?.message ?: it.message}" },
        )
        _position.value = c.currentPosition.coerceAtLeast(0L)
    }

    private fun withController(block: MediaController.() -> Unit) {
        val c = controller
        if (c != null) c.block() else pending.addLast(block)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        withController {
            setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
            prepare()
            play()
        }
    }

    fun playNext(track: Track) = withController {
        if (mediaItemCount == 0) {
            setMediaItem(track.toMediaItem())
            prepare()
            play()
        } else {
            addMediaItem(currentMediaItemIndex + 1, track.toMediaItem())
        }
    }

    fun addToQueue(track: Track) = enqueue(listOf(track))

    /** Añade canciones de la reproducción automática: se marcan para poder quitarlas al activar el bucle. */
    fun enqueueAuto(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        withController { addMediaItems(tracks.map { it.toMediaItem(auto = true) }) }
    }

    fun enqueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        withController {
            val items = tracks.map { it.toMediaItem() }
            if (mediaItemCount == 0) {
                setMediaItems(items)
                prepare()
                play()
            } else {
                addMediaItems(items)
            }
        }
    }

    fun playQueueItem(index: Int) = withController {
        if (index in 0 until mediaItemCount) {
            seekToDefaultPosition(index)
            prepare()
            play()
        }
    }

    fun removeQueueItem(index: Int) = withController {
        if (index in 0 until mediaItemCount) removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) = withController {
        if (from in 0 until mediaItemCount && to in 0 until mediaItemCount) moveMediaItem(from, to)
    }

    fun togglePlay() = withController {
        if (isPlaying) {
            pause()
        } else {
            if (playbackState == Player.STATE_IDLE) prepare()
            if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
            play()
        }
    }

    fun next() = withController { seekToNextMediaItem() }

    fun previous() = withController { seekToPrevious() }

    fun seek(positionMs: Long) {
        _position.value = positionMs
        withController { seekTo(positionMs) }
    }

    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }

    fun cycleRepeat() = withController {
        val next = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        // El bucle debe repetir lo que elegiste, no las canciones que la reproducción automática
        // añadió al final: se quitan (menos la que suena ahora) al activarlo.
        if (next == Player.REPEAT_MODE_ALL) {
            for (i in mediaItemCount - 1 downTo 0) {
                if (i != currentMediaItemIndex && getMediaItemAt(i).mediaMetadata.extras?.getBoolean(EXTRA_AUTO) == true) {
                    removeMediaItem(i)
                }
            }
        }
        repeatMode = next
    }

    /** Pausa la música tras [minutes] minutos; null lo cancela. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.value = _state.value.copy(sleepTimerEndsAt = null)
            return
        }
        val ends = System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepTimerEndsAt = ends)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            withController { pause() }
            _state.value = _state.value.copy(sleepTimerEndsAt = null)
        }
    }
}

private const val EXTRA_AUTO = "toastune_auto"

fun Track.toMediaItem(auto: Boolean = false): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri("$TRACK_SCHEME://track/$id")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setDurationMs(durationMs)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .apply { if (auto) setExtras(android.os.Bundle().apply { putBoolean(EXTRA_AUTO, true) }) }
            .build(),
    )
    .build()

fun MediaItem.toTrack(): Track = Track(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    durationMs = mediaMetadata.durationMs,
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
)

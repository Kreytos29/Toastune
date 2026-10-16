package app.toastune.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.toastune.LyricsUi
import app.toastune.MainViewModel
import app.toastune.core.Lyrics
import app.toastune.playback.DownloadPhase
import app.toastune.playback.PlayerState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Barra de progreso: lee la posición solo en esta hoja y se agranda al arrastrar. */
@Composable
fun SeekBar(position: StateFlow<Long>, durationMs: Long, accent: Color, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val pos by position.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (durationMs > 0) (pos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val thick by animateFloatAsState(if (dragging != null) 12f else 6f, spring(stiffness = Spring.StiffnessMedium), label = "thick")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Canvas(
        modifier
            .height(32.dp)
            .pointerInput(durationMs) {
                detectTapGestures { o -> onSeek((o.x / size.width * durationMs).toLong().coerceIn(0, durationMs)) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let { f -> onSeek((f * durationMs).toLong()) }; dragging = null },
                    onDragCancel = { dragging = null },
                    onHorizontalDrag = { c, _ -> c.consume(); dragging = (c.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
    ) {
        val h = thick.dp.toPx()
        val y = size.height / 2 - h / 2
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(accent, Offset(0f, y), Size(size.width * fraction, h), CornerRadius(h / 2))
        drawCircle(Color.White, h * 0.9f, Offset(size.width * fraction, size.height / 2))
    }
}

@Composable
private fun TimeLabels(position: StateFlow<Long>, durationMs: Long) {
    val secs by remember(position) { position.map { it / 1000 }.distinctUntilChanged() }.collectAsState(0L)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(secs * 1000), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (durationMs > 0) formatTime(durationMs) else "--:--", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LyricsView(position: StateFlow<Long>, lyrics: Lyrics, accent: Color, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val pos by position.collectAsState()
    val active by remember(lyrics) {
        derivedStateOf {
            if (!lyrics.synced) -1 else lyrics.lines.indexOfLast { it.timeMs <= pos + 250 }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(active, -listState.layoutInfo.viewportSize.height / 3)
    }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp)) {
        itemsIndexed(lyrics.lines) { i, line ->
            val isActive = i == active
            val color by animateColorAsState(
                when {
                    !lyrics.synced -> MaterialTheme.colorScheme.onSurface
                    isActive -> accent
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                tween(300), label = "lyric",
            )
            val scale by animateFloatAsState(if (isActive) 1f else 0.9f, spring(stiffness = Spring.StiffnessLow), label = "lyricScale")
            Text(
                line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = lyrics.synced) { onSeek(line.timeMs) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val tint by animateColorAsState(if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant, label = "tool")
    Box(Modifier.size(48.dp).clip(CircleShape).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(25.dp), tint = tint)
    }
}

@Composable
fun NowPlayingScreen(
    vm: MainViewModel,
    state: PlayerState,
    accent: Color,
    onClose: () -> Unit,
    onQueue: () -> Unit,
    onSleep: () -> Unit,
    onMore: () -> Unit,
) {
    val track = state.track ?: return
    val player = vm.player
    val favoriteIds by vm.favoriteIds.collectAsState()
    val downloads by vm.downloadStates.collectAsState()
    val lyricsUi by vm.lyrics.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    val isFav = track.id in favoriteIds
    val dl = downloads[track.id]
    val coverScale by animateFloatAsState(
        if (state.isPlaying) 1f else 0.9f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "coverScale",
    )

    LaunchedEffect(showLyrics, track.id) { if (showLyrics) vm.loadLyrics() }

    Box(Modifier.fillMaxSize().blockTouches()) {
        AuroraBackground(accent, intensity = 1.4f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.KeyboardArrowDown, "Cerrar", onClose)
                Text("Reproduciendo", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GlassIconButton(Icons.Rounded.MoreVert, "Más", onMore)
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(showLyrics, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "lyricsSwitch") { lyr ->
                    if (!lyr) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Cover(
                                track.thumbnailUrl,
                                Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = coverScale; scaleY = coverScale }.sharedCover(),
                                RoundedCornerShape(32.dp),
                                px = 900,
                            )
                        }
                    } else {
                        when (val l = lyricsUi) {
                            is LyricsUi.Found -> LyricsView(player.position, l.lyrics, accent, player::seek, Modifier.fillMaxSize())
                            LyricsUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No encontré la letra de esta canción", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val heart by animateFloatAsState(if (isFav) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "heart")
                Box(
                    Modifier.size(48.dp).graphicsLayer { scaleX = heart; scaleY = heart }.clip(CircleShape).bounceClick { vm.toggleFavorite(track) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Favorito", Modifier.size(28.dp),
                        tint = if (isFav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            SeekBar(player.position, state.durationMs, accent, player::seek, Modifier.fillMaxWidth().padding(top = 8.dp))
            TimeLabels(player.position, state.durationMs)

            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ToolButton(Icons.Rounded.Shuffle, "Aleatorio", state.shuffle, accent) { player.toggleShuffle() }
                Box(Modifier.size(60.dp).clip(CircleShape).bounceClick(enabled = state.hasPrevious) { player.previous() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    Modifier.size(78.dp).clip(CircleShape).background(accent).bounceClick { player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.size(34.dp), color = Color.Black.copy(alpha = 0.8f), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.isPlaying) "Pausar" else "Reproducir",
                            Modifier.size(44.dp), tint = Color.Black.copy(alpha = 0.85f),
                        )
                    }
                }
                Box(Modifier.size(60.dp).clip(CircleShape).bounceClick(enabled = state.hasNext) { player.next() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SkipNext, "Siguiente", Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.hasNext) 1f else 0.35f))
                }
                ToolButton(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repetir", state.repeatMode != Player.REPEAT_MODE_OFF, accent,
                ) { player.cycleRepeat() }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp).frost(CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.Rounded.Lyrics, "Letra", showLyrics, accent) { showLyrics = !showLyrics }
                ToolButton(Icons.Rounded.Bedtime, "Temporizador", state.sleepTimerEndsAt != null, accent, onSleep)
                when (dl?.phase) {
                    DownloadPhase.DONE -> ToolButton(Icons.Rounded.DownloadDone, "Descargada", true, accent) { vm.removeDownload(track.id) }
                    DownloadPhase.DOWNLOADING, DownloadPhase.QUEUED -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { (dl?.progress ?: 0f).coerceIn(0.03f, 1f) }, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = accent)
                    }
                    else -> ToolButton(Icons.Rounded.Download, "Descargar", false, accent) { vm.download(track) }
                }
                ToolButton(Icons.AutoMirrored.Rounded.QueueMusic, "Cola", false, accent, onQueue)
            }
        }
    }
}

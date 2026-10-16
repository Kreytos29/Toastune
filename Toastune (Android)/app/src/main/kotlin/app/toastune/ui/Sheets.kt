package app.toastune.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.toastune.core.Track
import app.toastune.data.Playlist
import app.toastune.playback.DownloadPhase
import app.toastune.playback.DownloadStatus

/** Hoja inferior: fondo oscurecido + panel de cristal que sube con un muelle. */
@Composable
fun SheetHost(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Scrim(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDismiss))
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it },
        exit = slideOutVertically(tween(200)) { it },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f))
                    .frost(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
                    .animateContentSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 12.dp),
            ) {
                Box(
                    Modifier.padding(top = 10.dp, bottom = 6.dp).align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                )
                content()
            }
        }
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun SheetTitle(track: Track) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(track.thumbnailUrl, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun shareTrack(context: Context, track: Track) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${track.title} – ${track.artist}\nhttps://music.youtube.com/watch?v=${track.id}")
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun TrackOptionsContent(
    track: Track,
    isFavorite: Boolean,
    download: DownloadStatus?,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    fun then(block: () -> Unit): () -> Unit = { block(); onDismiss() }
    SheetTitle(track)
    SheetAction(Icons.Rounded.SkipNext, "Reproducir a continuación", then(onPlayNext))
    SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Añadir a la cola", then(onAddToQueue))
    SheetAction(
        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
        then(onToggleFavorite),
        tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
    )
    when (download?.phase) {
        DownloadPhase.DONE -> SheetAction(Icons.Rounded.DownloadDone, "Quitar descarga", then(onRemoveDownload))
        DownloadPhase.DOWNLOADING, DownloadPhase.QUEUED -> SheetAction(Icons.Rounded.Close, "Cancelar descarga", then(onRemoveDownload))
        else -> SheetAction(Icons.Rounded.Download, "Descargar", then(onDownload))
    }
    SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Añadir a una playlist", onAddToPlaylist)
    SheetAction(Icons.Rounded.Share, "Compartir", then { shareTrack(context, track) })
    if (onRemoveFromPlaylist != null) {
        SheetAction(Icons.Rounded.Delete, "Quitar de esta playlist", then(onRemoveFromPlaylist), tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun NewPlaylistField(onCreate: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    fun create() { if (name.isNotBlank()) { onCreate(name); name = "" } }
    Row(
        modifier.fillMaxWidth().frost(RoundedCornerShape(20.dp)).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (name.isEmpty()) Text("Nueva playlist…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                name, { name = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlassIconButton(Icons.Rounded.Add, "Crear", { create() }, size = 40.dp)
    }
}

@Composable
fun PlaylistPickerContent(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    ytPlaylists: List<app.toastune.data.YtPlaylist> = emptyList(),
    onPickYt: (app.toastune.data.YtPlaylist) -> Unit = {},
) {
    Text("Añadir a una playlist", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
    NewPlaylistField(onCreate, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    LazyColumn(Modifier.heightIn(max = 320.dp)) {
        itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(p) }.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${p.trackCount} canciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (ytPlaylists.isNotEmpty()) {
            item(key = "yt-header") {
                Text(
                    "En tu YouTube Music",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
            items(ytPlaylists, key = { "yt-" + it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPickYt(p) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(16.dp))
                    Text(p.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SleepTimerContent(active: Boolean, onPick: (Int?) -> Unit) {
    Text("Temporizador", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
    listOf(15, 30, 45, 60, 90).forEach { m ->
        SheetAction(Icons.Rounded.Bedtime, "$m minutos", { onPick(m) })
    }
    if (active) SheetAction(Icons.Rounded.Close, "Desactivar", { onPick(null) }, tint = MaterialTheme.colorScheme.error)
}

@Composable
fun QueueContent(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val listState: LazyListState = rememberLazyListState()
    LaunchedEffect(Unit) { if (currentIndex > 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }
    Text("En cola · ${queue.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
    LazyColumn(Modifier.heightIn(max = 460.dp), state = listState) {
        itemsIndexed(queue, key = { i, t -> "$i-${t.id}" }) { i, t ->
            TrackRow(
                track = t,
                onClick = { onPlay(i) },
                onMore = { onRemove(i) },
                isCurrent = i == currentIndex,
                isPlaying = isPlaying,
                moreIcon = Icons.Rounded.Close,
            )
        }
    }
}

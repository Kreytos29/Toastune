package app.toastune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.toastune.MainViewModel
import app.toastune.core.Album
import app.toastune.core.Track
import app.toastune.playback.DownloadPhase

/** Fila de un álbum en resultados de búsqueda y en la biblioteca. */
@Composable
fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(album.thumbnailUrl, Modifier.size(64.dp), RoundedCornerShape(16.dp), px = 240)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Álbum · ${album.artist}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AlbumScreen(
    vm: MainViewModel,
    album: Album,
    accent: Color,
    bottomPad: Dp,
    onBack: () -> Unit,
    onMore: (Track) -> Unit,
) {
    var tracks by remember(album.id) { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(album.id) { tracks = vm.albumTracks(album) }

    val savedIds by vm.savedAlbumIds.collectAsState()
    val downloads by vm.downloadStates.collectAsState()
    val currentId by vm.player.currentId.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val saved = album.id in savedIds
    val list = tracks

    Box(Modifier.fillMaxSize().blockTouches().background(MaterialTheme.colorScheme.background)) {
        AuroraBackground(accent, intensity = 0.9f)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPad),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onBack)
                    }
                    Spacer(Modifier.height(8.dp))
                    Cover(album.thumbnailUrl, Modifier.size(220.dp), RoundedCornerShape(28.dp), px = 600)
                    Spacer(Modifier.height(16.dp))
                    Text(album.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(album.artist)
                            if (list != null && list.isNotEmpty()) append(" · ${list.size} canciones")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (list != null && list.isNotEmpty()) {
                        val allDone = list.all { downloads[it.id]?.phase == DownloadPhase.DONE }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Action(Icons.Rounded.PlayArrow, "Reproducir") { vm.play(list, 0) }
                            Action(Icons.Rounded.Shuffle, "Aleatorio") { vm.play(list.shuffled(), 0) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Action(
                                if (saved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                if (saved) "Guardado" else "Guardar álbum",
                            ) { vm.toggleAlbum(album, list) }
                            Action(
                                if (allDone) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                if (allDone) "Descargado" else "Descargar álbum",
                            ) { if (allDone) vm.removeDownloads(list) else vm.downloadAlbum(album, list) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            when {
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
                list.isEmpty() -> item {
                    EmptyState(Icons.Rounded.CloudOff, "No se pudo cargar el álbum", "Revisa tu conexión e inténtalo de nuevo.", Modifier.padding(top = 24.dp))
                }
                else -> itemsIndexed(list, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { i, t ->
                    TrackRow(
                        track = t,
                        onClick = { vm.play(list, i) },
                        onMore = { onMore(t) },
                        isCurrent = currentId == t.id,
                        isPlaying = playing,
                        download = downloads[t.id],
                    )
                }
            }
        }
    }
}

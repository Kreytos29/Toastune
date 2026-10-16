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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
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
import app.toastune.core.Artist
import app.toastune.core.ArtistPage
import app.toastune.core.Track

/** Fila de un artista en los resultados de búsqueda. */
@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(artist.thumbnailUrl, Modifier.size(64.dp), CircleShape, px = 240)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Artista", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ArtistScreen(
    vm: MainViewModel,
    artist: Artist,
    accent: Color,
    bottomPad: Dp,
    onBack: () -> Unit,
    onAlbum: (Album) -> Unit,
    onMore: (Track) -> Unit,
) {
    var loaded by remember(artist.id) { mutableStateOf(false) }
    var page by remember(artist.id) { mutableStateOf<ArtistPage?>(null) }
    LaunchedEffect(artist.id) {
        page = vm.artistPage(artist)
        loaded = true
    }
    val downloads by vm.downloadStates.collectAsState()
    val currentId by vm.player.currentId.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val data = page

    Box(Modifier.fillMaxSize().blockTouches().background(MaterialTheme.colorScheme.background)) {
        AuroraBackground(accent, intensity = 0.9f)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad)) {
            item {
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onBack)
                    }
                    Spacer(Modifier.height(8.dp))
                    Cover(artist.thumbnailUrl, Modifier.size(170.dp), CircleShape, px = 600)
                    Spacer(Modifier.height(14.dp))
                    Text(artist.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    if (data != null && data.songs.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Action(Icons.Rounded.PlayArrow, "Reproducir") { vm.play(data.songs, 0) }
                            Action(Icons.Rounded.Shuffle, "Aleatorio") { vm.play(data.songs.shuffled(), 0) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            when {
                !loaded -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
                data == null || (data.songs.isEmpty() && data.albums.isEmpty()) -> item {
                    EmptyState(Icons.Rounded.CloudOff, "No se pudo cargar el artista", "Revisa tu conexión e inténtalo de nuevo.", Modifier.padding(top = 24.dp))
                }
                else -> {
                    if (data.songs.isNotEmpty()) {
                        item { SectionHeader("Canciones") }
                        itemsIndexed(data.songs, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { i, t ->
                            TrackRow(
                                track = t,
                                onClick = { vm.play(data.songs, i) },
                                onMore = { onMore(t) },
                                isCurrent = currentId == t.id,
                                isPlaying = playing,
                                download = downloads[t.id],
                            )
                        }
                    }
                    if (data.albums.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)); SectionHeader("Álbumes y sencillos") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(data.albums, key = { it.id }, contentType = { "album" }) { a ->
                                    Column(Modifier.width(150.dp).bounceClick { onAlbum(a) }) {
                                        Cover(a.thumbnailUrl, Modifier.size(150.dp), RoundedCornerShape(22.dp), px = 360)
                                        Spacer(Modifier.height(8.dp))
                                        Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

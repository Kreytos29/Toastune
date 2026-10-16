package app.toastune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.toastune.MainViewModel
import app.toastune.core.Album
import app.toastune.core.Track
import app.toastune.data.Playlist

private enum class Section(val label: String) { Favorites("Favoritos"), Downloads("Descargas"), History("Historial"), Albums("Álbumes"), Playlists("Playlists"), YouTube("YouTube Music") }

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    listState: LazyListState,
    bottomPad: Dp,
    onMore: (Track, Long?) -> Unit,
    onLogin: () -> Unit,
    onAlbum: (Album) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(Section.Favorites.name) }
    var openPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    val sec = Section.valueOf(section)
    val playlists by vm.playlists.collectAsState()
    val openPlaylist = playlists.firstOrNull { it.id == openPlaylistId }

    BackHandler(enabled = openPlaylist != null) { openPlaylistId = null }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        if (openPlaylist != null) {
            PlaylistDetail(vm, openPlaylist, bottomPad, onBack = { openPlaylistId = null }, onMore = onMore)
            return@Column
        }
        Text("Biblioteca", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Section.entries) { s -> GlassChip(s.label, s == sec, { section = s.name }) }
        }
        Spacer(Modifier.size(8.dp))

        when (sec) {
            Section.Favorites -> {
                val list by vm.favorites.collectAsState()
                TrackSection(
                    vm, list, listState, bottomPad, onMore,
                    Icons.Rounded.FavoriteBorder, "Aún no tienes favoritos", "Toca ⋮ en una canción y añádela a favoritos.",
                    extra = { Action(Icons.Rounded.Download, "Descargar todo") { vm.downloadAll(list) } },
                )
            }
            Section.Downloads -> {
                val list by vm.downloaded.collectAsState()
                TrackSection(
                    vm, list, listState, bottomPad, onMore,
                    Icons.Rounded.Download, "Sin descargas", "Descarga canciones para escucharlas sin conexión.",
                    extra = { Action(Icons.Rounded.Delete, "Eliminar todas") { vm.removeAllDownloads() } },
                )
            }
            Section.History -> {
                val list by vm.history.collectAsState()
                TrackSection(
                    vm, list, listState, bottomPad, onMore,
                    Icons.Rounded.History, "Historial vacío", "Las canciones que escuches aparecerán aquí.",
                    extra = { Action(Icons.Rounded.Delete, "Borrar") { vm.clearHistory() } },
                )
            }
            Section.Albums -> {
                val albums by vm.savedAlbums.collectAsState()
                if (albums.isEmpty()) {
                    EmptyState(Icons.Rounded.Album, "Sin álbumes guardados", "Busca un álbum y toca «Guardar álbum».", Modifier.padding(top = 32.dp))
                } else {
                    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottomPad)) {
                        items(albums, key = { it.id }, contentType = { "album" }) { a -> AlbumRow(a) { onAlbum(a) } }
                    }
                }
            }
            Section.Playlists -> PlaylistsSection(vm, playlists, bottomPad) { openPlaylistId = it.id }
            Section.YouTube -> YtSection(vm, bottomPad, onMore, onLogin)
        }
    }
}

@Composable
internal fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.frost(androidx.compose.foundation.shape.CircleShape).bounceClick(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TrackSection(
    vm: MainViewModel,
    list: List<Track>,
    listState: LazyListState,
    bottomPad: Dp,
    onMore: (Track, Long?) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    extra: @Composable () -> Unit,
) {
    val currentId by vm.player.currentId.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val downloads by vm.downloadStates.collectAsState()
    if (list.isEmpty()) {
        EmptyState(emptyIcon, emptyTitle, emptySubtitle, Modifier.padding(top = 32.dp))
        return
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(Icons.Rounded.PlayArrow, "Reproducir") { vm.play(list, 0) }
                Action(Icons.Rounded.Shuffle, "Aleatorio") { vm.play(list.shuffled(), 0) }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${list.size} canciones", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                extra()
            }
            Spacer(Modifier.size(4.dp))
        }
        itemsIndexed(list, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { i, t ->
            TrackRow(
                track = t,
                onClick = { vm.play(list, i) },
                onMore = { onMore(t, null) },
                isCurrent = currentId == t.id,
                isPlaying = playing,
                download = downloads[t.id],
            )
        }
    }
}

@Composable
private fun PlaylistsSection(vm: MainViewModel, playlists: List<Playlist>, bottomPad: Dp, onOpen: (Playlist) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
        item { NewPlaylistField({ vm.createPlaylist(it) }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        if (playlists.isEmpty()) {
            item { EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "Crea tu primera playlist", "Escribe un nombre arriba y añade canciones desde ⋮.") }
        }
        items(playlists, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).frost(RoundedCornerShape(22.dp)).clickable { onOpen(p) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text("${p.trackCount} canciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GlassIconButton(Icons.Rounded.Delete, "Eliminar playlist", { vm.deletePlaylist(p.id) }, size = 40.dp)
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    vm: MainViewModel,
    playlist: Playlist,
    bottomPad: Dp,
    onBack: () -> Unit,
    onMore: (Track, Long?) -> Unit,
) {
    val tracks by remember(playlist.id) { vm.playlistTracks(playlist.id) }.collectAsState(emptyList())
    val currentId by vm.player.currentId.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val downloads by vm.downloadStates.collectAsState()
    Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onBack)
        Spacer(Modifier.width(14.dp))
        Text(playlist.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
    }
    if (tracks.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "Playlist vacía", "Añade canciones desde el menú ⋮ de cualquier canción.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(Icons.Rounded.PlayArrow, "Reproducir") { vm.play(tracks, 0) }
                Action(Icons.Rounded.Shuffle, "Aleatorio") { vm.play(tracks.shuffled(), 0) }
                Action(Icons.Rounded.Download, "Descargar") { vm.downloadAll(tracks) }
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(
                track = t,
                onClick = { vm.play(tracks, i) },
                onMore = { onMore(t, playlist.id) },
                isCurrent = currentId == t.id,
                isPlaying = playing,
                download = downloads[t.id],
            )
        }
    }
}

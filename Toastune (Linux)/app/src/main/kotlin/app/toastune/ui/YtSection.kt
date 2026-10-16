package app.toastune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.toastune.MainViewModel
import app.toastune.core.Track
import app.toastune.data.YtPlaylist
import kotlin.coroutines.cancellation.CancellationException

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

@Composable
private fun <T> rememberLoad(key: Any, block: suspend () -> T): State<Load<T>> =
    produceState<Load<T>>(Load.Loading, key) {
        value = try {
            Load.Ready(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(e.message ?: "No se pudo cargar")
        }
    }

/** Biblioteca de la cuenta de YouTube Music: me gusta, historial, biblioteca y playlists. */
@Composable
fun YtSection(vm: MainViewModel, bottomPad: Dp, onMore: (Track, Long?) -> Unit, onLogin: () -> Unit) {
    val account by vm.ytAccount.collectAsState()
    var view by rememberSaveable { mutableStateOf("home") }
    var viewTitle by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = view != "home") { view = "home" }

    val acc = account
    if (acc == null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                Icons.Rounded.LibraryMusic,
                "Conecta tu YouTube Music",
                "Inicia sesión para ver las canciones que te gustan, tu historial y tus playlists, y traerlas a Toastune.",
                Modifier.padding(top = 32.dp),
            )
            GlassChip("Iniciar sesión", true, onLogin)
        }
        return
    }

    if (view == "home") {
        YtHome(vm, acc.name ?: "YouTube Music", acc.email, bottomPad) { key, title -> view = key; viewTitle = title }
    } else {
        YtTracks(vm, view, viewTitle, bottomPad, onMore) { view = "home" }
    }
}

@Composable
private fun NavTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).frost(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun YtHome(
    vm: MainViewModel,
    name: String,
    email: String?,
    bottomPad: Dp,
    open: (key: String, title: String) -> Unit,
) {
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    val playlists by rememberLoad("playlists-$refresh") { vm.ytPlaylists() }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                if (email != null) Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Cerrar sesión", false, { vm.ytLogout() })
                    GlassChip("Actualizar", false, { vm.ytRefresh(); refresh++ })
                }
            }
            NavTile(Icons.Rounded.Favorite, "Canciones que te gustan", "Tu lista de Me gusta", { open("liked", "Me gusta") })
            NavTile(Icons.Rounded.LibraryMusic, "Tu biblioteca", "Canciones guardadas", { open("library", "Biblioteca") })
            NavTile(Icons.Rounded.History, "Historial", "Lo último que escuchaste en YouTube Music", { open("history", "Historial") })
            SectionHeader("Tus playlists")
        }
        when (val p = playlists) {
            Load.Loading -> item { LoadingBox() }
            is Load.Failed -> item { EmptyState(Icons.Rounded.CloudOff, "No se pudieron cargar", p.message) }
            is Load.Ready<List<YtPlaylist>> -> items(p.value, key = { it.id }) { pl ->
                Row(
                    Modifier.fillMaxWidth().clickable { open("pl:${pl.id}", pl.title) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(pl.thumbnailUrl, Modifier.size(54.dp), px = 180)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pl.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pl.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun YtTracks(
    vm: MainViewModel,
    key: String,
    title: String,
    bottomPad: Dp,
    onMore: (Track, Long?) -> Unit,
    onBack: () -> Unit,
) {
    val load by rememberLoad(key) { vm.ytTracks(key) }
    val currentId by vm.player.currentId.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val downloads by vm.downloadStates.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onBack)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (val l = load) {
            Load.Loading -> LoadingBox()
            is Load.Failed -> EmptyState(Icons.Rounded.CloudOff, "No se pudo cargar", l.message)
            is Load.Ready<List<Track>> -> {
                val list = l.value
                if (list.isEmpty()) {
                    EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "Nada por aquí", "Esta lista está vacía.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Action(Icons.Rounded.PlayArrow, "Reproducir") { vm.play(list, 0) }
                                Action(Icons.Rounded.Shuffle, "Aleatorio") { vm.play(list.shuffled(), 0) }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when {
                                    key == "liked" || key == "library" ->
                                        Action(Icons.Rounded.Favorite, "Añadir a mis favoritos") { vm.importYtLikes(list) }
                                    key.startsWith("pl:") ->
                                        Action(Icons.Rounded.Download, "Guardar en Toastune") { vm.importYtPlaylist(title, list) }
                                }
                            }
                            Text("${list.size} canciones", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
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
            }
        }
    }
}

package app.toastune.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.toastune.MainViewModel
import app.toastune.SearchUi
import app.toastune.core.Album
import app.toastune.core.SearchFilter
import app.toastune.core.Track
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun SearchScreen(
    vm: MainViewModel,
    listState: LazyListState,
    bottomPad: Dp,
    onMore: (Track) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (app.toastune.core.Artist) -> Unit,
) {
    // El texto vive solo aquí: escribir no recompone el resto de la app.
    var text by remember { mutableStateOf(vm.query.value) }
    var editing by remember { mutableStateOf(vm.search.value is SearchUi.Idle) }
    val keyboard = LocalSoftwareKeyboardController.current
    val search by vm.search.collectAsState()
    val filter by vm.filter.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val recent by vm.recentSearches.collectAsState()
    val currentId by vm.player.currentId.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val downloads by vm.downloadStates.collectAsState()

    fun submit(q: String) {
        text = q
        editing = false
        keyboard?.hide()
        vm.submitSearch(q)
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text("Buscar", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).frost(CircleShape).padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).padding(vertical = 15.dp)) {
                if (text.isEmpty()) Text("Canciones, artistas…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; editing = true; vm.onQueryChange(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (text.isNotEmpty()) {
                GlassIconButton(Icons.Rounded.Close, "Borrar", { text = ""; editing = true; vm.onQueryChange("") }, size = 40.dp)
            }
        }
        Spacer(Modifier.size(12.dp))
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { GlassChip("Canciones", filter == SearchFilter.SONGS, { vm.setFilter(SearchFilter.SONGS) }) }
            item { GlassChip("Álbumes", filter == SearchFilter.ALBUMS, { vm.setFilter(SearchFilter.ALBUMS) }) }
            item { GlassChip("Artistas", filter == SearchFilter.ARTISTS, { vm.setFilter(SearchFilter.ARTISTS) }) }
            item { GlassChip("Videos", filter == SearchFilter.VIDEOS, { vm.setFilter(SearchFilter.VIDEOS) }) }
        }
        Spacer(Modifier.size(8.dp))

        val mode = when {
            editing && text.isBlank() -> 0 // recientes
            editing -> 1 // sugerencias
            else -> 2 // resultados
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 }) togetherWith fadeOut(tween(120))
            },
            label = "searchMode",
            modifier = Modifier.fillMaxSize(),
        ) { m ->
            when (m) {
                0 -> LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
                    if (recent.isEmpty()) {
                        item {
                            EmptyState(Icons.Rounded.Search, "Encuentra tu música", "Escribe el nombre de una canción o de un artista.", Modifier.padding(top = 32.dp))
                        }
                    } else {
                        item { SectionHeader("Recientes", action = "Borrar todo", onAction = vm::clearSearches) }
                        items(recent, key = { it }) { term ->
                            Row(
                                Modifier.fillMaxWidth().clickable { submit(term) }.padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(16.dp))
                                Text(term, Modifier.weight(1f).padding(vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
                                Box(Modifier.size(40.dp).clickable { vm.deleteSearch(term) }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Close, "Quitar", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                1 -> LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
                    items(suggestions, key = { it }) { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { submit(s) }.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text(s, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                else -> when (val r = search) {
                    SearchUi.Idle -> Box(Modifier.fillMaxSize())
                    SearchUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        CircularProgressIndicator(Modifier.padding(top = 64.dp), strokeWidth = 3.dp)
                    }
                    is SearchUi.Error -> EmptyState(Icons.Rounded.CloudOff, "No se pudo buscar", r.message, Modifier.padding(top = 32.dp))
                    is SearchUi.Albums -> {
                        if (r.albums.isEmpty()) {
                            EmptyState(Icons.Rounded.Search, "Sin álbumes", "Prueba con el nombre del álbum o del artista.", Modifier.padding(top = 32.dp))
                        } else {
                            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottomPad)) {
                                items(r.albums, key = { it.id }, contentType = { "album" }) { a ->
                                    AlbumRow(a) { onAlbum(a) }
                                }
                            }
                        }
                    }
                    is SearchUi.Artists -> {
                        if (r.artists.isEmpty()) {
                            EmptyState(Icons.Rounded.Search, "Sin artistas", "Prueba con otro nombre.", Modifier.padding(top = 32.dp))
                        } else {
                            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottomPad)) {
                                items(r.artists, key = { it.id }, contentType = { "artist" }) { a ->
                                    ArtistRow(a) { onArtist(a) }
                                }
                            }
                        }
                    }
                    is SearchUi.Results -> {
                        LaunchedEffect(listState, r.tracks.size) {
                            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                                .distinctUntilChanged()
                                .filter { it >= r.tracks.size - 4 }
                                .collect { vm.loadMore() }
                        }
                        if (r.tracks.isEmpty()) {
                            EmptyState(Icons.Rounded.Search, "Sin resultados", "Prueba con otras palabras.", Modifier.padding(top = 32.dp))
                        } else {
                            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottomPad)) {
                                items(r.tracks, key = { it.id }, contentType = { "track" }) { t ->
                                    TrackRow(
                                        track = t,
                                        onClick = { vm.play(r.tracks, r.tracks.indexOf(t)) },
                                        onMore = { onMore(t) },
                                        isCurrent = currentId == t.id,
                                        isPlaying = playing,
                                        download = downloads[t.id],
                                    )
                                }
                                if (r.loadingMore) {
                                    item(contentType = "loading") {
                                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
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
}

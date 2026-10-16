package app.toastune.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toastune.core.Album
import app.toastune.core.SearchFilter
import app.toastune.core.Track

@Composable
fun MainScreen(app: AppState) {
    val cur = app.player.queue.collectAsState().value.current
    val prefs by app.settings.data.collectAsState()
    val accent = rememberAccent(cur?.thumbnailUrl, prefs.coverColors)
    CompositionLocalProvider(LocalAccent provides accent) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Backdrop(cur?.thumbnailUrl, accent)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                Sidebar(app)
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 28.dp, vertical = 24.dp)) {
                    app.startError?.let {
                        Text(it, color = Palette.rose, modifier = Modifier.align(Alignment.TopCenter))
                    }
                    AnimatedContent(
                        app.screen,
                        transitionSpec = {
                            (fadeIn(tween(300, delayMillis = 80)) +
                                slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 24 } +
                                scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.975f)) togetherWith
                                (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.985f))
                        },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            Screen.Home -> HomeScreen(app)
                            Screen.Search -> SearchScreen(app)
                            Screen.Albums -> AlbumsScreen(app)
                            Screen.Downloads -> DownloadsScreen(app)
                            Screen.NowPlaying -> NowPlayingScreen(app)
                            Screen.Settings -> SettingsScreen(app)
                            Screen.YouTube -> YouTubeScreen(app)
                            is Screen.YtList -> TrackListScreen(
                                app, s.title, app.ytTracks, "No hay canciones aquí.", back = true, loading = app.ytLoading,
                            )
                            is Screen.ArtistView -> ArtistScreen(app, s.name)
                            Screen.History -> TrackListScreen(app, "Historial", app.library.data.collectAsState().value.recent, "Aquí verás lo último que escuchaste.")
                            Screen.Favorites -> TrackListScreen(app, "Favoritos", app.library.data.collectAsState().value.favorites, "Marca canciones con el corazón y aparecerán aquí.")
                            Screen.Playlists -> PlaylistsScreen(app)
                            Screen.Queue -> QueueScreen(app)
                            is Screen.Playlist -> {
                                val list = app.library.data.collectAsState().value.playlists[s.name].orEmpty()
                                TrackListScreen(app, s.name, list, "La lista está vacía. Añade canciones desde el menú ⋮.", playlist = s.name)
                            }
                            is Screen.AlbumView -> AlbumScreen(app, s.album)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = app.screen != Screen.NowPlaying,
                enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200)),
            ) { PlayerBar(app) }
        }
        app.newListFor?.let { NewListDialog(app, it) }
        AnimatedVisibility(
            visible = !prefs.onboarded,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 1.04f),
        ) { OnboardingScreen(app) }
    }
    }
}

/** Fondo: la carátula de la canción actual, muy difuminada, con luces del color de la portada que se mueven despacio. */
@Composable
private fun Backdrop(url: String?, accent: Color) {
    Crossfade(url, animationSpec = tween(1000), label = "backdrop") { u ->
        val bmp = rememberImage(u)
        Box(Modifier.fillMaxSize()) {
            bmp?.let {
                Image(
                    it, null,
                    Modifier.fillMaxSize().blur(100.dp, BlurredEdgeTreatment.Unbounded).alpha(0.40f),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x660A0810), Color(0xE60A0810)))))
        }
    }
    val t = rememberInfiniteTransition(label = "aurora")
    val dx by t.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse), label = "auroraX")
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * (0.12f + 0.35f * dx), size.height * 0.12f), radius = size.minDimension * 0.95f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Palette.rose.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * (0.92f - 0.35f * dx), size.height * 0.95f), radius = size.minDimension * 0.85f,
                ),
            )
        },
    )
}

// ---------- Barra lateral ----------

@Composable
private fun Sidebar(app: AppState) {
    val lib by app.library.data.collectAsState()
    val tint by animateColorAsState(LocalAccent.current, tween(900), label = "sideTint")
    Column(
        Modifier.width(236.dp).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(Color(0xF2100E1A), Color(0xF20D0B16))))
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.10f), Color.Transparent)))
            .background(Brush.radialGradient(listOf(Palette.rose.copy(alpha = 0.10f), Color.Transparent), center = Offset(60f, 1400f), radius = 700f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.padding(8.dp, 6.dp, 8.dp, 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource("icon.png"), null, Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
            Text("Toastune", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.blue)
        }
        NavItem(Icons.Filled.Home, "Inicio", app.screen == Screen.Home) { app.goTo(Screen.Home) }
        NavItem(Icons.Filled.Search, "Buscar", app.screen is Screen.Search) { app.goTo(Screen.Search) }

        SectionLabel("Tu biblioteca")
        NavItem(Icons.Filled.History, "Historial", app.screen == Screen.History) { app.goTo(Screen.History) }
        NavItem(Icons.Filled.Favorite, "Favoritos", app.screen == Screen.Favorites, lib.favorites.size) { app.goTo(Screen.Favorites) }
        NavItem(Icons.Filled.Album, "Álbumes", app.screen == Screen.Albums, lib.albums.size) { app.goTo(Screen.Albums) }
        NavItem(Icons.Filled.LibraryMusic, "Listas", app.screen == Screen.Playlists, lib.playlists.size) { app.goTo(Screen.Playlists) }
        NavItem(Icons.Filled.DownloadForOffline, "Descargas", app.screen == Screen.Downloads, lib.downloads.size) { app.goTo(Screen.Downloads) }
        NavItem(Icons.Filled.Cloud, "YouTube Music", app.screen == Screen.YouTube || app.screen is Screen.YtList) { app.goTo(Screen.YouTube) }

        if (lib.playlists.isNotEmpty()) {
            SectionLabel("Tus listas")
            lib.playlists.keys.take(6).forEach { n ->
                NavItem(Icons.Filled.QueueMusic, n, (app.screen as? Screen.Playlist)?.name == n) { app.goTo(Screen.Playlist(n)) }
            }
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = Color.White.copy(alpha = 0.12f))
        NavItem(Icons.Filled.Menu, "Cola", app.screen == Screen.Queue) { app.goTo(Screen.Queue) }
        NavItem(Icons.Filled.Settings, "Ajustes", app.screen == Screen.Settings) { app.goTo(Screen.Settings) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, color = Palette.muted.copy(alpha = 0.6f),
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(300), label = "navGlow")
    val hint = when (label) {
        "Inicio" -> "Recomendaciones y lo que escuchaste hace poco"
        "Buscar" -> "Busca canciones, álbumes y artistas"
        "Historial" -> "Canciones que escuchaste últimamente"
        "Favoritos" -> "Las canciones que marcaste con ♥"
        "Álbumes" -> "Álbumes que guardaste"
        "Listas" -> "Tus listas de reproducción"
        "Descargas" -> "Música guardada para escuchar sin conexión"
        "YouTube Music" -> "Tu cuenta, tus listas y tus Me gusta"
        "Cola" -> "Lo que suena ahora y lo que viene después"
        "Ajustes" -> "Mezcla, ecualizador, apariencia y más"
        else -> "Abrir la lista «$label»"
    }
    Tip(hint) { Row(
        Modifier.fillMaxWidth().interactive(RoundedCornerShape(14.dp), false, onClick)
            .background(Brush.horizontalGradient(listOf(LocalAccent.current.copy(alpha = 0.32f), Palette.rose.copy(alpha = 0.22f))), RoundedCornerShape(14.dp), alpha = glow)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val tint by animateColorAsState(if (selected) LocalAccent.current else Palette.muted, tween(220), label = "tint")
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label, color = if (selected) Color.White else Palette.muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (badge > 0) Text("$badge", fontSize = 12.sp, color = Palette.muted.copy(alpha = 0.7f))
    } }
}

// ---------- Búsqueda ----------

private val genreColors = listOf(
    listOf(Color(0xFFFF5C8A), Color(0xFF8E2DE2)), listOf(Color(0xFF7FA8FF), Color(0xFF3A47D5)),
    listOf(Color(0xFFFF9966), Color(0xFFFF5E62)), listOf(Color(0xFF43CEA2), Color(0xFF185A9D)),
    listOf(Color(0xFFF7971E), Color(0xFFD4145A)), listOf(Color(0xFF5EE7DF), Color(0xFFB490CA)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(app: AppState) {
    val lib by app.library.data.collectAsState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = app.query, onValueChange = { app.onQuery(it) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { app.typing = it.isFocused }, singleLine = true, shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Canciones, álbumes, artistas…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (app.query.isNotEmpty()) AnimIconButton(onClick = { app.onQuery("") }, tooltip = "Borrar búsqueda") { Icon(Icons.Filled.Close, "Borrar") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { app.search() }),
        )
        AnimatedVisibility(
            visible = app.suggestions.isNotEmpty(),
            enter = fadeIn(tween(160)) + expandVertically(tween(220)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(160)),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).padding(6.dp)) {
                app.suggestions.forEach { sug ->
                    Row(
                        Modifier.fillMaxWidth().interactive(RoundedCornerShape(10.dp)) { app.search(sug) }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Palette.muted, modifier = Modifier.size(18.dp))
                        Text(sug, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (!app.searched) {
            if (lib.searches.isNotEmpty()) {
                Text("Búsquedas recientes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lib.searches, key = { it }) { q ->
                        InputChip(
                            selected = false, onClick = { app.search(q) }, label = { Text(q) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Quitar", Modifier.size(16.dp).clickable { app.library.removeSearch(q) }) },
                        )
                    }
                }
            }
            Text("Explorar géneros", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            LazyVerticalGrid(
                GridCells.Adaptive(170.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(GENRES) { i, g ->
                    GenreTile(g, genreColors[i % genreColors.size], Modifier.enter(i)) { app.search(Taste.genre(g)?.query ?: "$g mix") }
                }
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SearchFilter.SONGS to "Canciones", SearchFilter.ALBUMS to "Álbumes",
                SearchFilter.ARTISTS to "Artistas", SearchFilter.VIDEOS to "Videos",
            ).forEach { (f, label) ->
                FilterChip(selected = app.filter == f, onClick = { app.search(f = f) }, label = { Text(label) })
            }
        }
        if (app.searching) SkeletonRows(9)
        app.searchError?.let { Text(it, color = Palette.rose) }
        when (app.filter) {
            SearchFilter.ALBUMS -> SmoothList {
                itemsIndexed(app.albums, key = { _, a -> a.id }) { i, a ->
                    ItemRow(a.thumbnailUrl, a.title, "Álbum · ${a.artist}", Modifier.enter(i)) { app.openAlbum(a) }
                }
            }
            SearchFilter.ARTISTS -> SmoothList {
                itemsIndexed(app.artists, key = { _, a -> a.id }) { i, a ->
                    ItemRow(a.thumbnailUrl, a.name, "Artista", Modifier.enter(i), round = true) { app.openArtist(a.name, a.id) }
                }
            }
            else -> SmoothList {
                itemsIndexed(app.tracks, key = { _, t -> t.id }) { i, t ->
                    TrackRow(app, t, modifier = Modifier.enter(i).animateItem()) { app.player.playList(app.tracks, i) }
                }
                if (app.canLoadMore) item {
                    TextButton(onClick = { app.loadMore() }, modifier = Modifier.padding(8.dp)) { Text("Más resultados") }
                }
            }
        }
    }
}

@Composable
private fun GenreTile(name: String, colors: List<Color>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.04f else 1f, spring(0.6f, 400f), label = "genre")
    Box(
        modifier.height(92.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors))
            .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Icon(
            Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.BottomEnd).size(44.dp).graphicsLayer { rotationZ = -15f + 15f * (scale - 1f) * 25f },
        )
    }
}

@Composable
fun ItemRow(thumb: String?, title: String, subtitle: String, modifier: Modifier = Modifier, round: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().interactive(RoundedCornerShape(12.dp), onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(thumb, 56.dp, if (round) 28.dp else 10.dp)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, fontSize = 13.sp)
        }
    }
}

// ---------- Filas de canciones ----------

@Composable
fun TrackRow(app: AppState, t: Track, playlist: String? = null, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null, onPlay: () -> Unit) {
    val lib by app.library.data.collectAsState()
    val fav = lib.favorites.any { it.id == t.id }
    val current = app.player.queue.collectAsState().value.current?.id == t.id
    val have by app.downloads.have.collectAsState()
    val dl by app.downloads.tasks.collectAsState()
    val task = dl.firstOrNull { it.track.id == t.id }
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().interactive(RoundedCornerShape(12.dp), selected = current, onClick = onPlay).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Cover(t.thumbnailUrl, 48.dp)
            if (current) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    EqBars(app.mpv.state.collectAsState().value.playing)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (current) Palette.rose else Color.White)
            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, fontSize = 13.sp)
        }
        if (task != null) DownloadIndicator(task, Modifier.size(18.dp))
        else if (t.id in have) Icon(Icons.Filled.DownloadDone, "Descargada", tint = Palette.blue, modifier = Modifier.size(18.dp))
        t.durationMs?.let { Text(fmt(it), color = Palette.muted, fontSize = 13.sp) }
        if (onRemove != null) {
            AnimIconButton(onClick = onRemove, tooltip = "Quitar de la lista") { Icon(Icons.Filled.RemoveCircle, "Quitar", tint = Color(0xFFFF5C6C)) }
        }
        val heartScale by animateFloatAsState(if (fav) 1.1f else 0.9f, spring(dampingRatio = 0.3f, stiffness = 400f), label = "heart")
        val heartTint by animateColorAsState(if (fav) Palette.rose else Palette.muted, tween(200), label = "heartTint")
        AnimIconButton(onClick = { app.toggleFavorite(t) }, tooltip = if (fav) "Quitar de favoritos" else "Añadir a favoritos") {
            Icon(
                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorito", tint = heartTint,
                modifier = Modifier.scale(heartScale),
            )
        }
        Box {
            AnimIconButton(onClick = { menu = true }, tooltip = "Más opciones") { Icon(Icons.Filled.MoreVert, "Más", tint = Palette.muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Reproducir a continuación") }, leadingIcon = { Icon(Icons.Filled.PlaylistPlay, null) }, onClick = { app.player.playNext(t); menu = false })
                DropdownMenuItem(text = { Text("Añadir a la cola") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { app.player.enqueue(t); menu = false })
                DropdownMenuItem(text = { Text("Iniciar radio") }, leadingIcon = { Icon(Icons.Filled.Radio, null) }, onClick = { app.startRadio(t); menu = false })
                DropdownMenuItem(text = { Text("Ir al artista") }, leadingIcon = { Icon(Icons.Filled.Person, null) }, onClick = { app.openArtist(t.artist.split(",", "&").first().trim()); menu = false })
                DropdownMenuItem(text = { Text("Copiar enlace") }, leadingIcon = { Icon(Icons.Filled.Link, null) }, onClick = {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        java.awt.datatransfer.StringSelection("https://music.youtube.com/watch?v=${t.id}"), null,
                    )
                    menu = false
                })
                if (t.id in have) DropdownMenuItem(text = { Text("Quitar descarga") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { app.downloads.remove(t.id); menu = false })
                else DropdownMenuItem(text = { Text("Descargar") }, leadingIcon = { Icon(Icons.Filled.Download, null) }, onClick = { app.downloads.download(t); menu = false })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Nueva lista…") }, leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) }, onClick = { app.newListFor = t; menu = false })
                lib.playlists.keys.forEach { name ->
                    DropdownMenuItem(text = { Text("Añadir a «$name»") }, leadingIcon = { Icon(Icons.Filled.LibraryAdd, null) }, onClick = { app.library.addToPlaylist(name, t); menu = false })
                }
                if (app.ytStore.state.collectAsState().value != null) app.ytPlaylists.take(6).forEach { pl ->
                    DropdownMenuItem(text = { Text("Añadir a «${pl.title}» (YouTube)") }, leadingIcon = { Icon(Icons.Filled.Cloud, null) }, onClick = { app.addToYtPlaylist(pl.id, t); menu = false })
                }
                if (playlist != null) {
                    DropdownMenuItem(text = { Text("Quitar de la lista") }, leadingIcon = { Icon(Icons.Filled.RemoveCircleOutline, null) }, onClick = { app.library.removeFromPlaylist(playlist, t.id); menu = false })
                }
            }
        }
    }
}

// ---------- Cabecera de colecciones ----------

@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    covers: List<String?>,
    onBack: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    extra: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.enter(0)) {
        if (onBack != null) AnimIconButton(onClick = onBack, tooltip = "Volver") { Icon(Icons.Filled.ArrowBack, "Atrás") }
        Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            val distinct = covers.filterNotNull().distinct()
            if (distinct.size >= 4) {
                Column(Modifier.clip(RoundedCornerShape(20.dp))) {
                    for (r in 0..1) Row { for (c in 0..1) Cover(distinct[r * 2 + c], 88.dp, 0.dp) }
                }
            } else Cover(distinct.firstOrNull(), 176.dp, 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Palette.muted)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onPlay != null) Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(containerColor = Palette.rose, contentColor = Color.White)) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Reproducir")
                    }
                    if (onShuffle != null) OutlinedButton(onClick = onShuffle) {
                        Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Aleatorio")
                    }
                    extra()
                }
            }
        }
    }
}

/** Duración total; las canciones sin duración se estiman con el promedio de las demás. */
fun totalMs(tracks: List<Track>): Pair<Long, Boolean> {
    val known = tracks.mapNotNull { it.durationMs }.filter { it > 0 }
    val missing = tracks.size - known.size
    val avg = if (known.isNotEmpty()) known.average().toLong() else 210_000L
    return (known.sum() + missing * avg) to (missing > 0)
}

fun fmtTotal(ms: Long): String {
    val m = ms / 60_000
    return when {
        m >= 60 -> "${m / 60} h ${m % 60} min"
        m >= 1 -> "$m min"
        else -> "${ms / 1000} s"
    }
}

fun summary(tracks: List<Track>): String {
    val n = tracks.size
    val (ms, est) = totalMs(tracks)
    return "$n ${if (n == 1) "canción" else "canciones"}" + if (n > 0) " · " + (if (est) "≈ " else "") + fmtTotal(ms) else ""
}

@Composable
private fun TrackListScreen(app: AppState, title: String, tracks: List<Track>, empty: String, playlist: String? = null, back: Boolean = false, loading: Boolean = false) {
    var editing by remember(playlist) { mutableStateOf(false) }
    val cur = app.player.queue.collectAsState().value.current
    val curIdx = tracks.indexOfFirst { it.id == cur?.id }
    val remainingText = if (curIdx >= 0) " · quedan " + fmtTotal(totalMs(tracks.drop(curIdx + 1)).first) else ""
    Column(Modifier.fillMaxSize()) {
        CollectionHeader(
            title, summary(tracks) + remainingText, tracks.map { it.thumbnailUrl },
            onBack = if (playlist != null || back) ({ app.back() }) else null,
            onPlay = if (tracks.isNotEmpty()) ({ app.player.playList(tracks) }) else null,
            onShuffle = if (tracks.isNotEmpty()) ({ app.player.playList(tracks.shuffled()) }) else null,
        ) {
            if (playlist != null && tracks.isNotEmpty()) OutlinedButton(onClick = { editing = !editing }) {
                Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text(if (editing) "Listo" else "Editar")
            }
        }
        if (loading) SkeletonRows(8)
        else if (tracks.isEmpty()) Text(empty, color = Palette.muted, modifier = Modifier.padding(top = 12.dp))
        SmoothList {
            itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
                TrackRow(
                    app, t, playlist, Modifier.enter(i).animateItem(),
                    onRemove = if (editing && playlist != null) ({ app.library.removeFromPlaylist(playlist, t.id) }) else null,
                ) { app.player.playList(tracks, i) }
            }
        }
    }
}

@Composable
fun AlbumScreen(app: AppState, album: Album) {
    val saved = app.library.data.collectAsState().value.albums.any { it.id == album.id }
    Column(Modifier.fillMaxSize()) {
        CollectionHeader(
            album.title, album.artist + if (app.albumTracks.isNotEmpty()) " · " + summary(app.albumTracks) else "",
            listOf(album.thumbnailUrl),
            onBack = { app.back() },
            onPlay = if (app.albumTracks.isNotEmpty()) ({ app.player.playList(app.albumTracks) }) else null,
            onShuffle = if (app.albumTracks.isNotEmpty()) ({ app.player.playList(app.albumTracks.shuffled()) }) else null,
        ) {
            if (app.albumTracks.isNotEmpty()) {
                OutlinedButton(onClick = { app.toggleAlbum(album) }) {
                    Icon(if (saved) Icons.Filled.Check else Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(if (saved) "Guardado" else "Guardar")
                }
                OutlinedButton(onClick = { app.downloads.downloadAll(app.albumTracks) }) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Descargar")
                }
            }
        }
        if (app.albumLoading) SkeletonRows(7)
        SmoothList {
            itemsIndexed(app.albumTracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
                TrackRow(app, t, modifier = Modifier.enter(i).animateItem()) { app.player.playList(app.albumTracks, i) }
            }
        }
    }
}

// ---------- Listas y cola ----------

@Composable
private fun PlaylistsScreen(app: AppState) {
    val lib by app.library.data.collectAsState()
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Listas", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Nueva lista") }, shape = RoundedCornerShape(24.dp), modifier = Modifier.onFocusChanged { app.typing = it.isFocused })
            Button(onClick = { app.library.createPlaylist(name.trim()); name = "" }, enabled = name.isNotBlank()) { Text("Crear") }
        }
        if (lib.playlists.isEmpty()) Text("Aún no tienes listas.", color = Palette.muted)
        SmoothList {
            itemsIndexed(lib.playlists.keys.toList(), key = { _, k -> k }) { i, n ->
                val list = lib.playlists[n].orEmpty()
                Row(
                    Modifier.enter(i).animateItem().fillMaxWidth().interactive(RoundedCornerShape(12.dp)) { app.open(Screen.Playlist(n)) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Cover(list.firstOrNull()?.thumbnailUrl, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(n, fontWeight = FontWeight.Medium)
                        Text(summary(list), color = Palette.muted, fontSize = 13.sp)
                    }
                    AnimIconButton(onClick = { app.library.deletePlaylist(n) }, tooltip = "Borrar esta lista") { Icon(Icons.Filled.Delete, "Borrar", tint = Palette.muted) }
                }
            }
        }
    }
}

@Composable
fun QueueList(app: AppState, modifier: Modifier = Modifier) {
    val q by app.player.queue.collectAsState()
    val stride = with(LocalDensity.current) { 66.dp.toPx() }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val last = q.queue.lastIndex
    val target = if (dragFrom >= 0) (dragFrom + (dragDy / stride).roundToInt()).coerceIn(0, maxOf(last, 0)) else -1
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (q.queue.isEmpty()) "Sin canciones" else "${q.queue.size} en cola · arrastra ≡ para ordenar",
                color = Palette.muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            Tip("Desordena solo las canciones que vienen después de la actual") { TextButton(onClick = { app.player.shuffleUpcoming() }, enabled = q.queue.size > 2) { Text("Barajar") } }
            Tip("Vacía la cola y detiene la música") { TextButton(onClick = { app.player.clear() }, enabled = q.queue.isNotEmpty()) { Text("Limpiar") } }
        }
        SmoothList(Modifier.fillMaxSize()) {
            itemsIndexed(q.queue, key = { i, t -> "$i-${t.id}" }) { i, t ->
                val shift by animateFloatAsState(
                    when {
                        dragFrom < 0 || i == dragFrom -> 0f
                        dragFrom < target && i in (dragFrom + 1)..target -> -stride
                        target < dragFrom && i in target until dragFrom -> stride
                        else -> 0f
                    },
                    spring(dampingRatio = 0.8f, stiffness = 600f), label = "qShift",
                )
                QueueRow(
                    app, t, i, current = i == q.index, dragging = i == dragFrom,
                    modifier = Modifier.zIndex(if (i == dragFrom) 1f else 0f)
                        .graphicsLayer { translationY = if (i == dragFrom) dragDy else shift },
                    onDragStart = { dragFrom = i; dragDy = 0f },
                    onDrag = { dragDy += it },
                    onDragEnd = {
                        if (dragFrom >= 0 && target >= 0 && target != dragFrom) app.player.move(dragFrom, target)
                        dragFrom = -1; dragDy = 0f
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    app: AppState, t: Track, index: Int, current: Boolean, dragging: Boolean, modifier: Modifier,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
) {
    val startCb by rememberUpdatedState(onDragStart)
    val dragCb by rememberUpdatedState(onDrag)
    val endCb by rememberUpdatedState(onDragEnd)
    val lift by animateFloatAsState(if (dragging) 1.02f else 1f, spring(0.6f, 500f), label = "lift")
    val bg by animateColorAsState(if (dragging) Color.White.copy(alpha = 0.16f) else Color.Transparent, tween(150), label = "qbg")
    Row(
        modifier.fillMaxWidth().height(64.dp)
            .graphicsLayer { scaleX = lift; scaleY = lift; shadowElevation = if (dragging) 24f else 0f; shape = RoundedCornerShape(12.dp) }
            .clip(RoundedCornerShape(12.dp)).background(bg)
            .interactive(RoundedCornerShape(12.dp), selected = current && !dragging) { app.player.jumpTo(index) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tip("Mantén pulsado y arrastra para cambiar el orden") { Icon(
            Icons.Filled.DragHandle, "Arrastrar para ordenar", tint = Palette.muted,
            modifier = Modifier.padding(horizontal = 6.dp).size(24.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startCb() },
                        onDrag = { change, d -> change.consume(); dragCb(d.y) },
                        onDragEnd = { endCb() },
                        onDragCancel = { endCb() },
                    )
                },
        ) }
        Box(contentAlignment = Alignment.Center) {
            Cover(t.thumbnailUrl, 46.dp)
            if (current) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    EqBars(app.mpv.state.collectAsState().value.playing)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (current) Palette.rose else Color.White)
            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, fontSize = 13.sp)
        }
        t.durationMs?.let { Text(fmt(it), color = Palette.muted, fontSize = 13.sp) }
        AnimIconButton(onClick = { app.player.removeAt(index) }, tooltip = "Quitar de la cola") { Icon(Icons.Filled.Close, "Quitar de la cola", tint = Palette.muted) }
    }
}

@Composable
private fun QueueScreen(app: AppState) {
    val q by app.player.queue.collectAsState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cola", fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (q.queue.isNotEmpty()) OutlinedButton(onClick = {
                val name = "Cola " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                app.library.createPlaylist(name)
                q.queue.forEach { app.library.addToPlaylist(name, it) }
            }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("Guardar como lista") }
        }
        if (q.queue.isEmpty()) Text("No hay nada en la cola.", color = Palette.muted)
        QueueList(app)
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun DownloadIndicator(task: DlTask, modifier: Modifier = Modifier) {
    when {
        task.state == DlState.FAILED -> Icon(Icons.Filled.ErrorOutline, "Falló", tint = Palette.rose, modifier = modifier)
        task.state == DlState.ACTIVE && task.progress > 0f ->
            CircularProgressIndicator(progress = { task.progress }, modifier = modifier, strokeWidth = 2.dp)
        else -> CircularProgressIndicator(modifier = modifier, strokeWidth = 2.dp)
    }
}

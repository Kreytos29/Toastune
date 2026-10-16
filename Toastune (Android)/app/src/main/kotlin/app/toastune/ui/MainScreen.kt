package app.toastune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.toastune.MainViewModel
import app.toastune.core.Album
import app.toastune.core.Artist
import app.toastune.core.Track
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private enum class Sheet { None, Options, Playlist, Queue, Sleep }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val hazeState = remember { HazeState() }
    val player by vm.player.state.collectAsState()
    val appSettings by vm.settings.collectAsState()
    val glassOn = appSettings.glass
    val account by vm.ytAccount.collectAsState()
    val track = player.track

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var loginOpen by rememberSaveable { mutableStateOf(false) }
    var album by remember { mutableStateOf<Album?>(null) } // se conserva mientras se cierra
    var albumOpen by remember { mutableStateOf(false) }
    fun openAlbum(a: Album) { album = a; albumOpen = true }
    var artist by remember { mutableStateOf<Artist?>(null) }
    var artistOpen by remember { mutableStateOf(false) }
    fun openArtist(a: Artist) { artist = a; artistOpen = true }

    var sheet by remember { mutableStateOf(Sheet.None) }
    var shown by remember { mutableStateOf(Sheet.None) } // se conserva mientras la hoja se cierra
    var optionsTrack by remember { mutableStateOf<Track?>(null) }
    var optionsPlaylist by remember { mutableStateOf<Long?>(null) }

    fun open(s: Sheet) { shown = s; sheet = s }
    fun close() { sheet = Sheet.None }

    val tab = Tab.entries[tabIndex]
    val accent = rememberAccent(track?.thumbnailUrl, MaterialTheme.colorScheme.primary)

    val homeList = rememberLazyListState()
    val searchList = rememberLazyListState()
    val libraryList = rememberLazyListState()

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad = navInset + 8.dp + 66.dp + (if (track != null) 76.dp else 0.dp) + 20.dp

    BackHandler(enabled = expanded) { expanded = false }
    BackHandler(enabled = settingsOpen && !expanded) { settingsOpen = false }
    BackHandler(enabled = artistOpen && !expanded) { artistOpen = false }
    BackHandler(enabled = albumOpen && !expanded) { albumOpen = false }
    BackHandler(enabled = loginOpen) { loginOpen = false }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this, LocalHazeState provides hazeState) {
            Box(Modifier.fillMaxSize()) {
                // Todo lo que queda detrás del dock es la "fuente" del desenfoque.
                Box(Modifier.fillMaxSize().then(if (glassOn) Modifier.hazeSource(hazeState) else Modifier)) {
                    AuroraBackground(accent, intensity = 0.7f)
                    AnimatedContent(
                        targetState = if (settingsOpen) -1 else tabIndex,
                        // Solo aparece la nueva; la anterior desaparece al instante (menos trabajo por fotograma).
                        transitionSpec = { fadeIn(tween(160)) togetherWith ExitTransition.None },
                        label = "screen",
                    ) { target ->
                        when (target) {
                            -1 -> SettingsScreen(vm, bottomPad, onLogin = { loginOpen = true }, onBack = { settingsOpen = false })
                            0 -> HomeScreen(
                                vm, homeList, bottomPad,
                                onOpenSettings = { settingsOpen = true },
                                onLogin = { loginOpen = true },
                                onMore = { optionsTrack = it; optionsPlaylist = null; open(Sheet.Options) },
                            )
                            1 -> SearchScreen(
                                vm, searchList, bottomPad,
                                onMore = { optionsTrack = it; optionsPlaylist = null; open(Sheet.Options) },
                                onAlbum = ::openAlbum,
                                onArtist = ::openArtist,
                            )
                            else -> LibraryScreen(
                                vm, libraryList, bottomPad,
                                onMore = { t, pid -> optionsTrack = t; optionsPlaylist = pid; open(Sheet.Options) },
                                onLogin = { loginOpen = true },
                                onAlbum = ::openAlbum,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = artistOpen,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it / 14 },
                    exit = fadeOut(tween(150)),
                ) {
                    artist?.let { a ->
                        ArtistScreen(
                            vm = vm,
                            artist = a,
                            accent = accent,
                            bottomPad = bottomPad,
                            onBack = { artistOpen = false },
                            onAlbum = ::openAlbum,
                            onMore = { optionsTrack = it; optionsPlaylist = null; open(Sheet.Options) },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = albumOpen,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it / 14 },
                    exit = fadeOut(tween(150)),
                ) {
                    album?.let { a ->
                        AlbumScreen(
                            vm = vm,
                            album = a,
                            accent = accent,
                            bottomPad = bottomPad,
                            onBack = { albumOpen = false },
                            onMore = { optionsTrack = it; optionsPlaylist = null; open(Sheet.Options) },
                        )
                    }
                }

                Dock(
                    player = vm.player,
                    state = player,
                    accent = accent,
                    tab = tab,
                    expanded = expanded,
                    onTab = { settingsOpen = false; albumOpen = false; artistOpen = false; tabIndex = it.ordinal },
                    onOpenPlayer = { expanded = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = navInset + 8.dp),
                )

                AnimatedVisibility(
                    visible = expanded && track != null,
                    enter = fadeIn(tween(220)) + slideInVertically(tween(320)) { it / 10 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 10 },
                ) {
                    CompositionLocalProvider(LocalVisScope provides this) {
                        NowPlayingScreen(
                            vm = vm,
                            state = player,
                            accent = accent,
                            onClose = { expanded = false },
                            onQueue = { open(Sheet.Queue) },
                            onSleep = { open(Sheet.Sleep) },
                            onMore = {
                                optionsTrack = track
                                optionsPlaylist = null
                                open(Sheet.Options)
                            },
                        )
                    }
                }

                AnimatedVisibility(visible = !appSettings.onboarded, enter = fadeIn(tween(200)), exit = fadeOut(tween(250))) {
                    OnboardingScreen(
                        initialGenres = appSettings.genres,
                        initialArtists = appSettings.artists,
                        accountName = account?.name,
                        onLogin = { loginOpen = true },
                        onFinish = { g, a -> vm.finishOnboarding(g, a) },
                    )
                }

                AnimatedVisibility(visible = loginOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
                    YtLoginScreen(
                        onClose = { loginOpen = false },
                        onCaptured = { cookie, visitor, user ->
                            loginOpen = false
                            if (appSettings.onboarded) { tabIndex = 2; settingsOpen = false }
                            vm.onYtLogin(cookie, visitor, user)
                        },
                    )
                }

                SheetHost(visible = sheet != Sheet.None, onDismiss = ::close) {
                    SheetBody(vm, shown, optionsTrack, optionsPlaylist, onNavigate = ::open, onDismiss = ::close)
                }
            }
        }
    }
}

@Composable
private fun SheetBody(
    vm: MainViewModel,
    sheet: Sheet,
    track: Track?,
    playlistContext: Long?,
    onNavigate: (Sheet) -> Unit,
    onDismiss: () -> Unit,
) {
    val player = vm.player
    val state by player.state.collectAsState()
    when (sheet) {
        Sheet.Options -> if (track != null) {
            val favoriteIds by vm.favoriteIds.collectAsState()
            val downloads by vm.downloadStates.collectAsState()
            TrackOptionsContent(
                track = track,
                isFavorite = track.id in favoriteIds,
                download = downloads[track.id],
                onPlayNext = { player.playNext(track) },
                onAddToQueue = { player.addToQueue(track) },
                onToggleFavorite = { vm.toggleFavorite(track) },
                onDownload = { vm.download(track) },
                onRemoveDownload = { vm.removeDownload(track.id) },
                onAddToPlaylist = { onNavigate(Sheet.Playlist) },
                onRemoveFromPlaylist = playlistContext?.let { pid -> { vm.removeFromPlaylist(pid, track.id) } },
                onDismiss = onDismiss,
            )
        }
        Sheet.Playlist -> if (track != null) {
            val playlists by vm.playlists.collectAsState()
            val account by vm.ytAccount.collectAsState()
            val ytPlaylists by androidx.compose.runtime.produceState(emptyList<app.toastune.data.YtPlaylist>(), account) {
                value = if (account == null) emptyList() else runCatching { vm.ytPlaylists() }.getOrDefault(emptyList())
            }
            PlaylistPickerContent(
                playlists = playlists,
                onPick = { vm.addToPlaylist(it.id, track); onDismiss() },
                onCreate = {
                    vm.createPlaylist(it, track)
                    if (account != null) vm.createYtPlaylist(it, track)
                    onDismiss()
                },
                ytPlaylists = ytPlaylists,
                onPickYt = { vm.addToYtPlaylist(it, track); onDismiss() },
            )
        }
        Sheet.Queue -> QueueContent(
            queue = state.queue,
            currentIndex = state.queueIndex,
            isPlaying = state.isPlaying,
            onPlay = { player.playQueueItem(it) },
            onRemove = { player.removeQueueItem(it) },
        )
        Sheet.Sleep -> SleepTimerContent(
            active = state.sleepTimerEndsAt != null,
            onPick = { player.setSleepTimer(it); onDismiss() },
        )
        Sheet.None -> Unit
    }
}

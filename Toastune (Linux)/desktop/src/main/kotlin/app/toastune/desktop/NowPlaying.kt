package app.toastune.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toastune.core.Lyrics
import app.toastune.core.Track

// ---------- Piezas compartidas ----------

@Composable
fun ProgressRow(app: AppState, modifier: Modifier = Modifier) {
    val st by app.mpv.state.collectAsState()
    val track = app.player.queue.collectAsState().value.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = if (st.durationMs > 0) st.durationMs else (track?.durationMs ?: 0L)
    val frac = if (dur > 0) (st.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(fmt(((dragging ?: frac) * dur).toLong()), fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.widthIn(min = 38.dp))
        Box(Modifier.weight(1f)) { SlimSlider(
            value = dragging ?: frac,
            onChange = { dragging = it },
            onFinish = { dragging?.let { app.player.seekTo((it * dur).toLong()) }; dragging = null },
            enabled = track != null && dur > 0,
            modifier = Modifier.fillMaxWidth(),
        ) }
        Text(fmt(dur), fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.widthIn(min = 38.dp))
    }
}

@Composable
fun TransportControls(app: AppState, big: Boolean = false) {
    val q by app.player.queue.collectAsState()
    val st by app.mpv.state.collectAsState()
    val btn = if (big) 76.dp else 48.dp
    val icon = if (big) 40.dp else 28.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 14.dp else 6.dp)) {
        AnimIconButton(onClick = { app.player.toggleShuffle() }, tooltip = "Aleatorio: reproduce en orden al azar") {
            val c by animateColorAsState(if (q.shuffle) Palette.rose else Palette.muted, tween(200), label = "sh")
            Icon(Icons.Filled.Shuffle, "Aleatorio", tint = c)
        }
        AnimIconButton(onClick = { app.player.previous() }, tooltip = "Anterior") { Icon(Icons.Filled.SkipPrevious, "Anterior", modifier = Modifier.size(if (big) 34.dp else 24.dp)) }
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.5f, 500f), label = "playScale")
        FilledIconButton(
            onClick = { app.player.togglePlay() }, enabled = q.current != null, interactionSource = src,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (big) LocalAccent.current else Palette.rose, contentColor = Color.White),
            modifier = Modifier.size(btn).graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Crossfade(if (st.loading) 2 else if (st.playing) 1 else 0, animationSpec = tween(180), label = "play") { mode ->
                if (mode == 2) CircularProgressIndicator(Modifier.size(icon * 0.75f), color = Color.White, strokeWidth = 2.5.dp)
                else Icon(if (mode == 1) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir/Pausar", modifier = Modifier.size(icon))
            }
        }
        AnimIconButton(onClick = { app.player.next() }, tooltip = "Siguiente") { Icon(Icons.Filled.SkipNext, "Siguiente", modifier = Modifier.size(if (big) 34.dp else 24.dp)) }
        AnimIconButton(onClick = { app.player.cycleRepeat() }, tooltip = "Repetir: toda la cola / una canción / nada") {
            val c by animateColorAsState(if (q.repeat == Repeat.OFF) Palette.muted else Palette.rose, tween(200), label = "rp")
            Icon(if (q.repeat == Repeat.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repetir", tint = c)
        }
    }
}

@Composable
fun VolumeControl(app: AppState, width: Dp = 120.dp) {
    val vol = app.settings.data.collectAsState().value.volume
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimIconButton(onClick = { app.setVolume(if (vol == 0) 80 else 0) }, tooltip = if (vol == 0) "Activar sonido" else "Silenciar") {
            Icon(if (vol == 0) Icons.Filled.VolumeOff else if (vol < 50) Icons.Filled.VolumeDown else Icons.Filled.VolumeUp, "Volumen", tint = Palette.muted)
        }
        SlimSlider(value = vol / 100f, onChange = { app.setVolume((it * 100).toInt()) }, modifier = Modifier.width(width))
    }
}

@Composable
fun SleepButton(app: AppState) {
    var menu by remember { mutableStateOf(false) }
    val active = app.sleepEndsAt != null
    Box {
        AnimIconButton(onClick = { menu = true }, tooltip = "Temporizador: pausa la música más tarde") {
            val c by animateColorAsState(if (active) Palette.rose else Palette.muted, tween(200), label = "sleep")
            Icon(Icons.Filled.Bedtime, "Temporizador", tint = c)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(15, 30, 45, 60).forEach { m ->
                DropdownMenuItem(text = { Text("Pausar en $m min") }, onClick = { app.setSleep(m); menu = false })
            }
            if (active) DropdownMenuItem(text = { Text("Cancelar temporizador") }, onClick = { app.setSleep(null); menu = false })
        }
    }
}

@Composable
fun FavButton(app: AppState, track: Track?) {
    val fav = track != null && app.library.data.collectAsState().value.favorites.any { it.id == track.id }
    val scale by animateFloatAsState(if (fav) 1.12f else 0.92f, spring(dampingRatio = 0.3f, stiffness = 400f), label = "fav")
    val tint by animateColorAsState(if (fav) Palette.rose else Palette.muted, tween(200), label = "favTint")
    AnimIconButton(onClick = { track?.let { app.toggleFavorite(it) } }, enabled = track != null, tooltip = if (fav) "Quitar de favoritos" else "Añadir a favoritos") {
        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorito", tint = tint, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
    }
}

// ---------- Barra inferior ----------

@Composable
fun PlayerBar(app: AppState) {
    val q by app.player.queue.collectAsState()
    val track = q.current
    Surface(color = Palette.surface.copy(alpha = 0.9f), tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Izquierda: pulsar abre la pantalla de la canción
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f, fill = false).interactive(RoundedCornerShape(14.dp)) { if (track != null) app.openNowPlaying() }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Cover(track?.thumbnailUrl, 58.dp, 12.dp)
                    AnimatedContent(
                        track,
                        transitionSpec = {
                            (fadeIn(tween(280, 90)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                                (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 3 })
                        },
                        label = "title",
                    ) { tr ->
                        Column(Modifier.widthIn(max = 260.dp)) {
                            Text(tr?.title ?: "Nada sonando", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(tr?.artist ?: "Elige una canción", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, fontSize = 13.sp)
                        }
                    }
                }
                FavButton(app, track)
            }
            // Centro: controles y progreso
            Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                TransportControls(app)
                ProgressRow(app, Modifier.fillMaxWidth().widthIn(max = 640.dp))
            }
            // Derecha
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                MixPill(app)
                MixButton(app)
                SleepButton(app)
                AnimIconButton(onClick = { app.openNowPlaying(0) }, tooltip = "Ver la letra en pantalla completa") { Icon(Icons.Filled.Subject, "Letra", tint = Palette.muted) }
                AnimIconButton(onClick = { app.goTo(Screen.Queue) }, tooltip = "Ver y ordenar la cola") { Icon(Icons.Filled.QueueMusic, "Cola", tint = Palette.muted) }
                VolumeControl(app, 110.dp)
            }
        }
    }
}

// ---------- Pantalla de la canción ----------

@Composable
fun NowPlayingScreen(app: AppState) {
    val q by app.player.queue.collectAsState()
    val st by app.mpv.state.collectAsState()
    val fade by app.mpv.fadeProgress.collectAsState()
    val cf = app.settings.data.collectAsState().value.crossfade
    val track = q.current
    val lib by app.library.data.collectAsState()
    val accent = LocalAccent.current

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimIconButton(onClick = { app.back() }, tooltip = "Cerrar y volver") { Icon(Icons.Filled.KeyboardArrowDown, "Cerrar", modifier = Modifier.size(32.dp)) }
            Text("Reproduciendo", color = Palette.muted, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            MixPill(app)
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            // Izquierda: portada (flexible) + información y controles (alto fijo)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val cover = minOf(maxWidth - 48.dp, maxHeight - 24.dp).coerceIn(110.dp, 460.dp)
                    val scale by animateFloatAsState(if (st.playing) 1f else 0.92f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "npCover")
                    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
                        // Resplandor circular con el color de la portada (sin recuadro)
                        Box(
                            Modifier.requiredSize(cover * 1.7f).drawBehind {
                                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.50f), accent.copy(alpha = 0.16f), Color.Transparent)))
                            },
                        )
                        Box(Modifier.shadow(36.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent)) {
                            // Con mezcla, la portada se funde durante el mismo tiempo que el audio
                            Crossfade(track?.thumbnailUrl, animationSpec = tween(if (fade >= 0f && cf > 0) cf * 1000 else 500), label = "npImg") { u -> Cover(u, cover, 28.dp) }
                        }
                    }
                }
                AnimatedContent(
                    track,
                    transitionSpec = { (fadeIn(tween(300, 100)) + slideInVertically(tween(340)) { it / 4 }) togetherWith fadeOut(tween(120)) },
                    label = "npTitle",
                ) { tr ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr?.title ?: "Nada sonando", fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 28.sp)
                        Tip("Ir a la página del artista") {
                            Text(
                                tr?.artist.orEmpty(), color = Palette.muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = tr != null) {
                                    tr?.let { app.openArtist(it.artist.split(",", "&").first().trim()) }
                                }.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FavButton(app, track)
                    val have by app.downloads.have.collectAsState()
                    val dl by app.downloads.tasks.collectAsState()
                    val task = track?.let { tr -> dl.firstOrNull { it.track.id == tr.id } }
                    AnimIconButton(
                        onClick = { track?.let { if (it.id in have) app.downloads.remove(it.id) else app.downloads.download(it) } }, enabled = track != null,
                        tooltip = if (track != null && track.id in have) "Quitar descarga" else "Descargar para escuchar sin conexión",
                    ) {
                        if (task != null) DownloadIndicator(task, Modifier.size(20.dp))
                        else Icon(if (track != null && track.id in have) Icons.Filled.DownloadDone else Icons.Filled.Download, "Descargar", tint = if (track != null && track.id in have) Palette.blue else Palette.muted)
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        AnimIconButton(onClick = { menu = true }, enabled = track != null, tooltip = "Añadir a una lista") { Icon(Icons.Filled.LibraryAdd, "Añadir a lista", tint = Palette.muted) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Nueva lista…") }, onClick = { track?.let { app.newListFor = it }; menu = false })
                            lib.playlists.keys.forEach { n ->
                                DropdownMenuItem(text = { Text(n) }, onClick = { track?.let { app.library.addToPlaylist(n, it) }; menu = false })
                            }
                        }
                    }
                    AnimIconButton(onClick = { track?.let { app.startRadio(it) } }, enabled = track != null, tooltip = "Radio: canciones parecidas a esta") { Icon(Icons.Filled.Radio, "Radio", tint = Palette.muted) }
                    MixButton(app)
                    SleepButton(app)
                }
                ProgressRow(app, Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = 4.dp))
                TransportControls(app, big = true)
                VolumeControl(app, 150.dp)
            }
            // Derecha: letra / cola
            Column(Modifier.weight(1.1f).fillMaxHeight()) {
                TabsRow(app.npTab, listOf("Letra", "Cola"), listOf("Letra de la canción, resaltada al ritmo", "Lo que viene después; arrastra para ordenar")) { app.npTab = it }
                Spacer(Modifier.height(14.dp))
                AnimatedContent(
                    app.npTab,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0f to Color.Transparent, 0.07f to Color.Black, 0.93f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(280, 60)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 10 * dir }) togetherWith
                            (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { -it / 10 * dir })
                    },
                    label = "npTab",
                ) { tab ->
                    if (tab == 0) LyricsPanel(app, track) else QueueList(app)
                }
            }
        }
    }
}

@Composable
private fun TabsRow(selected: Int, labels: List<String>, hints: List<String>, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) accent else Color.White.copy(alpha = 0.08f), tween(250), label = "tabBg")
            Tip(hints.getOrElse(i) { label }) {
                Box(
                    Modifier.clip(CircleShape).background(bg).clickable { onSelect(i) }.padding(horizontal = 22.dp, vertical = 8.dp),
                ) { Text(label, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Palette.muted) }
            }
        }
    }
}

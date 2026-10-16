package app.toastune.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toastune.core.Track

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = modifier.padding(bottom = 10.dp))
}

@Composable
private fun QuickTile(thumb: String?, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f))
            .interactive(RoundedCornerShape(14.dp), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(thumb, 64.dp, 0.dp)
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(end = 10.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppState) {
    val lib by app.library.data.collectAsState()
    LaunchedEffect(lib.recent.firstOrNull()?.id, lib.taste) { app.loadForYou() }

    val quick: List<Pair<Track, List<Track>>> = (lib.recent.take(6) + lib.favorites)
        .distinctBy { it.id }.take(6).let { l -> l.map { it to l } }

    SmoothList(spacing = 24.dp) {
        item {
            Column(Modifier.enter(0)) {
                Text(greeting(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("¿Qué quieres escuchar hoy?", color = Palette.muted)
            }
        }
        item {
            val q by app.player.queue.collectAsState()
            val st by app.mpv.state.collectAsState()
            val cur = q.current
            if (cur != null) {
                val accent = LocalAccent.current
                Row(
                    Modifier.enter(1).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.35f), Palette.rose.copy(alpha = 0.20f))))
                        .interactive(RoundedCornerShape(24.dp)) { app.openNowPlaying(0) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Cover(cur.thumbnailUrl, 112.dp, 18.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (st.playing) "SONANDO AHORA" else "CONTINÚA DONDE LO DEJASTE", fontSize = 11.sp, letterSpacing = 1.4.sp, color = Palette.muted, fontWeight = FontWeight.SemiBold)
                        Text(cur.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(cur.artist, color = Palette.muted)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { app.player.togglePlay() },
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.rose, contentColor = Color.White),
                            ) {
                                Icon(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                                Spacer(Modifier.width(6.dp)); Text(if (st.playing) "Pausar" else "Reproducir")
                            }
                            OutlinedButton(onClick = { app.openNowPlaying(0) }) { Text("Ver letra") }
                        }
                    }
                }
            }
        }
        if (quick.isNotEmpty()) item {
            Column(Modifier.enter(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quick.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (t, all) ->
                            QuickTile(t.thumbnailUrl, t.title, Modifier.weight(1f)) { app.player.playList(all, all.indexOf(t)) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (lib.taste.isEmpty()) item {
            Column(
                Modifier.enter(2).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Elige tus géneros", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Así podremos recomendarte música desde el primer día.", color = Palette.muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GENRES, key = { it }) { g ->
                        FilterChip(selected = g in lib.taste, onClick = { app.library.toggleTaste(g) }, label = { Text(g) })
                    }
                }
            }
        }
        if (lib.recent.isNotEmpty()) item {
            Column(Modifier.enter(3)) {
                SectionHeader("Seguir escuchando")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(lib.recent.take(20), key = { _, t -> t.id }) { i, t ->
                        MediaCard(t.thumbnailUrl, t.title, t.artist) { app.player.playList(lib.recent.take(20), i) }
                    }
                }
            }
        }
        if (app.forYou.isNotEmpty() || app.forYouLoading) item {
            Column(Modifier.enter(4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Para ti", Modifier.weight(1f))
                    AnimIconButton(onClick = { app.loadForYou(force = true) }, tooltip = "Actualizar recomendaciones") { Icon(Icons.Filled.Refresh, "Actualizar", tint = Palette.muted) }
                }
                if (app.forYouLoading && app.forYou.isEmpty()) {
                    val brush = shimmerBrush()
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(6.dp)) {
                        repeat(7) { i ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.graphicsLayer { alpha = 1f - i * 0.1f }) {
                                Box(Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)).background(brush))
                                Box(Modifier.width(110.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                                Box(Modifier.width(70.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                            }
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(app.forYou, key = { _, t -> t.id }) { i, t ->
                        MediaCard(t.thumbnailUrl, t.title, t.artist) { app.player.playList(app.forYou, i) }
                    }
                }
            }
        }
        if (lib.albums.isNotEmpty()) item {
            Column(Modifier.enter(5)) {
                SectionHeader("Tus álbumes")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(lib.albums, key = { it.id }) { a -> MediaCard(a.thumbnailUrl, a.title, a.artist) { app.openAlbum(a) } }
                }
            }
        }
        if (lib.favorites.isNotEmpty()) item {
            Column(Modifier.enter(6)) {
                SectionHeader("Tus favoritos")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(lib.favorites.take(20), key = { _, t -> t.id }) { i, t ->
                        MediaCard(t.thumbnailUrl, t.title, t.artist) { app.player.playList(lib.favorites, i) }
                    }
                }
            }
        }
        if (lib.recent.isEmpty() && lib.favorites.isEmpty() && lib.albums.isEmpty()) item {
            Text("Busca algo en «Buscar» para empezar. Aquí aparecerán tus recientes y recomendaciones.", color = Palette.muted)
        }
    }
}

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when { h < 6 -> "Buenas noches"; h < 13 -> "Buenos días"; h < 20 -> "Buenas tardes"; else -> "Buenas noches" }
}

@Composable
fun AlbumsScreen(app: AppState) {
    val albums = app.library.data.collectAsState().value.albums
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Álbumes", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        if (albums.isEmpty()) Text("Guarda álbumes desde la búsqueda y aparecerán aquí.", color = Palette.muted)
        SmoothList {
            itemsIndexed(albums, key = { _, a -> a.id }) { i, a ->
                ItemRow(a.thumbnailUrl, a.title, "Álbum · ${a.artist}", Modifier.enter(i).animateItem()) { app.openAlbum(a) }
            }
        }
    }
}

@Composable
fun DownloadsScreen(app: AppState) {
    val list = app.library.data.collectAsState().value.downloads
    Column(Modifier.fillMaxSize()) {
        CollectionHeader(
            "Descargas", summary(list) + " · sin conexión", list.map { it.thumbnailUrl },
            onPlay = if (list.isNotEmpty()) ({ app.player.playList(list) }) else null,
            onShuffle = if (list.isNotEmpty()) ({ app.player.playList(list.shuffled()) }) else null,
        ) {
            if (list.isNotEmpty()) OutlinedButton(onClick = { app.downloads.removeAll(list) }) {
                Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Borrar todo")
            }
        }
        val queue by app.downloads.tasks.collectAsState()
        if (list.isEmpty() && queue.isEmpty()) Text("Descarga canciones o álbumes desde el menú ⋮ para escucharlos sin conexión.", color = Palette.muted)
        SmoothList {
            if (queue.isNotEmpty()) item {
                DownloadQueue(app, queue)
            }
            itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                TrackRow(app, t, modifier = Modifier.enter(i).animateItem()) { app.player.playList(list, i) }
            }
        }
    }
}

@Composable
fun ArtistScreen(app: AppState, name: String) {
    Column(Modifier.fillMaxSize()) {
        CollectionHeader(
            name, if (app.artistTracks.isNotEmpty()) summary(app.artistTracks) else "Artista",
            app.artistTracks.map { it.thumbnailUrl },
            onBack = { app.back() },
            onPlay = if (app.artistTracks.isNotEmpty()) ({ app.player.playList(app.artistTracks) }) else null,
            onShuffle = if (app.artistTracks.isNotEmpty()) ({ app.player.playList(app.artistTracks.shuffled()) }) else null,
        )
        if (app.artistLoading) SkeletonRows(7)
        SmoothList {
            if (app.artistAlbums.isNotEmpty()) item {
                Column {
                    SectionHeader("Álbumes")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(app.artistAlbums, key = { it.id }) { a -> MediaCard(a.thumbnailUrl, a.title, a.artist, size = 130.dp) { app.openAlbum(a) } }
                    }
                    SectionHeader("Canciones", Modifier.padding(top = 14.dp))
                }
            }
            itemsIndexed(app.artistTracks, key = { _, t -> t.id }) { i, t ->
                TrackRow(app, t, modifier = Modifier.enter(i).animateItem()) { app.player.playList(app.artistTracks, i) }
            }
        }
    }
}

@Composable
private fun DownloadQueue(app: AppState, queue: List<DlTask>) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val active = queue.count { it.state != DlState.FAILED }
            Text(
                if (active > 0) "Descargando · $active en cola" else "Descargas con error",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.blue, modifier = Modifier.weight(1f),
            )
            if (queue.any { it.state == DlState.FAILED }) TextButton(onClick = { app.downloads.retryFailed() }) { Text("Reintentar") }
            TextButton(onClick = { app.downloads.cancelAll() }) { Text("Cancelar todo") }
        }
        queue.forEach { task ->
            key(task.track.id) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Cover(task.track.thumbnailUrl, 44.dp, 8.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(task.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        when (task.state) {
                            DlState.FAILED -> Text(task.error ?: "Error", color = Palette.rose, fontSize = 12.sp, maxLines = 1)
                            DlState.QUEUED -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Palette.muted)
                                Text("En espera", color = Palette.muted, fontSize = 12.sp)
                            }
                            DlState.ACTIVE -> {
                                val p by animateFloatAsState(task.progress, tween(300), label = "dlp")
                                if (p > 0f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Palette.rose)
                                else LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Palette.rose)
                                Text(if (p > 0f) "${(p * 100).toInt()} %" else "Conectando…", color = Palette.muted, fontSize = 12.sp)
                            }
                        }
                    }
                    if (task.state == DlState.FAILED) AnimIconButton(onClick = { app.downloads.download(task.track) }, tooltip = "Reintentar descarga") { Icon(Icons.Filled.Refresh, "Reintentar", tint = Palette.muted) }
                    AnimIconButton(onClick = { app.downloads.cancel(task.track.id) }, tooltip = "Cancelar descarga") { Icon(Icons.Filled.Close, "Cancelar", tint = Palette.muted) }
                }
            }
        }
    }
}

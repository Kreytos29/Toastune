package app.toastune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.toastune.MainViewModel
import app.toastune.R
import app.toastune.core.Track
import java.util.Calendar

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
fun ShelfCard(track: Track, current: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.width(150.dp).bounceClick(onClick = onClick)) {
        Cover(track.thumbnailUrl, Modifier.size(150.dp), RoundedCornerShape(22.dp), px = 360)
        Spacer(Modifier.height(8.dp))
        Text(
            track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Shelf(
    title: String,
    tracks: List<Track>,
    currentId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
) {
    SectionHeader(title)
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(tracks, key = { _, t -> t.id }, contentType = { _, _ -> "card" }) { i, t ->
            ShelfCard(t, t.id == currentId, { onPlay(tracks, i) }, { onMore(t) })
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LoginCard(onLogin: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.40f), scheme.secondary.copy(alpha = 0.40f))))
            .padding(20.dp),
    ) {
        Text("Conecta tu YouTube Music", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("Trae tus me gusta, tu historial y tus playlists.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Iniciar sesión", onLogin)
    }
}

@Composable
fun HomeScreen(
    vm: MainViewModel,
    listState: LazyListState,
    bottomPad: Dp,
    onOpenSettings: () -> Unit,
    onLogin: () -> Unit,
    onMore: (Track) -> Unit,
) {
    val history by vm.history.collectAsState()
    val forYou by vm.forYou.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val downloaded by vm.downloaded.collectAsState()
    val online by vm.online.collectAsState()
    val account by vm.ytAccount.collectAsState()
    val taste by vm.tasteShelves.collectAsState()
    val current by vm.player.currentId.collectAsState()
    val hello = remember { greeting() }
    val firstName = account?.name?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    val recents = remember(history) { history.take(6) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPad),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_logo), null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (firstName != null) "$hello, $firstName" else hello, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Toastune", style = MaterialTheme.typography.headlineMedium)
                }
                if (account == null) {
                    Box(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(onClick = onLogin).padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { Text("Iniciar sesión", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary) }
                } else {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).bounceClick(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((firstName ?: "?").take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Rounded.Settings, "Ajustes", onOpenSettings)
            }
        }
        if (!online) item { OfflineBanner(Modifier.padding(bottom = 12.dp)) }
        if (account == null) item { LoginCard(onLogin) }

        if (recents.isNotEmpty()) {
            item { SectionHeader("Sigue escuchando") }
            items(recents.chunked(2), key = { it.first().id }) { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t ->
                        Row(
                            Modifier.weight(1f).frost(RoundedCornerShape(20.dp)).bounceClick { vm.play(recents, recents.indexOf(t)) }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(t.thumbnailUrl, Modifier.size(48.dp), RoundedCornerShape(14.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                t.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = if (t.id == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(end = 6.dp),
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
        items(taste, key = { it.title }, contentType = { "shelf" }) { sh -> Shelf(sh.title, sh.tracks, current, vm::play, onMore) }
        if (forYou.isNotEmpty()) item { Shelf("Para ti", forYou, current, vm::play, onMore) }
        if (favorites.isNotEmpty()) item { Shelf("Tus favoritos", favorites, current, vm::play, onMore) }
        if (downloaded.isNotEmpty()) item { Shelf("Descargadas", downloaded, current, vm::play, onMore) }
        if (history.size > 6) item { Shelf("Escuchado hace poco", history.drop(6).take(20), current, vm::play, onMore) }

        if (history.isEmpty() && favorites.isEmpty() && downloaded.isEmpty() && taste.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.History,
                    "Aquí aparecerá tu música",
                    "Busca una canción y empieza a escuchar. Lo que reproduzcas se mostrará aquí.",
                    Modifier.padding(top = 40.dp),
                )
            }
        }
    }
}

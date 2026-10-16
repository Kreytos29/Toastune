package app.toastune.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.toastune.playback.PlayerConnection
import app.toastune.playback.PlayerState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Rounded.Home),
    Search("Buscar", Icons.Rounded.Search),
    Library("Biblioteca", Icons.Rounded.LibraryMusic),
}

/** Línea de progreso del mini reproductor: es la única parte que se redibuja cada 250 ms. */
@Composable
private fun MiniProgress(position: StateFlow<Long>, durationMs: Long, color: Color, modifier: Modifier = Modifier) {
    val pos by position.collectAsState()
    Canvas(modifier) {
        val f = if (durationMs > 0) (pos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        drawRoundRect(color.copy(alpha = 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        drawRoundRect(
            color,
            size = androidx.compose.ui.geometry.Size(size.width * f, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        )
    }
}

@Composable
fun Dock(
    player: PlayerConnection,
    state: PlayerState,
    accent: Color,
    tab: Tab,
    expanded: Boolean,
    onTab: (Tab) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track
    Column(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(32.dp)),
    ) {
        AnimatedVisibility(
            visible = track != null && !expanded,
            enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically(),
        ) {
            CompositionLocalProvider(LocalVisScope provides this) {
                if (track != null) MiniPlayer(player, state, accent, onOpenPlayer)
            }
        }
        NavRow(tab, accent, onTab)
    }
}

@Composable
private fun MiniPlayer(player: PlayerConnection, state: PlayerState, accent: Color, onOpen: () -> Unit) {
    val track = state.track ?: return
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = drag.value * 0.6f; alpha = 1f - (kotlin.math.abs(drag.value) / 900f).coerceIn(0f, 0.6f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val d = drag.value
                        if (d < -140f) player.next() else if (d > 140f) player.previous()
                        scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { drag.snapTo(drag.value + amount) }
                    },
                )
            }
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(track.thumbnailUrl, Modifier.sharedCover().size(48.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(accent).bounceClick { player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pausar" else "Reproducir",
                    Modifier.size(26.dp),
                    tint = Color.Black.copy(alpha = 0.85f),
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).bounceClick(enabled = state.hasNext) { player.next() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.SkipNext, "Siguiente", Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.hasNext) 1f else 0.35f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MiniProgress(player.position, state.durationMs, accent, Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(3.dp))
    }
}

@Composable
private fun NavRow(tab: Tab, accent: Color, onTab: (Tab) -> Unit) {
    val index by animateFloatAsState(
        tab.ordinal.toFloat(),
        spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "tabIndex",
    )
    val count = Tab.entries.size
    Box(Modifier.fillMaxWidth().padding(6.dp)) {
        // Burbuja que se desliza bajo la pestaña activa.
        Box(
            Modifier
                .fillMaxWidth(1f / count)
                .height(54.dp)
                .graphicsLayer { translationX = index * size.width }
                .clip(RoundedCornerShape(26.dp))
                .background(accent.copy(alpha = 0.22f)),
        )
        Row(Modifier.fillMaxWidth()) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                val tint by animateColorAsState(
                    if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabTint",
                )
                Column(
                    Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(26.dp)).bounceClick { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, t.label, Modifier.size(24.dp), tint = tint)
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }
}

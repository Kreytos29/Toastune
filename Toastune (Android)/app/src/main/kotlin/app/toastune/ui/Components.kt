package app.toastune.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.toastune.core.Track
import app.toastune.playback.DownloadPhase
import app.toastune.playback.DownloadStatus
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Click con rebote: se encoge al pulsar y vuelve con un muelle. Sin ripple y sin `composed`
 * (que es lento en listas): un solo Animatable por elemento y la escala se lee al dibujar.
 */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val current by androidx.compose.runtime.rememberUpdatedState(onClick)
    val spec = remember { spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium) }
    return this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .semantics(mergeDescendants = true) {
            role = androidx.compose.ui.semantics.Role.Button
            if (enabled) semanticsOnClick { current(); true }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    kotlinx.coroutines.coroutineScope {
                        launch { scale.animateTo(0.95f, spec) }
                        tryAwaitRelease()
                        launch { scale.animateTo(1f, spec) }
                    }
                },
                onTap = { current() },
            )
        }
}

/** Las pantallas que se superponen no deben dejar pasar los toques a lo que hay debajo. */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val ytThumb = Regex("(/vi(?:_webp)?/[^/]+/)[^/?]+")

/** Pide la portada en mayor resolución: YouTube Music entrega miniaturas diminutas por defecto. */
fun hiRes(url: String, px: Int): String = when {
    "googleusercontent.com" in url || "ggpht.com" in url -> {
        val i = url.indexOf('=')
        if (i > 0) url.substring(0, i) + "=w$px-h$px-l90-rj" else url
    }
    "ytimg.com" in url -> ytThumb.replace(url.substringBefore('?')) {
        it.groupValues[1] + (if (px >= 360) "hq720.jpg" else "mqdefault.jpg")
    }
    else -> url
}

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp), px: Int = 240) {
    var failed by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    val model = if (url == null) null else if (failed) url else hiRes(url, px)
    // Tamaño fijo: carga sin esperar al layout y la caché de memoria acierta siempre.
    val request = remember(model, px) {
        model?.let {
            ImageRequest.Builder(context).data(it).size(px).crossfade(px >= 800).build()
        }
    }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (request == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.outline)
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        }
    }
}

/** Tres barras que bailan; la animación solo se lee al dibujar. */
@Composable
fun EqualizerBars(color: Color, modifier: Modifier = Modifier, playing: Boolean = true) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.9f, 0.2f, infiniteRepeatable(tween(430, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(610, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "c")
    Canvas(modifier.size(18.dp)) {
        val w = size.width / 5f
        val vals = if (playing) floatArrayOf(a, b, c) else floatArrayOf(0.35f, 0.35f, 0.35f)
        vals.forEachIndexed { i, v ->
            val h = size.height * v
            drawRoundRect(
                color,
                Offset(w * (i * 2), size.height - h),
                Size(w, h),
                CornerRadius(w / 2, w / 2),
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    download: DownloadStatus? = null,
    moreIcon: ImageVector = Icons.Rounded.MoreVert,
) {
    val scheme = MaterialTheme.colorScheme
    val titleColor = if (isCurrent) scheme.primary else scheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Cover(track.thumbnailUrl, Modifier.size(54.dp), px = 180)
            if (isCurrent) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { EqualizerBars(Color.White, playing = isPlaying) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (download?.phase == DownloadPhase.DONE) {
                    Icon(Icons.Rounded.DownloadDone, null, Modifier.size(15.dp).padding(end = 3.dp), tint = scheme.primary)
                }
                Text(
                    track.artist.ifBlank { "Artista desconocido" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (download != null && (download.phase == DownloadPhase.DOWNLOADING || download.phase == DownloadPhase.QUEUED)) {
                Spacer(Modifier.height(5.dp))
                ProgressLine(download.progress, Modifier.fillMaxWidth().height(3.dp))
            }
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) { Icon(moreIcon, "Más opciones", tint = scheme.onSurfaceVariant) }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Canvas(modifier) {
        drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
        if (p > 0f) drawLine(fill, Offset(0f, size.height / 2), Offset(size.width * p, size.height / 2), size.height, StrokeCap.Round)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).frost(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "chipBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, label = "chipFg")
    Box(
        modifier.frost(CircleShape).background(bg).bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = fg) }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier.size(size).frost(CircleShape).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(size * 0.5f), tint = tint) }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).frost(RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Sin conexión", style = MaterialTheme.typography.titleSmall)
            Text("Puedes escuchar tus descargas y lo que ya está en caché.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package app.toastune.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toastune.core.Lyrics
import app.toastune.core.Track
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Cuánto dura "cantada" una línea: proporcional al texto, sin pasarse del hueco hasta la siguiente. */
private fun sungMs(text: String, start: Long, next: Long?): Float {
    val gap = ((next ?: (start + 5000)) - start).coerceAtLeast(1)
    return min(gap * 0.92f, max(1200f, text.length * 75f))
}

/**
 * Letra sincronizada estilo karaoke: la línea actual se enciende letra a letra con un barrido suave,
 * las demás se difuminan según su distancia, y en las partes instrumentales aparecen tres puntos.
 * Se puede tocar una línea para saltar a ella, ajustar la sincronía y cambiar el tamaño.
 */
@Composable
fun LyricsPanel(app: AppState, track: Track?) {
    val st by app.mpv.state.collectAsState()
    val size = app.settings.data.collectAsState().value.lyricSize
    var loading by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val lyrics by produceState<Lyrics?>(null, track?.id, retry) {
        value = null
        if (track != null) {
            loading = true
            value = app.lyrics.lyrics(track.title, track.artist, track.durationMs ?: st.durationMs.takeIf { it > 0 }, force = retry > 0)
            loading = false
        }
    }
    var offset by remember(track?.id) { mutableLongStateOf(track?.let { app.lyricOffsets[it.id] } ?: 0L) }

    // Posición interpolada fotograma a fotograma (mpv avisa solo cada ~100 ms).
    var anchorPos by remember { mutableLongStateOf(0L) }
    var anchorAt by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(st.positionMs) { anchorPos = st.positionMs; anchorAt = System.nanoTime() }
    val pos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            pos.longValue = if (st.playing) anchorPos + (System.nanoTime() - anchorAt) / 1_000_000 else anchorPos
        }
    }
    // Posición corregida con el ajuste de sincronía
    val p = { pos.longValue + offset }

    Column(Modifier.fillMaxSize()) {
        val l = lyrics
        // Barra de ajustes
        if (l != null) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (l.synced) "Sincronizada" else "Sin sincronizar", fontSize = 12.sp, color = Palette.muted,
                modifier = Modifier.weight(1f),
            )
            if (l.synced) {
                MiniChip("−0,5 s", "Retrasar la letra medio segundo (si va adelantada)") { offset -= 500; track?.let { app.lyricOffsets[it.id] = offset } }
                Text(if (offset == 0L) "Sincronía" else "%+.1f s".format(offset / 1000.0), fontSize = 12.sp, color = if (offset == 0L) Palette.muted else LocalAccent.current)
                MiniChip("+0,5 s", "Adelantar la letra medio segundo (si va atrasada)") { offset += 500; track?.let { app.lyricOffsets[it.id] = offset } }
                Spacer(Modifier.width(8.dp))
            }
            MiniChip("A−", "Letra más pequeña") { app.settings.update { it.copy(lyricSize = (it.lyricSize - 4).coerceAtLeast(22)) } }
            MiniChip("A+", "Letra más grande") { app.settings.update { it.copy(lyricSize = (it.lyricSize + 4).coerceAtMost(44)) } }
        }
        when {
            track == null -> Text("No hay nada sonando.", color = Palette.muted)
            loading -> SkeletonRows(5)
            l == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("No se encontró la letra de esta canción.", color = Palette.muted)
                Tip("Vuelve a buscarla ignorando lo guardado") { OutlinedButton(onClick = { retry++ }) { Text("Reintentar") } }
            }
            else -> LyricsList(app, l, size, p, pos)
        }
    }
}

@Composable
private fun MiniChip(label: String, tip: String, onClick: () -> Unit) {
    Tip(tip) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.09f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun LyricsList(app: AppState, l: Lyrics, size: Int, p: () -> Long, pos: State<Long>) {
    val accent = LocalAccent.current
    val active by remember(l) {
        derivedStateOf { if (l.synced) l.lines.indexOfLast { it.timeMs <= p() }.coerceAtLeast(0) else -1 }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == active }
        if (item != null) {
            listState.animateScrollBy(item.offset - info.viewportSize.height * 0.30f, tween(700, easing = FastOutSlowInEasing))
        } else {
            listState.scrollToItem(max(0, active - 2))
        }
    }
    val fs = size.sp
    val lh = (size + 7).sp
    LazyColumn(
        Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(top = 12.dp, bottom = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(l.lines) { i, line ->
            val on = i == active
            val dist = if (active < 0) 0 else abs(i - active)
            val blurTarget by animateDpAsState((if (!l.synced) 0 else min(dist, 4)).times(0.9f).dp, tween(500), label = "lyricBlur")
            val a by animateFloatAsState(
                when { !l.synced || on -> 1f; i < active -> 0.5f; else -> 0.36f }, tween(450), label = "lyricA",
            )
            val k by animateFloatAsState(if (on || !l.synced) 1f else 0.92f, spring(0.7f, 220f), label = "lyricScale")
            val next = l.lines.getOrNull(i + 1)?.timeMs
            // Hueco instrumental antes de esta línea
            val gapStart = if (i == 0) 0L else l.lines[i - 1].let { it.timeMs + sungMs(it.text, it.timeMs, line.timeMs).toLong() }
            val longGap = l.synced && line.timeMs - gapStart >= 6000 && line.text.isNotBlank()
            Column(Modifier.fillMaxWidth()) {
                if (longGap) {
                    val inGap by remember { derivedStateOf { p() >= gapStart && p() < line.timeMs } }
                    AnimatedVisibility(inGap, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        GapDots({ ((p() - gapStart).toFloat() / (line.timeMs - gapStart).coerceAtLeast(1)).coerceIn(0f, 1f) }, accent)
                    }
                }
                Box(
                    Modifier.fillMaxWidth()
                        .graphicsLayer { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0.5f) }
                        .blur(blurTarget, BlurredEdgeTreatment.Unbounded)
                        .clip(RoundedCornerShape(14.dp))
                        .then(if (on) Modifier.drawBehind {
                            drawRect(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent)))
                        } else Modifier)
                        .clickable(enabled = l.synced) { app.player.seekTo((line.timeMs - 200).coerceAtLeast(0)) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    if (on && l.synced && line.text.isNotBlank()) {
                        KaraokeLine(line.text, line.timeMs, next, fs, lh, accent) { p() }
                    } else {
                        Text(
                            line.text.ifBlank { "♪" }, fontSize = fs, fontWeight = FontWeight.Bold,
                            lineHeight = lh, color = Color.White.copy(alpha = a),
                        )
                    }
                }
            }
        }
    }
}

/** Tres puntos que se van llenando durante una parte instrumental. */
@Composable
private fun GapDots(progress: () -> Float, accent: Color) {
    val pulse by rememberInfiniteTransition(label = "gap").animateFloat(
        1f, 1.18f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gapPulse",
    )
    val pr = progress()
    Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            val f = ((pr * 3f) - i).coerceIn(0f, 1f)
            Box(
                Modifier.size(14.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .clip(CircleShape).background(lerp(Color.White.copy(alpha = 0.25f), accent, f)),
            )
        }
    }
}

/** Línea activa: se ilumina letra a letra con un barrido suave y un brillo del color de la portada. */
@Composable
private fun KaraokeLine(text: String, start: Long, next: Long?, fs: androidx.compose.ui.unit.TextUnit, lh: androidx.compose.ui.unit.TextUnit, accent: Color, pos: () -> Long) {
    val n = text.length
    val sung = sungMs(text, start, next)
    val ramp = 2.5f
    val dim = Color.White.copy(alpha = 0.36f)
    val u = ((pos() - start) / sung).coerceIn(0f, 1f)
    val c = u * (n + ramp)
    val annotated = buildAnnotatedString {
        for (j in 0 until n) {
            val f = ((c - j) / ramp).coerceIn(0f, 1f)
            withStyle(
                SpanStyle(
                    color = lerp(dim, Color.White, f),
                    shadow = if (f > 0.3f) Shadow(accent.copy(alpha = 0.55f * f), Offset.Zero, 16f) else null,
                ),
            ) { append(text[j]) }
        }
    }
    Text(annotated, fontSize = fs, fontWeight = FontWeight.ExtraBold, lineHeight = lh)
}

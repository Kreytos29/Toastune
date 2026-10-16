package app.toastune.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fila/botón con resaltado suave al pasar el mouse, y un pequeño "hundido" al pulsar. */
@Composable
fun Modifier.interactive(
    shape: Shape = RoundedCornerShape(12.dp),
    selected: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val accent = LocalAccent.current
    val bg by animateColorAsState(
        when {
            selected -> accent.copy(alpha = 0.22f)
            hovered -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        tween(180), label = "bg",
    )
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(bg)
        .hoverable(src)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Lista con barra de desplazamiento fina. */
@Composable
fun SmoothList(
    modifier: Modifier = Modifier.fillMaxSize(),
    spacing: Dp = 2.dp,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    Box(modifier) {
        LazyColumn(
            Modifier.fillMaxSize().padding(end = 10.dp), state = state,
            verticalArrangement = Arrangement.spacedBy(spacing), content = content,
        )
        VerticalScrollbar(
            rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = ScrollbarStyle(
                minimalHeight = 32.dp, thickness = 6.dp, shape = RoundedCornerShape(3.dp),
                hoverDurationMillis = 300,
                unhoverColor = Color.White.copy(alpha = 0.18f), hoverColor = Color.White.copy(alpha = 0.45f),
            ),
        )
    }
}

/** Tres barritas que bailan: indican qué canción suena. */
@Composable
fun EqBars(playing: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val h = (0 until 3).map { i ->
        t.animateFloat(
            0.25f, 1f,
            infiniteRepeatable(tween(420 + i * 130, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        h.forEach { v ->
            Box(Modifier.width(3.dp).fillMaxHeight(if (playing) v.value else 0.25f).clip(RoundedCornerShape(1.dp)).background(Palette.rose))
        }
    }
}

/** Barra de progreso/volumen fina: crece al pasar el mouse y muestra el tirador. */
@Composable
fun SlimSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFinish: () -> Unit = {},
) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(1f) }
    val active = enabled && (hovered || dragging)
    val thumb by animateDpAsState(if (active) 14.dp else 0.dp, tween(150), label = "thumb")
    val bar by animateDpAsState(if (active) 6.dp else 4.dp, tween(150), label = "bar")
    val v = value.coerceIn(0f, 1f)
    val onChangeNow by rememberUpdatedState(onChange)
    val onFinishNow by rememberUpdatedState(onFinish)
    val accent = LocalAccent.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        modifier.height(22.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .hoverable(src)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { onChangeNow((it.x / width).coerceIn(0f, 1f)); onFinishNow() }
            }
            .pointerInput(enabled) {
                if (enabled) detectHorizontalDragGestures(
                    onDragStart = { dragging = true; onChangeNow((it.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = { dragging = false; onFinishNow() },
                    onDragCancel = { dragging = false; onFinishNow() },
                ) { change, _ -> change.consume(); onChangeNow((change.position.x / width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(bar).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)))
        Box(
            Modifier.fillMaxWidth(v).height(bar).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accent, Palette.rose))),
        )
        Box(
            Modifier.offset { IntOffset((v * width - with(density) { thumb.toPx() } / 2).roundToInt(), 0) }
                .size(thumb).clip(CircleShape).background(Color.White),
        )
    }
}

/** Botón de icono que se encoge al pulsar y crece un poco al pasar el mouse; con [tooltip] muestra una ayuda. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltip: String? = null,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.80f else if (hovered) 1.12f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 520f), label = "iconScale",
    )
    val button = @Composable {
        androidx.compose.material3.IconButton(
            onClick = onClick, enabled = enabled, interactionSource = src,
            modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            content = content,
        )
    }
    if (tooltip == null) button() else Tip(tooltip) { button() }
}

@Composable
fun shimmerBrush(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-400f, 1400f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "shimmerX")
    return Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f)),
        start = Offset(x, 0f), end = Offset(x + 400f, 160f),
    )
}

/** Filas "fantasma" que brillan mientras carga el contenido. */
@Composable
fun SkeletonRows(count: Int = 8) {
    val brush = shimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count) { i ->
            Row(
                Modifier.fillMaxWidth().padding(8.dp).graphicsLayer { alpha = 1f - i * 0.08f },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(brush))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width((180 + (i * 37) % 120).dp).height(13.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                    Box(Modifier.width(110.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                }
            }
        }
    }
}

/** Aparece desde abajo con un fundido, escalonado según la posición (solo los primeros elementos). */
@Composable
fun Modifier.enter(index: Int): Modifier {
    val a = remember { Animatable(if (index > 14) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (index <= 14) {
            kotlinx.coroutines.delay(index * 30L)
            a.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 28f }
}

/** Tarjeta de carátula: se eleva al pasar el mouse y muestra un botón de reproducir. */
@Composable
fun MediaCard(
    thumb: String?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    round: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else if (hovered) 1.04f else 1f, spring(0.6f, 400f), label = "card")
    val play by animateFloatAsState(if (hovered) 1f else 0f, tween(220), label = "cardPlay")
    Column(
        modifier.width(size + 12.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (hovered) 0.07f else 0f))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Cover(thumb, size, if (round) size / 2 else 12.dp)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp)
                    .graphicsLayer { alpha = play; translationY = (1f - play) * 16f; scaleX = 0.7f + 0.3f * play; scaleY = 0.7f + 0.3f * play }
                    .clip(CircleShape).background(Palette.rose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
            }
        }
        androidx.compose.material3.Text(
            title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp),
            color = Color.White,
        )
        androidx.compose.material3.Text(
            subtitle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = Palette.muted, fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
        )
    }
}


val LocalAccent = compositionLocalOf { Palette.blue }

/** Color vivo y representativo de una carátula (promedio ponderado por saturación). */
suspend fun accentOf(bmp: ImageBitmap): Color = withContext(Dispatchers.Default) {
    val px = bmp.toPixelMap()
    val step = maxOf(1, minOf(bmp.width, bmp.height) / 28)
    var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
    var y = 0
    while (y < bmp.height) {
        var x = 0
        while (x < bmp.width) {
            val c = px[x, y]
            val mx = maxOf(c.red, c.green, c.blue); val mn = minOf(c.red, c.green, c.blue)
            val sat = if (mx == 0f) 0f else (mx - mn) / mx
            val w = (sat * sat * mx + 0.01f).toDouble()
            r += c.red * w; g += c.green * w; b += c.blue * w; total += w
            x += step
        }
        y += step
    }
    if (total == 0.0) return@withContext Palette.blue
    val hsb = java.awt.Color.RGBtoHSB((r / total * 255).toInt(), (g / total * 255).toInt(), (b / total * 255).toInt(), null)
    Color.hsv(hsb[0] * 360f, hsb[1].coerceIn(0.45f, 0.85f), hsb[2].coerceIn(0.78f, 1f))
}

@Composable
fun rememberAccent(url: String?, enabled: Boolean): Color {
    val bmp = rememberImage(url)
    val target by produceState(Palette.blue, bmp, enabled) {
        value = if (enabled && bmp != null) accentOf(bmp) else Palette.blue
    }
    val animated by animateColorAsState(target, tween(900), label = "accent")
    return animated
}


/**
 * Globo de ayuda al pasar el mouse (explica para qué sirve la opción).
 * Sale pegado al componente —encima si está en la mitad baja de la ventana— para no taparlo ni quitarle los clics.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Tip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var above by remember { mutableStateOf(false) }
    val winH = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    TooltipArea(
        tooltip = {
            androidx.compose.material3.Surface(
                color = Palette.surfaceHigh, shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp,
            ) {
                androidx.compose.material3.Text(
                    text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp), color = Color.White,
                )
            }
        },
        modifier = modifier.onGloballyPositioned { c -> above = c.positionInWindow().y > winH * 0.55f },
        delayMillis = 450,
        tooltipPlacement = if (above) TooltipPlacement.ComponentRect(
            anchor = Alignment.TopCenter, alignment = Alignment.TopCenter,
            offset = androidx.compose.ui.unit.DpOffset(0.dp, (-34).dp),
        ) else TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter, alignment = Alignment.BottomCenter,
            offset = androidx.compose.ui.unit.DpOffset(0.dp, 34.dp),
        ),
    ) { content() }
}

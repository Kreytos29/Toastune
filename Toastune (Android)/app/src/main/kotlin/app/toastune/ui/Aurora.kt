package app.toastune.ui

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import app.toastune.ui.theme.LocalCoverColors
import app.toastune.ui.theme.LocalGlassColors
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val accentCache = LruCache<String, Int>(64)

/** Color dominante y agradable de una portada; con animación suave al cambiar de canción. */
@Composable
fun rememberAccent(url: String?, fallback: Color): Color {
    val ctx = LocalContext.current
    val enabled = LocalCoverColors.current
    val dark = LocalGlassColors.current.dark
    var target by remember(url) {
        mutableStateOf(url?.let { accentCache.get(it) }?.let { Color(it) } ?: fallback)
    }
    LaunchedEffect(url, enabled, fallback) {
        if (!enabled || url == null) {
            target = fallback
            return@LaunchedEffect
        }
        accentCache.get(url)?.let { target = Color(it); return@LaunchedEffect }
        val request = ImageRequest.Builder(ctx).data(url).size(96).allowHardware(false).build()
        val bitmap = (ctx.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        if (bitmap != null) {
            val argb = withContext(Dispatchers.Default) { pickAccent(bitmap, dark) }
            if (argb != null) {
                accentCache.put(url, argb)
                target = Color(argb)
            }
        }
    }
    return animateColorAsState(target, tween(700), label = "accent").value
}

private fun pickAccent(bitmap: android.graphics.Bitmap, dark: Boolean): Int? {
    val p = Palette.from(bitmap).maximumColorCount(12).generate()
    val rgb = (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch)?.rgb ?: return null
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = hsl[1].coerceIn(0.45f, 0.9f)
    hsl[2] = if (dark) hsl[2].coerceIn(0.5f, 0.68f) else hsl[2].coerceIn(0.38f, 0.52f)
    return ColorUtils.HSLToColor(hsl)
}

/**
 * Fondo de aurora: tres manchas de luz de color. Con [animate] derivan despacio (solo en la pantalla
 * de reproducción); el fondo general es estático para no obligar al desenfoque del dock a
 * recalcularse en cada fotograma.
 */
@Composable
fun AuroraBackground(accent: Color, modifier: Modifier = Modifier, intensity: Float = 1f, animate: Boolean = false) {
    if (animate) {
        val t = rememberInfiniteTransition(label = "aurora")
        val phase by t.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "phase",
        )
        AuroraCanvas(accent, intensity, modifier) { phase }
    } else {
        AuroraCanvas(accent, intensity, modifier) { 0.5f }
    }
}

@Composable
private fun AuroraCanvas(accent: Color, intensity: Float, modifier: Modifier, phase: () -> Float) {
    val bg = MaterialTheme.colorScheme.background
    val second = MaterialTheme.colorScheme.secondary
    val dark = LocalGlassColors.current.dark
    val alpha = (if (dark) 0.5f else 0.35f) * intensity
    Canvas(modifier.fillMaxSize()) {
        drawRect(bg)
        val w = size.width
        val h = size.height
        val p = phase()
        fun blob(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = alpha), Color.Transparent),
                center = Offset(cx, cy),
                radius = r,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        blob(accent, w * (0.15f + 0.35f * p), h * (0.08f + 0.06f * p), w * 0.95f)
        blob(second, w * (0.95f - 0.4f * p), h * (0.45f + 0.1f * p), w * 0.8f)
        blob(accent, w * (0.2f + 0.2f * p), h * (0.95f - 0.12f * p), w * 0.9f)
    }
}

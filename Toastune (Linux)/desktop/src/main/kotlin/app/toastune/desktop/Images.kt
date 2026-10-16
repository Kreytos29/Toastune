package app.toastune.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val ytThumb = Regex("""(/vi(?:_webp)?/[^/]+/)[a-z0-9]+\.(?:jpg|webp)""")

/** Pide la versión grande de la imagen (YouTube Music y Google entregan miniaturas diminutas). */
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

object Images {
    private val client = OkHttpClient()
    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 200
    }

    fun peek(url: String): ImageBitmap? = synchronized(cache) { cache[url] }

    suspend fun load(url: String): ImageBitmap? {
        peek(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val bytes = r.body?.bytes() ?: return@use null
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }?.also { synchronized(cache) { cache[url] = it } }
            } catch (_: Exception) { null }
        }
    }
}

@Composable
fun rememberImage(url: String?): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(url?.let { Images.peek(it) }, url) { value = url?.let { Images.load(it) } }
    return bmp
}

/** Carátula que aparece con un fundido suave mientras carga. */
@Composable
fun Cover(url: String?, size: Dp, radius: Dp = 10.dp, modifier: Modifier = Modifier) {
    val px = (size.value * 2).toInt()
    val bmp by produceState<ImageBitmap?>(null, url, px) {
        if (url == null) { value = null; return@produceState }
        val hi = hiRes(url, px)
        value = Images.peek(hi) ?: Images.peek(url)
        // Si la versión grande no existe, se usa la original.
        value = Images.load(hi) ?: Images.load(url)
    }
    val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(350), label = "cover")
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(Palette.surfaceHigh, Palette.surface))),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it, null, Modifier.size(size).alpha(alpha), contentScale = ContentScale.Crop) }
    }
}

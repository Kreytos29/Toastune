package app.toastune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.toastune.ui.theme.LocalGlassColors
import app.toastune.ui.theme.LocalGlassEnabled
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Estado compartido del desenfoque: el contenido es la "fuente" y cada panel de cristal lo lee. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

val PanelShape = RoundedCornerShape(28.dp)
val PillShape = CircleShape

private fun Modifier.rim(shape: Shape, top: Color, bottom: Color): Modifier =
    if (top == bottom) border(1.dp, top, shape) else border(0.8.dp, Brush.verticalGradient(listOf(top, bottom)), shape)

/**
 * Cristal de verdad: desenfoca lo que hay detrás. Es el efecto caro, así que solo se usa en
 * pocas superficies grandes (dock, hojas). Sin desenfoque cae a un panel translúcido.
 */
@Composable
fun Modifier.glass(shape: Shape = PanelShape): Modifier {
    val g = LocalGlassColors.current
    val haze = LocalHazeState.current
    val blur = LocalGlassEnabled.current && haze != null
    val base = if (blur && haze != null) {
        clip(shape).hazeEffect(
            haze,
            HazeStyle(
                backgroundColor = g.base,
                tints = listOf(HazeTint(g.tint)),
                blurRadius = 28.dp,
                noiseFactor = 0.03f,
            ),
        )
    } else {
        clip(shape).background(g.base.copy(alpha = 0.97f))
    }
    return base
        .background(Brush.verticalGradient(listOf(g.highlight, Color.Transparent)))
        .rim(shape, g.borderTop, g.borderBottom)
}

/** Cristal "barato": solo translucidez y borde, sin desenfoque. Para tarjetas y filas. */
@Composable
fun Modifier.frost(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val g = LocalGlassColors.current
    return clip(shape)
        .background(g.frost)
        .rim(shape, g.borderTop, g.borderBottom)
}

/** Fondo oscurecido detrás de una hoja modal. */
@Composable
fun Scrim(modifier: Modifier = Modifier) {
    Box(modifier.background(LocalGlassColors.current.scrim))
}

@Composable
fun GlassCircle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.frost(CircleShape), contentAlignment = Alignment.Center) { content() }
}

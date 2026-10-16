package app.toastune.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ajustes de la mezcla entre canciones (se usa en Ajustes y en el botón de la pantalla de la canción). */
@Composable
fun MixPanel(app: AppState, modifier: Modifier = Modifier) {
    val s by app.settings.data.collectAsState()
    val on = s.crossfade > 0
    val accent = LocalAccent.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mezcla automática", fontWeight = FontWeight.SemiBold)
                Text("Funde el final de cada canción con la siguiente, como un DJ.", color = Palette.muted, fontSize = 12.sp)
            }
            Tip(if (on) "Desactivar la mezcla" else "Activar la mezcla entre canciones") {
                Switch(checked = on, onCheckedChange = { app.setCrossfade(if (it) 6 else 0) })
            }
        }
        AnimatedVisibility(on, enter = fadeIn(tween(250)) + expandVertically(tween(300)), exit = fadeOut(tween(150)) + shrinkVertically(tween(250))) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MixCurve(s.mixStyle, Modifier.fillMaxWidth().height(64.dp))
                Label("Duración de la mezcla")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 6, 8, 12).forEach { v ->
                        Tip("Las canciones se funden durante $v segundos") {
                            FilterChip(selected = s.crossfade == v, onClick = { app.setCrossfade(v) }, label = { Text("$v s") })
                        }
                    }
                }
                Label("Estilo")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleCard("Suave", "Fundido parejo", 0, s.mixStyle == 0, Modifier.weight(1f)) { app.updateMix { it.copy(mixStyle = 0) } }
                    StyleCard("DJ", "Entra rápido", 1, s.mixStyle == 1, Modifier.weight(1f)) { app.updateMix { it.copy(mixStyle = 1) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleCard("Ambiental", "Respiro en medio", 2, s.mixStyle == 2, Modifier.weight(1f)) { app.updateMix { it.copy(mixStyle = 2) } }
                    StyleCard("Directa", "Corte de 2 s", 3, s.mixStyle == 3, Modifier.weight(1f)) { app.updateMix { it.copy(mixStyle = 3) } }
                }
                Tip("Salta al final de la canción actual para que oigas la mezcla ya") {
                    OutlinedButton(onClick = { app.player.previewMix() }, modifier = Modifier.fillMaxWidth()) { Text("Probar mezcla ahora") }
                }
                MixSwitch("Mezclar al saltar", "También al pulsar siguiente o elegir otra canción (máx. 3 s).", s.mixOnSkip) { v -> app.updateMix { it.copy(mixOnSkip = v) } }
                MixSwitch("Saltar silencio inicial", "Recorta el silencio del principio para que la entrada sea inmediata.", s.mixTrim) { v -> app.updateMix { it.copy(mixTrim = v) } }
            }
        }
    }
}

@Composable
private fun Label(t: String) = Text(t.uppercase(), fontSize = 11.sp, letterSpacing = 1.1.sp, color = Palette.muted, fontWeight = FontWeight.SemiBold)

@Composable
private fun MixSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Tip(sub) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp)
                Text(sub, color = Palette.muted, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun StyleCard(title: String, sub: String, style: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val border by animateColorAsState(if (selected) accent else Color.White.copy(alpha = 0.12f), tween(200), label = "styleBorder")
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f), tween(200), label = "styleBg")
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, border, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MixCurve(style, Modifier.fillMaxWidth().height(26.dp), pad = 0.dp)
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Text(sub, color = Palette.muted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

/** Dibujo de cómo bajan y suben los volúmenes durante la mezcla. */
@Composable
private fun MixCurve(style: Int, modifier: Modifier = Modifier, pad: androidx.compose.ui.unit.Dp = 8.dp) {
    val accent = LocalAccent.current
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).padding(pad)) {
        fun path(pick: (Pair<Double, Double>) -> Double) = Path().apply {
            for (i in 0..60) {
                val t = i / 60.0
                val x = size.width * t.toFloat()
                val y = size.height * (1f - pick(mixGains(t, style)).toFloat())
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path { it.first }, Palette.muted, style = Stroke(3.dp.toPx()))
        drawPath(path { it.second }, accent, style = Stroke(3.dp.toPx()))
    }
}

@Composable
fun MixButton(app: AppState) {
    var menu by remember { mutableStateOf(false) }
    val cf = app.settings.data.collectAsState().value.crossfade
    val fade by app.mpv.fadeProgress.collectAsState()
    Box {
        AnimIconButton(onClick = { menu = true }, tooltip = if (cf > 0) "Mezcla activada ($cf s): ajustar" else "Mezcla entre canciones: activar") {
            val c by animateColorAsState(if (cf > 0) LocalAccent.current else Palette.muted, tween(200), label = "mix")
            Icon(Icons.Filled.GraphicEq, "Mezcla", tint = if (fade >= 0f) Color.White else c)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MixPanel(app, Modifier.width(330.dp).padding(16.dp))
        }
    }
}

/** Aparece mientras dos canciones se mezclan. */
@Composable
fun MixPill(app: AppState) {
    val fade by app.mpv.fadeProgress.collectAsState()
    val accent = LocalAccent.current
    AnimatedVisibility(fade >= 0f, enter = fadeIn(tween(250)), exit = fadeOut(tween(400))) {
        Row(
            Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.35f), Color.White.copy(alpha = 0.10f))))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqBars(true)
            Text("Mezclando", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.width(70.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fade.coerceIn(0f, 1f)).background(Color.White))
            }
        }
    }
}

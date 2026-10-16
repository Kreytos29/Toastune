package app.toastune.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toastune.core.Track

/** Primera vez: géneros, artistas y (opcional) conectar YouTube Music. */
@Composable
fun OnboardingScreen(app: AppState) {
    var step by remember { mutableIntStateOf(0) }
    val genres = remember { mutableStateListOf<String>() }
    val artists = remember { mutableStateListOf<String>() }
    var custom by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF141B3D), Color(0xFF1B1535), Color(0xFF281231)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(820.dp).heightIn(max = 640.dp).fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.06f)).padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.Image(painterResource("icon.png"), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                Text("Toastune", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.blue, modifier = Modifier.weight(1f))
                TextButton(onClick = { app.finishOnboarding(emptyList(), emptyList()) }) { Text("Omitir", color = Palette.muted) }
            }
            AnimatedContent(
                step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(300, 80)) + slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 8 * dir }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(240)) { -it / 8 * dir })
                },
                label = "onboarding",
            ) { s ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (s) {
                        0 -> {
                            Text("¿Qué te gusta escuchar?", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text("Elige los géneros que quieras. Con eso armamos tu inicio.", color = Palette.muted)
                            LazyVerticalGrid(
                                GridCells.Adaptive(150.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f),
                            ) {
                                items(Taste.genres, key = { it.label }) { g ->
                                    val on = g.label in genres
                                    val bg by animateColorAsState(if (on) Palette.rose else Color.White.copy(alpha = 0.08f), tween(200), label = "gbg")
                                    Box(
                                        Modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                                            .clickable { if (on) genres.remove(g.label) else genres.add(g.label) },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(g.label, fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                        1 -> {
                            Text("¿Y qué artistas?", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text("Toca los que te gustan o escribe el nombre de otro.", color = Palette.muted)
                            OutlinedTextField(
                                custom, { custom = it }, singleLine = true, shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().onFocusChanged { app.typing = it.isFocused },
                                placeholder = { Text("Escribe un artista…") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    val n = custom.trim(); if (n.isNotEmpty() && n !in artists) artists.add(n); custom = ""
                                }),
                            )
                            LazyVerticalGrid(
                                GridCells.Adaptive(150.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f),
                            ) {
                                items((artists.toList() + Taste.suggestedArtists(genres.toList())).distinct(), key = { it }) { a ->
                                    val on = a in artists
                                    val bg by animateColorAsState(if (on) Palette.rose else Color.White.copy(alpha = 0.08f), tween(200), label = "abg")
                                    Box(
                                        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(bg)
                                            .clickable { if (on) artists.remove(a) else artists.add(a) },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(a, maxLines = 1, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp)) }
                                }
                            }
                        }
                        else -> {
                            Text("Conecta tu YouTube Music", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Trae tus «Me gusta», tus listas y tu historial, y lo que marques aquí se refleja en tu cuenta. " +
                                    "Puedes hacerlo ahora o más tarde desde el menú «YouTube Music».",
                                color = Palette.muted, lineHeight = 22.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { app.finishOnboarding(genres.toList(), artists.toList()); app.goTo(Screen.YouTube) },
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.rose, contentColor = Color.White),
                            ) { Icon(Icons.Filled.Cloud, null); Spacer(Modifier.width(8.dp)); Text("Conectar ahora") }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i ->
                        val w by animateDpAsState(if (i == step) 26.dp else 8.dp, tween(250), label = "dot")
                        val c by animateColorAsState(if (i == step) Palette.rose else Color.White.copy(alpha = 0.25f), tween(250), label = "dotc")
                        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                    }
                }
                if (step > 0) TextButton(onClick = { step-- }) { Text("Atrás") }
                Button(
                    onClick = { if (step < 2) step++ else app.finishOnboarding(genres.toList(), artists.toList()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.rose, contentColor = Color.White),
                ) { Text(if (step < 2) "Siguiente" else "Empezar") }
            }
        }
    }
}

@Composable
fun NewListDialog(app: AppState, track: Track) {
    var name by remember { mutableStateOf("") }
    val done = {
        val n = name.trim()
        if (n.isNotEmpty()) { app.library.createPlaylist(n); app.library.addToPlaylist(n, track) }
        app.typing = false
        app.newListFor = null
    }
    AlertDialog(
        onDismissRequest = { app.typing = false; app.newListFor = null },
        title = { Text("Nueva lista") },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.onFocusChanged { app.typing = it.isFocused },
                placeholder = { Text("Nombre de la lista") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { done() }),
            )
        },
        confirmButton = { TextButton(onClick = { done() }, enabled = name.isNotBlank()) { Text("Crear y añadir") } },
        dismissButton = { TextButton(onClick = { app.typing = false; app.newListFor = null }) { Text("Cancelar") } },
        containerColor = Palette.surfaceHigh,
    )
}

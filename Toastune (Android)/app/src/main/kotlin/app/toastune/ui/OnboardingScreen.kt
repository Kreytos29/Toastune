package app.toastune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.toastune.R
import app.toastune.data.Taste

/** Pantalla de bienvenida: géneros → artistas → cuenta de YouTube Music. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    initialGenres: List<String>,
    initialArtists: List<String>,
    accountName: String?,
    onLogin: () -> Unit,
    onFinish: (genres: List<String>, artists: List<String>) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val genres = remember { mutableStateListOf<String>().apply { addAll(initialGenres) } }
    val artists = remember { mutableStateListOf<String>().apply { addAll(initialArtists) } }
    BackHandler(enabled = step > 0) { step-- }

    Box(Modifier.fillMaxSize().blockTouches().background(MaterialTheme.colorScheme.background)) {
        AuroraBackground(MaterialTheme.colorScheme.primary, intensity = 1f)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_logo), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text("Toastune", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i ->
                        val c by animateColorAsState(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), label = "dot")
                        Box(Modifier.size(width = if (i == step) 22.dp else 8.dp, height = 8.dp).clip(CircleShape).background(c))
                    }
                }
            }

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it / 6 * dir }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(250)) { -it / 6 * dir })
                },
                label = "onboardingStep",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 12.dp)) {
                    when (s) {
                        0 -> {
                            Text("¿Qué te gusta escuchar?", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(8.dp))
                            Text("Elige los géneros que quieras. Con eso armamos tu inicio.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Taste.genres.forEach { g ->
                                    BigChip(g.label, g.label in genres) { if (g.label in genres) genres.remove(g.label) else genres.add(g.label) }
                                }
                            }
                        }
                        1 -> {
                            Text("¿Y qué artistas?", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(8.dp))
                            Text("Toca los que te gustan o escribe el nombre de otro.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(20.dp))
                            AddArtistField { name -> if (name !in artists) artists.add(name) }
                            Spacer(Modifier.height(16.dp))
                            // Sugeridos para los géneros elegidos + los que el usuario escribió.
                            val shown = (artists + Taste.suggestedArtists(genres)).distinct()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                shown.forEach { a ->
                                    BigChip(a, a in artists) { if (a in artists) artists.remove(a) else artists.add(a) }
                                }
                            }
                        }
                        else -> {
                            Text("Conecta tu YouTube Music", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Trae tus canciones favoritas, tu historial y tus playlists. Es opcional, puedes hacerlo luego en Ajustes.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(32.dp))
                            if (accountName != null) {
                                Row(
                                    Modifier.fillMaxWidth().frost(RoundedCornerShape(24.dp)).padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.size(14.dp))
                                    Column {
                                        Text("Conectado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Text(accountName, style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            } else {
                                PrimaryButton("Iniciar sesión con YouTube Music", onLogin, Modifier.fillMaxWidth(), icon = Icons.Rounded.LibraryMusic)
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step < 2) {
                    Text(
                        "Saltar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).bounceClick { if (step == 0) step = 1 else step = 2 }.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    if (step < 2) "Siguiente" else if (accountName != null) "Empezar" else "Ahora no, empezar",
                    { if (step < 2) step++ else onFinish(genres.toList(), artists.toList()) },
                    filled = step < 2 || accountName != null,
                )
            }
        }
    }
}

@Composable
private fun BigChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "bigChipBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, label = "bigChipFg")
    Row(
        Modifier.frost(CircleShape).background(bg).bounceClick(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp).padding(end = 0.dp), tint = fg)
            Spacer(Modifier.size(6.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = fg)
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            .then(if (filled) Modifier.background(scheme.primary) else Modifier.frost(CircleShape))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (filled) scheme.onPrimary else scheme.onSurface)
            Spacer(Modifier.size(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (filled) scheme.onPrimary else scheme.onSurface, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AddArtistField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun add() {
        val name = text.trim().replace("|", "")
        if (name.isNotEmpty()) { onAdd(name); text = "" }
    }
    Row(Modifier.fillMaxWidth().frost(CircleShape).padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (text.isEmpty()) Text("Escribe un artista…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlassIconButton(Icons.Rounded.Add, "Añadir", { add() }, size = 42.dp)
    }
}

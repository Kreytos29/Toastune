package app.toastune.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun YouTubeScreen(app: AppState) {
    val auth by app.ytStore.state.collectAsState()
    SmoothList(spacing = 16.dp) {
        item {
            Column(Modifier.enter(0)) {
                Text("YouTube Music", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Importa tus «Me gusta», mira tus listas y haz que lo que marques aquí se refleje en tu cuenta.",
                    color = Palette.muted,
                )
            }
        }
        if (auth == null) item { LoginCard(app) } else {
            item { AccountCard(app, auth!!) }
            item {
                Column(Modifier.enter(2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Tu música", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    ItemRow(null, "Canciones que me gustan", "Tu lista «Me gusta» de YouTube Music") { app.openYtList("liked", "Me gusta") }
                    ItemRow(null, "Historial", "Lo último que escuchaste") { app.openYtList("history", "Historial") }
                }
            }
            item {
                Text("Tus listas", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.enter(3))
            }
            if (app.ytPlaylists.isEmpty()) item { Text("No se encontraron listas todavía.", color = Palette.muted) }
            itemsIndexed(app.ytPlaylists, key = { _, p -> p.id }) { i, p ->
                ItemRow(p.thumbnailUrl, p.title, p.subtitle.ifBlank { "Lista" }, Modifier.enter(i + 3)) { app.openYtList(p.id, p.title) }
            }
        }
    }
}

@Composable
private fun Card(index: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.enter(index).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
private fun LoginCard(app: AppState) {
    var cookie by remember { mutableStateOf("") }
    Card(1) {
        Text("Conectar tu cuenta", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.blue)
        Text(
            "1. Abre music.youtube.com en tu navegador e inicia sesión.\n" +
                "2. Pulsa F12 → pestaña «Red» (Network) → recarga la página.\n" +
                "3. Elige cualquier petición a music.youtube.com, busca en «Cabeceras de la petición» la línea «Cookie» y copia su valor completo.\n" +
                "4. Pégalo aquí abajo.",
            color = Palette.muted, lineHeight = 22.sp,
        )
        OutlinedTextField(
            cookie, { cookie = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 4,
            placeholder = { Text("Pega aquí el valor de Cookie") },
            visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp),
        )
        Text(
            "La cookie se guarda solo en tu equipo (~/.local/share/toastune/yt_account.json) y únicamente se envía a YouTube.",
            color = Palette.muted.copy(alpha = 0.7f), fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { app.connectYt(cookie) }, enabled = cookie.isNotBlank() && !app.ytBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.rose, contentColor = Color.White),
            ) { Text("Conectar") }
            if (app.ytBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            app.ytMessage?.let { Text(it, color = Palette.blue, fontSize = 13.sp, modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun AccountCard(app: AppState, auth: YtAuth) {
    Card(1) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Filled.AccountCircle, null, tint = Palette.blue, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(auth.name ?: "Cuenta conectada", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                auth.email?.let { Text(it, color = Palette.muted, fontSize = 13.sp) }
            }
            OutlinedButton(onClick = { app.disconnectYt() }) { Text("Cerrar sesión") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { app.importYtLikes() }, enabled = !app.ytBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.rose, contentColor = Color.White),
            ) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Importar Me gusta a Favoritos") }
            OutlinedButton(onClick = { app.loadYtPlaylists() }) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Actualizar listas") }
            if (app.ytBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        app.ytMessage?.let { Text(it, color = Palette.blue, fontSize = 13.sp) }
        Text("Tus favoritos, álbumes guardados y las canciones que añadas a listas de YouTube se sincronizan con tu cuenta.", color = Palette.muted, fontSize = 13.sp)
    }
}

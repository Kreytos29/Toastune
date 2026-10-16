package app.toastune.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

private const val READ_CONFIG_JS = """(function(){try{var c=(window.yt&&window.yt.config_)||{};
var g=(window.ytcfg&&window.ytcfg.get)?window.ytcfg:null;
var v=c.VISITOR_DATA||(g&&g.get('VISITOR_DATA'))||'';
var s=(c.SESSION_INDEX!==undefined)?c.SESSION_INDEX:((g&&g.get('SESSION_INDEX'))||0);
return v+'|'+s;}catch(e){return '|0';}})()"""

private suspend fun WebView.eval(script: String): String = suspendCancellableCoroutine { c ->
    evaluateJavascript(script) { c.resume(it?.trim('"').orEmpty()) }
}

/**
 * Inicio de sesión como en Metrolist: se abre el login de Google dentro de un WebView y, al llegar a
 * music.youtube.com con sesión, se leen la cookie y el visitorData. La contraseña nunca pasa por Toastune.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtLoginScreen(onClose: () -> Unit, onCaptured: (cookie: String, visitorData: String, authUser: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().blockTouches().padding(start = 12.dp, top = 8.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.Close, "Cerrar", onClose)
            Spacer(Modifier.width(14.dp))
            Text("Iniciar sesión en YouTube Music", style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        val web = this
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(web, true)
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Google rechaza el login si el agente de usuario delata un WebView.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        webViewClient = object : WebViewClient() {
                            private var capturing = false

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                if (capturing || url == null || !url.startsWith("https://music.youtube.com")) return
                                val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
                                if (!cookie.contains("SAPISID")) return
                                capturing = true
                                scope.launch { capture(view, cookie, onCaptured) { finishing = true } }
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
                onRelease = { it.destroy() },
            )
            if (finishing) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

private suspend fun capture(
    view: WebView,
    cookie: String,
    onCaptured: (String, String, String) -> Unit,
    onBusy: () -> Unit,
) {
    onBusy()
    // La configuración de la página tarda un poco en estar lista: se reintenta unos segundos.
    var visitor = ""
    var index = "0"
    for (attempt in 0 until 12) {
        val parts = view.eval(READ_CONFIG_JS).split("|")
        visitor = parts.getOrNull(0).orEmpty()
        index = parts.getOrNull(1)?.filter { it.isDigit() }?.ifEmpty { "0" } ?: "0"
        if (visitor.isNotEmpty()) break
        delay(500)
    }
    onCaptured(cookie, visitor, index)
    // No se deja la sesión de Google abierta dentro del WebView.
    CookieManager.getInstance().removeAllCookies(null)
    WebStorage.getInstance().deleteAllData()
}

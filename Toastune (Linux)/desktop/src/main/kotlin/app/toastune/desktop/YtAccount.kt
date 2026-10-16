package app.toastune.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/** Sesión de YouTube Music: cookie + visitorData + índice de cuenta (como Metrolist). */
data class YtAuth(
    val cookie: String,
    val visitorData: String = "",
    val authUser: String = "0",
    val name: String? = null,
    val email: String? = null,
)

/** Guarda la sesión en ~/.local/share/toastune/yt_account.json, legible solo por tu usuario. */
class YtAccountStore {
    private val file: Path = Path.of(
        System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"),
        "toastune", "yt_account.json",
    )
    private val _state = MutableStateFlow(read())
    val state: StateFlow<YtAuth?> = _state

    private fun read(): YtAuth? = try {
        val o = JSONObject(Files.readString(file))
        YtAuth(
            cookie = o.getString("cookie"), visitorData = o.optString("visitor"),
            authUser = o.optString("authUser", "0"),
            name = o.optString("name").ifEmpty { null }, email = o.optString("email").ifEmpty { null },
        )
    } catch (_: Exception) { null }

    fun save(a: YtAuth) {
        try {
            Files.createDirectories(file.parent)
            val o = JSONObject().put("cookie", a.cookie).put("visitor", a.visitorData).put("authUser", a.authUser)
                .put("name", a.name ?: "").put("email", a.email ?: "")
            Files.writeString(file, o.toString())
            try { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")) } catch (_: Exception) {}
        } catch (_: Exception) {}
        _state.value = a
    }

    fun clear() {
        try { Files.deleteIfExists(file) } catch (_: Exception) {}
        _state.value = null
    }
}

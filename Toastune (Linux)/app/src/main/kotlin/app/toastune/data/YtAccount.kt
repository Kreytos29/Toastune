package app.toastune.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sesión de YouTube Music: lo mismo que guarda Metrolist (cookie + visitorData + índice de cuenta). */
data class YtAuth(
    val cookie: String,
    val visitorData: String,
    val authUser: String = "0",
    val name: String? = null,
    val email: String? = null,
)

/** Guarda la sesión en el almacenamiento privado de la app. Nunca entra en la copia de seguridad. */
class YtAccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("yt_account", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<YtAuth?> = _state.asStateFlow()

    private fun read(): YtAuth? {
        val cookie = prefs.getString("cookie", null) ?: return null
        return YtAuth(
            cookie = cookie,
            visitorData = prefs.getString("visitor", "").orEmpty(),
            authUser = prefs.getString("authUser", "0").orEmpty(),
            name = prefs.getString("name", null),
            email = prefs.getString("email", null),
        )
    }

    fun save(a: YtAuth) {
        prefs.edit()
            .putString("cookie", a.cookie).putString("visitor", a.visitorData).putString("authUser", a.authUser)
            .putString("name", a.name).putString("email", a.email)
            .apply()
        _state.value = a
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = null
    }
}

package app.toastune.data

import android.content.Context
import app.toastune.core.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    /** Desenfoque real del fondo (cristal). Si está apagado se usa un panel translúcido, más ligero. */
    val glass: Boolean = false,
    /** Tiñe el fondo y los controles con el color de la portada. */
    val coverColors: Boolean = true,
    val streamQuality: AudioQuality = AudioQuality.HIGH,
    val downloadQuality: AudioQuality = AudioQuality.HIGH,
    val wifiOnlyDownloads: Boolean = true,
    /** Tamaño máximo de la caché de música reproducida. Se aplica al reiniciar la app. */
    val cacheMb: Int = 512,
    val autoplay: Boolean = true,
    /** Fuente OpenDyslexic y más espacio entre letras y líneas. */
    val dyslexic: Boolean = false,
    /** Sincronizar con Google Drive (carpeta oculta de la app). */
    val driveSync: Boolean = false,
    val lastSyncMs: Long = 0L,
    /** Ya pasó por la pantalla de bienvenida (géneros y artistas). */
    val onboarded: Boolean = false,
    val genres: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    /** Ecualizador de 5 bandas; [eqLevels] en dB (de -12 a 12), vacío = plano. */
    val eqEnabled: Boolean = false,
    val eqLevels: List<Int> = emptyList(),
)

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            theme = enumOf(prefs.getString("theme", null), d.theme),
            glass = prefs.getBoolean("glass2", d.glass),
            coverColors = prefs.getBoolean("coverColors", d.coverColors),
            streamQuality = enumOf(prefs.getString("streamQuality", null), d.streamQuality),
            downloadQuality = enumOf(prefs.getString("downloadQuality", null), d.downloadQuality),
            wifiOnlyDownloads = prefs.getBoolean("wifiOnly", d.wifiOnlyDownloads),
            cacheMb = prefs.getInt("cacheMb", d.cacheMb),
            autoplay = prefs.getBoolean("autoplay", d.autoplay),
            dyslexic = prefs.getBoolean("dyslexic", d.dyslexic),
            driveSync = prefs.getBoolean("driveSync", d.driveSync),
            lastSyncMs = prefs.getLong("lastSyncMs", d.lastSyncMs),
            onboarded = prefs.getBoolean("onboarded", d.onboarded),
            genres = prefs.getString("genres", "").orEmpty().split("|").filter { it.isNotBlank() },
            artists = prefs.getString("artists", "").orEmpty().split("|").filter { it.isNotBlank() },
            eqEnabled = prefs.getBoolean("eqEnabled", d.eqEnabled),
            eqLevels = prefs.getString("eqLevels", "").orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() },
        )
    }

    private fun write(s: Settings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("glass2", s.glass)
            .putBoolean("coverColors", s.coverColors)
            .putString("streamQuality", s.streamQuality.name)
            .putString("downloadQuality", s.downloadQuality.name)
            .putBoolean("wifiOnly", s.wifiOnlyDownloads)
            .putInt("cacheMb", s.cacheMb)
            .putBoolean("autoplay", s.autoplay)
            .putBoolean("dyslexic", s.dyslexic)
            .putBoolean("driveSync", s.driveSync)
            .putLong("lastSyncMs", s.lastSyncMs)
            .putBoolean("onboarded", s.onboarded)
            .putString("genres", s.genres.joinToString("|"))
            .putString("artists", s.artists.joinToString("|"))
            .putBoolean("eqEnabled", s.eqEnabled)
            .putString("eqLevels", s.eqLevels.joinToString(","))
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}

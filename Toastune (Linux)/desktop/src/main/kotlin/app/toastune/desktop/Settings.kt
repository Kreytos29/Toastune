package app.toastune.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

data class SettingsData(
    val autoplay: Boolean = true,
    val highQuality: Boolean = true,
    val eqOn: Boolean = false,
    val eqLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val volume: Int = 100,
    val dyslexic: Boolean = false,
    val coverColors: Boolean = true,
    val downloadHigh: Boolean = true,
    val onboarded: Boolean = false,
    val closeToTray: Boolean = true,
    val crossfade: Int = 0,
    val mixStyle: Int = 0,
    val mixOnSkip: Boolean = true,
    val mixTrim: Boolean = true,
    val lyricSize: Int = 30,
)

object EqPresets {
    val bands = listOf(60, 230, 910, 3600, 14000)
    val presets = linkedMapOf(
        "Plano" to listOf(0, 0, 0, 0, 0),
        "Bajos" to listOf(7, 5, 0, -1, -1),
        "Voces" to listOf(-2, 0, 3, 4, 1),
        "Agudos" to listOf(-2, -1, 0, 4, 7),
        "Rock" to listOf(5, 3, -1, 3, 5),
        "Pop" to listOf(-1, 2, 4, 2, -1),
    )
}

class AppSettings {
    private val file: Path = Path.of(
        System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"),
        "toastune", "settings.json",
    )
    private val _data = MutableStateFlow(load())
    val data: StateFlow<SettingsData> = _data

    fun update(f: (SettingsData) -> SettingsData) {
        _data.value = f(_data.value)
        save()
    }

    private fun load(): SettingsData = try {
        val o = JSONObject(Files.readString(file))
        val d = SettingsData()
        val eq = o.optJSONArray("eq")
        SettingsData(
            autoplay = o.optBoolean("autoplay", d.autoplay),
            highQuality = o.optBoolean("highQuality", d.highQuality),
            eqOn = o.optBoolean("eqOn", d.eqOn),
            eqLevels = if (eq != null && eq.length() == 5) (0 until 5).map { eq.getInt(it) } else d.eqLevels,
            volume = o.optInt("volume", d.volume),
            dyslexic = o.optBoolean("dyslexic", d.dyslexic),
            coverColors = o.optBoolean("coverColors", d.coverColors),
            downloadHigh = o.optBoolean("downloadHigh", d.downloadHigh),
            onboarded = o.optBoolean("onboarded", d.onboarded),
            closeToTray = o.optBoolean("closeToTray", d.closeToTray),
            crossfade = o.optInt("crossfade", d.crossfade),
            mixStyle = o.optInt("mixStyle", d.mixStyle),
            mixOnSkip = o.optBoolean("mixOnSkip", d.mixOnSkip),
            mixTrim = o.optBoolean("mixTrim", d.mixTrim),
            lyricSize = o.optInt("lyricSize", d.lyricSize).coerceIn(20, 48),
        )
    } catch (_: Exception) { SettingsData() }

    private fun save() {
        try {
            val d = _data.value
            val o = JSONObject().put("autoplay", d.autoplay).put("highQuality", d.highQuality)
                .put("eqOn", d.eqOn).put("eq", JSONArray(d.eqLevels)).put("volume", d.volume).put("dyslexic", d.dyslexic).put("coverColors", d.coverColors).put("downloadHigh", d.downloadHigh).put("onboarded", d.onboarded).put("closeToTray", d.closeToTray).put("crossfade", d.crossfade).put("mixStyle", d.mixStyle).put("mixOnSkip", d.mixOnSkip).put("mixTrim", d.mixTrim).put("lyricSize", d.lyricSize)
            Files.createDirectories(file.parent)
            Files.writeString(file, o.toString())
        } catch (_: Exception) {}
    }
}

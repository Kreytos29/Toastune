package app.toastune.playback

import android.media.audiofx.Equalizer
import app.toastune.data.Settings
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ln

/**
 * Ecualizador del sistema enganchado a la sesión de audio del reproductor. La interfaz tiene 5 bandas
 * fijas ([FREQS]); cada banda real del teléfono toma el valor de la más cercana, así funciona igual
 * en dispositivos con 5, 6 o 10 bandas.
 */
class EqualizerController {

    // Crear y configurar efectos de audio habla con el servidor de audio del sistema y en algunos
    // teléfonos se queda esperando segundos: nunca en el hilo principal (provocaba "no responde").
    private val worker = Executors.newSingleThreadExecutor()
    private var eq: Equalizer? = null
    private var boundSession = 0

    @Volatile private var session = 0
    @Volatile private var last: Settings? = null

    fun attach(sessionId: Int) {
        session = sessionId
        worker.execute(::sync)
    }

    fun apply(s: Settings) {
        last = s
        worker.execute(::sync)
    }

    /** Libera el efecto y cierra el hilo; no se vuelve a usar después. */
    fun release() {
        worker.execute(::releaseNow)
        worker.shutdown()
    }

    private fun sync() {
        val s = last
        val id = session
        // Con el ecualizador apagado ni siquiera se crea el efecto.
        if (s == null || !s.eqEnabled || id <= 0) {
            releaseNow()
            return
        }
        if (eq == null || boundSession != id) {
            releaseNow()
            eq = runCatching { Equalizer(0, id) }.getOrNull()
            boundSession = id
        }
        val e = eq ?: return
        runCatching {
            e.enabled = true
            val range = e.bandLevelRange // milibelios
            for (band in 0 until e.numberOfBands) {
                val centerHz = e.getCenterFreq(band.toShort()) / 1000.0
                val nearest = FREQS.indices.minByOrNull { abs(ln(centerHz / FREQS[it])) } ?: 0
                val db = s.eqLevels.getOrElse(nearest) { 0 }
                e.setBandLevel(band.toShort(), (db * 100).coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
        }
    }

    private fun releaseNow() {
        runCatching { eq?.release() }
        eq = null
        boundSession = 0
    }

    companion object {
        val FREQS = doubleArrayOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
        val LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        val PRESETS: List<Pair<String, List<Int>>> = listOf(
            "Plano" to listOf(0, 0, 0, 0, 0),
            "Bajos" to listOf(7, 5, 1, 0, 0),
            "Voces" to listOf(-2, 1, 4, 3, 0),
            "Agudos" to listOf(0, 0, 1, 4, 6),
            "Rock" to listOf(5, 3, -1, 3, 5),
            "Pop" to listOf(-1, 2, 4, 2, -1),
        )
    }
}

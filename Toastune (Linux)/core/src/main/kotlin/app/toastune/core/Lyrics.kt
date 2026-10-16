package app.toastune.core

data class LyricLine(val timeMs: Long, val text: String)

/** [synced] indica que cada línea trae su marca de tiempo; si no, es texto plano. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

interface LyricsProvider {
    suspend fun lyrics(title: String, artist: String, durationMs: Long?): Lyrics?
}

/** Lector de formato LRC: líneas como "[01:23.45] texto" (admite varias marcas por línea). */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (raw in lrc.lines()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fraction = m.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                result += LyricLine(minutes * 60_000 + seconds * 1_000 + millis, text)
            }
        }
        return result.sortedBy { it.timeMs }
    }
}

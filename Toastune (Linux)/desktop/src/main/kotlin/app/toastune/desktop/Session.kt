package app.toastune.desktop

import app.toastune.core.Track
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

/** Recuerda la cola al cerrar para retomarla al abrir. */
object Session {
    private val file: Path = Path.of(
        System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"),
        "toastune", "session.json",
    )

    fun save(q: QueueState) {
        try {
            val arr = JSONArray()
            q.queue.take(300).forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)
                        .put("durationMs", it.durationMs ?: JSONObject.NULL).put("thumb", it.thumbnailUrl ?: JSONObject.NULL),
                )
            }
            Files.createDirectories(file.parent)
            Files.writeString(file, JSONObject().put("queue", arr).put("index", q.index).toString())
        } catch (_: Exception) {}
    }

    fun load(): Pair<List<Track>, Int>? = try {
        val o = JSONObject(Files.readString(file))
        val a = o.getJSONArray("queue")
        val list = (0 until a.length()).map {
            val t = a.getJSONObject(it)
            Track(
                t.getString("id"), t.getString("title"), t.optString("artist"),
                if (t.isNull("durationMs")) null else t.getLong("durationMs"),
                if (t.isNull("thumb")) null else t.getString("thumb"),
            )
        }
        if (list.isEmpty()) null else list to o.optInt("index", 0)
    } catch (_: Exception) { null }
}

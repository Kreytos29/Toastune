package app.toastune.core

/** Proveedor de prueba con dos canciones de demostración, útil para probar el reproductor sin YouTube. */
class DemoProvider : MusicProvider {
    override val name = "Demo"
    private val base = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-"

    private val songs = listOf(
        Track("1", "Demo Song 1", "SoundHelix"),
        Track("2", "Demo Song 2", "SoundHelix"),
    )

    override suspend fun search(query: String, filter: SearchFilter) = SearchPage(
        songs.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) },
    )

    override suspend fun loadMore(continuation: Any) = SearchPage(emptyList())

    override suspend fun suggestions(query: String): List<String> = emptyList()

    override suspend fun getStreamUrl(trackId: String, quality: AudioQuality) = "$base$trackId.mp3"

    override suspend fun related(trackId: String): List<Track> = songs.filter { it.id != trackId }
}

package app.toastune.core

data class Artist(val id: String, val name: String, val thumbnailUrl: String? = null)

/** Lo que se muestra en la página de un artista: sus canciones más escuchadas y sus álbumes. */
data class ArtistPage(val songs: List<Track>, val albums: List<Album>)

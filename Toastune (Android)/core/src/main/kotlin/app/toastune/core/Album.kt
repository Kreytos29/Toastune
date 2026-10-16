package app.toastune.core

/** [id] es el identificador de la lista de reproducción del álbum (OLAK5uy_…). */
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
)

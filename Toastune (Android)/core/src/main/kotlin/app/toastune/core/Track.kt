package app.toastune.core

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
)

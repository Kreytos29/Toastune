package app.toastune.desktop

/** Géneros que se ofrecen al empezar, con una búsqueda para llenar el inicio y artistas conocidos para elegir. */
object Taste {
    class Genre(val label: String, val query: String, val artists: List<String>)

    val genres = listOf(
        Genre("Cumbia", "cumbia éxitos", listOf("Grupo 5", "Agua Marina", "Corazón Serrano", "Armonía 10", "Hermanos Yaipén", "Los Ángeles Azules", "Los Mirlos", "Amar Azul")),
        Genre("Trap", "trap latino éxitos", listOf("Duki", "Anuel AA", "Eladio Carrión", "Bad Bunny", "Lunay", "Jhayco", "Myke Towers", "Rauw Alejandro")),
        Genre("Rap", "rap en español éxitos", listOf("Paulo Londra", "Trueno", "Residente", "Nach", "Eminem", "Kendrick Lamar", "Drake", "J. Cole")),
        Genre("Reguetón", "reggaetón éxitos", listOf("Daddy Yankee", "Karol G", "Feid", "J Balvin", "Ozuna", "Maluma", "Bad Bunny", "Don Omar")),
        Genre("Pop", "pop latino éxitos", listOf("Shakira", "Dua Lipa", "Bruno Mars", "The Weeknd", "Ed Sheeran", "Taylor Swift", "Sebastián Yatra", "Camilo")),
        Genre("Salsa", "salsa clásicos", listOf("Marc Anthony", "Celia Cruz", "Héctor Lavoe", "Grupo Niche", "Willie Colón", "Gilberto Santa Rosa")),
        Genre("Rock", "rock en español clásicos", listOf("Soda Stereo", "Maná", "Zoé", "Caifanes", "Los Fabulosos Cadillacs", "Linkin Park", "Queen", "Imagine Dragons")),
        Genre("Baladas", "baladas románticas", listOf("Ricardo Arjona", "Luis Miguel", "Juan Gabriel", "Sin Bandera", "Alejandro Sanz", "Franco De Vita")),
        Genre("Electrónica", "música electrónica éxitos", listOf("David Guetta", "Calvin Harris", "Daft Punk", "Marshmello", "Alan Walker", "Martin Garrix")),
        Genre("Regional mexicano", "regional mexicano éxitos", listOf("Peso Pluma", "Fuerza Regida", "Grupo Frontera", "Junior H", "Natanael Cano", "Eslabon Armado")),
        Genre("Indie", "indie alternativo", listOf("Arctic Monkeys", "Tame Impala", "Cigarettes After Sex", "Mac DeMarco", "Beach House", "Cuarteto de Nos")),
        Genre("K-pop", "k-pop éxitos", listOf("BTS", "BLACKPINK", "Stray Kids", "NewJeans", "TWICE", "SEVENTEEN")),
    )

    fun genre(label: String): Genre? = genres.firstOrNull { it.label == label }

    /** Artistas sugeridos para los géneros elegidos (o los más populares si no eligió ninguno). */
    fun suggestedArtists(selected: List<String>): List<String> {
        val source = selected.mapNotNull(::genre).ifEmpty { genres.take(4) }
        return source.flatMap { it.artists }.distinct()
    }
}

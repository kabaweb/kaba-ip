package com.ipvio.stream

/**
 * Channels by genre (1.2): "Sports", "Movies", "Documentaries", "Kids", "News", "Music".
 * Detected from the playlist's category and channel name – not perfect, but no effort for you.
 */
object LiveGenre {
    class Genre(val key: String, val label: String, val re: Regex?)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    val ALL = listOf(
        Genre("GEN:sport", "Sport", null),   // sport is detected by SportLogic
        Genre("GEN:movies", "Filme", rx("""\b(films?|movies?|cinema\w*|kino|cine\w*|sinema\w*|filmovi|cinemax|hbo|serien|series|serije|dizi|telecine|megapix|maxprime)\b""")),
        Genre("GEN:doku", "Dokumentationen", rx("""documentar\w*|\bdoku\w*|discovery|nat\s?geo|national\s?geographic|\bhistory\b|animal\s?planet|\bdmax\b|nautilus|explore|\bscience\b|dokumentar\w*""")),
        Genre("GEN:kids", "Kinder", rx("""\b(kids?|kinder|children|cartoon\w*|nick\w*|junior|baby\w*|toon\w*|boomerang|ragazzi|çocuk|djeca|dječji|kika|jetix|pogo|infant\w*|desenho\w*|gloob\w*|ra[- ]?tim[- ]?bum|zoomoo|animes?|disney)\b""")),
        Genre("GEN:news", "Nachrichten", rx("""\b(news|nachrichten|n-?tv|cnn|euronews|al\s?jazeera|tagesschau|welt|haber|vijesti|noticias|notizie|info(rmation)?|rtl\s?aktuell|jornalismo|cbn|bandnews|globonews|jovem\s?pan\s?news|record\s?news)\b""")),
        Genre("GEN:music", "Musik", rx("""\b(music\w*|musik|mtv\w*|vh1|viva|klubbing|radio|muzik\w*|música|musica|sertanejo|funk|pagode)\b"""))
    )

    fun byKey(k: String): Genre? = ALL.firstOrNull { it.key == k }

    fun isGenreKey(k: String) = k.startsWith("GEN:")

    fun matches(g: Genre, i: Item): Boolean {
        val isSport = SportLogic.isSportChannel(i)
        if (g.key == "GEN:sport") return isSport
        if (isSport) return false // Sports channels never leak into kids, movies, etc.
        return g.re?.let { it.containsMatchIn(i.group) || it.containsMatchIn(i.title) } ?: false
    }

    /** Channels per genre; empty genres are missing. */
    fun split(live: List<Item>): LinkedHashMap<String, List<Item>> {
        // The same list is asked again and again (every recomposition, every tab): remember the last result
        synchronized(this) { last.firstOrNull { it.first === live }?.let { return it.second } }
        val r = compute(live)
        // Three results: a filtered source in Live TV no longer evicts the prepared result of all channels
        synchronized(this) { last.add(0, live to r); while (last.size > 3) last.removeAt(last.size - 1) }
        return r
    }

    private val last = ArrayList<Pair<List<Item>, LinkedHashMap<String, List<Item>>>>()

    private fun compute(live: List<Item>): LinkedHashMap<String, List<Item>> {
        val out = LinkedHashMap<String, List<Item>>()
        for (g in ALL) {
            val l = live.filter { matches(g, it) }
            if (l.isNotEmpty()) out[g.key] = l
        }
        return out
    }
}

package com.ipvio.stream.shared.model

enum class Kind { LIVE, MOVIE, SERIES }

data class Variant(val url: String, val label: String, val quality: String?, val lang: String?)

data class Episode(
    val season: Int, val num: Int, val title: String, val url: String,
    val variants: List<Variant> = emptyList(), val quality: String? = null
)

data class Item(
    val id: String,
    val kind: Kind,
    val title: String,
    val year: Int?,
    val url: String,
    val logo: String?,
    val group: String,
    val episodes: List<Episode> = emptyList(),
    val quality: String? = null,
    val tvgId: String? = null,
    val lang: String = "?",
    val src: Int = 0
) {
    private var mkCache: String? = null
    val mk: String get() = mkCache ?: (kind.name + "|" + title.lowercase() + "|" + (year ?: 0)).also { mkCache = it }
}

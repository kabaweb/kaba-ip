package com.ipvio.stream.shared.meta

import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import com.ipvio.stream.shared.portal.SharedPortalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class SharedMeta(
    val tmdbId: Int,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String?,
    val rating: Double?,
    val year: String?,
    val genres: List<String> = emptyList()
)

object SharedMetaRepo {
    private val cache = ConcurrentHashMap<String, SharedMeta>()
    private var tmdbKey: String = ""

    fun setTmdbKey(key: String) {
        tmdbKey = key.trim()
    }

    suspend fun getMeta(item: Item): SharedMeta? {
        val cached = cache[item.id]
        if (cached != null) return cached

        return withContext(Dispatchers.IO) {
            val key = if (tmdbKey.isNotBlank()) tmdbKey else SharedPortalManager.tmdbKey
            if (key.isBlank()) {
                val basic = SharedMeta(
                    tmdbId = 0,
                    title = item.title,
                    poster = item.logo,
                    backdrop = item.logo,
                    overview = "Disponível na categoria ${item.group}",
                    rating = null,
                    year = item.year?.toString(),
                    genres = listOf(item.group)
                )
                cache[item.id] = basic
                return@withContext basic
            }

            try {
                val cleanTitle = item.title.replace(Regex("""\s*[\[\(].*?[\]\)]\s*"""), "").trim()
                val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
                val type = if (item.kind == Kind.SERIES) "tv" else "movie"
                val urlStr = "https://api.themoviedb.org/3/search/$type?api_key=$key&query=$encoded&language=pt-BR"

                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }

                    val idMatch = Regex(""""id"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val posterPath = Regex(""""poster_path"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                    val backdropPath = Regex(""""backdrop_path"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                    val overview = Regex(""""overview"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                    val vote = Regex(""""vote_average"\s*:\s*([0-9.]+)""").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                    val titleMatch = Regex(""""(?:name|title)"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                    val releaseMatch = Regex(""""(?:first_air_date|release_date)"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                    val year = releaseMatch?.take(4) ?: item.year?.toString()

                    val meta = SharedMeta(
                        tmdbId = idMatch,
                        title = titleMatch ?: item.title,
                        poster = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else item.logo,
                        backdrop = if (!backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w1280$backdropPath" else if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w1280$posterPath" else item.logo,
                        overview = if (!overview.isNullOrBlank()) overview else "Sem sinopse disponível.",
                        rating = if (vote != null && vote > 0.0) vote else null,
                        year = year,
                        genres = listOf(item.group)
                    )
                    cache[item.id] = meta
                    return@withContext meta
                }
            } catch (_: Exception) {}

            val fallback = SharedMeta(
                tmdbId = 0,
                title = item.title,
                poster = item.logo,
                backdrop = item.logo,
                overview = "Categoria: ${item.group}",
                rating = null,
                year = item.year?.toString(),
                genres = listOf(item.group)
            )
            cache[item.id] = fallback
            fallback
        }
    }
}

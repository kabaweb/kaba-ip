package com.ipvio.stream.shared.meta

import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import com.ipvio.stream.shared.portal.SharedPortalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class SharedPerson(val id: Int, val name: String, val role: String?, val photo: String?)
data class SharedRef(val tmdbId: Int, val title: String, val poster: String?, val year: String?)
data class SharedCollection(val id: Int, val name: String, val poster: String?, val parts: List<SharedRef>)

data class SharedCredits(
    val cast: List<SharedPerson> = emptyList(),
    val similar: List<SharedRef> = emptyList(),
    val collection: SharedCollection? = null
)

data class SharedMeta(
    val tmdbId: Int,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String?,
    val rating: Double?,
    val year: String?,
    val genres: List<String> = emptyList(),
    val credits: SharedCredits? = null
)

object SharedMetaRepo {
    private const val IMG = "https://image.tmdb.org/t/p/"
    private val cache = ConcurrentHashMap<String, SharedMeta>()
    private var tmdbKey: String = ""

    fun setTmdbKey(key: String) {
        tmdbKey = key.trim()
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun cached(item: Item): SharedMeta? = cache[item.id]

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
                val cleanTitle = item.title
                    .replace(Regex("""\s*[\[\(].*?[\]\)]\s*"""), "")
                    .replace(Regex("""(?i)\b(4k|fhd|hd|1080p|720p|dublado|legendado)\b"""), "")
                    .trim()
                val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
                val isSeries = item.kind == Kind.SERIES
                val type = if (isSeries) "tv" else "movie"
                val searchUrl = "https://api.themoviedb.org/3/search/$type?api_key=$key&query=$encoded&language=pt-BR"

                val searchRes = httpGet(searchUrl) ?: return@withContext null
                val sObj = JSONObject(searchRes)
                val results = sObj.optJSONArray("results") ?: return@withContext null
                if (results.length() == 0) return@withContext null

                val first = results.getJSONObject(0)
                val tmdbId = first.getInt("id")

                // Detalhes estendidos com credits, similar e coleção (append_to_response)
                val extUrl = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$key&append_to_response=credits,recommendations,similar&language=pt-BR"
                val extRes = httpGet(extUrl)

                val detailsObj = if (extRes != null) JSONObject(extRes) else first

                val title = detailsObj.optString(if (isSeries) "name" else "title", item.title)
                val overview = detailsObj.optString("overview", "").ifBlank { null }
                val rating = detailsObj.optDouble("vote_average", 0.0).takeIf { it > 0.0 }
                val releaseDate = detailsObj.optString(if (isSeries) "first_air_date" else "release_date", "")
                val year = releaseDate.take(4).ifBlank { item.year?.toString() }

                val posterPath = detailsObj.optString("poster_path", "").ifBlank { null }
                val backdropPath = detailsObj.optString("backdrop_path", "").ifBlank { null }

                val poster = if (posterPath != null) IMG + "w500" + posterPath else item.logo
                val backdrop = if (backdropPath != null) IMG + "original" + backdropPath else poster

                // Gêneros
                val genresList = ArrayList<String>()
                detailsObj.optJSONArray("genres")?.let { ga ->
                    for (i in 0 until ga.length()) {
                        genresList.add(ga.getJSONObject(i).optString("name"))
                    }
                }

                // Elenco (Cast)
                val castList = ArrayList<SharedPerson>()
                detailsObj.optJSONObject("credits")?.optJSONArray("cast")?.let { ca ->
                    for (i in 0 until minOf(ca.length(), 16)) {
                        val p = ca.getJSONObject(i)
                        val pPhoto = p.optString("profile_path", "").ifBlank { null }?.let { IMG + "w185" + it }
                        castList.add(
                            SharedPerson(
                                id = p.optInt("id"),
                                name = p.optString("name"),
                                role = p.optString("character").ifBlank { null },
                                photo = pPhoto
                            )
                        )
                    }
                }

                // Similares / Recomendações
                val similarList = ArrayList<SharedRef>()
                val simArray = detailsObj.optJSONObject("recommendations")?.optJSONArray("results")
                    ?: detailsObj.optJSONObject("similar")?.optJSONArray("results")
                simArray?.let { sa ->
                    for (i in 0 until minOf(sa.length(), 15)) {
                        val r = sa.getJSONObject(i)
                        val rId = r.optInt("id")
                        val rTitle = r.optString(if (isSeries) "name" else "title")
                        val rPoster = r.optString("poster_path", "").ifBlank { null }?.let { IMG + "w342" + it }
                        val rDate = r.optString(if (isSeries) "first_air_date" else "release_date", "").take(4)
                        if (rTitle.isNotBlank()) {
                            similarList.add(SharedRef(rId, rTitle, rPoster, rDate.ifBlank { null }))
                        }
                    }
                }

                // Coleção (Franquia de filmes: Harry Potter, Star Wars, Velozes e Furiosos, etc.)
                var collectionInfo: SharedCollection? = null
                val belongsToCol = detailsObj.optJSONObject("belongs_to_collection")
                if (belongsToCol != null) {
                    val colId = belongsToCol.optInt("id")
                    val colName = belongsToCol.optString("name")
                    val colPoster = belongsToCol.optString("poster_path", "").ifBlank { null }?.let { IMG + "w500" + it }

                    // Carrega todas as partes da franquia
                    val colUrl = "https://api.themoviedb.org/3/collection/$colId?api_key=$key&language=pt-BR"
                    val colRes = httpGet(colUrl)
                    val partsList = ArrayList<SharedRef>()
                    if (colRes != null) {
                        val colObj = JSONObject(colRes)
                        colObj.optJSONArray("parts")?.let { pa ->
                            for (i in 0 until pa.length()) {
                                val part = pa.getJSONObject(i)
                                val pId = part.optInt("id")
                                val pTitle = part.optString("title")
                                val pPost = part.optString("poster_path", "").ifBlank { null }?.let { IMG + "w342" + it }
                                val pYear = part.optString("release_date", "").take(4)
                                partsList.add(SharedRef(pId, pTitle, pPost, pYear.ifBlank { null }))
                            }
                        }
                    }
                    collectionInfo = SharedCollection(colId, colName, colPoster, partsList)
                }

                val meta = SharedMeta(
                    tmdbId = tmdbId,
                    title = title,
                    poster = poster,
                    backdrop = backdrop,
                    overview = overview,
                    rating = rating,
                    year = year,
                    genres = genresList.ifEmpty { listOf(item.group) },
                    credits = SharedCredits(castList, similarList, collectionInfo)
                )

                cache[item.id] = meta
                meta
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }
        }
    }
}

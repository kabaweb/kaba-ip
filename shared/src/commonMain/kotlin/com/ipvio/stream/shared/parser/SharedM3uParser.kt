package com.ipvio.stream.shared.parser

import com.ipvio.stream.shared.model.Episode
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Classificador e Agrupador idêntico ao Android M3uParser original:
 * 1. Séries: Agrupa episódios (S01 E01) sob o mesmo Item pai com lista de episódios
 * 2. Filmes: Arquivos /movie/ ou .mp4/.mkv
 * 3. Ao Vivo: Transmissões contínuas .ts/.m3u8 (mesmo que na categoria 'FILMES E SERIES')
 */
object SharedM3uParser {
    private val SE_RE = Regex("""^(.*?)[\\s._-]*[Ss](\\d{1,2})[\\s._-]*[Ee](\\d{1,4})(.*)$""", RegexOption.IGNORE_CASE)
    private val YEAR_RE = Regex("""[\(\[]((?:19|20)\d{2})[\)\]]""")
    private val VIDEO_FILE_RE = Regex("""\.(mp4|mkv|avi)(\?.*)?$""", RegexOption.IGNORE_CASE)

    private fun attr(line: String, key: String): String? {
        val tag = "$key=\""
        val at = line.indexOf(tag)
        if (at < 0) return null
        val from = at + tag.length
        val end = line.indexOf('"', from)
        return if (end < 0) null else line.substring(from, end)
    }

    private class SeriesAccumulator(
        val seriesTitle: String,
        val logo: String?,
        val group: String,
        val year: Int?
    ) {
        val episodes = ArrayList<Episode>()
    }

    fun parseStream(input: InputStream): List<Item> {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val moviesAndLive = ArrayList<Item>()
        val seriesMap = LinkedHashMap<String, SeriesAccumulator>()

        var info: String? = null
        var name: String? = null
        var group = "Geral"

        reader.useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.startsWith("#EXTINF")) {
                    info = line
                    name = line.substringAfterLast(',').trim()
                    group = attr(line, "group-title")?.ifBlank { null } ?: "Geral"
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    val inf = info
                    val n = name
                    if (inf != null && n != null) {
                        val isSeriesUrl = line.contains("/series/")
                        val seMatch = SE_RE.find(n)
                        val isMovieUrl = line.contains("/movie/") || VIDEO_FILE_RE.containsMatchIn(line)

                        val logo = attr(inf, "tvg-logo")?.ifBlank { null }
                        val tvgId = attr(inf, "tvg-id")?.ifBlank { null }
                        val year = YEAR_RE.find(n)?.groupValues?.get(1)?.toIntOrNull()

                        if (isSeriesUrl || seMatch != null) {
                            // Agrupamento de Séries (Padrão Android)
                            val cleanSeriesName = if (seMatch != null) {
                                seMatch.groupValues[1].trim().removeSuffix("-").trim()
                            } else {
                                n.substringBeforeLast("-").trim()
                            }
                            val seasonNum = seMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1
                            val epNum = seMatch?.groupValues?.get(3)?.toIntOrNull() ?: 1
                            val epExtraTitle = seMatch?.groupValues?.get(4)?.trim()?.removePrefix("-")?.trim().orEmpty()

                            val seriesKey = cleanSeriesName.lowercase()
                            val acc = seriesMap.getOrPut(seriesKey) {
                                SeriesAccumulator(
                                    seriesTitle = cleanSeriesName,
                                    logo = logo,
                                    group = group,
                                    year = year
                                )
                            }

                            acc.episodes.add(
                                Episode(
                                    season = seasonNum,
                                    num = epNum,
                                    title = if (epExtraTitle.isNotBlank()) epExtraTitle else "Episódio $epNum",
                                    url = line
                                )
                            )
                        } else if (isMovieUrl) {
                            moviesAndLive.add(
                                Item(
                                    id = line.hashCode().toString(),
                                    kind = Kind.MOVIE,
                                    title = n,
                                    year = year,
                                    url = line,
                                    logo = logo,
                                    group = group,
                                    tvgId = tvgId
                                )
                            )
                        } else {
                            moviesAndLive.add(
                                Item(
                                    id = line.hashCode().toString(),
                                    kind = Kind.LIVE,
                                    title = n,
                                    year = year,
                                    url = line,
                                    logo = logo,
                                    group = group,
                                    tvgId = tvgId
                                )
                            )
                        }
                    }
                    info = null
                    name = null
                }
            }
        }

        // Converte as séries acumuladas em Itens
        val finalSeries = seriesMap.values.map { acc ->
            Item(
                id = acc.seriesTitle.hashCode().toString(),
                kind = Kind.SERIES,
                title = acc.seriesTitle,
                year = acc.year,
                url = acc.episodes.firstOrNull()?.url ?: "",
                logo = acc.logo,
                group = acc.group,
                episodes = acc.episodes.sortedWith(compareBy({ it.season }, { it.num }))
            )
        }

        return moviesAndLive + finalSeries
    }
}

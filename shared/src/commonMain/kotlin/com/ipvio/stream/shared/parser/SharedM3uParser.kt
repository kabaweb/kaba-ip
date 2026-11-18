package com.ipvio.stream.shared.parser

import com.ipvio.stream.shared.model.Episode
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parser M3U oficial idêntico ao Android:
 * - Agrupamento e higienização profunda de Séries (S01E01, 1x01, T01E01, Ep 01)
 * - Filmes VOD (/movie/ ou extensões de vídeo .mp4/.mkv)
 * - Canais Ao Vivo (/live/ ou .ts/.m3u8)
 */
object SharedM3uParser {
    // Padrões de Temporada e Episódio
    private val SE_1 = Regex("""^(.*?)[\s._-]*[Ss](\d{1,2})[\s._-]*[Ee](\d{1,4})(.*)$""", RegexOption.IGNORE_CASE)
    private val SE_2 = Regex("""^(.*?)[\s._-]*(\d{1,2})[xX](\d{1,4})(.*)$""", RegexOption.IGNORE_CASE)
    private val SE_3 = Regex("""^(.*?)[\s._-]*[Tt](?:emp(?:orada)?)?\s*(\d{1,2})[\s._-]*[Ee](?:p(?:is[oó]dio)?)?\s*(\d{1,4})(.*)$""", RegexOption.IGNORE_CASE)
    private val SE_4 = Regex("""^(.*?)[\s._-]*[Ee](?:p(?:is[oó]dio)?)?\s*(\d{1,4})(.*)$""", RegexOption.IGNORE_CASE)

    private val YEAR_RE = Regex("""[\(\[]((?:19|20)\d{2})[\)\]]""")
    private val VIDEO_FILE_RE = Regex("""\.(mp4|mkv|avi)(\?.*)?$""", RegexOption.IGNORE_CASE)

    // Higienização de títulos (remove tags como [BR], (DUBLADO), 4K, FHD, etc.)
    private val PREFIX_TAGS = Regex("""^\s*\[[^\]]*\]\s*|^\s*\([^\)]*\)\s*|^\s*[A-Za-z]{2,3}\s*[|:]\s*""")
    private val BRACKETS = Regex("""[\(\[][^\)\]]*[\)\]]""")
    private val CLEAN_TAGS = Regex("""(?i)\b(4k|uhd|fhd|hd|sd|hevc|h265|1080p|720p|dublado|legendado|dual|multi|br|latino|en|pt|nacional|legendas)\b""")
    private val EXTRA_SPACES = Regex("""\s{2,}""")

    fun cleanTitle(raw: String): String {
        var t = raw.replace(PREFIX_TAGS, "")
        t = t.replace(BRACKETS, "")
        t = t.replace(CLEAN_TAGS, "")
        t = t.replace(EXTRA_SPACES, " ").trim().trim('-', '|', ':', '.', ' ')
        return if (t.isBlank()) raw.trim() else t
    }

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
        val liveChannels = ArrayList<Item>()
        val movies = ArrayList<Item>()
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
                        val isMovieUrl = line.contains("/movie/") || (!line.contains("/live/") && !line.endsWith(".ts") && VIDEO_FILE_RE.containsMatchIn(line))

                        val m1 = SE_1.find(n)
                        val m2 = if (m1 == null) SE_2.find(n) else null
                        val m3 = if (m1 == null && m2 == null) SE_3.find(n) else null
                        val m4 = if (m1 == null && m2 == null && m3 == null && isSeriesUrl) SE_4.find(n) else null

                        val isSeries = isSeriesUrl || m1 != null || m2 != null || m3 != null

                        val logo = attr(inf, "tvg-logo")?.ifBlank { null }
                        val tvgId = attr(inf, "tvg-id")?.ifBlank { null }
                        val year = YEAR_RE.find(n)?.groupValues?.get(1)?.toIntOrNull()

                        if (isSeries) {
                            val rawSeriesName: String
                            val seasonNum: Int
                            val epNum: Int
                            val epExtra: String

                            when {
                                m1 != null -> {
                                    rawSeriesName = m1.groupValues[1]
                                    seasonNum = m1.groupValues[2].toIntOrNull() ?: 1
                                    epNum = m1.groupValues[3].toIntOrNull() ?: 1
                                    epExtra = m1.groupValues[4].trim().trim('-', ' ', ':')
                                }
                                m2 != null -> {
                                    rawSeriesName = m2.groupValues[1]
                                    seasonNum = m2.groupValues[2].toIntOrNull() ?: 1
                                    epNum = m2.groupValues[3].toIntOrNull() ?: 1
                                    epExtra = m2.groupValues[4].trim().trim('-', ' ', ':')
                                }
                                m3 != null -> {
                                    rawSeriesName = m3.groupValues[1]
                                    seasonNum = m3.groupValues[2].toIntOrNull() ?: 1
                                    epNum = m3.groupValues[3].toIntOrNull() ?: 1
                                    epExtra = m3.groupValues[4].trim().trim('-', ' ', ':')
                                }
                                m4 != null -> {
                                    rawSeriesName = m4.groupValues[1]
                                    seasonNum = 1
                                    epNum = m4.groupValues[2].toIntOrNull() ?: 1
                                    epExtra = m4.groupValues[3].trim().trim('-', ' ', ':')
                                }
                                else -> {
                                    rawSeriesName = n.substringBeforeLast("-").trim()
                                    seasonNum = 1
                                    epNum = 1
                                    epExtra = ""
                                }
                            }

                            val cleanName = cleanTitle(rawSeriesName).ifBlank { rawSeriesName.trim() }
                            val seriesKey = cleanName.lowercase()

                            val acc = seriesMap.getOrPut(seriesKey) {
                                SeriesAccumulator(
                                    seriesTitle = cleanName,
                                    logo = logo,
                                    group = group,
                                    year = year
                                )
                            }

                            val epTitle = if (epExtra.isNotBlank()) "Episódio $epNum - $epExtra" else "Episódio $epNum"
                            acc.episodes.add(
                                Episode(
                                    season = seasonNum,
                                    num = epNum,
                                    title = epTitle,
                                    url = line
                                )
                            )
                        } else if (isMovieUrl) {
                            movies.add(
                                Item(
                                    id = line.hashCode().toString(),
                                    kind = Kind.MOVIE,
                                    title = cleanTitle(n).ifBlank { n },
                                    year = year,
                                    url = line,
                                    logo = logo,
                                    group = group,
                                    tvgId = tvgId
                                )
                            )
                        } else {
                            liveChannels.add(
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

        // Ordena episódios de cada série rigorosamente por temporada e número
        val finalizedSeries = seriesMap.values.map { acc ->
            acc.episodes.sortWith(compareBy({ it.season }, { it.num }))
            Item(
                id = "s_${acc.seriesTitle.hashCode()}",
                kind = Kind.SERIES,
                title = acc.seriesTitle,
                year = acc.year,
                url = acc.episodes.firstOrNull()?.url.orEmpty(),
                logo = acc.logo,
                group = acc.group,
                episodes = acc.episodes
            )
        }

        return liveChannels + movies + finalizedSeries
    }
}

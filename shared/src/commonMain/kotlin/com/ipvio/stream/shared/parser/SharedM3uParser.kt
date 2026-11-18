package com.ipvio.stream.shared.parser

import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Classificador idêntico ao Android M3uParser original:
 * 1. URLs com '/series/' ou padrão 'S01E01' -> SÉRIES
 * 2. URLs com '/movie/' ou extensão .mp4 / .mkv / .avi -> FILMES
 * 3. Canais ao vivo com .ts ou m3u8 (mesmo que o nome da categoria seja 'FILMES E SERIES') -> AO VIVO
 */
object SharedM3uParser {
    private val SE_RE = Regex("""^(.*?)[\s._-]*[Ss](\d{1,2})[\s._-]*[Ee](\d{1,4})""")
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

    fun parseStream(input: InputStream): List<Item> {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val items = ArrayList<Item>()
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
                        // Classificação exata do Android:
                        val isSeriesUrl = line.contains("/series/")
                        val hasSeasonEpisode = SE_RE.containsMatchIn(n)
                        val isMovieUrl = line.contains("/movie/") || VIDEO_FILE_RE.containsMatchIn(line)

                        val kind = when {
                            isSeriesUrl || hasSeasonEpisode -> Kind.SERIES
                            isMovieUrl -> Kind.MOVIE
                            else -> Kind.LIVE // Canais de TV .ts mesmo se a categoria for "FILMES E SERIES"
                        }

                        val logo = attr(inf, "tvg-logo")?.ifBlank { null }
                        val tvgId = attr(inf, "tvg-id")?.ifBlank { null }
                        val year = YEAR_RE.find(n)?.groupValues?.get(1)?.toIntOrNull()

                        val item = Item(
                            id = line.hashCode().toString(),
                            kind = kind,
                            title = n,
                            year = year,
                            url = line,
                            logo = logo,
                            group = group,
                            tvgId = tvgId
                        )
                        items.add(item)
                    }
                    info = null
                    name = null
                }
            }
        }
        return items
    }
}

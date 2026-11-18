package com.ipvio.stream.shared.parser

import com.ipvio.stream.shared.model.Episode
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object SharedM3uParser {
    private val SE_RE = Regex("""^(.*?)[\s._-]*[Ss](\d{1,2})[\s._-]*[Ee](\d{1,4})""")
    private val YEAR_RE = Regex("""[\(\[]((?:19|20)\d{2})[\)\]]""")
    private val VOD_GROUP = Regex("""film|movie|vod|kino|cinema""", RegexOption.IGNORE_CASE)

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
                        val isSeries = line.contains("/series/") || SE_RE.containsMatchIn(n)
                        val isMovie = line.contains("/movie/") || VOD_GROUP.containsMatchIn(group)
                        val kind = when {
                            isSeries -> Kind.SERIES
                            isMovie -> Kind.MOVIE
                            else -> Kind.LIVE
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

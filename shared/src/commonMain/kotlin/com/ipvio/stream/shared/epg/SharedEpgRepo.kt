package com.ipvio.stream.shared.epg

import com.ipvio.stream.shared.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class SharedEpgEntry(
    val title: String,
    val desc: String,
    val start: Long,
    val end: Long
)

object SharedEpgRepo {
    private val LIVE_RE = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.\w+)?(?:\?.*)?$""")
    private val cache = ConcurrentHashMap<String, Pair<Long, List<SharedEpgEntry>>>()

    fun xtreamApi(url: String, action: String = "get_short_epg"): String? {
        val p = LIVE_RE.find(url)?.groupValues ?: return null
        return "${p[1]}/player_api.php?username=${p[2]}&password=${p[3]}&action=$action&stream_id=${p[4]}"
    }

    private fun decode(s: String): String {
        if (s.isBlank()) return ""
        return try {
            val bytes = Base64.getDecoder().decode(s)
            val d = String(bytes, Charsets.UTF_8)
            if (d.isBlank() || d.contains('\uFFFD')) s else d
        } catch (_: Throwable) {
            s
        }
    }

    suspend fun getEpg(channel: Item): List<SharedEpgEntry> {
        val nowMs = System.currentTimeMillis()
        cache[channel.id]?.let {
            if (nowMs - it.first < 180_000) return it.second
        }

        return withContext(Dispatchers.IO) {
            val apiUrl = xtreamApi(channel.url) ?: return@withContext emptyList()
            try {
                val conn = URL("$apiUrl&limit=8").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode !in 200..299) return@withContext emptyList()

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val arr = obj.optJSONArray("epg_listings") ?: return@withContext emptyList()

                val list = ArrayList<SharedEpgEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val s = o.optString("start_timestamp").toLongOrNull() ?: continue
                    val e = o.optString("stop_timestamp").toLongOrNull() ?: continue
                    val title = decode(o.optString("title"))
                    val desc = decode(o.optString("description"))
                    if (title.isNotBlank()) {
                        list.add(SharedEpgEntry(title, desc, s, e))
                    }
                }
                list.sortBy { it.start }
                cache[channel.id] = nowMs to list
                list
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun current(list: List<SharedEpgEntry>): SharedEpgEntry? {
        val now = System.currentTimeMillis() / 1000
        return list.firstOrNull { now >= it.start && now < it.end }
    }

    fun next(list: List<SharedEpgEntry>): SharedEpgEntry? {
        val cur = current(list)
        return if (cur != null) list.firstOrNull { it.start >= cur.end } else list.firstOrNull()
    }
}

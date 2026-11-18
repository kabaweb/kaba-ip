package com.ipvio.stream.shared.epg

import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.portal.SharedPortalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class SharedEpgEntry(
    val title: String,
    val desc: String,
    val start: Long,
    val end: Long,
    val isNowPlaying: Boolean = false
)

object SharedEpgRepo {
    // Regex flexível que aceita com ou sem /live/, diferentes portas e extensões (.ts, .m3u8, etc.)
    private val LIVE_RE = Regex("""^(https?://[^/]+)(?:/.*)?/([^/]+)/([^/]+)/(\d+)(?:\.\w+)?(?:\?.*)?$""")
    private val ID_RE = Regex("""(?:stream_id=|/)?(\d+)(?:\.\w+)?(?:\?.*)?$""")

    private val cache = ConcurrentHashMap<String, Pair<Long, List<SharedEpgEntry>>>()

    /**
     * Resolve a URL da API Xtream para consultar o EPG
     */
    fun xtreamApi(channel: Item, action: String = "get_short_epg"): String? {
        // 1. Tenta extrair diretamente da URL do canal
        val m = LIVE_RE.find(channel.url)
        if (m != null) {
            val base = m.groupValues[1]
            val u = m.groupValues[2]
            val p = m.groupValues[3]
            val id = m.groupValues[4]
            return "$base/player_api.php?username=$u&password=$p&action=$action&stream_id=$id"
        }

        // 2. Fallback: usa credenciais do portal + ID numérico do canal
        val host = SharedPortalManager.xtreamHost
        val user = SharedPortalManager.xtreamUser
        val pass = SharedPortalManager.xtreamPass
        if (!host.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()) {
            val id = channel.tvgId?.toLongOrNull()?.toString()
                ?: ID_RE.find(channel.url)?.groupValues?.get(1)
            if (!id.isNullOrBlank()) {
                var cleanHost = host.trimEnd('/')
                if (!cleanHost.startsWith("http://") && !cleanHost.startsWith("https://")) {
                    cleanHost = "http://$cleanHost"
                }
                return "$cleanHost/player_api.php?username=$user&password=$pass&action=$action&stream_id=$id"
            }
        }

        return null
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

    private val dateFmt by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private fun parseSec(obj: JSONObject, tsKey: String, dateKey: String): Long? {
        val direct = obj.optString(tsKey).toLongOrNull() ?: obj.optLong(tsKey, 0L).takeIf { it > 0 }
        if (direct != null && direct > 0) return direct

        val dtStr = obj.optString(dateKey)
        if (dtStr.isNotBlank()) {
            try {
                val parsed = dateFmt.parse(dtStr)
                if (parsed != null) return parsed.time / 1000L
            } catch (_: Throwable) {}
        }
        return null
    }

    suspend fun getEpg(channel: Item): List<SharedEpgEntry> {
        val cacheKey = channel.id.ifBlank { channel.url }
        val nowMs = System.currentTimeMillis()
        cache[cacheKey]?.let {
            if (nowMs - it.first < 180_000) return it.second
        }

        return withContext(Dispatchers.IO) {
            val apiUrl = xtreamApi(channel) ?: return@withContext emptyList()
            try {
                val conn = URL("$apiUrl&limit=10").openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "KorpTV/1.0.0")
                if (conn.responseCode !in 200..299) return@withContext emptyList()

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val arr = obj.optJSONArray("epg_listings") ?: return@withContext emptyList()

                val list = ArrayList<SharedEpgEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val s = parseSec(o, "start_timestamp", "start") ?: continue
                    val e = parseSec(o, "stop_timestamp", "end") ?: (s + 3600)

                    val title = decode(o.optString("title")).trim()
                    val desc = decode(o.optString("description")).trim()
                    val isNow = o.optInt("now_playing", 0) == 1 || o.optBoolean("now_playing", false)

                    if (title.isNotBlank()) {
                        list.add(SharedEpgEntry(title, desc, s, e, isNow))
                    }
                }
                list.sortBy { it.start }
                cache[cacheKey] = nowMs to list
                list
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun current(list: List<SharedEpgEntry>): SharedEpgEntry? {
        if (list.isEmpty()) return null

        // 1. Prioriza item explicitamente marcado como 'now_playing' pelo servidor
        list.firstOrNull { it.isNowPlaying }?.let { return it }

        // 2. Verifica se o timestamp atual está dentro do intervalo
        val now = System.currentTimeMillis() / 1000
        val inInterval = list.firstOrNull { now >= it.start && now < it.end }
        if (inInterval != null) return inInterval

        // 3. Fallback tolerante para desvios de fuso horário: primeiro item ou mais recente
        return list.firstOrNull()
    }

    fun next(list: List<SharedEpgEntry>): SharedEpgEntry? {
        if (list.isEmpty()) return null
        val cur = current(list)
        return if (cur != null) {
            list.firstOrNull { it.start >= cur.end } ?: list.getOrNull(list.indexOf(cur) + 1)
        } else {
            list.getOrNull(1)
        }
    }
}

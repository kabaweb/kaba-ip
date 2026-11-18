package com.ipvio.stream.shared.portal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ipvio.stream.shared.device.SharedDeviceIdentity
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.parser.SharedM3uParser
import com.ipvio.stream.shared.prefs.getPreferences
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SharedPortalManager {
    private const val PREFS = "ipvio_portal"
    private const val KEY_PORTAL_URL = "portal_url"
    private const val KEY_SAVED_PLAYLIST = "saved_playlist_url"
    const val DEFAULT_PORTAL_URL = "http://192.168.1.99:3002"

    var isBlocked by mutableStateOf(false)
        private set
    var isConnected by mutableStateOf(false)
        private set
    var isLoadingPlaylist by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf("Desconectado")
        private set

    var tmdbKey by mutableStateOf("")
        private set

    var xtreamHost by mutableStateOf<String?>(null)
    var xtreamUser by mutableStateOf<String?>(null)
    var xtreamPass by mutableStateOf<String?>(null)

    val playlistItems = mutableStateListOf<Item>()

    private val cacheDir by lazy {
        File(System.getProperty("user.home"), ".korptv").apply { mkdirs() }
    }
    private val cacheFile by lazy {
        File(cacheDir, "playlist_cache.m3u")
    }

    fun getPortalUrl(): String {
        val sp = getPreferences(PREFS)
        val stored = sp.getString(KEY_PORTAL_URL, "")?.trim().orEmpty()
        return if (stored.isNotBlank()) stored else DEFAULT_PORTAL_URL
    }

    fun setPortalUrl(url: String) {
        val sp = getPreferences(PREFS)
        val clean = url.trim().removeSuffix("/")
        sp.putString(KEY_PORTAL_URL, clean)
    }

    fun getSavedPlaylistUrl(): String? {
        return getPreferences(PREFS).getString(KEY_SAVED_PLAYLIST, null)
    }

    fun setSavedPlaylistUrl(url: String?) {
        getPreferences(PREFS).putString(KEY_SAVED_PLAYLIST, url)
    }

    private fun httpPostJson(urlStr: String, jsonBody: String): String? {
        return runCatching {
            val u = URL(urlStr)
            val conn = u.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonBody) }

            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun httpGet(urlStr: String): InputStream? {
        return runCatching {
            val u = URL(urlStr)
            val conn = u.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 40000
            conn.setRequestProperty("User-Agent", "KorpTV/1.0.0")
            conn.setRequestProperty("Accept", "*/*")
            if (conn.responseCode in 200..299) conn.inputStream else null
        }.getOrNull()
    }

    private fun xtreamM3uUrl(host: String, user: String, pass: String): String {
        var h = host.trim().trimEnd('/')
        if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) h = "http://$h"
        val u = URLEncoder.encode(user.trim(), "UTF-8")
        val p = URLEncoder.encode(pass.trim(), "UTF-8")
        return "$h/get.php?username=$u&password=$p&type=m3u_plus&output=ts"
    }

    private var pollJob: Job? = null

    private fun detectCredentialsFromItems(items: List<Item>) {
        if (!xtreamHost.isNullOrBlank() && !xtreamUser.isNullOrBlank()) return
        val liveItem = items.firstOrNull { it.kind == com.ipvio.stream.shared.model.Kind.LIVE && it.url.contains("://") } ?: return
        val match = Regex("""^(https?://[^/]+)(?:/.*)?/([^/]+)/([^/]+)/(\d+)(?:\.\w+)?(?:\?.*)?$""").find(liveItem.url)
        if (match != null) {
            xtreamHost = match.groupValues[1]
            xtreamUser = match.groupValues[2]
            xtreamPass = match.groupValues[3]
        }
    }

    /**
     * Carrega a playlist do disco local instantaneamente sem esperar rede
     */
    private suspend fun loadFromDiskCache(): Boolean {
        if (!cacheFile.exists() || cacheFile.length() <= 0) return false
        return withContext(Dispatchers.IO) {
            try {
                val list = SharedM3uParser.parseStream(cacheFile.inputStream())
                if (list.isNotEmpty()) {
                    detectCredentialsFromItems(list)
                    withContext(Dispatchers.Main) {
                        playlistItems.clear()
                        playlistItems.addAll(list)
                        statusMessage = "Carregado do cache: ${list.size} itens"
                    }
                    true
                } else false
            } catch (e: Throwable) {
                e.printStackTrace()
                false
            }
        }
    }

    fun startPolling(scope: CoroutineScope) {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            // 1. CARREGAMENTO IMEDIATO DO DISCO LOCAL AO LIGAR O APP (INSTANTÂNEO)
            if (playlistItems.isEmpty()) {
                val hasCached = loadFromDiskCache()
                if (!hasCached) {
                    val saved = getSavedPlaylistUrl()
                    if (!saved.isNullOrBlank()) {
                        loadPlaylist(saved)
                    }
                }
            }

            // 2. POLING E VERIFICAÇÃO COM O PORTAL EM SEGUNDO PLANO
            while (isActive) {
                val portalUrl = getPortalUrl()
                val devId = SharedDeviceIdentity.getDeviceId()
                val mac = SharedDeviceIdentity.getVirtualMac()
                val pairingCode = SharedDeviceIdentity.getPairingCode()
                val model = SharedDeviceIdentity.getDeviceModel()

                val json = """
                    {
                        "deviceId": "$devId",
                        "mac": "$mac",
                        "pairingCode": "$pairingCode",
                        "model": "$model",
                        "appVersion": "1.0.0",
                        "versionCode": 23
                    }
                """.trimIndent()

                val res = httpPostJson("$portalUrl/api/device/register", json)
                if (res != null) {
                    isConnected = true
                    statusMessage = "Conectado ao Portal"
                    parsePortalResponse(res)
                } else {
                    isConnected = false
                    if (playlistItems.isEmpty()) {
                        statusMessage = "Falha ao conectar no Portal ($portalUrl)"
                    }
                }

                delay(if (playlistItems.isEmpty()) 4000L else 45000L)
            }
        }
    }

    private suspend fun parsePortalResponse(rawJson: String) {
        val metaKey = Regex(""""tmdbKey"\s*:\s*"([^"]*)"""").find(rawJson)?.groupValues?.get(1)
        if (!metaKey.isNullOrBlank()) {
            tmdbKey = metaKey
        }

        val plIdx = rawJson.indexOf("\"playlist\":")
        if (plIdx >= 0) {
            val plSub = rawJson.substring(plIdx)
            val endObj = plSub.indexOf('}')
            if (endObj >= 0) {
                val plObj = plSub.substring(0, endObj)
                val type = Regex(""""type"\s*:\s*"([^"]*)"""").find(plObj)?.groupValues?.get(1)
                val host = Regex(""""host"\s*:\s*"([^"]*)"""").find(plObj)?.groupValues?.get(1)
                val user = Regex(""""user"\s*:\s*"([^"]*)"""").find(plObj)?.groupValues?.get(1)
                val pass = Regex(""""pass"\s*:\s*"([^"]*)"""").find(plObj)?.groupValues?.get(1)
                val url = Regex(""""url"\s*:\s*"([^"]*)"""").find(plObj)?.groupValues?.get(1)

                if (!host.isNullOrBlank()) xtreamHost = host
                if (!user.isNullOrBlank()) xtreamUser = user
                if (!pass.isNullOrBlank()) xtreamPass = pass

                var targetUrl: String? = null
                if ((type == "xtream" || (!user.isNullOrBlank() && !pass.isNullOrBlank())) && !host.isNullOrBlank()) {
                    targetUrl = xtreamM3uUrl(host, user ?: "", pass ?: "")
                } else if (!url.isNullOrBlank()) {
                    targetUrl = url
                }

                if (!targetUrl.isNullOrBlank() && (targetUrl != getSavedPlaylistUrl() || playlistItems.isEmpty())) {
                    setSavedPlaylistUrl(targetUrl)
                    loadPlaylist(targetUrl)
                }
            }
        }
    }

    suspend fun loadPlaylist(url: String) {
        if (playlistItems.isEmpty()) {
            isLoadingPlaylist = true
            statusMessage = "Baixando lista de canais..."
        }

        withContext(Dispatchers.IO) {
            val stream = httpGet(url)
            if (stream != null) {
                val tmpFile = File(cacheDir, "playlist_cache.tmp")
                try {
                    // Grava stream no arquivo temporário para persistência em disco
                    FileOutputStream(tmpFile).use { out ->
                        stream.copyTo(out)
                    }

                    // Processa do arquivo salvo
                    val list = SharedM3uParser.parseStream(tmpFile.inputStream())
                    if (list.isNotEmpty()) {
                        detectCredentialsFromItems(list)
                        // Substitui cache local definitivo
                        if (cacheFile.exists()) cacheFile.delete()
                        tmpFile.renameTo(cacheFile)

                        withContext(Dispatchers.Main) {
                            playlistItems.clear()
                            playlistItems.addAll(list)
                            isLoadingPlaylist = false
                            statusMessage = "Pronto: ${list.size} itens"
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isLoadingPlaylist = false
                    }
                }
            } else {
                // Se falhou o download, tenta o cache local
                if (playlistItems.isEmpty()) {
                    loadFromDiskCache()
                }
                withContext(Dispatchers.Main) {
                    isLoadingPlaylist = false
                }
            }
        }
    }
}

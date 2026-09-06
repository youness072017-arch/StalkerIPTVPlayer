package com.stalker.iptvplayer

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StalkerClient {

    companion object {
        private const val TAG = "StalkerClient"

        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000

        private const val USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) " +
                    "AppleWebKit/533.3 (KHTML, like Gecko) " +
                    "MAG250 stbapp ver: 2 rev: 250 Safari/533.3"

        private const val X_USER_AGENT = "Model: MAG250; Link: WiFi"

        private const val DEFAULT_LANGUAGE = "en"
        private const val DEFAULT_TIMEZONE = "Europe/Paris"

        private const val MAX_PAGES = 500
    }

    data class Channel(
        val id: String,
        val name: String,
        val logo: String = "",
        val cmd: String = "",
        val categoryId: String = "",
        val categoryName: String = ""
    )

    data class VodItem(
        val id: String,
        val name: String,
        val logo: String = "",
        val cmd: String = "",
        val categoryId: String = "",
        val categoryName: String = "",
        val year: String = "",
        val description: String = ""
    )

    data class SeriesItem(
        val id: String,
        val name: String,
        val logo: String = "",
        val categoryId: String = "",
        val categoryName: String = "",
        val year: String = "",
        val description: String = ""
    )

    private data class Session(
        val token: String,
        val random: String,
        val loadUrl: String
    )

    fun authenticateAndFetchChannels(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, String) -> Unit
    ) {
        fetchChannels(portalUrl, macAddress) { success, channels, message ->
            if (success) {
                onResult(
                    true,
                    "Connected successfully. ${channels.size} Live TV channels found."
                )
            } else {
                onResult(false, message)
            }
        }
    }

    // ============================================================
    // LIVE TV
    // ============================================================

    fun fetchChannels(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, List<Channel>, String) -> Unit
    ) {
        Thread {
            try {
                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isEmpty()) {
                    finishChannels(onResult, false, emptyList(), "Portal URL is empty or invalid.")
                    return@Thread
                }

                if (!isValidMac(mac)) {
                    finishChannels(onResult, false, emptyList(), "Invalid MAC address.")
                    return@Thread
                }

                val loadUrl = discoverLoadUrl(portal, mac)
                if (loadUrl.isEmpty()) {
                    finishChannels(onResult, false, emptyList(), "Could not find a compatible Stalker portal endpoint.")
                    return@Thread
                }

                val session = performHandshake(loadUrl, mac)

                try {
                    getProfile(session, mac)
                } catch (e: Exception) {
                    Log.w(TAG, "Profile failed: ${e.message}")
                }

                val categories = try {
                    requestGenres(session, mac)
                } catch (e: Exception) {
                    emptyMap()
                }

                val channels = requestAllLiveChannels(session, mac, categories)

                if (channels.isEmpty()) {
                    finishChannels(onResult, false, emptyList(), "Portal connected, but no Live TV channels were found.")
                    return@Thread
                }

                finishChannels(onResult, true, channels, "Connected successfully. ${channels.size} Live TV channels found.")

            } catch (e: Exception) {
                Log.e(TAG, "LIVE failed", e)
                finishChannels(onResult, false, emptyList(), e.message ?: "Unknown portal connection error.")
            }
        }.start()
    }

    private fun requestAllLiveChannels(
        session: Session,
        macAddress: String,
        categories: Map<String, String>
    ): List<Channel> {
        val allChannels = LinkedHashMap<String, Channel>()

        try {
            val response = request(
                session.loadUrl,
                mapOf(
                    "type" to "itv",
                    "action" to "get_all_channels",
                    "JsHttpRequest" to "1-xml"
                ),
                macAddress,
                session.token
            )

            val parsed = parseChannels(response, categories)
            for (channel in parsed) {
                val key = channel.id.ifEmpty { "${channel.categoryId}|${channel.name}" }
                if (key.isNotEmpty()) allChannels[key] = channel
            }

            if (allChannels.size > 20) {
                return allChannels.values.toList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "get_all_channels failed: ${e.message}")
        }

        if (categories.isNotEmpty()) {
            for ((categoryId, categoryName) in categories) {
                try {
                    val categoryChannels = requestCategoryPages(
                        session, macAddress, categoryId, categoryName, categories
                    )
                    for (channel in categoryChannels) {
                        val key = channel.id.ifEmpty { "${channel.categoryId}|${channel.name}" }
                        if (key.isNotEmpty()) allChannels[key] = channel
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Category $categoryName failed: ${e.message}")
                }
            }
        }

        if (allChannels.isEmpty()) {
            try {
                val globalChannels = requestGlobalPages(session, macAddress, categories)
                for (channel in globalChannels) {
                    val key = channel.id.ifEmpty { "${channel.categoryId}|${channel.name}" }
                    if (key.isNotEmpty()) allChannels[key] = channel
                }
            } catch (e: Exception) {
                Log.w(TAG, "Global pagination failed: ${e.message}")
            }
        }

        return allChannels.values.toList()
    }

    private fun requestCategoryPages(
        session: Session,
        macAddress: String,
        categoryId: String,
        categoryName: String,
        categories: Map<String, String>
    ): List<Channel> {
        val result = LinkedHashMap<String, Channel>()
        var page = 1
        var totalItems = -1
        var pageSize = 14

        while (page <= MAX_PAGES) {
            val response = request(
                session.loadUrl,
                mapOf(
                    "type" to "itv",
                    "action" to "get_ordered_list",
                    "genre" to categoryId,
                    "p" to page.toString(),
                    "JsHttpRequest" to "1-xml"
                ),
                macAddress,
                session.token
            )

            val root = parseJson(response)
            val metadata = extractPagination(root)

            if (metadata.first > 0) totalItems = metadata.first
            if (metadata.second > 0) pageSize = metadata.second

            val channels = parseChannels(response, categories).map { channel ->
                if (channel.categoryId.isEmpty()) {
                    channel.copy(categoryId = categoryId, categoryName = categoryName)
                } else if (channel.categoryName.isEmpty()) {
                    channel.copy(categoryName = categories[channel.categoryId] ?: categoryName)
                } else {
                    channel
                }
            }

            if (channels.isEmpty()) break

            for (channel in channels) {
                val key = channel.id.ifEmpty { "${channel.categoryId}|${channel.name}" }
                if (key.isNotEmpty()) result[key] = channel
            }

            if (totalItems > 0 && result.size >= totalItems) break
            if (channels.size < pageSize) break
            page++
        }

        return result.values.toList()
    }

    private fun requestGlobalPages(
        session: Session,
        macAddress: String,
        categories: Map<String, String>
    ): List<Channel> {
        val result = LinkedHashMap<String, Channel>()
        var page = 1
        var totalItems = -1
        var pageSize = 14

        while (page <= MAX_PAGES) {
            val response = request(
                session.loadUrl,
                mapOf(
                    "type" to "itv",
                    "action" to "get_ordered_list",
                    "p" to page.toString(),
                    "JsHttpRequest" to "1-xml"
                ),
                macAddress,
                session.token
            )

            val root = parseJson(response)
            val metadata = extractPagination(root)

            if (metadata.first > 0) totalItems = metadata.first
            if (metadata.second > 0) pageSize = metadata.second

            val channels = parseChannels(response, categories)
            if (channels.isEmpty()) break

            for (channel in channels) {
                val key = channel.id.ifEmpty { "${channel.categoryId}|${channel.name}" }
                if (key.isNotEmpty()) result[key] = channel
            }

            if (totalItems > 0 && result.size >= totalItems) break
            if (channels.size < pageSize) break
            page++
        }

        return result.values.toList()
    }

    private fun requestGenres(session: Session, macAddress: String): Map<String, String> {
        val response = request(
            session.loadUrl,
            mapOf("type" to "itv", "action" to "get_genres", "JsHttpRequest" to "1-xml"),
            macAddress,
            session.token
        )
        return parseCategories(response)
    }

    // ============================================================
    // MOVIES
    // ============================================================

    fun fetchMovies(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, List<VodItem>, String) -> Unit
    ) {
        Thread {
            try {
                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isEmpty()) {
                    finishMovies(onResult, false, emptyList(), "Portal URL is empty or invalid.")
                    return@Thread
                }

                val loadUrl = discoverLoadUrl(portal, mac)
                if (loadUrl.isEmpty()) {
                    finishMovies(onResult, false, emptyList(), "Could not find a compatible Stalker portal endpoint.")
                    return@Thread
                }

                val session = performHandshake(loadUrl, mac)
                val categories = try { requestVodCategories(session, mac) } catch (e: Exception) { emptyMap() }

                val response = request(
                    session.loadUrl,
                    mapOf("type" to "vod", "action" to "get_ordered_list", "p" to "1", "JsHttpRequest" to "1-xml"),
                    mac,
                    session.token
                )

                val movies = parseVodItems(response, categories)
                finishMovies(
                    onResult,
                    movies.isNotEmpty(),
                    movies,
                    if (movies.isEmpty()) "No Movies/VOD were found." else "Movies loaded: ${movies.size}"
                )
            } catch (e: Exception) {
                finishMovies(onResult, false, emptyList(), e.message ?: "Movies request failed.")
            }
        }.start()
    }

    private fun requestVodCategories(session: Session, macAddress: String): Map<String, String> {
        val response = request(
            session.loadUrl,
            mapOf("type" to "vod", "action" to "get_categories", "JsHttpRequest" to "1-xml"),
            macAddress,
            session.token
        )
        return parseCategories(response)
    }

    // ============================================================
    // SERIES
    // ============================================================

    fun fetchSeries(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, List<SeriesItem>, String) -> Unit
    ) {
        Thread {
            try {
                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isEmpty()) {
                    finishSeries(onResult, false, emptyList(), "Portal URL is empty or invalid.")
                    return@Thread
                }

                val loadUrl = discoverLoadUrl(portal, mac)
                if (loadUrl.isEmpty()) {
                    finishSeries(onResult, false, emptyList(), "Could not find a compatible Stalker portal endpoint.")
                    return@Thread
                }

                val session = performHandshake(loadUrl, mac)
                val categories = try {
                    val response = request(
                        session.loadUrl,
                        mapOf("type" to "series", "action" to "get_categories", "JsHttpRequest" to "1-xml"),
                        mac,
                        session.token
                    )
                    parseCategories(response)
                } catch (e: Exception) {
                    emptyMap()
                }

                val response = request(
                    session.loadUrl,
                    mapOf("type" to "series", "action" to "get_ordered_list", "p" to "1", "JsHttpRequest" to "1-xml"),
                    mac,
                    session.token
                )

                val series = parseSeriesItems(response, categories)
                finishSeries(
                    onResult,
                    series.isNotEmpty(),
                    series,
                    if (series.isEmpty()) "No Series were found." else "Series loaded: ${series.size}"
                )
            } catch (e: Exception) {
                finishSeries(onResult, false, emptyList(), e.message ?: "Series request failed.")
            }
        }.start()
    }

    // ============================================================
    // STREAM LINK RESOLVER
    // ============================================================

    fun createLink(
        portalUrl: String,
        macAddress: String,
        cmd: String,
        onResult: (Boolean, String, String) -> Unit
    ) {
        Thread {
            try {
                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)
                val loadUrl = discoverLoadUrl(portal, mac)
                val session = performHandshake(loadUrl, mac)

                val encodedCmd = URLEncoder.encode(cmd, "UTF-8")
                val response = request(
                    session.loadUrl,
                    mapOf(
                        "type" to "itv",
                        "action" to "create_link",
                        "cmd" to encodedCmd,
                        "JsHttpRequest" to "1-xml"
                    ),
                    mac,
                    session.token
                )

                val root = parseJson(response)
                val js = root.getAsJsonObject("js")
                val cmdObj = js?.get("cmd")

                var streamUrl = ""
                if (cmdObj != null && cmdObj.isJsonObject) {
                    streamUrl = cmdObj.asJsonObject.get("cmd")?.asString ?: ""
                } else if (cmdObj != null && cmdObj.isPrimitive) {
                    streamUrl = cmdObj.asString
                }

                if (streamUrl.isEmpty()) {
                    streamUrl = js?.get("url")?.asString ?: ""
                }

                if (streamUrl.isNotEmpty()) {
                    onResult(true, "Stream link ready", streamUrl)
                } else {
                    onResult(false, "Failed to resolve stream link", "")
                }

            } catch (e: Exception) {
                onResult(false, e.message ?: "Stream link error", "")
            }
        }.start()
    }

    // ============================================================
    // HELPERS & PARSING
    // ============================================================

    private fun normalizePortalUrl(url: String): String = url.trim().removeSuffix("/")

    private fun normalizeMac(mac: String): String {
        val clean = mac.replace(Regex("[^0-9a-fA-F]"), "").uppercase()
        return if (clean.length == 12) clean.chunked(2).joinToString(":") else mac
    }

    private fun isValidMac(mac: String): Boolean {
        val clean = mac.replace(Regex("[^0-9a-fA-F]"), "")
        return clean.length == 12
    }

    private fun discoverLoadUrl(portal: String, mac: String): String {
        val candidates = listOf(
            "$portal/stalker_portal/server/load.php",
            "$portal/server/load.php",
            "$portal/c/portal.php"
        )
        for (url in candidates) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("X-User-Agent", X_USER_AGENT)
                conn.setRequestProperty("Cookie", "mac=$mac")
                if (conn.responseCode in 200..399) {
                    return url
                }
            } catch (_: Exception) {}
        }
        return candidates[0]
    }

    private fun performHandshake(loadUrl: String, mac: String): Session {
        val response = request(
            loadUrl,
            mapOf("type" to "stb", "action" to "handshake", "preloaded" to "true", "JsHttpRequest" to "1-xml"),
            mac,
            ""
        )
        val root = parseJson(response)
        val js = root.getAsJsonObject("js") ?: throw Exception("Invalid handshake response")
        val token = js.get("token")?.asString ?: ""
        val random = js.get("random")?.asString ?: ""
        return Session(token, random, loadUrl)
    }

    private fun getProfile(session: Session, mac: String) {
        request(
            session.loadUrl,
            mapOf(
                "type" to "stb",
                "action" to "get_profile",
                "hd" to "1",
                "ver" to "3.2.2",
                "stb_type" to "MAG250",
                "lang" to DEFAULT_LANGUAGE,
                "timezone" to DEFAULT_TIMEZONE,
                "JsHttpRequest" to "1-xml"
            ),
            mac,
            session.token
        )
    }

    private fun request(
        urlStr: String,
        params: Map<String, String>,
        mac: String,
        token: String
    ): String {
        val query = params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val fullUrl = if (urlStr.contains("?")) "$urlStr&$query" else "$urlStr?$query"

        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("X-User-Agent", X_USER_AGENT)
        conn.setRequestProperty("Cookie", "mac=$mac; stb_lang=$DEFAULT_LANGUAGE; timezone=${URLEncoder.encode(DEFAULT_TIMEZONE, "UTF-8")}")
        if (token.isNotEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }

        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = reader.readLine() } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    private fun parseJson(jsonStr: String): JsonObject {
        val element = JsonParser.parseString(jsonStr)
        if (element.isJsonObject) {
            return element.asJsonObject
        }
        throw Exception("Invalid JSON format")
    }

    private fun extractPagination(root: JsonObject): Pair<Int, Int> {
        var total = -1
        var pageSize = 14
        try {
            val js = root.getAsJsonObject("js")
            if (js != null) {
                if (js.has("total_items")) total = js.get("total_items").asInt
                if (js.has("max_page_items")) pageSize = js.get("max_page_items").asInt
            }
        } catch (_: Exception) {}
        return Pair(total, pageSize)
    }

    private fun parseCategories(response: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        try {
            val root = parseJson(response)
            val js = root.get("js")
            val array = if (js != null && js.isJsonArray) js.asJsonArray else null
            if (array != null) {
                for (elem in array) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        val id = obj.get("id")?.asString ?: obj.get("alias")?.asString ?: continue
                        val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: "Category"
                        map[id] = title
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun parseChannels(response: String, categories: Map<String, String>): List<Channel> {
        val list = mutableListOf<Channel>()
        try {
            val root = parseJson(response)
            val js = root.get("js")
            val data = when {
                js != null && js.isJsonObject && js.asJsonObject.has("data") -> js.asJsonObject.getAsJsonArray("data")
                js != null && js.isJsonArray -> js.asJsonArray
                else -> null
            }

            if (data != null) {
                for (elem in data) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: obj.get("number")?.asString ?: ""
                    val name = obj.get("name")?.asString ?: "Unknown"
                    val logo = obj.get("logo")?.asString ?: ""
                    val cmd = obj.get("cmd")?.asString ?: ""
                    val catId = obj.get("tv_genre_id")?.asString ?: obj.get("genre_id")?.asString ?: ""
                    val catName = categories[catId] ?: ""

                    list.add(Channel(id, name, logo, cmd, catId, catName))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseVodItems(response: String, categories: Map<String, String>): List<VodItem> {
        val list = mutableListOf<VodItem>()
        try {
            val root = parseJson(response)
            val js = root.get("js")
            val data = when {
                js != null && js.isJsonObject && js.asJsonObject.has("data") -> js.asJsonObject.getAsJsonArray("data")
                js != null && js.isJsonArray -> js.asJsonArray
                else -> null
            }
            if (data != null) {
                for (elem in data) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: ""
                    val name = obj.get("name")?.asString ?: ""
                    val logo = obj.get("screenshot_uri")?.asString ?: obj.get("logo")?.asString ?: ""
                    val cmd = obj.get("cmd")?.asString ?: ""
                    val catId = obj.get("category_id")?.asString ?: ""
                    val year = obj.get("year")?.asString ?: ""
                    val desc = obj.get("description")?.asString ?: ""
                    list.add(VodItem(id, name, logo, cmd, catId, categories[catId] ?: "", year, desc))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseSeriesItems(response: String, categories: Map<String, String>): List<SeriesItem> {
        val list = mutableListOf<SeriesItem>()
        try {
            val root = parseJson(response)
            val js = root.get("js")
            val data = when {
                js != null && js.isJsonObject && js.asJsonObject.has("data") -> js.asJsonObject.getAsJsonArray("data")
                js != null && js.isJsonArray -> js.asJsonArray
                else -> null
            }
            if (data != null) {
                for (elem in data) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: ""
                    val name = obj.get("name")?.asString ?: ""
                    val logo = obj.get("screenshot_uri")?.asString ?: ""
                    val catId = obj.get("category_id")?.asString ?: ""
                    val year = obj.get("year")?.asString ?: ""
                    val desc = obj.get("description")?.asString ?: ""
                    list.add(SeriesItem(id, name, logo, catId, categories[catId] ?: "", year, desc))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun finishChannels(
        onResult: (Boolean, List<Channel>, String) -> Unit,
        success: Boolean,
        channels: List<Channel>,
        msg: String
    ) {
        Handler(Looper.getMainLooper()).post {
            onResult(success, channels, msg)
        }
    }

    private fun finishMovies(
        onResult: (Boolean, List<VodItem>, String) -> Unit,
        success: Boolean,
        items: List<VodItem>,
        msg: String
    ) {
        Handler(Looper.getMainLooper()).post {
            onResult(success, items, msg)
        }
    }

    private fun finishSeries(
        onResult: (Boolean, List<SeriesItem>, String) -> Unit,
        success: Boolean,
        items: List<SeriesItem>,
        msg: String
    ) {
        Handler(Looper.getMainLooper()).post {
            onResult(success, items, msg)
        }
    }
}

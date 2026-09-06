package com.stalker.iptvplayer

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class StalkerClient {

    companion object {
        private const val TAG = "StalkerClient"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 20000

        private const val USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) " +
                    "AppleWebKit/533.3 (KHTML, like Gecko) " +
                    "MAG250 stbapp ver: 2 rev: 250 Safari/533.3"

        private const val X_USER_AGENT = "Model: MAG250; Link: WiFi"
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

    // ============================================================
    // PUBLIC: CONNECT + LIVE
    // ============================================================

    fun authenticateAndFetchChannels(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isEmpty()) {
                    finish(onResult, false, "Portal URL is empty or invalid.")
                    return@Thread
                }

                if (!isValidMac(mac)) {
                    finish(onResult, false, "Invalid MAC address.")
                    return@Thread
                }

                Log.d(TAG, "Portal: $portal")
                Log.d(TAG, "MAC: $mac")

                val loadUrl = discoverLoadUrl(portal, mac)

                if (loadUrl.isEmpty()) {
                    finish(
                        onResult,
                        false,
                        "Could not find a compatible Stalker portal endpoint."
                    )
                    return@Thread
                }

                val session = performHandshake(
                    loadUrl = loadUrl,
                    macAddress = mac
                )

                Log.d(TAG, "Handshake successful")

                try {
                    getProfile(
                        session = session,
                        macAddress = mac
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Profile request failed: ${e.message}")
                }

                try {
                    request(
                        loadUrl = session.loadUrl,
                        params = mapOf(
                            "type" to "itv",
                            "action" to "get_genres",
                            "JsHttpRequest" to "1-xml"
                        ),
                        macAddress = mac,
                        token = session.token
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Genres request failed: ${e.message}")
                }

                val channelsResponse = requestChannels(
                    session = session,
                    macAddress = mac
                )

                val channels = parseChannels(channelsResponse)

                if (channels.isEmpty()) {
                    finish(
                        onResult,
                        false,
                        "Portal connected, but no Live TV channels were found."
                    )
                    return@Thread
                }

                Log.d(TAG, "Parsed ${channels.size} channels")

                finish(
                    onResult,
                    true,
                    "Connected successfully. ${channels.size} Live TV channels found."
                )

            } catch (e: Exception) {
                Log.e(TAG, "Stalker connection failed", e)

                finish(
                    onResult,
                    false,
                    e.message ?: "Unknown portal connection error."
                )
            }
        }.start()
    }

    // ============================================================
    // PUBLIC: MOVIES / VOD
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
                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Portal URL is empty or invalid."
                    )
                    return@Thread
                }

                if (!isValidMac(mac)) {
                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Invalid MAC address."
                    )
                    return@Thread
                }

                Log.d(TAG, "MOVIES: starting connection")

                val loadUrl = discoverLoadUrl(portal, mac)

                if (loadUrl.isEmpty()) {
                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Could not find Stalker portal endpoint."
                    )
                    return@Thread
                }

                val session = performHandshake(
                    loadUrl = loadUrl,
                    macAddress = mac
                )

                Log.d(TAG, "MOVIES: handshake successful")

                val response = requestMovies(
                    session = session,
                    macAddress = mac
                )

                val movies = parseVodItems(response)

                Log.d(TAG, "MOVIES: parsed ${movies.size} items")

                if (movies.isEmpty()) {
                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Connected, but no Movies/VOD were returned by the portal."
                    )
                    return@Thread
                }

                finishMovies(
                    onResult,
                    true,
                    movies,
                    "Movies loaded: ${movies.size}"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Movies request failed", e)

                finishMovies(
                    onResult,
                    false,
                    emptyList(),
                    e.message ?: "Movies request failed."
                )
            }
        }.start()
    }

    private fun requestMovies(
        session: Session,
        macAddress: String
    ): String {

        // First request categories.
        try {
            request(
                loadUrl = session.loadUrl,
                params = mapOf(
                    "type" to "vod",
                    "action" to "get_categories",
                    "JsHttpRequest" to "1-xml"
                ),
                macAddress = macAddress,
                token = session.token
            )

            Log.d(TAG, "MOVIES: categories request successful")

        } catch (e: Exception) {
            Log.w(
                TAG,
                "MOVIES: categories failed: ${e.message}"
            )
        }

        // Main VOD request.
        return request(
            loadUrl = session.loadUrl,
            params = mapOf(
                "type" to "vod",
                "action" to "get_ordered_list",
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress = macAddress,
            token = session.token
        )
    }

    // ============================================================
    // PUBLIC: SERIES
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
                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Portal URL is empty or invalid."
                    )
                    return@Thread
                }

                if (!isValidMac(mac)) {
                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Invalid MAC address."
                    )
                    return@Thread
                }

                Log.d(TAG, "SERIES: starting connection")

                val loadUrl = discoverLoadUrl(portal, mac)

                if (loadUrl.isEmpty()) {
                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Could not find Stalker portal endpoint."
                    )
                    return@Thread
                }

                val session = performHandshake(
                    loadUrl = loadUrl,
                    macAddress = mac
                )

                Log.d(TAG, "SERIES: handshake successful")

                val response = requestSeries(
                    session = session,
                    macAddress = mac
                )

                val series = parseSeriesItems(response)

                Log.d(TAG, "SERIES: parsed ${series.size} items")

                if (series.isEmpty()) {
                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Connected, but no Series were returned by the portal."
                    )
                    return@Thread
                }

                finishSeries(
                    onResult,
                    true,
                    series,
                    "Series loaded: ${series.size}"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Series request failed", e)

                finishSeries(
                    onResult,
                    false,
                    emptyList(),
                    e.message ?: "Series request failed."
                )
            }
        }.start()
    }

    private fun requestSeries(
        session: Session,
        macAddress: String
    ): String {

        // First request categories.
        try {
            request(
                loadUrl = session.loadUrl,
                params = mapOf(
                    "type" to "series",
                    "action" to "get_categories",
                    "JsHttpRequest" to "1-xml"
                ),
                macAddress = macAddress,
                token = session.token
            )

            Log.d(TAG, "SERIES: categories request successful")

        } catch (e: Exception) {
            Log.w(
                TAG,
                "SERIES: categories failed: ${e.message}"
            )
        }

        // Main series request.
        return request(
            loadUrl = session.loadUrl,
            params = mapOf(
                "type" to "series",
                "action" to "get_ordered_list",
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress = macAddress,
            token = session.token
        )
    }

    // ============================================================
    // DISCOVER PORTAL
    // ============================================================

    private fun discoverLoadUrl(
        portal: String,
        macAddress: String
    ): String {

        val base = normalizeBaseUrl(portal)

        val candidates = linkedSetOf(
            "$base/stalker_portal/server/load.php",
            "$base/portal.php",
            "$base/server/load.php"
        )

        for (candidate in candidates) {
            try {
                Log.d(TAG, "Testing endpoint: $candidate")

                val response = request(
                    loadUrl = candidate,
                    params = mapOf(
                        "type" to "stb",
                        "action" to "handshake",
                        "token" to "",
                        "JsHttpRequest" to "1-xml"
                    ),
                    macAddress = macAddress,
                    token = null
                )

                val root = JsonParser().parse(response)

                if (!root.isJsonObject) {
                    continue
                }

                val json = root.asJsonObject

                if (!json.has("js")) {
                    continue
                }

                val js = json.get("js")

                if (!js.isJsonObject) {
                    continue
                }

                val token = getString(
                    js.asJsonObject,
                    "token"
                )

                if (token.isNotEmpty()) {
                    Log.d(TAG, "Compatible endpoint: $candidate")
                    return candidate
                }

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Endpoint failed: $candidate -> ${e.message}"
                )
            }
        }

        return ""
    }

    // ============================================================
    // NORMALIZATION
    // ============================================================

    private fun normalizePortalUrl(
        input: String
    ): String {

        var value = input.trim()

        if (value.isEmpty()) {
            return ""
        }

        if (!value.startsWith("http://", true) &&
            !value.startsWith("https://", true)
        ) {
            value = "http://$value"
        }

        return normalizeBaseUrl(value)
    }

    private fun normalizeBaseUrl(
        input: String
    ): String {

        var value = input.trim().removeSuffix("/")

        val suffixes = listOf(
            "/load.php",
            "/server",
            "/stalker_portal",
            "/c"
        )

        var changed = true

        while (changed) {
            changed = false

            for (suffix in suffixes) {
                if (value.endsWith(suffix, true)) {
                    value = value
                        .dropLast(suffix.length)
                        .removeSuffix("/")

                    changed = true
                    break
                }
            }
        }

        return value
    }

    private fun normalizeMac(
        input: String
    ): String {
        return input
            .trim()
            .uppercase(Locale.US)
    }

    private fun isValidMac(
        mac: String
    ): Boolean {

        return Regex(
            "^([0-9A-F]{2}:){5}[0-9A-F]{2}$"
        ).matches(mac)
    }

    // ============================================================
    // HANDSHAKE
    // ============================================================

    private fun performHandshake(
        loadUrl: String,
        macAddress: String
    ): Session {

        val response = request(
            loadUrl = loadUrl,
            params = mapOf(
                "type" to "stb",
                "action" to "handshake",
                "token" to "",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress = macAddress,
            token = null
        )

        val root = JsonParser().parse(response)

        if (!root.isJsonObject) {
            throw Exception("Handshake returned invalid JSON.")
        }

        val json = root.asJsonObject

        if (!json.has("js") ||
            !json.get("js").isJsonObject
        ) {
            throw Exception(
                "Handshake response is missing 'js'."
            )
        }

        val js = json
            .get("js")
            .asJsonObject

        val token = getString(js, "token")

        if (token.isEmpty()) {
            throw Exception(
                "Handshake failed: portal did not return a token."
            )
        }

        val random = getString(js, "random")

        return Session(
            token = token,
            random = random,
            loadUrl = loadUrl
        )
    }

    // ============================================================
    // PROFILE
    // ============================================================

    private fun getProfile(
        session: Session,
        macAddress: String
    ): JsonObject {

        val response = request(
            loadUrl = session.loadUrl,
            params = mapOf(
                "type" to "stb",
                "action" to "get_profile",
                "hd" to "1",
                "stb_type" to "MAG250",
                "image_version" to "218",
                "auth_second_step" to "0",
                "client_type" to "STB",
                "num_banks" to "1",
                "not_valid_token" to "0",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress = macAddress,
            token = session.token
        )

        val root = JsonParser().parse(response)

        if (!root.isJsonObject) {
            throw Exception("Profile returned invalid JSON.")
        }

        val json = root.asJsonObject

        if (!json.has("js") ||
            !json.get("js").isJsonObject
        ) {
            throw Exception(
                "Profile response is missing 'js'."
            )
        }

        return json
            .get("js")
            .asJsonObject
    }

    // ============================================================
    // LIVE
    // ============================================================

    private fun requestChannels(
        session: Session,
        macAddress: String
    ): String {

        return try {

            request(
                loadUrl = session.loadUrl,
                params = mapOf(
                    "type" to "itv",
                    "action" to "get_all_channels",
                    "JsHttpRequest" to "1-xml"
                ),
                macAddress = macAddress,
                token = session.token
            )

        } catch (firstError: Exception) {

            Log.w(
                TAG,
                "get_all_channels failed: ${firstError.message}"
            )

            request(
                loadUrl = session.loadUrl,
                params = mapOf(
                    "type" to "itv",
                    "action" to "get_channels",
                    "JsHttpRequest" to "1-xml"
                ),
                macAddress = macAddress,
                token = session.token
            )
        }
    }

    // ============================================================
    // HTTP REQUEST
    // ============================================================

    private fun request(
        loadUrl: String,
        params: Map<String, String>,
        macAddress: String,
        token: String?
    ): String {

        val query = buildQuery(params)

        val finalUrl =
            if (query.isEmpty()) {
                loadUrl
            } else {
                "$loadUrl?$query"
            }

        Log.d(TAG, "Request: $finalUrl")

        val connection =
            URL(finalUrl).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.useCaches = false

            connection.setRequestProperty(
                "User-Agent",
                USER_AGENT
            )

            connection.setRequestProperty(
                "X-User-Agent",
                X_USER_AGENT
            )

            connection.setRequestProperty(
                "Accept",
                "application/json, text/javascript, */*; q=0.01"
            )

            connection.setRequestProperty(
                "Referer",
                buildReferer(loadUrl)
            )

            connection.setRequestProperty(
                "Cookie",
                "mac=$macAddress; stb_lang=en; timezone=Europe/Paris"
            )

            connection.setRequestProperty(
                "Connection",
                "Keep-Alive"
            )

            if (!token.isNullOrBlank()) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
            }

            val responseCode =
                connection.responseCode

            val stream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val response =
                stream?.let {
                    BufferedReader(
                        InputStreamReader(it)
                    ).use { reader ->
                        reader.readText()
                    }
                } ?: ""

            Log.d(
                TAG,
                "HTTP $responseCode | ${response.take(1000)}"
            )

            if (responseCode !in 200..299) {
                throw Exception(
                    "Portal returned HTTP $responseCode"
                )
            }

            if (response.isBlank()) {
                throw Exception(
                    "Portal returned an empty response."
                )
            }

            return response

        } finally {
            connection.disconnect()
        }
    }

    // ============================================================
    // PARSE MOVIES
    // ============================================================

    private fun parseVodItems(
        response: String
    ): List<VodItem> {

        val result = mutableListOf<VodItem>()

        try {
            val root = JsonParser().parse(response)

            if (!root.isJsonObject) {
                return emptyList()
            }

            val json = root.asJsonObject

            if (!json.has("js") ||
                !json.get("js").isJsonObject
            ) {
                return emptyList()
            }

            val js = json.get("js").asJsonObject

            val data =
                if (js.has("data")) js.get("data")
                else if (js.has("results")) js.get("results")
                else null

            if (data != null && data.isJsonArray) {

                for (item in data.asJsonArray) {

                    if (!item.isJsonObject) {
                        continue
                    }

                    val obj = item.asJsonObject

                    val id = firstValue(
                        obj,
                        "id",
                        "movie_id",
                        "vod_id"
                    )

                    val name = firstValue(
                        obj,
                        "name",
                        "title"
                    )

                    val logo = firstValue(
                        obj,
                        "logo",
                        "screenshot_uri",
                        "poster",
                        "cover"
                    )

                    val cmd = firstValue(
                        obj,
                        "cmd",
                        "stream_url",
                        "url"
                    )

                    val categoryId = firstValue(
                        obj,
                        "category_id",
                        "genre_id"
                    )

                    val year = firstValue(
                        obj,
                        "year"
                    )

                    val description = firstValue(
                        obj,
                        "description",
                        "descr"
                    )

                    if (id.isNotEmpty() || name.isNotEmpty()) {

                        result.add(
                            VodItem(
                                id = id,
                                name = name.ifEmpty {
                                    "Unknown Movie"
                                },
                                logo = logo,
                                cmd = cmd,
                                categoryId = categoryId,
                                year = year,
                                description = description
                            )
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "VOD parsing failed",
                e
            )
        }

        return result
    }

    // ============================================================
    // PARSE SERIES
    // ============================================================

    private fun parseSeriesItems(
        response: String
    ): List<SeriesItem> {

        val result = mutableListOf<SeriesItem>()

        try {
            val root = JsonParser().parse(response)

            if (!root.isJsonObject) {
                return emptyList()
            }

            val json = root.asJsonObject

            if (!json.has("js") ||
                !json.get("js").isJsonObject
            ) {
                return emptyList()
            }

            val js = json.get("js").asJsonObject

            val data =
                if (js.has("data")) js.get("data")
                else if (js.has("results")) js.get("results")
                else null

            if (data != null && data.isJsonArray) {

                for (item in data.asJsonArray) {

                    if (!item.isJsonObject) {
                        continue
                    }

                    val obj = item.asJsonObject

                    val id = firstValue(
                        obj,
                        "id",
                        "series_id"
                    )

                    val name = firstValue(
                        obj,
                        "name",
                        "title"
                    )

                    val logo = firstValue(
                        obj,
                        "logo",
                        "screenshot_uri",
                        "poster",
                        "cover"
                    )

                    val categoryId = firstValue(
                        obj,
                        "category_id",
                        "genre_id"
                    )

                    val year = firstValue(
                        obj,
                        "year"
                    )

                    val description = firstValue(
                        obj,
                        "description",
                        "descr"
                    )

                    if (id.isNotEmpty() || name.isNotEmpty()) {

                        result.add(
                            SeriesItem(
                                id = id,
                                name = name.ifEmpty {
                                    "Unknown Series"
                                },
                                logo = logo,
                                categoryId = categoryId,
                                year = year,
                                description = description
                            )
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Series parsing failed",
                e
            )
        }

        return result
    }

    // ============================================================
    // PARSE LIVE
    // ============================================================

    private fun parseChannels(
        response: String
    ): List<Channel> {

        val result = mutableListOf<Channel>()

        try {
            val root =
                JsonParser().parse(response)

            if (!root.isJsonObject) {
                return emptyList()
            }

            val json =
                root.asJsonObject

            if (!json.has("js") ||
                !json.get("js").isJsonObject
            ) {
                return emptyList()
            }

            val js =
                json.get("js").asJsonObject

            if (js.has("data")) {

                val data = js.get("data")

                if (data.isJsonArray) {

                    for (item in data.asJsonArray) {

                        if (!item.isJsonObject) {
                            continue
                        }

                        val channel =
                            parseChannelObject(
                                item.asJsonObject
                            )

                        if (channel != null) {
                            result.add(channel)
                        }
                    }

                } else if (data.isJsonObject) {

                    val channel =
                        parseChannelObject(
                            data.asJsonObject
                        )

                    if (channel != null) {
                        result.add(channel)
                    }
                }
            }

            if (result.isEmpty() &&
                js.has("results")
            ) {

                val results =
                    js.get("results")

                if (results.isJsonArray) {

                    for (item in results.asJsonArray) {

                        if (!item.isJsonObject) {
                            continue
                        }

                        val channel =
                            parseChannelObject(
                                item.asJsonObject
                            )

                        if (channel != null) {
                            result.add(channel)
                        }
                    }
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Channel parsing failed",
                e
            )
        }

        return result
    }

    private fun parseChannelObject(
        obj: JsonObject
    ): Channel? {

        val id =
            firstValue(
                obj,
                "id",
                "ch_id",
                "channel_id"
            )

        val name =
            firstValue(
                obj,
                "name",
                "title"
            )

        val logo =
            firstValue(
                obj,
                "logo",
                "logo_url",
                "icon"
            )

        val cmd =
            firstValue(
                obj,
                "cmd",
                "stream_url",
                "url"
            )

        val categoryId =
            firstValue(
                obj,
                "tv_genre_id",
                "genre_id",
                "category_id"
            )

        if (id.isEmpty() && name.isEmpty()) {
            return null
        }

        return Channel(
            id = id,
            name = name.ifEmpty {
                "Unknown Channel"
            },
            logo = logo,
            cmd = cmd,
            categoryId = categoryId
        )
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private fun firstValue(
        obj: JsonObject,
        vararg keys: String
    ): String {

        for (key in keys) {

            if (!obj.has(key)) {
                continue
            }

            try {

                val value = obj.get(key)

                if (!value.isJsonNull) {

                    val text = value.asString

                    if (!text.isNullOrBlank()) {
                        return text
                    }
                }

            } catch (_: Exception) {
                // Try next key.
            }
        }

        return ""
    }

    private fun getString(
        obj: JsonObject,
        key: String
    ): String {

        if (!obj.has(key)) {
            return ""
        }

        return try {

            val value = obj.get(key)

            if (value.isJsonNull) {
                ""
            } else {
                value.asString ?: ""
            }

        } catch (_: Exception) {
            ""
        }
    }

    private fun buildReferer(
        loadUrl: String
    ): String {

        return try {
            val url = URL(loadUrl)

            "${url.protocol}://${url.authority}/stalker_portal/c/"

        } catch (_: Exception) {
            loadUrl
        }
    }

    private fun buildQuery(
        params: Map<String, String>
    ): String {

        return params.entries.joinToString("&") { entry ->

            val key =
                URLEncoder.encode(
                    entry.key,
                    "UTF-8"
                )

            val value =
                URLEncoder.encode(
                    entry.value,
                    "UTF-8"
                )

            "$key=$value"
        }
    }

    private fun finish(
        callback: (Boolean, String) -> Unit,
        success: Boolean,
        message: String
    ) {
        callback(success, message)
    }

    private fun finishMovies(
        callback: (Boolean, List<VodItem>, String) -> Unit,
        success: Boolean,
        movies: List<VodItem>,
        message: String
    ) {
        callback(success, movies, message)
    }

    private fun finishSeries(
        callback: (Boolean, List<SeriesItem>, String) -> Unit,
        success: Boolean,
        series: List<SeriesItem>,
        message: String
    ) {
        callback(success, series, message)
    }
}

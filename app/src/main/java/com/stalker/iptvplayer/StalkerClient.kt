package com.stalker.iptvplayer

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
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

        private const val X_USER_AGENT =
            "Model: MAG250; Link: WiFi"

        private const val DEFAULT_LANGUAGE = "en"
        private const val DEFAULT_TIMEZONE = "Europe/Paris"
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
    // PUBLIC LIVE TV - OLD CALLBACK
    // Kept for compatibility with the current DashboardActivity.
    // ============================================================

    fun authenticateAndFetchChannels(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, String) -> Unit
    ) {
        fetchChannels(
            portalUrl = portalUrl,
            macAddress = macAddress
        ) { success, channels, message ->

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
    // PUBLIC LIVE TV - RETURNS ACTUAL CHANNEL LIST
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
                    finishChannels(
                        onResult,
                        false,
                        emptyList(),
                        "Portal URL is empty or invalid."
                    )
                    return@Thread
                }

                if (!isValidMac(mac)) {
                    finishChannels(
                        onResult,
                        false,
                        emptyList(),
                        "Invalid MAC address."
                    )
                    return@Thread
                }

                Log.d(TAG, "LIVE: Portal = $portal")
                Log.d(TAG, "LIVE: MAC = $mac")

                val loadUrl = discoverLoadUrl(
                    portal = portal,
                    macAddress = mac
                )

                if (loadUrl.isEmpty()) {
                    finishChannels(
                        onResult,
                        false,
                        emptyList(),
                        "Could not find a compatible Stalker portal endpoint."
                    )
                    return@Thread
                }

                Log.d(TAG, "LIVE: endpoint = $loadUrl")

                val session = performHandshake(
                    loadUrl = loadUrl,
                    macAddress = mac
                )

                Log.d(TAG, "LIVE: handshake successful")
                Log.d(TAG, "LIVE: token received")

                // Profile is useful but some portals don't require it.
                try {
                    getProfile(
                        session = session,
                        macAddress = mac
                    )

                    Log.d(TAG, "LIVE: profile request successful")
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "LIVE: profile request failed: ${e.message}"
                    )
                }

                // Try to load genres first.
                val categories = try {
                    requestGenres(
                        session = session,
                        macAddress = mac
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "LIVE: genres request failed: ${e.message}"
                    )
                    emptyMap()
                }

                val channelsResponse = requestChannels(
                    session = session,
                    macAddress = mac
                )

                val channels = parseChannels(
                    response = channelsResponse,
                    categories = categories
                )

                Log.d(
                    TAG,
                    "LIVE: parsed ${channels.size} channels"
                )

                if (channels.isEmpty()) {
                    Log.w(
                        TAG,
                        "LIVE: server response = ${channelsResponse.take(2000)}"
                    )

                    finishChannels(
                        onResult,
                        false,
                        emptyList(),
                        "Portal connected, but no Live TV channels were found."
                    )
                    return@Thread
                }

                channels.take(20).forEachIndexed { index, channel ->
                    Log.d(
                        TAG,
                        "LIVE Channel[$index]: " +
                                "${channel.name} | " +
                                "id=${channel.id} | " +
                                "category=${channel.categoryName} | " +
                                "cmd=${channel.cmd.take(120)}"
                    )
                }

                finishChannels(
                    onResult,
                    true,
                    channels,
                    "Connected successfully. ${channels.size} Live TV channels found."
                )

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "LIVE: Stalker connection failed",
                    e
                )

                finishChannels(
                    onResult,
                    false,
                    emptyList(),
                    e.message ?: "Unknown portal connection error."
                )
            }
        }.start()
    }

    // ============================================================
    // PUBLIC MOVIES / VOD
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

                val loadUrl = discoverLoadUrl(
                    portal,
                    mac
                )

                if (loadUrl.isEmpty()) {
                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Could not find a compatible Stalker portal endpoint."
                    )
                    return@Thread
                }

                val session = performHandshake(
                    loadUrl,
                    mac
                )

                Log.d(TAG, "MOVIES: handshake successful")

                val categories = try {
                    requestVodCategories(
                        session,
                        mac
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "MOVIES: categories failed: ${e.message}"
                    )
                    emptyMap()
                }

                val response = requestMovies(
                    session,
                    mac
                )

                val movies = parseVodItems(
                    response,
                    categories
                )

                Log.d(
                    TAG,
                    "MOVIES: parsed ${movies.size} items"
                )

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
                Log.e(
                    TAG,
                    "Movies request failed",
                    e
                )

                finishMovies(
                    onResult,
                    false,
                    emptyList(),
                    e.message ?: "Movies request failed."
                )
            }
        }.start()
    }

    // ============================================================
    // PUBLIC SERIES
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

                val loadUrl = discoverLoadUrl(
                    portal,
                    mac
                )

                if (loadUrl.isEmpty()) {
                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Could not find a compatible Stalker portal endpoint."
                    )
                    return@Thread
                }

                val session = performHandshake(
                    loadUrl,
                    mac
                )

                Log.d(TAG, "SERIES: handshake successful")

                val categories = try {
                    requestSeriesCategories(
                        session,
                        mac
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "SERIES: categories failed: ${e.message}"
                    )
                    emptyMap()
                }

                val response = requestSeries(
                    session,
                    mac
                )

                val series = parseSeriesItems(
                    response,
                    categories
                )

                Log.d(
                    TAG,
                    "SERIES: parsed ${series.size} items"
                )

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
                Log.e(
                    TAG,
                    "Series request failed",
                    e
                )

                finishSeries(
                    onResult,
                    false,
                    emptyList(),
                    e.message ?: "Series request failed."
                )
            }
        }.start()
    }

    // ============================================================
    // PORTAL DISCOVERY
    // ============================================================

    private fun discoverLoadUrl(
        portal: String,
        macAddress: String
    ): String {

        val base = normalizeBaseUrl(portal)

        val candidates = linkedSetOf(
            "$base/stalker_portal/server/load.php",
            "$base/portal.php",
            "$base/server/load.php",
            "$base/stalker_portal/load.php"
        )

        for (candidate in candidates) {
            try {
                Log.d(
                    TAG,
                    "DISCOVERY: testing $candidate"
                )

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

                val root = parseJson(response)

                if (!root.isJsonObject) {
                    continue
                }

                val json = root.asJsonObject
                val js = jsonObject(json, "js")

                if (js == null) {
                    continue
                }

                val token = firstValue(
                    js,
                    "token"
                )

                if (token.isNotEmpty()) {
                    Log.d(
                        TAG,
                        "DISCOVERY: compatible endpoint = $candidate"
                    )

                    return candidate
                }

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "DISCOVERY failed: $candidate -> ${e.message}"
                )
            }
        }

        return ""
    }

    // ============================================================
    // URL / MAC NORMALIZATION
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

        var value = input
            .trim()
            .removeSuffix("/")

        val suffixes = listOf(
            "/load.php",
            "/server/load.php",
            "/server",
            "/stalker_portal/c",
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

        val root = parseJson(response)

        if (!root.isJsonObject) {
            throw Exception(
                "Handshake returned invalid JSON."
            )
        }

        val json = root.asJsonObject

        val js = jsonObject(
            json,
            "js"
        ) ?: throw Exception(
            "Handshake response is missing 'js'."
        )

        val token = firstValue(
            js,
            "token"
        )

        if (token.isEmpty()) {
            throw Exception(
                "Handshake failed: portal did not return a token."
            )
        }

        val random = firstValue(
            js,
            "random"
        )

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

        val root = parseJson(response)

        if (!root.isJsonObject) {
            throw Exception(
                "Profile returned invalid JSON."
            )
        }

        val json = root.asJsonObject

        return jsonObject(
            json,
            "js"
        ) ?: throw Exception(
            "Profile response is missing 'js'."
        )
    }

    // ============================================================
    // LIVE TV REQUESTS
    // ============================================================

    private fun requestGenres(
        session: Session,
        macAddress: String
    ): Map<String, String> {

        val response = request(
            loadUrl = session.loadUrl,
            params = mapOf(
                "type" to "itv",
                "action" to "get_genres",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress = macAddress,
            token = session.token
        )

        return parseCategories(response)
    }

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

            try {

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

            } catch (secondError: Exception) {

                Log.w(
                    TAG,
                    "get_channels failed: ${secondError.message}"
                )

                request(
                    loadUrl = session.loadUrl,
                    params = mapOf(
                        "type" to "itv",
                        "action" to "get_ordered_list",
                        "p" to "1",
                        "JsHttpRequest" to "1-xml"
                    ),
                    macAddress = macAddress,
                    token = session.token
                )
            }
        }
    }

    // ============================================================
    // MOVIES REQUESTS
    // ============================================================

    private fun requestVodCategories(
        session: Session,
        macAddress: String
    ): Map<String, String> {

        val response = request(
            loadUrl = session.loadUrl,
            params = mapOf(
                "type" to "vod",
                "action" to "get_categories",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress = macAddress,
            token = session.token
        )

        return parseCategories(response)
    }

    private fun requestMovies(
        session: Session,
        macAddress: String
    ): String {

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
    // SERIES REQUESTS
    // ============================================================

    private fun requestSeriesCategories(
        session: Session,
        macAddress: String
    ): Map<String, String> {

        val response = request(
            loadUrl = session.loadUrl,
            params = mapOf(
                "type" to "series",
                "action" to "get_categories",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress = macAddress,
            token = session.token
        )

        return parseCategories(response)
    }

    private fun requestSeries(
        session: Session,
        macAddress: String
    ): String {

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

        Log.d(
            TAG,
            "HTTP GET: $finalUrl"
        )

        val connection =
            URL(finalUrl)
                .openConnection() as HttpURLConnection

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
                "Accept-Language",
                "en-US,en;q=0.9"
            )

            connection.setRequestProperty(
                "Referer",
                buildReferer(loadUrl)
            )

            connection.setRequestProperty(
                "Cookie",
                "mac=$macAddress; " +
                        "stb_lang=$DEFAULT_LANGUAGE; " +
                        "timezone=$DEFAULT_TIMEZONE"
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
    // REFERER
    // ============================================================

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

    // ============================================================
    // QUERY BUILDER
    // ============================================================

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

    // ============================================================
    // CHANNEL PARSER
    // ============================================================

    private fun parseChannels(
        response: String,
        categories: Map<String, String> = emptyMap()
    ): List<Channel> {

        val result = mutableListOf<Channel>()

        try {

            val root = parseJson(response)

            if (!root.isJsonObject) {
                return emptyList()
            }

            val json = root.asJsonObject

            val js = jsonObject(
                json,
                "js"
            ) ?: return emptyList()

            val items =
                extractItems(
                    js
                )

            for (item in items) {

                val channel =
                    parseChannelObject(
                        item,
                        categories
                    )

                if (channel != null) {
                    result.add(channel)
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
        obj: JsonObject,
        categories: Map<String, String>
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
                "title",
                "channel_name"
            )

        val logo =
            firstValue(
                obj,
                "logo",
                "logo_url",
                "icon",
                "tvg_logo"
            )

        val cmd =
            firstValue(
                obj,
                "cmd",
                "stream_url",
                "url",
                "stream"
            )

        val categoryId =
            firstValue(
                obj,
                "tv_genre_id",
                "genre_id",
                "category_id",
                "genre"
            )

        if (id.isEmpty() && name.isEmpty()) {
            return null
        }

        val categoryName =
            categories[categoryId] ?: ""

        return Channel(
            id = id,
            name = name.ifEmpty {
                "Unknown Channel"
            },
            logo = logo,
            cmd = cmd,
            categoryId = categoryId,
            categoryName = categoryName
        )
    }

    // ============================================================
    // VOD PARSER
    // ============================================================

    private fun parseVodItems(
        response: String,
        categories: Map<String, String> = emptyMap()
    ): List<VodItem> {

        val result = mutableListOf<VodItem>()

        try {

            val root = parseJson(response)

            if (!root.isJsonObject) {
                return emptyList()
            }

            val json = root.asJsonObject

            val js = jsonObject(
                json,
                "js"
            ) ?: return emptyList()

            val items =
                extractItems(
                    js
                )

            for (item in items) {

                val id =
                    firstValue(
                        item,
                        "id",
                        "vclub_id",
                        "movie_id"
                    )

                val name =
                    firstValue(
                        item,
                        "name",
                        "title"
                    )

                if (id.isEmpty() && name.isEmpty()) {
                    continue
                }

                val logo =
                    firstValue(
                        item,
                        "screenshot_uri",
                        "logo",
                        "cover",
                        "poster",
                        "icon"
                    )

                val cmd =
                    firstValue(
                        item,
                        "cmd",
                        "stream_url",
                        "url"
                    )

                val categoryId =
                    firstValue(
                        item,
                        "category_id",
                        "genre_id"
                    )

                val year =
                    firstValue(
                        item,
                        "year",
                        "release_year"
                    )

                val description =
                    firstValue(
                        item,
                        "description",
                        "descr",
                        "plot"
                    )

                result.add(
                    VodItem(
                        id = id,
                        name = name.ifEmpty {
                            "Unknown Movie"
                        },
                        logo = logo,
                        cmd = cmd,
                        categoryId = categoryId,
                        categoryName = categories[categoryId] ?: "",
                        year = year,
                        description = description
                    )
                )
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
    // SERIES PARSER
    // ============================================================

    private fun parseSeriesItems(
        response: String,
        categories: Map<String, String> = emptyMap()
    ): List<SeriesItem> {

        val result = mutableListOf<SeriesItem>()

        try {

            val root = parseJson(response)

            if (!root.isJsonObject) {
                return emptyList()
            }

            val json = root.asJsonObject

            val js = jsonObject(
                json,
                "js"
            ) ?: return emptyList()

            val items =
                extractItems(
                    js
                )

            for (item in items) {

                val id =
                    firstValue(
                        item,
                        "id",
                        "series_id"
                    )

                val name =
                    firstValue(
                        item,
                        "name",
                        "title"
                    )

                if (id.isEmpty() && name.isEmpty()) {
                    continue
                }

                val logo =
                    firstValue(
                        item,
                        "screenshot_uri",
                        "logo",
                        "cover",
                        "poster",
                        "icon"
                    )

                val categoryId =
                    firstValue(
                        item,
                        "category_id",
                        "genre_id"
                    )

                val year =
                    firstValue(
                        item,
                        "year",
                        "release_year"
                    )

                val description =
                    firstValue(
                        item,
                        "description",
                        "descr",
                        "plot"
                    )

                result.add(
                    SeriesItem(
                        id = id,
                        name = name.ifEmpty {
                            "Unknown Series"
                        },
                        logo = logo,
                        categoryId = categoryId,
                        categoryName = categories[categoryId] ?: "",
                        year = year,
                        description = description
                    )
                )
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
    // GENERIC CATEGORY PARSER
    // ============================================================

    private fun parseCategories(
        response: String
    ): Map<String, String> {

        val result =
            linkedMapOf<String, String>()

        try {

            val root = parseJson(response)

            if (!root.isJsonObject) {
                return emptyMap()
            }

            val json = root.asJsonObject

            val js = jsonObject(
                json,
                "js"
            ) ?: return emptyMap()

            val items =
                extractItems(
                    js
                )

            for (item in items) {

                val id =
                    firstValue(
                        item,
                        "id",
                        "category_id",
                        "genre_id"
                    )

                val name =
                    firstValue(
                        item,
                        "title",
                        "name"
                    )

                if (id.isNotEmpty() &&
                    name.isNotEmpty()
                ) {
                    result[id] = name
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Category parsing failed",
                e
            )
        }

        return result
    }

    // ============================================================
    // EXTRACT ITEMS FROM DIFFERENT STALKER RESPONSE SHAPES
    // ============================================================

    private fun extractItems(
        js: JsonObject
    ): List<JsonObject> {

        val result =
            mutableListOf<JsonObject>()

        val possibleKeys = listOf(
            "data",
            "results",
            "channels",
            "items",
            "list"
        )

        for (key in possibleKeys) {

            if (!js.has(key)) {
                continue
            }

            val value =
                js.get(key)

            if (value.isJsonArray) {

                val array =
                    value.asJsonArray

                for (item in array) {

                    if (item.isJsonObject) {
                        result.add(
                            item.asJsonObject
                        )
                    }
                }

                if (result.isNotEmpty()) {
                    return result
                }
            }

            if (value.isJsonObject) {

                val obj =
                    value.asJsonObject

                // Some portals return:
                // data: { ... }
                // while others return:
                // data: { results: [...] }

                val nested =
                    extractItems(
                        obj
                    )

                if (nested.isNotEmpty()) {
                    result.addAll(nested)
                    return result
                }

                result.add(obj)
                return result
            }
        }

        // Last fallback:
        // if "js" itself contains numeric object keys.
        for ((key, value) in js.entrySet()) {

            if (key == "total_items" ||
                key == "total" ||
                key == "max_page_items"
            ) {
                continue
            }

            if (value.isJsonObject) {

                result.add(
                    value.asJsonObject
                )
            }
        }

        return result
    }

    // ============================================================
    // JSON HELPERS
    // ============================================================

    private fun parseJson(
        response: String
    ): JsonElement {

        return JsonParser()
            .parse(response)
    }

    private fun jsonObject(
        parent: JsonObject,
        key: String
    ): JsonObject? {

        if (!parent.has(key)) {
            return null
        }

        return try {

            val value =
                parent.get(key)

            if (value.isJsonObject) {
                value.asJsonObject
            } else {
                null
            }

        } catch (_: Exception) {
            null
        }
    }

    private fun firstValue(
        obj: JsonObject,
        vararg keys: String
    ): String {

        for (key in keys) {

            if (!obj.has(key)) {
                continue
            }

            try {

                val value =
                    obj.get(key)

                if (value.isJsonNull) {
                    continue
                }

                if (value.isJsonPrimitive) {

                    val primitive =
                        value.asJsonPrimitive

                    val text =
                        when {
                            primitive.isString ->
                                primitive.asString

                            primitive.isNumber ->
                                primitive.asNumber.toString()

                            primitive.isBoolean ->
                                primitive.asBoolean.toString()

                            else ->
                                ""
                        }

                    if (text.isNotBlank()) {
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
        return firstValue(
            obj,
            key
        )
    }

    // ============================================================
    // CALLBACK HELPERS
    // ============================================================

    private fun finishChannels(
        callback: (Boolean, List<Channel>, String) -> Unit,
        success: Boolean,
        channels: List<Channel>,
        message: String
    ) {

        callback(
            success,
            channels,
            message
        )
    }

    private fun finishMovies(
        callback: (Boolean, List<VodItem>, String) -> Unit,
        success: Boolean,
        movies: List<VodItem>,
        message: String
    ) {

        callback(
            success,
            movies,
            message
        )
    }

    private fun finishSeries(
        callback: (Boolean, List<SeriesItem>, String) -> Unit,
        success: Boolean,
        series: List<SeriesItem>,
        message: String
    ) {

        callback(
            success,
            series,
            message
        )
    }
}

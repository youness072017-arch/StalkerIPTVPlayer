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
        private const val READ_TIMEOUT = 30000

        private const val USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) " +
                    "AppleWebKit/533.3 (KHTML, like Gecko) " +
                    "MAG250 stbapp ver: 2 rev: 250 Safari/533.3"

        private const val X_USER_AGENT =
            "Model: MAG250; Link: WiFi"

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

                Log.d(TAG, "LIVE portal=$portal")
                Log.d(TAG, "LIVE mac=$mac")

                val loadUrl = discoverLoadUrl(portal, mac)

                if (loadUrl.isEmpty()) {
                    finishChannels(
                        onResult,
                        false,
                        emptyList(),
                        "Could not find a compatible Stalker portal endpoint."
                    )
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
                    Log.w(TAG, "Genres failed: ${e.message}")
                    emptyMap()
                }

                Log.d(TAG, "LIVE categories=${categories.size}")

                val channels = requestAllLiveChannels(
                    session,
                    mac,
                    categories
                )

                Log.d(TAG, "LIVE FINAL channels=${channels.size}")

                if (channels.isEmpty()) {
                    finishChannels(
                        onResult,
                        false,
                        emptyList(),
                        "Portal connected, but no Live TV channels were found."
                    )
                    return@Thread
                }

                finishChannels(
                    onResult,
                    true,
                    channels,
                    "Connected successfully. ${channels.size} Live TV channels found."
                )

            } catch (e: Exception) {
                Log.e(TAG, "LIVE failed", e)

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
    // GET ALL LIVE CHANNELS
    // ============================================================

    private fun requestAllLiveChannels(
        session: Session,
        macAddress: String,
        categories: Map<String, String>
    ): List<Channel> {

        val allChannels = LinkedHashMap<String, Channel>()

        // --------------------------------------------------------
        // FIRST: try get_all_channels
        // --------------------------------------------------------

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

            Log.d(
                TAG,
                "get_all_channels returned ${parsed.size}"
            )

            for (channel in parsed) {
                val key = channel.id.ifEmpty {
                    "${channel.categoryId}|${channel.name}"
                }

                if (key.isNotEmpty()) {
                    allChannels[key] = channel
                }
            }

            /*
             * Some portals really return everything here.
             * If we have a large complete result, use it.
             */
            if (allChannels.size > 20) {
                Log.d(
                    TAG,
                    "Using get_all_channels result: ${allChannels.size}"
                )

                return allChannels.values.toList()
            }

        } catch (e: Exception) {
            Log.w(
                TAG,
                "get_all_channels failed: ${e.message}"
            )
        }

        // --------------------------------------------------------
        // SECOND: load every genre/category separately
        // --------------------------------------------------------

        if (categories.isNotEmpty()) {

            for ((categoryId, categoryName) in categories) {

                try {
                    val categoryChannels = requestCategoryPages(
                        session = session,
                        macAddress = macAddress,
                        categoryId = categoryId,
                        categoryName = categoryName,
                        categories = categories
                    )

                    Log.d(
                        TAG,
                        "CATEGORY $categoryName -> ${categoryChannels.size}"
                    )

                    for (channel in categoryChannels) {
                        val key = channel.id.ifEmpty {
                            "${channel.categoryId}|${channel.name}"
                        }

                        if (key.isNotEmpty()) {
                            allChannels[key] = channel
                        }
                    }

                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Category $categoryName failed: ${e.message}"
                    )
                }
            }
        }

        // --------------------------------------------------------
        // THIRD: fallback to global pagination
        // --------------------------------------------------------

        if (allChannels.isEmpty()) {

            try {
                val globalChannels = requestGlobalPages(
                    session,
                    macAddress,
                    categories
                )

                for (channel in globalChannels) {
                    val key = channel.id.ifEmpty {
                        "${channel.categoryId}|${channel.name}"
                    }

                    if (key.isNotEmpty()) {
                        allChannels[key] = channel
                    }
                }

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Global pagination failed: ${e.message}"
                )
            }
        }

        return allChannels.values.toList()
    }

    // ============================================================
    // CATEGORY PAGINATION
    // ============================================================

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

            if (metadata.first > 0) {
                totalItems = metadata.first
            }

            if (metadata.second > 0) {
                pageSize = metadata.second
            }

            val channels = parseChannels(
                response,
                categories
            ).map { channel ->

                if (channel.categoryId.isEmpty()) {
                    channel.copy(
                        categoryId = categoryId,
                        categoryName = categoryName
                    )
                } else if (channel.categoryName.isEmpty()) {
                    channel.copy(
                        categoryName =
                            categories[channel.categoryId]
                                ?: categoryName
                    )
                } else {
                    channel
                }
            }

            Log.d(
                TAG,
                "CATEGORY page=$page category=$categoryName " +
                        "items=${channels.size} total=$totalItems size=$pageSize"
            )

            if (channels.isEmpty()) {
                break
            }

            for (channel in channels) {
                val key = channel.id.ifEmpty {
                    "${channel.categoryId}|${channel.name}"
                }

                if (key.isNotEmpty()) {
                    result[key] = channel
                }
            }

            if (totalItems > 0 &&
                result.size >= totalItems
            ) {
                break
            }

            if (channels.size < pageSize) {
                break
            }

            page++
        }

        return result.values.toList()
    }

    // ============================================================
    // GLOBAL PAGINATION
    // ============================================================

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

            if (metadata.first > 0) {
                totalItems = metadata.first
            }

            if (metadata.second > 0) {
                pageSize = metadata.second
            }

            val channels = parseChannels(
                response,
                categories
            )

            Log.d(
                TAG,
                "GLOBAL page=$page items=${channels.size} " +
                        "total=$totalItems size=$pageSize"
            )

            if (channels.isEmpty()) {
                break
            }

            for (channel in channels) {
                val key = channel.id.ifEmpty {
                    "${channel.categoryId}|${channel.name}"
                }

                if (key.isNotEmpty()) {
                    result[key] = channel
                }
            }

            if (totalItems > 0 &&
                result.size >= totalItems
            ) {
                break
            }

            if (channels.size < pageSize) {
                break
            }

            page++
        }

        return result.values.toList()
    }

    // ============================================================
    // GENRES
    // ============================================================

    private fun requestGenres(
        session: Session,
        macAddress: String
    ): Map<String, String> {

        val response = request(
            session.loadUrl,
            mapOf(
                "type" to "itv",
                "action" to "get_genres",
                "JsHttpRequest" to "1-xml"
            ),
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
                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Portal URL is empty or invalid."
                    )
                    return@Thread
                }

                val loadUrl = discoverLoadUrl(portal, mac)

                if (loadUrl.isEmpty()) {
                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Could not find a compatible Stalker portal endpoint."
                    )
                    return@Thread
                }

                val session = performHandshake(loadUrl, mac)

                val categories = try {
                    requestVodCategories(session, mac)
                } catch (e: Exception) {
                    emptyMap()
                }

                val response = request(
                    session.loadUrl,
                    mapOf(
                        "type" to "vod",
                        "action" to "get_ordered_list",
                        "p" to "1",
                        "JsHttpRequest" to "1-xml"
                    ),
                    mac,
                    session.token
                )

                val movies = parseVodItems(
                    response,
                    categories
                )

                finishMovies(
                    onResult,
                    movies.isNotEmpty(),
                    movies,
                    if (movies.isEmpty()) {
                        "No Movies/VOD were found."
                    } else {
                        "Movies loaded: ${movies.size}"
                    }
                )

            } catch (e: Exception) {
                finishMovies(
                    onResult,
                    false,
                    emptyList(),
                    e.message ?: "Movies request failed."
                )
            }
        }.start()
    }

    private fun requestVodCategories(
        session: Session,
        macAddress: String
    ): Map<String, String> {

        val response = request(
            session.loadUrl,
            mapOf(
                "type" to "vod",
                "action" to "get_categories",
                "JsHttpRequest" to "1-xml"
            ),
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
                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Portal URL is empty or invalid."
                    )
                    return@Thread
                }

                val loadUrl = discoverLoadUrl(portal, mac)

                if (loadUrl.isEmpty()) {
                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Could not find a compatible Stalker portal endpoint."
                    )
                    return@Thread
                }

                val session = performHandshake(loadUrl, mac)

                val categories = try {
                    val response = request(
                        session.loadUrl,
                        mapOf(
                            "type" to "series",
                            "action" to "get_categories",
                            "JsHttpRequest" to "1-xml"
                        ),
                        mac,
                        session.token
                    )

                    parseCategories(response)

                } catch (e: Exception) {
                    emptyMap()
                }

                val response = request(
                    session.loadUrl,
                    mapOf(
                        "type" to "series",
                        "action" to "get_ordered_list",
                        "p" to "1",
                        "JsHttpRequest" to "1-xml"
                    ),
                    mac,
                    session.token
                )

                val series = parseSeriesItems(
                    response,
                    categories
                )

                finishSeries(
                    onResult,
                    series.isNotEmpty(),
                    series,
                    if (series.isEmpty()) {
                        "No Series were found."
                    } else {
                        "Series loaded: ${series.size}"
                    }
                )

            } catch (e: Exception) {
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

                val response = request(
                    candidate,
                    mapOf(
                        "type" to "stb",
                        "action" to "handshake",
                        "token" to "",
                        "JsHttpRequest" to "1-xml"
                    ),
                    macAddress,
                    null
                )

                val root = parseJson(response)

                if (!root.isJsonObject) {
                    continue
                }

                val js = jsonObject(
                    root.asJsonObject,
                    "js"
                ) ?: continue

                val token = firstValue(
                    js,
                    "token"
                )

                if (token.isNotEmpty()) {
                    return candidate
                }

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Discovery failed $candidate: ${e.message}"
                )
            }
        }

        return ""
    }

    // ============================================================
    // HANDSHAKE
    // ============================================================

    private fun performHandshake(
        loadUrl: String,
        macAddress: String
    ): Session {

        val response = request(
            loadUrl,
            mapOf(
                "type" to "stb",
                "action" to "handshake",
                "token" to "",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress,
            null
        )

        val root = parseJson(response)

        if (!root.isJsonObject) {
            throw Exception("Handshake returned invalid JSON.")
        }

        val js = jsonObject(
            root.asJsonObject,
            "js"
        ) ?: throw Exception(
            "Handshake response is missing js."
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

    private fun getProfile(
        session: Session,
        macAddress: String
    ): JsonObject {

        val response = request(
            session.loadUrl,
            mapOf(
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
            macAddress,
            session.token
        )

        val root = parseJson(response)

        if (!root.isJsonObject) {
            throw Exception("Profile returned invalid JSON.")
        }

        return jsonObject(
            root.asJsonObject,
            "js"
        ) ?: throw Exception(
            "Profile response is missing js."
        )
    }

    // ============================================================
    // HTTP
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

        Log.d(TAG, "HTTP GET: $finalUrl")

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

            val responseCode = connection.responseCode

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
                "HTTP response=$responseCode length=${response.length}"
            )

            if (responseCode !in 200..299 &&
                response.isBlank()
            ) {
                throw Exception(
                    "HTTP error $responseCode"
                )
            }

            return response

        } finally {
            connection.disconnect()
        }
    }

    private fun buildQuery(
        params: Map<String, String>
    ): String {

        return params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=" +
                    URLEncoder.encode(it.value, "UTF-8")
        }
    }

    private fun buildReferer(
        loadUrl: String
    ): String {

        return try {
            val url = URL(loadUrl)

            "${url.protocol}://${url.host}/"
        } catch (e: Exception) {
            loadUrl
        }
    }

    // ============================================================
    // PARSING
    // ============================================================

    private fun parseChannels(
        response: String,
        categories: Map<String, String>
    ): List<Channel> {

        if (response.isBlank()) {
            return emptyList()
        }

        return try {

            val root = parseJson(response)

            val objects = extractChannelObjects(root)

            val result = ArrayList<Channel>()

            for (obj in objects) {

                val channel =
                    parseChannelObject(
                        obj,
                        categories
                    )

                if (channel != null) {
                    result.add(channel)
                }
            }

            result

        } catch (e: Exception) {

            Log.w(
                TAG,
                "parseChannels failed: ${e.message}"
            )

            emptyList()
        }
    }

    private fun extractChannelObjects(
        root: JsonElement
    ): List<JsonObject> {

        val result = ArrayList<JsonObject>()

        fun addFromArray(array: JsonArray) {
            for (element in array) {
                if (element.isJsonObject) {
                    result.add(element.asJsonObject)
                }
            }
        }

        fun inspectObject(obj: JsonObject) {

            val directKeys = listOf(
                "data",
                "results",
                "channels",
                "items",
                "list",
                "rows"
            )

            for (key in directKeys) {

                val value = obj.get(key)
                    ?: continue

                if (value.isJsonArray) {
                    addFromArray(value.asJsonArray)
                } else if (value.isJsonObject) {
                    inspectObject(value.asJsonObject)
                }
            }

            for ((key, value) in obj.entrySet()) {

                if (key.all { it.isDigit() } &&
                    value.isJsonObject
                ) {
                    result.add(
                        value.asJsonObject
                    )
                }
            }
        }

        if (root.isJsonArray) {
            addFromArray(root.asJsonArray)
        } else if (root.isJsonObject) {

            val rootObject = root.asJsonObject

            val js = rootObject.get("js")

            if (js != null) {

                if (js.isJsonArray) {
                    addFromArray(js.asJsonArray)
                } else if (js.isJsonObject) {
                    inspectObject(js.asJsonObject)
                }
            }

            inspectObject(rootObject)
        }

        return result.distinctBy {
            val id = firstValue(
                it,
                "id",
                "ch_id",
                "channel_id"
            )

            if (id.isNotEmpty()) {
                "id:$id"
            } else {
                "name:${firstValue(it, "name", "title")}"
            }
        }
    }

    private fun parseChannelObject(
        obj: JsonObject,
        categories: Map<String, String>
    ): Channel? {

        val id = firstValue(
            obj,
            "id",
            "ch_id",
            "channel_id"
        )

        val name = firstValue(
            obj,
            "name",
            "title",
            "channel_name"
        )

        if (id.isEmpty() && name.isEmpty()) {
            return null
        }

        val logo = firstValue(
            obj,
            "logo",
            "logo_url",
            "icon",
            "tvg_logo",
            "screenshot_uri"
        )

        val cmd = firstValue(
            obj,
            "cmd",
            "stream_url",
            "url",
            "stream",
            "cmd_url"
        )

        val categoryId = firstValue(
            obj,
            "tv_genre_id",
            "genre_id",
            "category_id",
            "genre"
        )

        val categoryName =
            categories[categoryId]
                ?: firstValue(
                    obj,
                    "tv_genre_name",
                    "genre_name",
                    "category_name"
                )

        return Channel(
            id = id,
            name = if (name.isNotEmpty()) name else "Channel $id",
            logo = logo,
            cmd = cmd,
            categoryId = categoryId,
            categoryName = categoryName
        )
    }

    private fun parseVodItems(
        response: String,
        categories: Map<String, String>
    ): List<VodItem> {

        return try {

            val root = parseJson(response)
            val objects = extractGenericObjects(root)

            objects.mapNotNull { obj ->

                val id = firstValue(
                    obj,
                    "id",
                    "movie_id",
                    "v_id"
                )

                val name = firstValue(
                    obj,
                    "name",
                    "title"
                )

                if (id.isEmpty() && name.isEmpty()) {
                    return@mapNotNull null
                }

                val categoryId = firstValue(
                    obj,
                    "category_id",
                    "genre_id"
                )

                VodItem(
                    id = id,
                    name = name.ifEmpty { "Movie $id" },
                    logo = firstValue(
                        obj,
                        "logo",
                        "cover",
                        "poster"
                    ),
                    cmd = firstValue(
                        obj,
                        "cmd",
                        "stream_url",
                        "url"
                    ),
                    categoryId = categoryId,
                    categoryName =
                        categories[categoryId]
                            ?: firstValue(
                                obj,
                                "category_name",
                                "genre_name"
                            ),
                    year = firstValue(
                        obj,
                        "year"
                    ),
                    description = firstValue(
                        obj,
                        "description",
                        "plot"
                    )
                )
            }

        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSeriesItems(
        response: String,
        categories: Map<String, String>
    ): List<SeriesItem> {

        return try {

            val root = parseJson(response)
            val objects = extractGenericObjects(root)

            objects.mapNotNull { obj ->

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

                if (id.isEmpty() && name.isEmpty()) {
                    return@mapNotNull null
                }

                val categoryId = firstValue(
                    obj,
                    "category_id",
                    "genre_id"
                )

                SeriesItem(
                    id = id,
                    name = name.ifEmpty { "Series $id" },
                    logo = firstValue(
                        obj,
                        "logo",
                        "cover",
                        "poster"
                    ),
                    categoryId = categoryId,
                    categoryName =
                        categories[categoryId]
                            ?: firstValue(
                                obj,
                                "category_name",
                                "genre_name"
                            ),
                    year = firstValue(
                        obj,
                        "year"
                    ),
                    description = firstValue(
                        obj,
                        "description",
                        "plot"
                    )
                )
            }

        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractGenericObjects(
        root: JsonElement
    ): List<JsonObject> {

        val result = ArrayList<JsonObject>()

        fun inspect(obj: JsonObject) {

            val keys = listOf(
                "data",
                "results",
                "items",
                "list",
                "rows",
                "movies",
                "series"
            )

            for (key in keys) {

                val value = obj.get(key)
                    ?: continue

                if (value.isJsonArray) {

                    for (element in value.asJsonArray) {
                        if (element.isJsonObject) {
                            result.add(
                                element.asJsonObject
                            )
                        }
                    }

                } else if (value.isJsonObject) {
                    inspect(value.asJsonObject)
                }
            }

            for ((key, value) in obj.entrySet()) {
                if (key.all { it.isDigit() } &&
                    value.isJsonObject
                ) {
                    result.add(
                        value.asJsonObject
                    )
                }
            }
        }

        if (root.isJsonArray) {

            for (element in root.asJsonArray) {
                if (element.isJsonObject) {
                    result.add(
                        element.asJsonObject
                    )
                }
            }

        } else if (root.isJsonObject) {

            val obj = root.asJsonObject
            val js = obj.get("js")

            if (js != null) {

                if (js.isJsonArray) {

                    for (element in js.asJsonArray) {
                        if (element.isJsonObject) {
                            result.add(
                                element.asJsonObject
                            )
                        }
                    }

                } else if (js.isJsonObject) {
                    inspect(js.asJsonObject)
                }
            }

            inspect(obj)
        }

        return result.distinctBy {
            firstValue(
                it,
                "id",
                "movie_id",
                "series_id",
                "name",
                "title"
            )
        }
    }

    // ============================================================
    // CATEGORY PARSER
    // ============================================================

    private fun parseCategories(
        response: String
    ): Map<String, String> {

        val result = LinkedHashMap<String, String>()

        try {

            val root = parseJson(response)
            val objects = extractGenericObjects(root)

            for (obj in objects) {

                val id = firstValue(
                    obj,
                    "id",
                    "category_id",
                    "genre_id",
                    "tv_genre_id"
                )

                val name = firstValue(
                    obj,
                    "title",
                    "name",
                    "category_name",
                    "genre_name"
                )

                if (id.isNotEmpty() &&
                    name.isNotEmpty()
                ) {
                    result[id] = name
                }
            }

        } catch (e: Exception) {
            Log.w(
                TAG,
                "parseCategories failed: ${e.message}"
            )
        }

        return result
    }

    // ============================================================
    // PAGINATION METADATA
    // ============================================================

    private fun extractPagination(
        root: JsonElement
    ): Pair<Int, Int> {

        var total = -1
        var pageSize = -1

        fun inspect(obj: JsonObject) {

            val totalValue =
                firstValue(
                    obj,
                    "total_items",
                    "total",
                    "count"
                )

            val pageValue =
                firstValue(
                    obj,
                    "max_page_items",
                    "items_per_page",
                    "page_items"
                )

            totalValue.toIntOrNull()?.let {
                if (it > 0) {
                    total = it
                }
            }

            pageValue.toIntOrNull()?.let {
                if (it > 0) {
                    pageSize = it
                }
            }

            val data = obj.get("data")

            if (data != null &&
                data.isJsonObject
            ) {
                inspect(data.asJsonObject)
            }

            val js = obj.get("js")

            if (js != null &&
                js.isJsonObject
            ) {
                inspect(js.asJsonObject)
            }
        }

        try {
            if (root.isJsonObject) {
                inspect(root.asJsonObject)
            }
        } catch (_: Exception) {
        }

        return Pair(
            total,
            pageSize
        )
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
        obj: JsonObject,
        key: String
    ): JsonObject? {

        val value = obj.get(key)

        return if (
            value != null &&
            value.isJsonObject
        ) {
            value.asJsonObject
        } else {
            null
        }
    }

    private fun firstValue(
        obj: JsonObject,
        vararg keys: String
    ): String {

        for (key in keys) {

            val value = obj.get(key)
                ?: continue

            if (value.isJsonNull) {
                continue
            }

            if (value.isJsonPrimitive) {
                return try {
                    value.asString.trim()
                } catch (_: Exception) {
                    ""
                }
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

        if (!value.startsWith(
                "http://",
                true
            ) &&
            !value.startsWith(
                "https://",
                true
            )
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

                if (value.endsWith(
                        suffix,
                        true
                    )
                ) {

                    value = value
                        .dropLast(
                            suffix.length
                        )
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
    // CALLBACKS
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

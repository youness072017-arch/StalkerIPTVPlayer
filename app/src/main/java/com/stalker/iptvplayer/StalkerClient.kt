package com.stalker.iptvplayer

import android.os.Handler
import android.os.Looper
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
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.concurrent.thread

class StalkerClient {

    companion object {
        private const val TAG = "StalkerClient"

        private const val LOAD_PATH =
            "/stalker_portal/server/load.php"

        private const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGES = 50

        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 30_000
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------
    // MODELS
    // -------------------------------------------------------------------------

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

    data class SeriesSeason(
        val id: String,
        val name: String,
        val seasonNumber: Int = 0
    )

    data class SeriesEpisode(
        val id: String,
        val name: String,
        val episodeNumber: Int = 0,
        val seasonNumber: Int = 0,
        val cmd: String = "",
        val logo: String = "",
        val description: String = ""
    )

    data class Profile(
        val id: String = "",
        val mac: String = "",
        val username: String = "",
        val status: String = "",
        val expiryDate: String = "",
        val tariffPlan: String = "",
        val accountBalance: String = ""
    )

    data class StalkerSession(
        val loadUrl: String,
        val token: String = "",
        val random: String = ""
    )

    // -------------------------------------------------------------------------
    // SESSION
    // -------------------------------------------------------------------------

    @Volatile
    private var currentSession: StalkerSession? = null

    @Volatile
    private var currentPortal: String = ""

    @Volatile
    private var currentMac: String = ""

    fun clearSession() {
        currentSession = null
        currentPortal = ""
        currentMac = ""
    }

    // -------------------------------------------------------------------------
    // LIVE TV
    // -------------------------------------------------------------------------

    fun fetchChannels(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, List<Channel>, String) -> Unit
    ) {
        runAsync {
            try {
                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isBlank() || mac.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        emptyList(),
                        "Portal or MAC is missing."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val categories = fetchCategoriesInternal(
                    session,
                    mac,
                    "itv"
                )

                val categoryMap = categories.associateBy(
                    { jsonString(it, "id") },
                    { firstString(it, "title", "name") }
                )

                val channels = mutableListOf<Channel>()

                for (page in 1..MAX_PAGES) {

                    val response = request(
                        session = session,
                        mac = mac,
                        params = mapOf(
                            "type" to "itv",
                            "action" to "get_ordered_list",
                            "p" to page.toString(),
                            "JsHttpRequest" to "1-xml"
                        )
                    )

                    val items = extractArray(response, "js", "data")

                    if (items.isEmpty()) {
                        break
                    }

                    for (item in items) {

                        val id = firstString(
                            item,
                            "id",
                            "ch_id",
                            "number"
                        )

                        val name = firstString(
                            item,
                            "name",
                            "title"
                        )

                        if (id.isBlank() && name.isBlank()) {
                            continue
                        }

                        val categoryId = firstString(
                            item,
                            "tv_genre_id",
                            "category_id",
                            "genre_id"
                        )

                        channels.add(
                            Channel(
                                id = id,
                                name = name,
                                logo = firstString(
                                    item,
                                    "logo",
                                    "icon"
                                ),
                                cmd = firstString(
                                    item,
                                    "cmd",
                                    "command"
                                ),
                                categoryId = categoryId,
                                categoryName = categoryMap[categoryId].orEmpty()
                            )
                        )
                    }

                    if (!hasMorePages(response, items.size)) {
                        break
                    }
                }

                postResult(
                    onResult,
                    true,
                    channels.distinctBy {
                        it.id.ifBlank { it.name }
                    },
                    "Live TV loaded."
                )

            } catch (e: Exception) {

                Log.e(TAG, "fetchChannels failed", e)

                postResult(
                    onResult,
                    false,
                    emptyList(),
                    safeError(e)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // MOVIES / VOD
    // -------------------------------------------------------------------------

    fun fetchMovies(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, List<VodItem>, String) -> Unit
    ) {
        runAsync {
            try {
                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isBlank() || mac.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        emptyList(),
                        "Portal or MAC is missing."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val categories = fetchCategoriesInternal(
                    session,
                    mac,
                    "vod"
                )

                val categoryMap = categories.associateBy(
                    { jsonString(it, "id") },
                    { firstString(it, "title", "name") }
                )

                val movies = mutableListOf<VodItem>()

                for (page in 1..MAX_PAGES) {

                    val response = request(
                        session = session,
                        mac = mac,
                        params = mapOf(
                            "type" to "vod",
                            "action" to "get_ordered_list",
                            "p" to page.toString(),
                            "JsHttpRequest" to "1-xml"
                        )
                    )

                    val items = extractArray(response, "js", "data")

                    if (items.isEmpty()) {
                        break
                    }

                    for (item in items) {

                        val id = firstString(
                            item,
                            "id",
                            "movie_id"
                        )

                        val name = firstString(
                            item,
                            "name",
                            "title"
                        )

                        if (id.isBlank() && name.isBlank()) {
                            continue
                        }

                        val categoryId = firstString(
                            item,
                            "category_id",
                            "genre_id"
                        )

                        movies.add(
                            VodItem(
                                id = id,
                                name = name,
                                logo = firstString(
                                    item,
                                    "logo",
                                    "icon"
                                ),
                                cmd = firstString(
                                    item,
                                    "cmd",
                                    "command"
                                ),
                                categoryId = categoryId,
                                categoryName = categoryMap[categoryId].orEmpty(),
                                year = firstString(
                                    item,
                                    "year",
                                    "released"
                                ),
                                description = firstString(
                                    item,
                                    "description",
                                    "descr",
                                    "plot"
                                )
                            )
                        )
                    }

                    if (!hasMorePages(response, items.size)) {
                        break
                    }
                }

                postResult(
                    onResult,
                    true,
                    movies.distinctBy {
                        it.id.ifBlank { it.name }
                    },
                    "Movies loaded."
                )

            } catch (e: Exception) {

                Log.e(TAG, "fetchMovies failed", e)

                postResult(
                    onResult,
                    false,
                    emptyList(),
                    safeError(e)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // SERIES
    // -------------------------------------------------------------------------

    fun fetchSeries(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, List<SeriesItem>, String) -> Unit
    ) {
        runAsync {
            try {
                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isBlank() || mac.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        emptyList(),
                        "Portal or MAC is missing."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val categories = fetchCategoriesInternal(
                    session,
                    mac,
                    "series"
                )

                val categoryMap = categories.associateBy(
                    { jsonString(it, "id") },
                    { firstString(it, "title", "name") }
                )

                val seriesList = mutableListOf<SeriesItem>()

                for (page in 1..MAX_PAGES) {

                    val response = request(
                        session = session,
                        mac = mac,
                        params = mapOf(
                            "type" to "series",
                            "action" to "get_ordered_list",
                            "p" to page.toString(),
                            "JsHttpRequest" to "1-xml"
                        )
                    )

                    val items = extractArray(response, "js", "data")

                    if (items.isEmpty()) {
                        break
                    }

                    for (item in items) {

                        val id = firstString(
                            item,
                            "id",
                            "series_id"
                        )

                        val name = firstString(
                            item,
                            "name",
                            "title"
                        )

                        if (id.isBlank() && name.isBlank()) {
                            continue
                        }

                        val categoryId = firstString(
                            item,
                            "category_id",
                            "genre_id"
                        )

                        seriesList.add(
                            SeriesItem(
                                id = id,
                                name = name,
                                logo = firstString(
                                    item,
                                    "logo",
                                    "icon"
                                ),
                                categoryId = categoryId,
                                categoryName = categoryMap[categoryId].orEmpty(),
                                year = firstString(
                                    item,
                                    "year",
                                    "released"
                                ),
                                description = firstString(
                                    item,
                                    "description",
                                    "descr",
                                    "plot"
                                )
                            )
                        )
                    }

                    if (!hasMorePages(response, items.size)) {
                        break
                    }
                }

                postResult(
                    onResult,
                    true,
                    seriesList.distinctBy {
                        it.id.ifBlank { it.name }
                    },
                    "Series loaded."
                )

            } catch (e: Exception) {

                Log.e(TAG, "fetchSeries failed", e)

                postResult(
                    onResult,
                    false,
                    emptyList(),
                    safeError(e)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // SERIES SEASONS
    // -------------------------------------------------------------------------

    fun fetchSeriesSeasons(
        portalUrl: String,
        macAddress: String,
        seriesId: String,
        onResult: (Boolean, List<SeriesSeason>, String) -> Unit
    ) {
        runAsync {
            try {

                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isBlank() || mac.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        emptyList(),
                        "Portal or MAC is missing."
                    )
                    return@runAsync
                }

                if (seriesId.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        emptyList(),
                        "Series ID is missing."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val response = request(
                    session = session,
                    mac = mac,
                    params = mapOf(
                        "type" to "series",
                        "action" to "get_ordered_list",
                        "p" to "1",
                        "movie_id" to seriesId,
                        "JsHttpRequest" to "1-xml"
                    )
                )

                val seasons = parseSeasons(response)

                postResult(
                    onResult,
                    true,
                    seasons,
                    "Seasons loaded."
                )

            } catch (e: Exception) {

                Log.e(TAG, "fetchSeriesSeasons failed", e)

                postResult(
                    onResult,
                    false,
                    emptyList(),
                    safeError(e)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // SERIES EPISODES
    // -------------------------------------------------------------------------

    fun fetchSeriesEpisodes(
        portalUrl: String,
        macAddress: String,
        seriesId: String,
        seasonId: String,
        onResult: (Boolean, List<SeriesEpisode>, String) -> Unit
    ) {
        runAsync {
            try {

                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isBlank() || mac.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        emptyList(),
                        "Portal or MAC is missing."
                    )
                    return@runAsync
                }

                if (seriesId.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        emptyList(),
                        "Series ID is missing."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val response = request(
                    session = session,
                    mac = mac,
                    params = mapOf(
                        "type" to "series",
                        "action" to "get_ordered_list",
                        "p" to "1",
                        "movie_id" to seriesId,
                        "season_id" to seasonId,
                        "JsHttpRequest" to "1-xml"
                    )
                )

                val episodes = parseEpisodes(response)

                postResult(
                    onResult,
                    true,
                    episodes,
                    "Episodes loaded."
                )

            } catch (e: Exception) {

                Log.e(TAG, "fetchSeriesEpisodes failed", e)

                postResult(
                    onResult,
                    false,
                    emptyList(),
                    safeError(e)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // PUBLIC CATEGORIES API
    //
    // Kept callback-based so existing Dashboard/UI code remains compatible.
    // -------------------------------------------------------------------------

    fun fetchCategories(
        portalUrl: String,
        macAddress: String,
        type: String,
        onResult: (Boolean, List<Pair<String, String>>, String) -> Unit
    ) {
        runAsync {
            try {

                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isBlank() || mac.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        emptyList(),
                        "Portal or MAC is missing."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val array = fetchCategoriesInternal(
                    session,
                    mac,
                    type
                )

                val result = array.mapNotNull { item ->

                    val id = firstString(
                        item,
                        "id"
                    )

                    val title = firstString(
                        item,
                        "title",
                        "name"
                    )

                    if (id.isBlank() && title.isBlank()) {
                        null
                    } else {
                        id to title
                    }
                }

                postResult(
                    onResult,
                    true,
                    result,
                    "Categories loaded."
                )

            } catch (e: Exception) {

                Log.e(TAG, "fetchCategories failed", e)

                postResult(
                    onResult,
                    false,
                    emptyList(),
                    safeError(e)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // INTERNAL CATEGORIES
    // -------------------------------------------------------------------------

    private fun fetchCategoriesInternal(
        session: StalkerSession,
        mac: String,
        type: String
    ): List<JsonObject> {

        val response = request(
            session = session,
            mac = mac,
            params = mapOf(
                "type" to type,
                "action" to "get_categories",
                "JsHttpRequest" to "1-xml"
            )
        )

        return extractArray(response, "js", "data")
    }

    // -------------------------------------------------------------------------
    // CREATE LINK
    // -------------------------------------------------------------------------

    fun createLink(
        portalUrl: String,
        macAddress: String,
        cmd: String,
        onResult: (Boolean, String, String) -> Unit
    ) {
        runAsync {

            try {

                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isBlank() || mac.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        "",
                        "Portal or MAC is missing."
                    )
                    return@runAsync
                }

                if (cmd.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        "",
                        "Stream command is missing."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(
                    portal,
                    mac
                )

                val cleanCmd = decodeCommand(cmd)

                val response = request(
                    session = session,
                    mac = mac,
                    params = mapOf(
                        "type" to "itv",
                        "action" to "create_link",
                        "cmd" to cleanCmd,
                        "JsHttpRequest" to "1-xml"
                    )
                )

                val streamUrl = extractStreamUrl(response)

                if (streamUrl.isBlank()) {

                    postResult(
                        onResult,
                        false,
                        "",
                        "Server did not return a stream URL."
                    )

                } else {

                    postResult(
                        onResult,
                        true,
                        streamUrl,
                        "Stream link created."
                    )
                }

            } catch (e: Exception) {

                Log.e(TAG, "createLink failed", e)

                postResult(
                    onResult,
                    false,
                    "",
                    safeError(e)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // PROFILE
    // -------------------------------------------------------------------------

    fun getProfile(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, Profile?, String) -> Unit
    ) {
        runAsync {

            try {

                val portal = normalizePortalUrl(portalUrl)
                val mac = normalizeMac(macAddress)

                if (portal.isBlank() || mac.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        null,
                        "Portal or MAC is missing."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(
                    portal,
                    mac
                )

                val response = request(
                    session = session,
                    mac = mac,
                    params = mapOf(
                        "type" to "stb",
                        "action" to "get_profile",
                        "JsHttpRequest" to "1-xml"
                    )
                )

                val obj = extractObject(
                    response,
                    "js",
                    "data"
                )

                if (obj == null) {

                    postResult(
                        onResult,
                        false,
                        null,
                        "Profile not found."
                    )

                    return@runAsync
                }

                val profile = Profile(
                    id = firstString(
                        obj,
                        "id",
                        "stb_id"
                    ),
                    mac = firstString(
                        obj,
                        "mac",
                        "mac_address"
                    ).ifBlank {
                        mac
                    },
                    username = firstString(
                        obj,
                        "username",
                        "login"
                    ),
                    status = firstString(
                        obj,
                        "status"
                    ),
                    expiryDate = firstString(
                        obj,
                        "expire_date",
                        "end_date",
                        "expiration"
                    ),
                    tariffPlan = firstString(
                        obj,
                        "tariff_plan",
                        "plan"
                    ),
                    accountBalance = firstString(
                        obj,
                        "account_balance",
                        "balance"
                    )
                )

                postResult(
                    onResult,
                    true,
                    profile,
                    "Profile loaded."
                )

            } catch (e: Exception) {

                Log.e(TAG, "getProfile failed", e)

                postResult(
                    onResult,
                    false,
                    null,
                    safeError(e)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // SESSION CREATION
    // -------------------------------------------------------------------------

    private fun getOrCreateSession(
        portal: String,
        mac: String
    ): StalkerSession {

        val oldSession = currentSession

        if (
            oldSession != null &&
            currentPortal == portal &&
            currentMac == mac
        ) {
            return oldSession
        }

        val loadUrl = discoverLoadUrl(
            portal,
            mac
        )

        val session = performHandshake(
            loadUrl,
            mac
        )

        currentPortal = portal
        currentMac = mac
        currentSession = session

        return session
    }

    // -------------------------------------------------------------------------
    // DISCOVER LOAD URL
    // -------------------------------------------------------------------------

    private fun discoverLoadUrl(
        portal: String,
        mac: String
    ): String {

        val normalized = normalizePortalUrl(
            portal
        )

        if (
            normalized.endsWith(
                LOAD_PATH,
                ignoreCase = true
            )
        ) {
            return normalized
        }

        return normalized + LOAD_PATH
    }

    // -------------------------------------------------------------------------
    // HANDSHAKE
    // -------------------------------------------------------------------------

    private fun performHandshake(
        loadUrl: String,
        mac: String
    ): StalkerSession {

        val random = UUID.randomUUID()
            .toString()
            .replace("-", "")

        val params = mapOf(
            "type" to "stb",
            "action" to "handshake",
            "token" to "",
            "prehash" to "",
            "JsHttpRequest" to "1-xml"
        )

        val response = requestRaw(
            url = loadUrl,
            mac = mac,
            token = "",
            params = params
        )

        val obj = extractObject(
            response,
            "js"
        )

        val token = if (obj != null) {
            firstString(
                obj,
                "token"
            )
        } else {
            ""
        }

        return StalkerSession(
            loadUrl = loadUrl,
            token = token,
            random = random
        )
    }

    // -------------------------------------------------------------------------
    // REQUEST
    // -------------------------------------------------------------------------

    private fun request(
        session: StalkerSession,
        mac: String,
        params: Map<String, String>
    ): JsonObject {

        val response = requestRaw(
            url = session.loadUrl,
            mac = mac,
            token = session.token,
            params = params
        )

        return parseJsonObject(
            response
        )
    }

    // -------------------------------------------------------------------------
    // RAW HTTP REQUEST
    // -------------------------------------------------------------------------

    private fun requestRaw(
        url: String,
        mac: String,
        token: String,
        params: Map<String, String>
    ): String {

        val query = StringBuilder()

        for ((key, value) in params) {

            if (query.isNotEmpty()) {
                query.append("&")
            }

            query.append(
                URLEncoder.encode(
                    key,
                    "UTF-8"
                )
            )

            query.append("=")

            query.append(
                URLEncoder.encode(
                    value,
                    "UTF-8"
                )
            )
        }

        val finalUrl = if (url.contains("?")) {
            "$url&$query"
        } else {
            "$url?$query"
        }

        Log.d(
            TAG,
            "Request: ${redactUrl(finalUrl)}"
        )

        val connection =
            openConnection(
                finalUrl,
                mac,
                token
            )

        try {

            val code =
                connection.responseCode

            val inputStream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            if (inputStream == null) {
                throw Exception(
                    "HTTP $code: empty response"
                )
            }

            val reader =
                BufferedReader(
                    InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8
                    )
                )

            val response =
                reader.use {
                    it.readText()
                }

            if (code !in 200..299) {
                throw Exception(
                    "HTTP $code"
                )
            }

            Log.d(
                TAG,
                "Response length=${response.length}"
            )

            return response

        } finally {

            connection.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // HTTP CONNECTION
    // -------------------------------------------------------------------------

    private fun openConnection(
        url: String,
        mac: String,
        token: String
    ): HttpURLConnection {

        val connection =
            URL(url).openConnection()
                    as HttpURLConnection

        connection.requestMethod = "GET"

        connection.connectTimeout =
            CONNECT_TIMEOUT

        connection.readTimeout =
            READ_TIMEOUT

        connection.useCaches = false

        connection.instanceFollowRedirects = true

        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0"
        )

        connection.setRequestProperty(
            "Accept",
            "*/*"
        )

        connection.setRequestProperty(
            "Connection",
            "Keep-Alive"
        )

        connection.setRequestProperty(
            "X-User-Agent",
            "Model: MAG250; Link: WiFi"
        )

        connection.setRequestProperty(
            "Cookie",
            "mac=$mac"
        )

        if (token.isNotBlank()) {

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )
        }

        return connection
    }

    // -------------------------------------------------------------------------
    // JSON PARSER
    //
    // IMPORTANT:
    // We intentionally use:
    // JsonParser().parse(...)
    //
    // instead of:
    // JsonParser.parseString(...)
    //
    // because the project's Gson version does not expose parseString().
    // -------------------------------------------------------------------------

    private fun parseJson(
        raw: String
    ): JsonElement {

        val cleaned =
            raw.trim()
                .removePrefix("\uFEFF")

        if (cleaned.isBlank()) {
            throw Exception(
                "Empty server response."
            )
        }

        return try {

            JsonParser()
                .parse(cleaned)

        } catch (e: Exception) {

            throw Exception(
                "Invalid JSON response: ${e.message}"
            )
        }
    }

    private fun parseJsonObject(
        raw: String
    ): JsonObject {

        val element =
            parseJson(raw)

        if (!element.isJsonObject) {

            throw Exception(
                "Server response is not a JSON object."
            )
        }

        return element.asJsonObject
    }

    // -------------------------------------------------------------------------
    // JSON HELPERS
    // -------------------------------------------------------------------------

    private fun extractArray(
        obj: JsonObject,
        vararg keys: String
    ): List<JsonObject> {

        for (key in keys) {

            if (!obj.has(key)) {
                continue
            }

            val element =
                obj.get(key)

            if (element == null ||
                element.isJsonNull
            ) {
                continue
            }

            if (element.isJsonArray) {

                return element.asJsonArray
                    .mapNotNull {
                        if (it.isJsonObject) {
                            it.asJsonObject
                        } else {
                            null
                        }
                    }
            }

            if (element.isJsonObject) {

                val nested =
                    element.asJsonObject

                val nestedArray =
                    findFirstArray(
                        nested,
                        "data",
                        "js",
                        "items",
                        "results"
                    )

                if (nestedArray != null) {

                    return nestedArray
                        .mapNotNull {
                            if (it.isJsonObject) {
                                it.asJsonObject
                            } else {
                                null
                            }
                        }
                }
            }
        }

        return emptyList()
    }

    private fun findFirstArray(
        obj: JsonObject,
        vararg keys: String
    ): JsonArray? {

        for (key in keys) {

            val element =
                obj.get(key)
                    ?: continue

            if (
                !element.isJsonNull &&
                element.isJsonArray
            ) {
                return element.asJsonArray
            }
        }

        return null
    }

    private fun extractObject(
        obj: JsonObject,
        vararg keys: String
    ): JsonObject? {

        for (key in keys) {

            val element =
                obj.get(key)
                    ?: continue

            if (
                !element.isJsonNull &&
                element.isJsonObject
            ) {
                return element.asJsonObject
            }
        }

        return null
    }

    private fun jsonString(
        obj: JsonObject,
        key: String
    ): String {

        return if (
            obj.has(key) &&
            !obj.get(key).isJsonNull
        ) {
            try {
                obj.get(key).asString
            } catch (
                _: Exception
            ) {
                ""
            }
        } else {
            ""
        }
    }

    private fun firstString(
        obj: JsonObject,
        vararg keys: String
    ): String {

        for (key in keys) {

            val value =
                jsonString(
                    obj,
                    key
                )

            if (value.isNotBlank()) {
                return value
            }
        }

        return ""
    }

    // -------------------------------------------------------------------------
    // STREAM URL
    // -------------------------------------------------------------------------

    private fun extractStreamUrl(
        response: JsonObject
    ): String {

        val directKeys =
            arrayOf(
                "cmd",
                "url",
                "link",
                "stream_url",
                "play_url"
            )

        for (key in directKeys) {

            val value =
                jsonString(
                    response,
                    key
                )

            if (
                value.startsWith(
                    "http://",
                    true
                ) ||
                value.startsWith(
                    "https://",
                    true
                )
            ) {
                return value
            }
        }

        val js =
            extractObject(
                response,
                "js"
            )

        if (js != null) {

            for (key in directKeys) {

                val value =
                    jsonString(
                        js,
                        key
                    )

                if (
                    value.startsWith(
                        "http://",
                        true
                    ) ||
                    value.startsWith(
                        "https://",
                        true
                    )
                ) {
                    return value
                }
            }
        }

        val data =
            extractObject(
                response,
                "data"
            )

        if (data != null) {

            for (key in directKeys) {

                val value =
                    jsonString(
                        data,
                        key
                    )

                if (
                    value.startsWith(
                        "http://",
                        true
                    ) ||
                    value.startsWith(
                        "https://",
                        true
                    )
                ) {
                    return value
                }
            }
        }

        return ""
    }

    // -------------------------------------------------------------------------
    // SERIES PARSING
    // -------------------------------------------------------------------------

    private fun parseSeasons(
        response: JsonObject
    ): List<SeriesSeason> {

        val items =
            extractArray(
                response,
                "js",
                "data"
            )

        return items.mapIndexedNotNull {
                index,
                item ->

            val id =
                firstString(
                    item,
                    "id",
                    "season_id"
                )

            val name =
                firstString(
                    item,
                    "name",
                    "title"
                )

            if (
                id.isBlank() &&
                name.isBlank()
            ) {
                null
            } else {

                val number =
                    firstString(
                        item,
                        "season",
                        "season_number",
                        "number"
                    ).toIntOrNull()
                        ?: (index + 1)

                SeriesSeason(
                    id = id,
                    name =
                        name.ifBlank {
                            "Season $number"
                        },
                    seasonNumber = number
                )
            }
        }
    }

    private fun parseEpisodes(
        response: JsonObject
    ): List<SeriesEpisode> {

        val items =
            extractArray(
                response,
                "js",
                "data"
            )

        return items.mapIndexedNotNull {
                index,
                item ->

            val id =
                firstString(
                    item,
                    "id",
                    "episode_id"
                )

            val name =
                firstString(
                    item,
                    "name",
                    "title"
                )

            if (
                id.isBlank() &&
                name.isBlank()
            ) {
                null
            } else {

                val episodeNumber =
                    firstString(
                        item,
                        "episode",
                        "episode_number",
                        "number"
                    ).toIntOrNull()
                        ?: (index + 1)

                val seasonNumber =
                    firstString(
                        item,
                        "season",
                        "season_number"
                    ).toIntOrNull()
                        ?: 0

                SeriesEpisode(
                    id = id,
                    name =
                        name.ifBlank {
                            "Episode $episodeNumber"
                        },
                    episodeNumber =
                        episodeNumber,
                    seasonNumber =
                        seasonNumber,
                    cmd =
                        firstString(
                            item,
                            "cmd",
                            "command"
                        ),
                    logo =
                        firstString(
                            item,
                            "logo",
                            "icon"
                        ),
                    description =
                        firstString(
                            item,
                            "description",
                            "descr",
                            "plot"
                        )
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // PAGINATION
    // -------------------------------------------------------------------------

    private fun hasMorePages(
        response: JsonObject,
        itemCount: Int
    ): Boolean {

        if (itemCount <= 0) {
            return false
        }

        if (itemCount < DEFAULT_PAGE_SIZE) {
            return false
        }

        val js =
            extractObject(
                response,
                "js"
            )

        if (js != null) {

            val total =
                firstString(
                    js,
                    "total_items",
                    "total",
                    "count"
                ).toIntOrNull()

            if (total != null &&
                total <= itemCount
            ) {
                return false
            }
        }

        return true
    }

    // -------------------------------------------------------------------------
    // COMMAND
    // -------------------------------------------------------------------------

    private fun decodeCommand(
        command: String
    ): String {

        var result =
            command.trim()

        repeat(2) {

            val decoded =
                try {
                    java.net.URLDecoder.decode(
                        result,
                        "UTF-8"
                    )
                } catch (
                    _: Exception
                ) {
                    result
                }

            if (decoded == result) {
                return@repeat
            }

            result = decoded
        }

        return result
    }

    // -------------------------------------------------------------------------
    // NORMALIZATION
    // -------------------------------------------------------------------------

    private fun normalizePortalUrl(
        value: String
    ): String {

        var portal =
            value.trim()

        if (portal.isBlank()) {
            return ""
        }

        if (
            !portal.startsWith(
                "http://",
                true
            ) &&
            !portal.startsWith(
                "https://",
                true
            )
        ) {
            portal =
                "http://$portal"
        }

        while (
            portal.endsWith("/")
        ) {
            portal =
                portal.dropLast(1)
        }

        if (
            portal.endsWith(
                LOAD_PATH,
                true
            )
        ) {
            return portal
        }

        return portal
    }

    private fun normalizeMac(
        value: String
    ): String {

        return value
            .trim()
            .uppercase()
            .replace(
                " ",
                ""
            )
    }

    // -------------------------------------------------------------------------
    // ASYNC
    // -------------------------------------------------------------------------

    private fun runAsync(
        block: () -> Unit
    ) {

        thread(
            name = "StalkerClient"
        ) {
            block()
        }
    }

    private fun <T> postResult(
        callback: (Boolean, T, String) -> Unit,
        success: Boolean,
        value: T,
        message: String
    ) {

        mainHandler.post {

            callback(
                success,
                value,
                message
            )
        }
    }

    // -------------------------------------------------------------------------
    // ERRORS
    // -------------------------------------------------------------------------

    private fun safeError(
        throwable: Throwable
    ): String {

        val message =
            throwable.message
                ?.trim()
                .orEmpty()

        return if (
            message.isNotBlank()
        ) {
            message
        } else {
            throwable.javaClass.simpleName
        }
    }

    // -------------------------------------------------------------------------
    // LOG REDACTION
    // -------------------------------------------------------------------------

    private fun redactUrl(
        url: String
    ): String {

        return url
            .replace(
                Regex(
                    "(mac=)[^&]+",
                    RegexOption.IGNORE_CASE
                ),
                "$1***"
            )
            .replace(
                Regex(
                    "(token=)[^&]+",
                    RegexOption.IGNORE_CASE
                ),
                "$1***"
            )
    }
}

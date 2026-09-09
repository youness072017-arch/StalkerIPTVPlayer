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

class StalkerClient {

    companion object {
        private const val TAG = "StalkerClient"

        private const val LOAD_PATH = "/stalker_portal/server/load.php"

        private const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGES = 50

        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 30_000
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------
    // Models
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
    // Session
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
    // Public: Live TV
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
                    postResult(onResult, false, emptyList(), "Portal or MAC is missing.")
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val categories = fetchCategories(
                    session,
                    mac,
                    "itv"
                )

                val categoryMap = categories.associateBy(
                    keySelector = { jsonString(it, "id") },
                    valueTransform = { jsonString(it, "title") }
                )

                val allChannels = mutableListOf<Channel>()

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

                    val items = extractArray(response, "data")

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

                        allChannels.add(
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
                    allChannels.distinctBy { it.id.ifBlank { it.name } },
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
    // Public: Movies / VOD
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
                    postResult(onResult, false, emptyList(), "Portal or MAC is missing.")
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val categories = fetchCategories(
                    session,
                    mac,
                    "vod"
                )

                val categoryMap = categories.associateBy(
                    keySelector = { jsonString(it, "id") },
                    valueTransform = { jsonString(it, "title") }
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

                    val items = extractArray(response, "data")

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
                    movies.distinctBy { it.id.ifBlank { it.name } },
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
    // Public: Series
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
                    postResult(onResult, false, emptyList(), "Portal or MAC is missing.")
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                val categories = fetchCategories(
                    session,
                    mac,
                    "series"
                )

                val categoryMap = categories.associateBy(
                    keySelector = { jsonString(it, "id") },
                    valueTransform = { jsonString(it, "title") }
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

                    val items = extractArray(response, "data")

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
                    seriesList.distinctBy { it.id.ifBlank { it.name } },
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
    // Public: Series seasons
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
    // Public: Series episodes
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
    // Public: Categories
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

                val session = getOrCreateSession(portal, mac)

                val json = request(
                    session = session,
                    mac = mac,
                    params = mapOf(
                        "type" to type,
                        "action" to "get_categories",
                        "JsHttpRequest" to "1-xml"
                    )
                )

                val array = extractArray(json, "js")

                val result = array.mapNotNull { item ->
                    val id = firstString(item, "id")
                    val title = firstString(item, "title", "name")

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
    // Public: Create stream link
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
                        "Stream command is empty."
                    )
                    return@runAsync
                }

                val session = getOrCreateSession(portal, mac)

                /*
                 * Important:
                 * Do NOT pre-URL-encode cmd here.
                 * request() performs the encoding once.
                 */
                val response = request(
                    session = session,
                    mac = mac,
                    params = mapOf(
                        "type" to "itv",
                        "action" to "create_link",
                        "cmd" to cmd,
                        "JsHttpRequest" to "1-xml"
                    )
                )

                val link = extractLink(response)

                if (link.isBlank()) {
                    postResult(
                        onResult,
                        false,
                        "",
                        "The server did not return a playable link."
                    )
                    return@runAsync
                }

                postResult(
                    onResult,
                    true,
                    link,
                    "Stream link created."
                )

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
    // Public: Profile
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

                val session = getOrCreateSession(portal, mac)

                val response = request(
                    session = session,
                    mac = mac,
                    params = mapOf(
                        "type" to "stb",
                        "action" to "get_profile",
                        "JsHttpRequest" to "1-xml"
                    )
                )

                val profileObject = extractObject(response, "js")

                val profile = Profile(
                    id = firstString(profileObject, "id"),
                    mac = firstString(
                        profileObject,
                        "mac",
                        "stb_mac"
                    ).ifBlank { mac },
                    username = firstString(
                        profileObject,
                        "login",
                        "username"
                    ),
                    status = firstString(
                        profileObject,
                        "status"
                    ),
                    expiryDate = firstString(
                        profileObject,
                        "expire_billing_date",
                        "end_date",
                        "expiry_date"
                    ),
                    tariffPlan = firstString(
                        profileObject,
                        "tariff_plan",
                        "plan"
                    ),
                    accountBalance = firstString(
                        profileObject,
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
    // Session / Handshake
    // -------------------------------------------------------------------------

    private fun getOrCreateSession(
        portal: String,
        mac: String
    ): StalkerSession {

        val existing = currentSession

        if (
            existing != null &&
            currentPortal == portal &&
            currentMac == mac &&
            existing.loadUrl.isNotBlank()
        ) {
            return existing
        }

        val loadUrl = discoverLoadUrl(portal)

        val session = performHandshake(
            loadUrl = loadUrl,
            mac = mac
        )

        currentPortal = portal
        currentMac = mac
        currentSession = session

        return session
    }

    private fun discoverLoadUrl(portal: String): String {
        val candidates = listOf(
            portal.trimEnd('/') + LOAD_PATH,
            portal.trimEnd('/') + "/server/load.php",
            portal.trimEnd('/') + "/stalker_portal/server/load.php"
        ).distinct()

        for (candidate in candidates) {
            try {
                val connection = openConnection(
                    candidate,
                    mac = "",
                    token = ""
                )

                connection.requestMethod = "GET"
                connection.connect()

                val code = connection.responseCode
                connection.disconnect()

                if (code in 200..499) {
                    return candidate
                }
            } catch (_: Exception) {
                // Try next candidate.
            }
        }

        return candidates.first()
    }

    private fun performHandshake(
        loadUrl: String,
        mac: String
    ): StalkerSession {

        val response = requestRaw(
            url = loadUrl,
            mac = mac,
            token = "",
            params = mapOf(
                "type" to "stb",
                "action" to "handshake",
                "token" to "",
                "JsHttpRequest" to "1-xml"
            )
        )

        val js = extractObject(response, "js")

        val token = firstString(
            js,
            "token"
        )

        val random = firstString(
            js,
            "random",
            "random_value"
        )

        return StalkerSession(
            loadUrl = loadUrl,
            token = token,
            random = random
        )
    }

    // -------------------------------------------------------------------------
    // Request
    // -------------------------------------------------------------------------

    private fun request(
        session: StalkerSession,
        mac: String,
        params: Map<String, String>
    ): JsonObject {
        return requestRaw(
            url = session.loadUrl,
            mac = mac,
            token = session.token,
            params = params
        )
    }

    private fun requestRaw(
        url: String,
        mac: String,
        token: String,
        params: Map<String, String>
    ): JsonObject {

        val query = params.entries.joinToString("&") { entry ->
            "${urlEncode(entry.key)}=${urlEncode(entry.value)}"
        }

        val separator = if (url.contains("?")) "&" else "?"

        val fullUrl = url + separator + query

        val connection = openConnection(
            fullUrl,
            mac = mac,
            token = token
        )

        return try {
            connection.requestMethod = "GET"
            connection.connect()

            val responseCode = connection.responseCode

            val stream =
                if (responseCode in 200..399) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body = if (stream != null) {
                BufferedReader(
                    InputStreamReader(
                        stream,
                        StandardCharsets.UTF_8
                    )
                ).use { reader ->
                    reader.readText()
                }
            } else {
                ""
            }

            if (responseCode !in 200..399) {
                throw RuntimeException(
                    "HTTP $responseCode"
                )
            }

            if (body.isBlank()) {
                throw RuntimeException(
                    "Empty server response."
                )
            }

            parseJsonObject(body)

        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        mac: String,
        token: String
    ): HttpURLConnection {

        val connection =
            URL(url).openConnection() as HttpURLConnection

        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.useCaches = false
        connection.instanceFollowRedirects = true

        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36"
        )

        connection.setRequestProperty(
            "Accept",
            "application/json, text/javascript, */*; q=0.01"
        )

        connection.setRequestProperty(
            "X-Requested-With",
            "XMLHttpRequest"
        )

        connection.setRequestProperty(
            "Referer",
            URL(url).protocol + "://" + URL(url).host + "/"
        )

        if (mac.isNotBlank()) {
            connection.setRequestProperty(
                "Cookie",
                "mac=$mac"
            )

            connection.setRequestProperty(
                "X-MAC",
                mac
            )
        }

        if (token.isNotBlank()) {
            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )
        }

        return connection
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private fun parseJsonObject(
        text: String
    ): JsonObject {

        val cleaned = text.trim()

        return try {
            JsonParser
                .parseString(cleaned)
                .asJsonObject
        } catch (e: Exception) {
            /*
             * Some Stalker installations return JSON wrapped
             * inside an XML/JS response. Try to locate the JSON object.
             */
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')

            if (start >= 0 && end > start) {
                JsonParser
                    .parseString(
                        cleaned.substring(start, end + 1)
                    )
                    .asJsonObject
            } else {
                throw e
            }
        }
    }

    private fun extractArray(
        root: JsonObject,
        key: String
    ): List<JsonObject> {

        val element = root.get(key)

        if (element == null || element.isJsonNull) {
            return emptyList()
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
            val nested = element.asJsonObject

            val possibleKeys = listOf(
                "data",
                "items",
                "results",
                "js"
            )

            for (nestedKey in possibleKeys) {
                val nestedElement = nested.get(nestedKey)

                if (
                    nestedElement != null &&
                    nestedElement.isJsonArray
                ) {
                    return nestedElement.asJsonArray
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

    private fun extractObject(
        root: JsonObject,
        key: String
    ): JsonObject {

        val element = root.get(key)

        if (element != null && element.isJsonObject) {
            return element.asJsonObject
        }

        return JsonObject()
    }

    private fun parseSeasons(
        response: JsonObject
    ): List<SeriesSeason> {

        val arrays = listOf(
            extractArray(response, "js"),
            extractArray(response, "data")
        )

        for (array in arrays) {
            if (array.isNotEmpty()) {
                return array.mapIndexedNotNull { index, item ->

                    val id = firstString(
                        item,
                        "id",
                        "season_id"
                    )

                    val number = firstString(
                        item,
                        "season",
                        "season_number",
                        "number"
                    ).toIntOrNull()
                        ?: (index + 1)

                    val name = firstString(
                        item,
                        "name",
                        "title"
                    ).ifBlank {
                        "Season $number"
                    }

                    if (id.isBlank() && name.isBlank()) {
                        null
                    } else {
                        SeriesSeason(
                            id = id,
                            name = name,
                            seasonNumber = number
                        )
                    }
                }
            }
        }

        return emptyList()
    }

    private fun parseEpisodes(
        response: JsonObject
    ): List<SeriesEpisode> {

        val arrays = listOf(
            extractArray(response, "js"),
            extractArray(response, "data")
        )

        for (array in arrays) {
            if (array.isNotEmpty()) {
                return array.mapIndexedNotNull { index, item ->

                    val id = firstString(
                        item,
                        "id",
                        "episode_id"
                    )

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

                    val name = firstString(
                        item,
                        "name",
                        "title"
                    ).ifBlank {
                        "Episode $episodeNumber"
                    }

                    if (id.isBlank() && name.isBlank()) {
                        null
                    } else {
                        SeriesEpisode(
                            id = id,
                            name = name,
                            episodeNumber = episodeNumber,
                            seasonNumber = seasonNumber,
                            cmd = firstString(
                                item,
                                "cmd",
                                "command"
                            ),
                            logo = firstString(
                                item,
                                "logo",
                                "icon"
                            ),
                            description = firstString(
                                item,
                                "description",
                                "descr",
                                "plot"
                            )
                        )
                    }
                }
            }
        }

        return emptyList()
    }

    private fun extractLink(
        response: JsonObject
    ): String {

        val candidates = mutableListOf<String>()

        val rootJs = response.get("js")

        if (rootJs != null) {
            if (rootJs.isJsonObject) {
                val obj = rootJs.asJsonObject

                candidates += firstString(
                    obj,
                    "cmd",
                    "link",
                    "url",
                    "stream_url"
                )
            } else if (rootJs.isJsonPrimitive) {
                candidates += rootJs.asString
            }
        }

        candidates += firstString(
            response,
            "cmd",
            "link",
            "url"
        )

        return candidates
            .map { it.trim() }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?: ""
    }

    private fun firstString(
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
                return value.asString
            }
        }

        return ""
    }

    private fun jsonString(
        obj: JsonObject,
        key: String
    ): String {
        return firstString(obj, key)
    }

    // -------------------------------------------------------------------------
    // Pagination
    // -------------------------------------------------------------------------

    private fun hasMorePages(
        response: JsonObject,
        itemCount: Int
    ): Boolean {

        if (itemCount <= 0) {
            return false
        }

        val js = response.get("js")

        if (js != null && js.isJsonObject) {
            val obj = js.asJsonObject

            val total = firstString(
                obj,
                "total_items",
                "total",
                "count"
            ).toIntOrNull()

            val pageItems = firstString(
                obj,
                "items_per_page",
                "page_items",
                "limit"
            ).toIntOrNull()

            if (total != null && pageItems != null) {
                return itemCount >= pageItems
            }
        }

        val data = response.get("data")

        if (data != null && data.isJsonObject) {
            val obj = data.asJsonObject

            val total = firstString(
                obj,
                "total_items",
                "total",
                "count"
            ).toIntOrNull()

            if (total != null) {
                return itemCount < total
            }
        }

        /*
         * If the server gives no pagination metadata,
         * a full page usually means there may be another page.
         */
        return itemCount >= DEFAULT_PAGE_SIZE
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun normalizePortalUrl(
        value: String
    ): String {

        var portal = value.trim()

        if (portal.isBlank()) {
            return ""
        }

        if (!portal.startsWith("http://") &&
            !portal.startsWith("https://")
        ) {
            portal = "http://$portal"
        }

        return portal.trimEnd('/')
    }

    private fun normalizeMac(
        value: String
    ): String {
        return value
            .trim()
            .uppercase()
    }

    private fun urlEncode(
        value: String
    ): String {
        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        )
    }

    private fun safeError(
        exception: Exception
    ): String {

        val message = exception.message
            ?.trim()
            .orEmpty()

        return when {
            message.isBlank() ->
                "Connection error."

            message.length > 250 ->
                message.take(250)

            else ->
                message
        }
    }

    private fun runAsync(
        block: () -> Unit
    ) {
        Thread {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Background operation failed", e)
            }
        }.start()
    }

    private fun <T> postResult(
        callback: (Boolean, T, String) -> Unit,
        success: Boolean,
        data: T,
        message: String
    ) {
        mainHandler.post {
            callback(
                success,
                data,
                message
            )
        }
    }
}

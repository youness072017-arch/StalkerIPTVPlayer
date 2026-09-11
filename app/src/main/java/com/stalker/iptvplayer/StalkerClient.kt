package com.stalker.iptvplayer

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class StalkerClient {

    companion object {
        private const val TAG = "StalkerClient"

        private const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGES = 50

        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 30_000

        private const val MAG_USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) " +
                    "AppleWebKit/533.3 (KHTML, like Gecko) " +
                    "MAG250 stbapp ver: 2 rev: 250 Safari/533.3"

        private const val MAG_X_USER_AGENT =
            "Model: MAG250; Link: WiFi"
    }

    // ------------------------------------------------------------------------
    // MODELS
    // ------------------------------------------------------------------------

    data class Channel(
        val id: String,
        val name: String,
        val logo: String,
        val cmd: String,
        val categoryId: String,
        val categoryName: String
    )

    data class VodItem(
        val id: String,
        val name: String,
        val logo: String,
        val cmd: String,
        val categoryId: String,
        val categoryName: String,
        val year: String,
        val description: String
    )

    data class SeriesItem(
        val id: String,
        val name: String,
        val logo: String,
        val categoryId: String,
        val categoryName: String,
        val year: String,
        val description: String
    )

    data class SeriesSeason(
        val id: String,
        val name: String,
        val seasonNumber: String
    )

    data class SeriesEpisode(
        val id: String,
        val name: String,
        val episodeNumber: String,
        val seasonNumber: String,
        val cmd: String,
        val logo: String,
        val description: String
    )

    data class Profile(
        val id: String,
        val mac: String,
        val username: String,
        val status: String,
        val expiryDate: String,
        val tariffPlan: String,
        val accountBalance: String
    )

    data class StalkerSession(
        val loadUrl: String,
        val token: String,
        val random: String
    )

    private data class HttpResult(
        val code: Int,
        val body: String,
        val finalUrl: String
    )

    private var currentSession: StalkerSession? = null
    private var currentPortal: String = ""
    private var currentMac: String = ""

    // ------------------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------------------

    fun fetchChannels(
        portal: String,
        mac: String,
        callback: (List<Channel>) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        executeAsync(callback, errorCallback) {

            val normalizedPortal = normalizePortalUrl(portal)
            val normalizedMac = normalizeMac(mac)

            val session = getOrCreateSession(
                normalizedPortal,
                normalizedMac
            )

            val categories = fetchCategoriesInternal(
                session,
                normalizedMac,
                "itv"
            )

            val result = mutableListOf<Channel>()

            var page = 1

            while (page <= MAX_PAGES) {

                val params = linkedMapOf(
                    "type" to "itv",
                    "action" to "get_ordered_list",
                    "p" to page.toString(),
                    "JsHttpRequest" to "1-xml"
                )

                val json = requestJson(
                    session,
                    normalizedMac,
                    params
                )

                val array = extractArray(json)

                if (array.isEmpty()) {
                    break
                }

                for (item in array) {
                    if (!item.isJsonObject) continue

                    val obj = item.asJsonObject

                    val id = firstString(
                        obj,
                        "id",
                        "ch_id",
                        "channel_id"
                    )

                    val name = firstString(
                        obj,
                        "name",
                        "title"
                    )

                    if (id.isBlank() || name.isBlank()) {
                        continue
                    }

                    val categoryId = firstString(
                        obj,
                        "tv_genre_id",
                        "category_id",
                        "genre_id"
                    )

                    val categoryName =
                        categories[categoryId].orEmpty()

                    val logo = firstString(
                        obj,
                        "logo",
                        "cmd_logo",
                        "icon"
                    )

                    val cmd = firstString(
                        obj,
                        "cmd",
                        "url",
                        "stream_url"
                    )

                    result.add(
                        Channel(
                            id = id,
                            name = name,
                            logo = normalizeHttpUrl(
                                logo,
                                normalizedPortal
                            ),
                            cmd = cmd,
                            categoryId = categoryId,
                            categoryName = categoryName
                        )
                    )
                }

                if (!hasMorePages(json, array.size)) {
                    break
                }

                page++
            }

            result.distinctBy {
                it.id.ifBlank { it.name }
            }
        }
    }

    fun fetchMovies(
        portal: String,
        mac: String,
        callback: (List<VodItem>) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        executeAsync(callback, errorCallback) {

            val normalizedPortal = normalizePortalUrl(portal)
            val normalizedMac = normalizeMac(mac)

            val session = getOrCreateSession(
                normalizedPortal,
                normalizedMac
            )

            val categories = fetchCategoriesInternal(
                session,
                normalizedMac,
                "vod"
            )

            val result = mutableListOf<VodItem>()

            var page = 1

            while (page <= MAX_PAGES) {

                val params = linkedMapOf(
                    "type" to "vod",
                    "action" to "get_ordered_list",
                    "p" to page.toString(),
                    "JsHttpRequest" to "1-xml"
                )

                val json = requestJson(
                    session,
                    normalizedMac,
                    params
                )

                val array = extractArray(json)

                if (array.isEmpty()) {
                    break
                }

                for (item in array) {

                    if (!item.isJsonObject) continue

                    val obj = item.asJsonObject

                    val id = firstString(
                        obj,
                        "id",
                        "movie_id",
                        "vod_id"
                    )

                    val name = firstString(
                        obj,
                        "name",
                        "title"
                    )

                    if (id.isBlank() || name.isBlank()) {
                        continue
                    }

                    val categoryId = firstString(
                        obj,
                        "category_id",
                        "category",
                        "genre_id"
                    )

                    val logo = firstString(
                        obj,
                        "logo",
                        "screenshot_uri",
                        "cover"
                    )

                    val cmd = firstString(
                        obj,
                        "cmd",
                        "url",
                        "stream_url"
                    )

                    val year = firstString(
                        obj,
                        "year",
                        "release_year"
                    )

                    val description = firstString(
                        obj,
                        "description",
                        "plot",
                        "desc"
                    )

                    result.add(
                        VodItem(
                            id = id,
                            name = name,
                            logo = normalizeHttpUrl(
                                logo,
                                normalizedPortal
                            ),
                            cmd = cmd,
                            categoryId = categoryId,
                            categoryName = categories[categoryId].orEmpty(),
                            year = year,
                            description = description
                        )
                    )
                }

                if (!hasMorePages(json, array.size)) {
                    break
                }

                page++
            }

            result.distinctBy {
                it.id.ifBlank { it.name }
            }
        }
    }

    fun fetchSeries(
        portal: String,
        mac: String,
        callback: (List<SeriesItem>) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        executeAsync(callback, errorCallback) {

            val normalizedPortal = normalizePortalUrl(portal)
            val normalizedMac = normalizeMac(mac)

            val session = getOrCreateSession(
                normalizedPortal,
                normalizedMac
            )

            val categories = fetchCategoriesInternal(
                session,
                normalizedMac,
                "series"
            )

            val result = mutableListOf<SeriesItem>()

            var page = 1

            while (page <= MAX_PAGES) {

                /*
                 * بعض Stalker versions:
                 * type=series
                 *
                 * وبعضها:
                 * type=vod + movie_type=series
                 *
                 * نجرب الصيغة الأساسية أولا.
                 */

                val params = linkedMapOf(
                    "type" to "series",
                    "action" to "get_ordered_list",
                    "p" to page.toString(),
                    "JsHttpRequest" to "1-xml"
                )

                var json = requestJson(
                    session,
                    normalizedMac,
                    params
                )

                var array = extractArray(json)

                /*
                 * Fallback للبوابات اللي كتخزن Series داخل VOD.
                 */
                if (array.isEmpty() && page == 1) {

                    val fallbackParams = linkedMapOf(
                        "type" to "vod",
                        "action" to "get_ordered_list",
                        "p" to "1",
                        "movie_type" to "series",
                        "JsHttpRequest" to "1-xml"
                    )

                    json = requestJson(
                        session,
                        normalizedMac,
                        fallbackParams
                    )

                    array = extractArray(json)
                }

                if (array.isEmpty()) {
                    break
                }

                for (item in array) {

                    if (!item.isJsonObject) continue

                    val obj = item.asJsonObject

                    val id = firstString(
                        obj,
                        "id",
                        "series_id",
                        "movie_id"
                    )

                    val name = firstString(
                        obj,
                        "name",
                        "title"
                    )

                    if (id.isBlank() || name.isBlank()) {
                        continue
                    }

                    val categoryId = firstString(
                        obj,
                        "category_id",
                        "category",
                        "genre_id"
                    )

                    val logo = firstString(
                        obj,
                        "logo",
                        "screenshot_uri",
                        "cover"
                    )

                    val year = firstString(
                        obj,
                        "year",
                        "release_year"
                    )

                    val description = firstString(
                        obj,
                        "description",
                        "plot",
                        "desc"
                    )

                    result.add(
                        SeriesItem(
                            id = id,
                            name = name,
                            logo = normalizeHttpUrl(
                                logo,
                                normalizedPortal
                            ),
                            categoryId = categoryId,
                            categoryName = categories[categoryId].orEmpty(),
                            year = year,
                            description = description
                        )
                    )
                }

                if (!hasMorePages(json, array.size)) {
                    break
                }

                page++
            }

            result.distinctBy {
                it.id.ifBlank { it.name }
            }
        }
    }

    fun fetchSeriesSeasons(
        portal: String,
        mac: String,
        seriesId: String,
        callback: (List<SeriesSeason>) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        executeAsync(callback, errorCallback) {

            val normalizedPortal = normalizePortalUrl(portal)
            val normalizedMac = normalizeMac(mac)

            val session = getOrCreateSession(
                normalizedPortal,
                normalizedMac
            )

            val params = linkedMapOf(
                "type" to "series",
                "action" to "get_ordered_list",
                "movie_id" to seriesId,
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            )

            val json = requestJson(
                session,
                normalizedMac,
                params
            )

            parseSeasons(json)
        }
    }

    fun fetchSeriesEpisodes(
        portal: String,
        mac: String,
        seriesId: String,
        seasonId: String,
        callback: (List<SeriesEpisode>) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        executeAsync(callback, errorCallback) {

            val normalizedPortal = normalizePortalUrl(portal)
            val normalizedMac = normalizeMac(mac)

            val session = getOrCreateSession(
                normalizedPortal,
                normalizedMac
            )

            val params = linkedMapOf(
                "type" to "series",
                "action" to "get_ordered_list",
                "movie_id" to seriesId,
                "season_id" to seasonId,
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            )

            val json = requestJson(
                session,
                normalizedMac,
                params
            )

            parseEpisodes(json)
        }
    }

    fun fetchCategories(
        portal: String,
        mac: String,
        type: String,
        callback: (Map<String, String>) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        executeAsync(callback, errorCallback) {

            val normalizedPortal = normalizePortalUrl(portal)
            val normalizedMac = normalizeMac(mac)

            val session = getOrCreateSession(
                normalizedPortal,
                normalizedMac
            )

            fetchCategoriesInternal(
                session,
                normalizedMac,
                type
            )
        }
    }

    fun createLink(
        portal: String,
        mac: String,
        cmd: String,
        type: String = "itv",
        callback: (String) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        executeAsync(callback, errorCallback) {

            val normalizedPortal = normalizePortalUrl(portal)
            val normalizedMac = normalizeMac(mac)

            val session = getOrCreateSession(
                normalizedPortal,
                normalizedMac
            )

            val safeType =
                when (type.lowercase(Locale.US)) {
                    "vod",
                    "movie",
                    "movies" -> "vod"

                    "series",
                    "episode",
                    "episodes" -> "vod"

                    else -> "itv"
                }

            val params = linkedMapOf(
                "type" to safeType,
                "action" to "create_link",
                "cmd" to cmd,
                "JsHttpRequest" to "1-xml"
            )

            val json = requestJson(
                session,
                normalizedMac,
                params
            )

            val link = extractLink(json)

            if (link.isBlank()) {
                throw RuntimeException(
                    "Playback link not found"
                )
            }

            link
        }
    }

    fun getProfile(
        portal: String,
        mac: String,
        callback: (Profile) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        executeAsync(callback, errorCallback) {

            val normalizedPortal = normalizePortalUrl(portal)
            val normalizedMac = normalizeMac(mac)

            val session = getOrCreateSession(
                normalizedPortal,
                normalizedMac
            )

            val params = linkedMapOf(
                "type" to "stb",
                "action" to "get_profile",
                "hd" to "1",
                "JsHttpRequest" to "1-xml"
            )

            val json = requestJson(
                session,
                normalizedMac,
                params
            )

            val obj = extractObject(
                json,
                "js"
            ) ?: json

            Profile(
                id = firstString(
                    obj,
                    "id",
                    "stb_id"
                ),
                mac = firstString(
                    obj,
                    "mac"
                ).ifBlank {
                    normalizedMac
                },
                username = firstString(
                    obj,
                    "login",
                    "username"
                ),
                status = firstString(
                    obj,
                    "status",
                    "status_text"
                ),
                expiryDate = firstString(
                    obj,
                    "end_date",
                    "expire_date",
                    "expiry_date",
                    "expire"
                ),
                tariffPlan = firstString(
                    obj,
                    "tariff_plan",
                    "tariff",
                    "plan"
                ),
                accountBalance = firstString(
                    obj,
                    "account_balance",
                    "balance"
                )
            )
        }
    }

    // ------------------------------------------------------------------------
    // SESSION
    // ------------------------------------------------------------------------

    private fun getOrCreateSession(
        portal: String,
        mac: String
    ): StalkerSession {

        val existing = currentSession

        if (
            existing != null &&
            currentPortal.equals(
                portal,
                ignoreCase = true
            ) &&
            currentMac.equals(
                mac,
                ignoreCase = true
            )
        ) {
            return existing
        }

        val loadUrl = discoverLoadUrl(
            portal,
            mac
        )

        val session = performHandshake(
            loadUrl,
            portal,
            mac
        )

        currentPortal = portal
        currentMac = mac
        currentSession = session

        return session
    }

    /**
     * أهم إصلاح هنا:
     *
     * الكود القديم كان يعتبر 404 endpoint صالح.
     *
     * دابا:
     * - 404 = نجرب endpoint آخر
     * - 500 = نجرب endpoint آخر
     * - HTML = ماشي API
     * - JSON فيه token = endpoint صالح
     *
     * ونجرب عدة صيغ منتشرة.
     */
    private fun discoverLoadUrl(
        portal: String,
        mac: String
    ): String {

        val candidates =
            buildEndpointCandidates(portal)

        var lastError = ""

        for (candidate in candidates) {

            try {

                Log.d(
                    TAG,
                    "Testing Stalker endpoint: $candidate"
                )

                val result = requestRaw(
                    loadUrl = candidate,
                    mac = mac,
                    token = "",
                    params = linkedMapOf(
                        "type" to "stb",
                        "action" to "handshake",
                        "token" to "",
                        "JsHttpRequest" to "1-xml"
                    )
                )

                Log.d(
                    TAG,
                    "Endpoint response ${result.code}: $candidate"
                )

                if (result.code !in 200..399) {
                    lastError =
                        "HTTP ${result.code}"
                    continue
                }

                if (looksLikeHtml(result.body)) {
                    lastError =
                        "HTML response"
                    continue
                }

                val json =
                    parseJsonSafe(result.body)

                if (json != null) {

                    val token =
                        extractString(
                            json,
                            "js",
                            "token"
                        )

                    if (token.isNotBlank()) {
                        return candidate
                    }

                    /*
                     * بعض السيرفرات يمكن ترجع JSON مختلف
                     * ولكن endpoint نفسه صحيح.
                     */
                    if (
                        json.isJsonObject &&
                        json.asJsonObject.has("js")
                    ) {
                        return candidate
                    }
                }

            } catch (e: Exception) {

                lastError =
                    e.message ?: "Unknown error"

                Log.w(
                    TAG,
                    "Endpoint failed: $candidate",
                    e
                )
            }
        }

        throw RuntimeException(
            "No working Stalker endpoint found. " +
                    "Last error: $lastError"
        )
    }

    private fun performHandshake(
        loadUrl: String,
        portal: String,
        mac: String
    ): StalkerSession {

        val json = requestJson(
            StalkerSession(
                loadUrl = loadUrl,
                token = "",
                random = ""
            ),
            mac,
            linkedMapOf(
                "type" to "stb",
                "action" to "handshake",
                "token" to "",
                "JsHttpRequest" to "1-xml"
            )
        )

        val js =
            extractObject(json, "js")
                ?: throw RuntimeException(
                    "Invalid handshake response"
                )

        val token =
            firstString(
                js,
                "token"
            )

        val random =
            firstString(
                js,
                "random"
            )

        if (token.isBlank()) {
            throw RuntimeException(
                "Handshake succeeded but token is empty"
            )
        }

        Log.d(
            TAG,
            "Handshake successful"
        )

        return StalkerSession(
            loadUrl = loadUrl,
            token = token,
            random = random
        )
    }

    // ------------------------------------------------------------------------
    // CATEGORIES
    // ------------------------------------------------------------------------

    private fun fetchCategoriesInternal(
        session: StalkerSession,
        mac: String,
        type: String
    ): Map<String, String> {

        return try {

            val json = requestJson(
                session,
                mac,
                linkedMapOf(
                    "type" to type,
                    "action" to "get_categories",
                    "JsHttpRequest" to "1-xml"
                )
            )

            val array = extractArray(json)

            val result =
                linkedMapOf<String, String>()

            for (item in array) {

                if (!item.isJsonObject) continue

                val obj =
                    item.asJsonObject

                val id =
                    firstString(
                        obj,
                        "id",
                        "category_id"
                    )

                val title =
                    firstString(
                        obj,
                        "title",
                        "name"
                    )

                if (id.isNotBlank()) {
                    result[id] = title
                }
            }

            result

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Category loading failed for $type",
                e
            )

            emptyMap()
        }
    }

    // ------------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------------

    private fun requestJson(
        session: StalkerSession,
        mac: String,
        params: Map<String, String>
    ): JsonObject {

        val result =
            requestRaw(
                loadUrl = session.loadUrl,
                mac = mac,
                token = session.token,
                params = params
            )

        if (result.code !in 200..399) {

            throw RuntimeException(
                "HTTP ${result.code}"
            )
        }

        if (looksLikeHtml(result.body)) {

            throw RuntimeException(
                "Portal returned HTML instead of Stalker JSON"
            )
        }

        val json =
            parseJsonSafe(result.body)
                ?: throw RuntimeException(
                    "Invalid JSON response"
                )

        if (json.isJsonObject) {
            return json.asJsonObject
        }

        throw RuntimeException(
            "Unexpected JSON response"
        )
    }

    private fun requestRaw(
        loadUrl: String,
        mac: String,
        token: String,
        params: Map<String, String>
    ): HttpResult {

        val query =
            params.entries.joinToString("&") { entry ->

                "${encode(entry.key)}=${encode(entry.value)}"
            }

        val finalUrl =
            if (loadUrl.contains("?")) {
                "$loadUrl&$query"
            } else {
                "$loadUrl?$query"
            }

        Log.d(
            TAG,
            "HTTP GET: $finalUrl"
        )

        val connection =
            URL(finalUrl).openConnection()
                    as HttpURLConnection

        try {

            connection.requestMethod = "GET"

            connection.connectTimeout =
                CONNECT_TIMEOUT

            connection.readTimeout =
                READ_TIMEOUT

            connection.instanceFollowRedirects =
                true

            connection.useCaches = false

            connection.setRequestProperty(
                "User-Agent",
                MAG_USER_AGENT
            )

            connection.setRequestProperty(
                "X-User-Agent",
                MAG_X_USER_AGENT
            )

            connection.setRequestProperty(
                "Accept",
                "application/json, text/plain, */*"
            )

            connection.setRequestProperty(
                "Accept-Encoding",
                "identity"
            )

            connection.setRequestProperty(
                "Connection",
                "Keep-Alive"
            )

            connection.setRequestProperty(
                "X-Requested-With",
                "XMLHttpRequest"
            )

            connection.setRequestProperty(
                "X-MAC",
                mac
            )

            connection.setRequestProperty(
                "X-Device-MAC",
                mac
            )

            connection.setRequestProperty(
                "Cookie",
                "mac=$mac; stb_lang=en; timezone=GMT"
            )

            val referer =
                buildReferer(loadUrl)

            connection.setRequestProperty(
                "Referer",
                referer
            )

            if (token.isNotBlank()) {

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
            }

            connection.connect()

            val responseCode =
                connection.responseCode

            val inputStream =
                if (responseCode >= 400) {
                    connection.errorStream
                } else {
                    connection.inputStream
                }

            val body =
                if (inputStream != null) {

                    BufferedReader(
                        InputStreamReader(
                            inputStream,
                            StandardCharsets.UTF_8
                        )
                    ).use {
                        it.readText()
                    }

                } else {
                    ""
                }

            Log.d(
                TAG,
                "HTTP $responseCode, body=${body.take(500)}"
            )

            return HttpResult(
                code = responseCode,
                body = body,
                finalUrl =
                    connection.url.toString()
            )

        } finally {

            connection.disconnect()
        }
    }

    // ------------------------------------------------------------------------
    // ENDPOINT DISCOVERY
    // ------------------------------------------------------------------------

    private fun buildEndpointCandidates(
        portal: String
    ): List<String> {

        val clean =
            portal.trim()
                .removeSuffix("/")

        val result =
            linkedSetOf<String>()

        /*
         * إذا المستخدم دخل endpoint كامل
         * نحافظ عليه أولا.
         */
        if (
            clean.endsWith(
                "/load.php",
                ignoreCase = true
            ) ||
            clean.endsWith(
                "/portal.php",
                ignoreCase = true
            )
        ) {
            result.add(clean)
        }

        val uri =
            try {
                java.net.URI(clean)
            } catch (_: Exception) {
                null
            }

        val scheme =
            uri?.scheme ?: "http"

        val authority =
            uri?.rawAuthority

        if (!authority.isNullOrBlank()) {

            val root =
                "$scheme://$authority"

            val path =
                uri.rawPath
                    ?: "/"

            val normalizedPath =
                path.removeSuffix("/")

            /*
             * بوابات Stalker التقليدية.
             */
            result.add(
                "$root/stalker_portal/server/load.php"
            )

            result.add(
                "$root/stalker_portal/server/portal.php"
            )

            result.add(
                "$root/server/load.php"
            )

            result.add(
                "$root/server/portal.php"
            )

            result.add(
                "$root/load.php"
            )

            result.add(
                "$root/portal.php"
            )

            result.add(
                "$root/c/server/load.php"
            )

            result.add(
                "$root/c/server/portal.php"
            )

            /*
             * إذا portal فيه /stalker_portal أو /c
             * نجرب المسار المبني عليه كذلك.
             */
            if (
                normalizedPath.contains(
                    "/stalker_portal",
                    ignoreCase = true
                )
            ) {

                result.add(
                    "$root${normalizedPath}/server/load.php"
                )

                result.add(
                    "$root${normalizedPath}/server/portal.php"
                )
            }

            if (
                normalizedPath.equals(
                    "/c",
                    ignoreCase = true
                ) ||
                normalizedPath.startsWith(
                    "/c/",
                    ignoreCase = true
                )
            ) {

                result.add(
                    "$root/c/server/load.php"
                )

                result.add(
                    "$root/c/server/portal.php"
                )
            }
        }

        /*
         * fallback إذا URI parsing ما خدمش.
         */
        if (result.isEmpty()) {

            result.add(
                "$clean/stalker_portal/server/load.php"
            )

            result.add(
                "$clean/stalker_portal/server/portal.php"
            )

            result.add(
                "$clean/server/load.php"
            )

            result.add(
                "$clean/server/portal.php"
            )

            result.add(
                "$clean/load.php"
            )

            result.add(
                "$clean/portal.php"
            )

            result.add(
                "$clean/c/server/load.php"
            )
        }

        return result.toList()
    }

    // ------------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------------

    private fun parseJsonSafe(
        text: String
    ): JsonElement? {

        val clean =
            text.trim()

        if (clean.isBlank()) {
            return null
        }

        return try {

            JsonParser()
                .parse(clean)

        } catch (e: Exception) {

            Log.w(
                TAG,
                "JSON parse failed: ${e.message}"
            )

            null
        }
    }

    private fun extractArray(
        root: JsonObject
    ): List<JsonElement> {

        val candidates =
            listOf(
                "js",
                "data",
                "items",
                "results"
            )

        for (key in candidates) {

            val element =
                root.get(key)

            if (
                element != null &&
                element.isJsonArray
            ) {
                return element.asJsonArray.toList()
            }

            if (
                element != null &&
                element.isJsonObject
            ) {

                val nested =
                    element.asJsonObject

                for (
                    nestedKey in listOf(
                        "data",
                        "items",
                        "results"
                    )
                ) {

                    val nestedElement =
                        nested.get(nestedKey)

                    if (
                        nestedElement != null &&
                        nestedElement.isJsonArray
                    ) {
                        return nestedElement
                            .asJsonArray
                            .toList()
                    }
                }
            }
        }

        return emptyList()
    }

    private fun extractObject(
        root: JsonObject,
        key: String
    ): JsonObject? {

        val element =
            root.get(key)

        return if (
            element != null &&
            element.isJsonObject
        ) {
            element.asJsonObject
        } else {
            null
        }
    }

    private fun extractString(
        root: JsonElement,
        parentKey: String,
        childKey: String
    ): String {

        if (!root.isJsonObject) {
            return ""
        }

        val parent =
            root.asJsonObject
                .get(parentKey)
                ?: return ""

        if (!parent.isJsonObject) {
            return ""
        }

        return firstString(
            parent.asJsonObject,
            childKey
        )
    }

    private fun firstString(
        obj: JsonObject,
        vararg keys: String
    ): String {

        for (key in keys) {

            val element =
                obj.get(key)
                    ?: continue

            if (element.isJsonNull) {
                continue
            }

            try {

                val value =
                    element.asString
                        .trim()

                if (value.isNotBlank()) {
                    return value
                }

            } catch (_: Exception) {
            }
        }

        return ""
    }

    // ------------------------------------------------------------------------
    // SERIES PARSING
    // ------------------------------------------------------------------------

    private fun parseSeasons(
        json: JsonObject
    ): List<SeriesSeason> {

        val array =
            extractArray(json)

        val result =
            mutableListOf<SeriesSeason>()

        for (item in array) {

            if (!item.isJsonObject) continue

            val obj =
                item.asJsonObject

            val id =
                firstString(
                    obj,
                    "id",
                    "season_id"
                )

            val name =
                firstString(
                    obj,
                    "name",
                    "title"
                )

            val number =
                firstString(
                    obj,
                    "season",
                    "season_number"
                )

            if (id.isNotBlank()) {

                result.add(
                    SeriesSeason(
                        id = id,
                        name = name.ifBlank {
                            if (number.isNotBlank()) {
                                "Season $number"
                            } else {
                                "Season"
                            }
                        },
                        seasonNumber = number
                    )
                )
            }
        }

        return result
    }

    private fun parseEpisodes(
        json: JsonObject
    ): List<SeriesEpisode> {

        val array =
            extractArray(json)

        val result =
            mutableListOf<SeriesEpisode>()

        for (item in array) {

            if (!item.isJsonObject) continue

            val obj =
                item.asJsonObject

            val id =
                firstString(
                    obj,
                    "id",
                    "episode_id"
                )

            val name =
                firstString(
                    obj,
                    "name",
                    "title"
                )

            if (id.isBlank()) {
                continue
            }

            result.add(
                SeriesEpisode(
                    id = id,
                    name = name,
                    episodeNumber =
                        firstString(
                            obj,
                            "episode",
                            "episode_number"
                        ),
                    seasonNumber =
                        firstString(
                            obj,
                            "season",
                            "season_number"
                        ),
                    cmd =
                        firstString(
                            obj,
                            "cmd",
                            "url",
                            "stream_url"
                        ),
                    logo =
                        firstString(
                            obj,
                            "logo",
                            "icon"
                        ),
                    description =
                        firstString(
                            obj,
                            "description",
                            "plot",
                            "desc"
                        )
                )
            )
        }

        return result
    }

    // ------------------------------------------------------------------------
    // PLAYBACK
    // ------------------------------------------------------------------------

    private fun extractLink(
        root: JsonObject
    ): String {

        val js =
            extractObject(
                root,
                "js"
            )

        if (js != null) {

            val link =
                firstString(
                    js,
                    "cmd",
                    "link",
                    "url",
                    "stream_url",
                    "playback_url"
                )

            if (link.isNotBlank()) {
                return link
            }
        }

        return firstString(
            root,
            "cmd",
            "link",
            "url",
            "stream_url",
            "playback_url"
        )
    }

    // ------------------------------------------------------------------------
    // PAGINATION
    // ------------------------------------------------------------------------

    private fun hasMorePages(
        json: JsonObject,
        itemCount: Int
    ): Boolean {

        val js =
            extractObject(
                json,
                "js"
            )

        val total =
            js?.let {
                firstString(
                    it,
                    "total_items",
                    "total",
                    "count"
                )
            }.orEmpty()

        val limit =
            js?.let {
                firstString(
                    it,
                    "items_per_page",
                    "page_items",
                    "limit"
                )
            }.orEmpty()

        val totalInt =
            total.toIntOrNull()

        val limitInt =
            limit.toIntOrNull()

        if (
            totalInt != null &&
            limitInt != null &&
            limitInt > 0
        ) {
            return totalInt > limitInt
        }

        if (totalInt != null) {
            return itemCount < totalInt
        }

        /*
         * إذا ما عطاش السيرفر total,
         * الصفحة الكاملة كتدل غالبا أن كاين page أخرى.
         */
        return itemCount >= DEFAULT_PAGE_SIZE
    }

    // ------------------------------------------------------------------------
    // URL / MAC
    // ------------------------------------------------------------------------

    private fun normalizePortalUrl(
        portal: String
    ): String {

        var value =
            portal.trim()

        if (value.isBlank()) {
            throw IllegalArgumentException(
                "Portal URL is empty"
            )
        }

        if (
            !value.startsWith(
                "http://",
                ignoreCase = true
            ) &&
            !value.startsWith(
                "https://",
                ignoreCase = true
            )
        ) {
            value = "http://$value"
        }

        return value.removeSuffix("/")
    }

    private fun normalizeMac(
        mac: String
    ): String {

        return mac
            .trim()
            .uppercase(Locale.US)
    }

    private fun normalizeHttpUrl(
        value: String,
        portal: String
    ): String {

        if (value.isBlank()) {
            return ""
        }

        if (
            value.startsWith(
                "http://",
                ignoreCase = true
            ) ||
            value.startsWith(
                "https://",
                ignoreCase = true
            )
        ) {
            return value
        }

        if (value.startsWith("//")) {

            val scheme =
                try {
                    java.net.URI(
                        portal
                    ).scheme
                } catch (_: Exception) {
                    "http"
                }

            return "$scheme:$value"
        }

        return value
    }

    private fun buildReferer(
        loadUrl: String
    ): String {

        return try {

            val uri =
                java.net.URI(loadUrl)

            val scheme =
                uri.scheme ?: "http"

            val authority =
                uri.rawAuthority
                    ?: return "$scheme://"

            "$scheme://$authority/stalker_portal/c/"

        } catch (_: Exception) {

            loadUrl.substringBefore(
                "/stalker_portal"
            ) + "/stalker_portal/c/"
        }
    }

    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        )
    }

    // ------------------------------------------------------------------------
    // RESPONSE CHECKS
    // ------------------------------------------------------------------------

    private fun looksLikeHtml(
        body: String
    ): Boolean {

        val value =
            body.trim()
                .lowercase(Locale.US)

        return value.startsWith("<!doctype") ||
                value.startsWith("<html") ||
                value.startsWith("<head") ||
                value.startsWith("<body")
    }

    // ------------------------------------------------------------------------
    // ASYNC
    // ------------------------------------------------------------------------

    private fun <T> executeAsync(
        callback: (T) -> Unit,
        errorCallback: (String) -> Unit,
        block: () -> T
    ) {

        Thread {

            try {

                val result =
                    block()

                Handler(
                    Looper.getMainLooper()
                ).post {
                    callback(result)
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Stalker request failed",
                    e
                )

                val message =
                    e.message
                        ?: "Unknown error"

                Handler(
                    Looper.getMainLooper()
                ).post {
                    errorCallback(message)
                }
            }

        }.start()
    }
}

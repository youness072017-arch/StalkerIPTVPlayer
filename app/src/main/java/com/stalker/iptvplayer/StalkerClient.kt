package com.stalker.iptvplayer

import android.util.Log
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
    // LIVE TV
    // ============================================================

    fun authenticateAndFetchChannels(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, String) -> Unit
    ) {
        fetchChannels(portalUrl, macAddress) { success, channels, message ->
            onResult(
                success,
                if (success) {
                    "Connected successfully. ${channels.size} Live TV channels found."
                } else {
                    message
                }
            )
        }
    }

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

                Log.d(TAG, "LIVE: portal=$portal")

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

                Log.d(TAG, "LIVE: endpoint=$loadUrl")

                val session = performHandshake(
                    loadUrl,
                    mac
                )

                Log.d(TAG, "LIVE: handshake successful")

                try {
                    getProfile(
                        session,
                        mac
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "LIVE: profile failed: ${e.message}"
                    )
                }

                val categories = try {
                    requestGenres(
                        session,
                        mac
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "LIVE: genres failed: ${e.message}"
                    )

                    emptyMap()
                }

                val response = requestChannels(
                    session,
                    mac
                )

                val channels = parseChannels(
                    response,
                    categories
                )

                Log.d(
                    TAG,
                    "LIVE: parsed ${channels.size} channels"
                )

                if (channels.isEmpty()) {

                    Log.w(
                        TAG,
                        "LIVE: raw response=${response.take(4000)}"
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
                        "LIVE[$index] ${channel.name} " +
                                "id=${channel.id} " +
                                "category=${channel.categoryName}"
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
                    "LIVE request failed",
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

                if (!isValidMac(mac)) {
                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Invalid MAC address."
                    )
                    return@Thread
                }

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

                val categories = try {
                    requestVodCategories(
                        session,
                        mac
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "MOVIES categories failed: ${e.message}"
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
                    "MOVIES parsed ${movies.size}"
                )

                if (movies.isEmpty()) {

                    finishMovies(
                        onResult,
                        false,
                        emptyList(),
                        "Connected, but no Movies/VOD were returned by the portal."
                    )

                } else {

                    finishMovies(
                        onResult,
                        true,
                        movies,
                        "Movies loaded: ${movies.size}"
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "MOVIES request failed",
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

                if (!isValidMac(mac)) {
                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Invalid MAC address."
                    )
                    return@Thread
                }

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

                val categories = try {
                    requestSeriesCategories(
                        session,
                        mac
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "SERIES categories failed: ${e.message}"
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
                    "SERIES parsed ${series.size}"
                )

                if (series.isEmpty()) {

                    finishSeries(
                        onResult,
                        false,
                        emptyList(),
                        "Connected, but no Series were returned by the portal."
                    )

                } else {

                    finishSeries(
                        onResult,
                        true,
                        series,
                        "Series loaded: ${series.size}"
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "SERIES request failed",
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
                    "DISCOVERY testing $candidate"
                )

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

                if (!root.isJsonObject) continue

                val js = root.asJsonObject.get("js")

                val token = findString(
                    js,
                    "token"
                )

                if (token.isNotEmpty()) {

                    Log.d(
                        TAG,
                        "DISCOVERY compatible endpoint=$candidate"
                    )

                    return candidate
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "DISCOVERY failed $candidate: ${e.message}"
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

        var value = input
            .trim()
            .removeSuffix("/")

        val suffixes = listOf(
            "/stalker_portal/server/load.php",
            "/server/load.php",
            "/stalker_portal/load.php",
            "/portal.php",
            "/stalker_portal/c",
            "/stalker_portal",
            "/server",
            "/c",
            "/load.php"
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
            throw Exception(
                "Handshake returned invalid JSON."
            )
        }

        val js = root.asJsonObject.get("js")

            ?: throw Exception(
                "Handshake response is missing 'js'."
            )

        val token = findString(
            js,
            "token"
        )

        if (token.isEmpty()) {

            throw Exception(
                "Handshake failed: portal did not return a token."
            )
        }

        return Session(
            token = token,
            random = findString(
                js,
                "random"
            ),
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
            session.loadUrl,
            mapOf(
                "type" to "stb",
                "action" to "get_profile",
                "hd" to "1",
                "ver" to "ImageDescription: 0.2.18-r23",
                "num_banks" to "2",
                "sn" to "",
                "stb_type" to "MAG250",
                "image_version" to "218",
                "video_out" to "hdmi",
                "device_id" to "",
                "device_id2" to "",
                "signature" to "",
                "auth_second_step" to "0",
                "hw_version" to "1.7-BD-00",
                "not_valid" to "0",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress,
            session.token
        )

        val root = parseJson(response)

        if (!root.isJsonObject) {
            return JsonObject()
        }

        val js = root.asJsonObject.get("js")

        return if (
            js != null &&
            js.isJsonObject
        ) {
            js.asJsonObject
        } else {
            JsonObject()
        }
    }

    // ============================================================
    // LIVE REQUESTS
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

        return parseCategoryMap(
            response
        )
    }

    private fun requestChannels(
        session: Session,
        macAddress: String
    ): String {

        val requests = listOf(

            mapOf(
                "type" to "itv",
                "action" to "get_all_channels",
                "JsHttpRequest" to "1-xml"
            ),

            mapOf(
                "type" to "itv",
                "action" to "get_channels",
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            ),

            mapOf(
                "type" to "itv",
                "action" to "get_ordered_list",
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            )
        )

        var lastResponse = ""

        for (params in requests) {

            try {

                val response = request(
                    session.loadUrl,
                    params,
                    macAddress,
                    session.token
                )

                lastResponse = response

                if (
                    parseChannels(
                        response,
                        emptyMap()
                    ).isNotEmpty()
                ) {
                    return response
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "LIVE ${params["action"]} failed: ${e.message}"
                )
            }
        }

        return lastResponse
    }

    // ============================================================
    // MOVIE REQUESTS
    // ============================================================

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

        return parseCategoryMap(
            response
        )
    }

    private fun requestMovies(
        session: Session,
        macAddress: String
    ): String {

        val attempts = listOf(

            mapOf(
                "type" to "vod",
                "action" to "get_ordered_list",
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            ),

            mapOf(
                "type" to "vod",
                "action" to "get_vod",
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            )
        )

        var last = ""

        for (params in attempts) {

            try {

                last = request(
                    session.loadUrl,
                    params,
                    macAddress,
                    session.token
                )

                if (
                    parseVodItems(
                        last,
                        emptyMap()
                    ).isNotEmpty()
                ) {
                    return last
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "MOVIES ${params["action"]} failed: ${e.message}"
                )
            }
        }

        return last
    }

    // ============================================================
    // SERIES REQUESTS
    // ============================================================

    private fun requestSeriesCategories(
        session: Session,
        macAddress: String
    ): Map<String, String> {

        val response = request(
            session.loadUrl,
            mapOf(
                "type" to "series",
                "action" to "get_categories",
                "JsHttpRequest" to "1-xml"
            ),
            macAddress,
            session.token
        )

        return parseCategoryMap(
            response
        )
    }

    private fun requestSeries(
        session: Session,
        macAddress: String
    ): String {

        val attempts = listOf(

            mapOf(
                "type" to "series",
                "action" to "get_ordered_list",
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            ),

            mapOf(
                "type" to "series",
                "action" to "get_series",
                "p" to "1",
                "JsHttpRequest" to "1-xml"
            )
        )

        var last = ""

        for (params in attempts) {

            try {

                last = request(
                    session.loadUrl,
                    params,
                    macAddress,
                    session.token
                )

                if (
                    parseSeriesItems(
                        last,
                        emptyMap()
                    ).isNotEmpty()
                ) {
                    return last
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "SERIES ${params["action"]} failed: ${e.message}"
                )
            }
        }

        return last
    }

    // ============================================================
    // CHANNEL PARSER
    // ============================================================

    private fun parseChannels(
        response: String,
        categories: Map<String, String>
    ): List<Channel> {

        val result = mutableListOf<Channel>()

        try {

            val root = parseJson(
                response
            )

            if (!root.isJsonObject) {
                return emptyList()
            }

            val js = root.asJsonObject.get(
                "js"
            ) ?: return emptyList()

            val objects = extractObjects(
                js
            )

            Log.d(
                TAG,
                "LIVE parser extracted ${objects.size} objects"
            )

            for (obj in objects) {

                val channel = parseChannelObject(
                    obj,
                    categories
                )

                if (channel != null) {
                    result.add(channel)
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "LIVE parser error",
                e
            )
        }

        return result.distinctBy {
            it.id.ifEmpty {
                it.name
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
            "channel_id",
            "tv_id"
        )

        val name = firstValue(
            obj,
            "name",
            "title",
            "channel_name"
        ).trim()

        if (
            id.isEmpty() &&
            name.isEmpty()
        ) {
            return null
        }

        val categoryId = firstValue(
            obj,
            "tv_genre_id",
            "genre_id",
            "category_id",
            "genre"
        )

        return Channel(
            id = id.ifEmpty {
                name
            },

            name = name.ifEmpty {
                "Channel $id"
            },

            logo = firstValue(
                obj,
                "logo",
                "logo_url",
                "icon",
                "tvg_logo"
            ),

            cmd = firstValue(
                obj,
                "cmd",
                "stream_url",
                "url",
                "stream"
            ),

            categoryId = categoryId,

            categoryName = categories[
                categoryId
            ].orEmpty()
        )
    }

    // ============================================================
    // VOD PARSER
    // ============================================================

    private fun parseVodItems(
        response: String,
        categories: Map<String, String>
    ): List<VodItem> {

        val result = mutableListOf<VodItem>()

        try {

            val root = parseJson(response)

            if (!root.isJsonObject) {
                return emptyList()
            }

            val js = root.asJsonObject.get(
                "js"
            ) ?: return emptyList()

            for (obj in extractObjects(js)) {

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

                if (
                    id.isEmpty() &&
                    name.isEmpty()
                ) {
                    continue
                }

                val categoryId = firstValue(
                    obj,
                    "category_id",
                    "cat_id",
                    "genre_id"
                )

                result.add(
                    VodItem(
                        id = id.ifEmpty {
                            name
                        },

                        name = name.ifEmpty {
                            "Movie $id"
                        },

                        logo = firstValue(
                            obj,
                            "logo",
                            "poster",
                            "icon"
                        ),

                        cmd = firstValue(
                            obj,
                            "cmd",
                            "url",
                            "stream_url"
                        ),

                        categoryId = categoryId,

                        categoryName = categories[
                            categoryId
                        ].orEmpty(),

                        year = firstValue(
                            obj,
                            "year",
                            "release_year"
                        ),

                        description = firstValue(
                            obj,
                            "description",
                            "plot"
                        )
                    )
               

package com.stalker.iptvplayer

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * Stalker / MAG Portal Client
 *
 * الوظائف:
 * - اكتشاف المسار الأساسي للـ Stalker Portal
 * - Handshake حقيقي
 * - Token / Bearer Authorization
 * - Cookies
 * - Profile
 * - Genres
 * - Live TV channels
 * - دعم اختلافات شائعة في شكل الـ JSON
 *
 * ملاحظة:
 * DashboardActivity الحالي مازال يستعمل callback القديم:
 *
 * onResult(Boolean, String)
 *
 * لذلك هذا الملف يحافظ على نفس الـ callback حتى ما يكسرش المشروع حالياً.
 */
class StalkerClient {

    companion object {
        private const val TAG = "StalkerClient"

        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 20000

        private const val USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) " +
                    "AppleWebKit/533.3 (KHTML, like Gecko) " +
                    "MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        private const val X_USER_AGENT =
            "Model: MAG250; Link: WiFi"
    }

    /**
     * بيانات جلسة Stalker الحالية.
     */
    private data class Session(
        val token: String,
        val random: String = "",
        val portalBase: String,
        val loadUrl: String
    )

    /**
     * بيانات قناة Live TV.
     */
    data class Channel(
        val id: String,
        val name: String,
        val logo: String = "",
        val cmd: String = "",
        val categoryId: String = "",
        val categoryName: String = ""
    )

    private val gson = Gson()

    /**
     * نقطة الدخول الرئيسية التي يستعملها Dashboard حالياً.
     */
    fun authenticateAndFetchChannels(
        portalUrl: String,
        macAddress: String,
        onResult: (Boolean, String) -> Unit
    ) {

        Thread {
            try {

                val cleanPortal = normalizePortalUrl(portalUrl)
                val cleanMac = normalizeMac(macAddress)

                if (cleanPortal.isEmpty()) {
                    finish(
                        onResult,
                        false,
                        "Portal URL is empty or invalid."
                    )
                    return@Thread
                }

                if (!isValidMac(cleanMac)) {
                    finish(
                        onResult,
                        false,
                        "Invalid MAC address."
                    )
                    return@Thread
                }

                Log.d(TAG, "Portal: $cleanPortal")
                Log.d(TAG, "MAC: $cleanMac")

                /*
                 * 1. اكتشاف load.php الصحيح
                 */
                val loadUrl = discoverLoadUrl(cleanPortal)

                Log.d(TAG, "Using load URL: $loadUrl")

                /*
                 * 2. Handshake
                 */
                val session = performHandshake(
                    portalBase = cleanPortal,
                    loadUrl = loadUrl,
                    macAddress = cleanMac
                )

                Log.d(TAG, "Handshake successful")
                Log.d(TAG, "Token received")

                /*
                 * 3. Profile
                 *
                 * بعض البوابات تحتاج get_profile بعد الـhandshake.
                 * إذا فشل profile بشكل غير قاتل، نكمل للقنوات.
                 */
                try {
                    getProfile(
                        session = session,
                        macAddress = cleanMac
                    )
                    Log.d(TAG, "Profile request completed")
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Profile request failed, continuing: ${e.message}"
                    )
                }

                /*
                 * 4. Genres / Categories
                 */
                try {
                    val genresResponse = request(
                        loadUrl = session.loadUrl,
                        params = mapOf(
                            "type" to "itv",
                            "action" to "get_genres",
                            "JsHttpRequest" to "1-xml"
                        ),
                        macAddress = cleanMac,
                        token = session.token
                    )

                    Log.d(
                        TAG,
                        "Genres response received: ${genresResponse.length} chars"
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Genres request failed: ${e.message}"
                    )
                }

                /*
                 * 5. Live TV
                 */
                val channelsResponse = requestChannels(
                    session = session,
                    macAddress = cleanMac
                )

                val channels = parseChannels(channelsResponse)

                if (channels.isEmpty()) {
                    Log.d(
                        TAG,
                        "Portal answered successfully but no channels were parsed."
                    )

                    finish(
                        onResult,
                        false,
                        "Portal connected, but no Live TV channels were found."
                    )

                    return@Thread
                }

                /*
                 * حالياً Dashboard القديم مازال ينتظر String فقط.
                 * لذلك نسجل عدد القنوات، والمرحلة القادمة غادي نربط
                 * channels بواجهة RecyclerView.
                 */
                Log.d(
                    TAG,
                    "Successfully parsed ${channels.size} channels"
                )

                channels.take(20).forEachIndexed { index, channel ->
                    Log.d(
                        TAG,
                        "Channel[$index]: ${channel.name} | id=${channel.id}"
                    )
                }

                finish(
                    onResult,
                    true,
                    "Connected successfully. ${channels.size} Live TV channels found."
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Stalker connection failed",
                    e
                )

                finish(
                    onResult,
                    false,
                    e.message ?: "Unknown portal connection error."
                )
            }
        }.start()
    }

    // ------------------------------------------------------------------------
    // Portal discovery
    // ------------------------------------------------------------------------

    private fun discoverLoadUrl(portalUrl: String): String {

        val base = portalUrl
            .removeSuffix("/")
            .removeSuffix("/c")
            .removeSuffix("/stalker_portal")
            .removeSuffix("/server")
            .removeSuffix("/load.php")
            .removeSuffix("/")

        /*
         * المسار الأكثر شيوعاً في Stalker Portal.
         */
        return "$base/stalker_portal/server/load.php"
    }

    private fun normalizePortalUrl(input: String): String {

        var value = input.trim()

        if (value.isEmpty()) {
            return ""
        }

        /*
         * إذا المستخدم دخل:
         * example.com
         *
         * نضيف http:// بدل ما URL يرمي Exception.
         */
        if (!value.startsWith("http://", true) &&
            !value.startsWith("https://", true)
        ) {
            value = "http://$value"
        }

        /*
         * إزالة /c/ من النهاية لأننا نبني endpoint بأنفسنا.
         */
        value = value.removeSuffix("/")

        if (value.endsWith("/c", true)) {
            value = value.removeSuffix("/c")
        }

        if (value.endsWith("/stalker_portal", true)) {
            value = value.removeSuffix("/stalker_portal")
        }

        if (value.endsWith("/server", true)) {
            value = value.removeSuffix("/server")
        }

        if (value.endsWith("/load.php", true)) {
            value = value.removeSuffix("/load.php")
        }

        return value.removeSuffix("/")
    }

    // ------------------------------------------------------------------------
    // MAC
    // ------------------------------------------------------------------------

    private fun normalizeMac(input: String): String {
        return input
            .trim()
            .uppercase(Locale.US)
    }

    private fun isValidMac(mac: String): Boolean {

        val regex = Regex(
            "^([0-9A-F]{2}:){5}[0-9A-F]{2}$"
        )

        return regex.matches(mac)
    }

    // ------------------------------------------------------------------------
    // Handshake
    // ------------------------------------------------------------------------

    private fun performHandshake(
        portalBase: String,
        loadUrl: String,
        macAddress: String
    ): Session {

        /*
         * بعض البوابات لا تحتاج mac في query.
         * لكن Cookie mac مهمة جداً.
         */
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

        val json = parseJson(response)

        val js = json
            .getAsJsonObject("js")

        val token = getString(
            js,
            "token"
        )

        if (token.isEmpty()) {
            throw Exception(
                "Handshake failed: portal did not return a token."
            )
        }

        val random = getString(
            js,
            "random"
        )

        return Session(
            token = token,
            random = random,
            portalBase = portalBase,
            loadUrl = loadUrl
        )
    }

    // ------------------------------------------------------------------------
    // Profile
    // ------------------------------------------------------------------------

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

        val json = parseJson(response)

        return json
            .getAsJsonObject("js")
    }

    // ------------------------------------------------------------------------
    // Channels
    // ------------------------------------------------------------------------

    private fun requestChannels(
        session: Session,
        macAddress: String
    ): String {

        /*
         * المسار القياسي.
         */
        try {

            return request(
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

            /*
             * بعض البوابات القديمة تستعمل get_channels.
             */
            return request(
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

    // ------------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------------

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
            "Request: $finalUrl"
        )

        val connection =
            URL(finalUrl).openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true

            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            connection.useCaches = false

            /*
             * Headers التي تستعملها أجهزة MAG/Stalker عادةً.
             */
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

            val responseCode = connection.responseCode

            val stream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val response = stream?.let {
                BufferedReader(
                    InputStreamReader(it)
                ).use { reader ->
                    reader.readText()
                }
            } ?: ""

            Log.d(
                TAG,
                "HTTP $responseCode | ${response.take(500)}"
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

            /*
             * نتأكد أن الرد JSON فعلاً.
             */
            try {
                JsonParser.parseString(response)
            } catch (e: Exception) {
                throw Exception(
                    "Portal returned invalid JSON."
                )
            }

            return response

        } finally {
            connection.disconnect()
        }
    }

    private fun buildReferer(
        loadUrl: String
    ): String {

        return try {

            val url = URL(loadUrl)

            "${url.protocol}://${url.authority}/stalker_portal/c/"

        } catch (_: Exception) {

            "$loadUrl/../c/"
        }
    }

    private fun buildQuery(
        params: Map<String, String>
    ): String {

        return params.entries.joinToString("&") { entry ->

            val key = URLEncoder.encode(
                entry.key,
                "UTF-8"
            )

            val value = URLEncoder.encode(
                entry.value,
                "UTF-8"
            )

            "$key=$value"
        }
    }

    // ------------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------------

    private fun parseJson(
        response: String
    ): JsonObject {

        val root =
            JsonParser.parseString(response)

        if (!root.isJsonObject) {
            throw Exception(
                "Portal response is not a JSON object."
            )
        }

        val json =
            root.asJsonObject

        /*
         * بعض البوابات ترجع:
         *
         * {"js": {...}}
         *
         * وإذا ما كانش js، فالرد غير صالح بالنسبة
         * للـStalker API المتوقع.
         */
        if (!json.has("js")) {
            throw Exception(
                "Invalid Stalker response: missing 'js'."
            )
        }

        return json
    }

    private fun getString(
        obj: JsonObject,
        key: String
    ): String {

        val element: JsonElement =
            obj.get(key) ?: return ""

        if (element.isJsonNull) {
            return ""
        }

        return try {
            element.asString ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    // ------------------------------------------------------------------------
    // Channel parser
    // ------------------------------------------------------------------------

    private fun parseChannels(
        response: String
    ): List<Channel> {

        val result = mutableListOf<Channel>()

        try {

            val json =
                JsonParser.parseString(response)
                    .asJsonObject

            val js =
                json.getAsJsonObject("js")
                    ?: return emptyList()

            /*
             * الشكل الأكثر شيوعاً:
             *
             * js.data = [...]
             */
            if (js.has("data")) {

                val data = js.get("data")

                if (data.isJsonArray) {

                    data.asJsonArray.forEach { item ->

                        if (!item.isJsonObject) {
                            return@forEach
                        }

                        val obj =
                            item.asJsonObject

                        val channel =
                            parseChannelObject(obj)

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

            /*
             * بعض implementations ترجع results.
             */
            if (result.isEmpty() && js.has("results")) {

                val results =
                    js.get("results")

                if (results.isJsonArray) {

                    results.asJsonArray.forEach { item ->

                        if (!item.isJsonObject) {
                            return@forEach
                        }

                        val channel =
                            parseChannelObject(
           

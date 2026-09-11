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

                if (!hasMorePages(json, array.size, page)) {
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
               

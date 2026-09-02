package com.stalker.iptvplayer

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class StalkerClient {

    fun authenticateAndFetchChannels(portalUrl: String, macAddress: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val cleanUrl = portalUrl.removeSuffix("/")
                val targetUrl = if (cleanUrl.contains("/c/") || cleanUrl.contains("load.php")) {
                    cleanUrl
                } else {
                    "$cleanUrl/stalker_portal/server/load.php?type=itv&action=handshake"
                }

                val url = URL(targetUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C)")
                connection.setRequestProperty("Cookie", "mac=$macAddress")
                connection.connectTimeout = 12000
                connection.readTimeout = 12000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    onResult(true, "Connected Successfully!")
                } else {
                    onResult(false, "Server Error: Code $responseCode")
                }
            } catch (e: Exception) {
                Log.e("StalkerClient", "Connection Exception", e)
                onResult(false, "Connection Failed: ${e.localizedMessage}")
            }
        }.start()
    }
}

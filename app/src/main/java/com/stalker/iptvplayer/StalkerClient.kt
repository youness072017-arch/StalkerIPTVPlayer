package com.stalker.iptvplayer

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class StalkerClient {

    fun authenticateAndFetchChannels(portalUrl: String, macAddress: String, onResult: (String) -> Unit) {
        Thread {
            try {
                // تنظيف الرابط من أي شرطة مائلة في الأخير لضمان سلامة المسار
                val cleanUrl = portalUrl.removeSuffix("/")
                
                // تجربة مسار الاتصال الخاص بـ Stalker / Ministra
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
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    onResult("Success! Connected to Portal.")
                } else {
                    onResult("Failed: Server responded with code $responseCode")
                }
            } catch (e: Exception) {
                Log.e("StalkerClient", "Error connecting to portal", e)
                onResult("Error: ${e.localizedMessage}")
            }
        }.start()
    }
}

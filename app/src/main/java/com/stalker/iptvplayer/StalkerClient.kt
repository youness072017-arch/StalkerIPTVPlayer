package com.stalker.iptvplayer

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class StalkerClient {

    fun authenticateAndFetchChannels(portalUrl: String, macAddress: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val cleanUrl = portalUrl.removeSuffix("/")
                
                // تحديد المسار الصحيح للسيرفر
                val targetUrl = if (cleanUrl.contains("/c/") || cleanUrl.contains("load.php")) {
                    "$cleanUrl?type=itv&action=handshake&mac=$macAddress"
                } else {
                    "$cleanUrl/stalker_portal/server/load.php?type=itv&action=handshake&mac=$macAddress"
                }

                val url = URL(targetUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                // الإعدادات الاحترافية للاتصال بسيرفرات Stalker
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stb app")
                connection.setRequestProperty("Cookie", "mac=$macAddress; stb_lang=en; timezone=Europe/Paris")
                connection.setRequestProperty("Referer", "$cleanUrl/stalker_portal/c/")
                connection.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("StalkerClient", "Response: $response")
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

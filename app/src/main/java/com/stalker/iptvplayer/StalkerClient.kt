package com.stalker.iptvplayer

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class StalkerClient {

    fun authenticateAndFetchChannels(portalUrl: String, macAddress: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val cleanUrl = portalUrl.removeSuffix("/")
                
                // الانتقال مباشرة إلى صفحة البورتال الرئيسية /c/ كما تفعله أجهزة MAG
                val targetUrl = "$cleanUrl/c/"

                val url = URL(targetUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stb app")
                connection.setRequestProperty("Cookie", "mac=$macAddress")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode == 200 || responseCode == 302) {
                    onResult(true, "Portal Handshake OK! Code: $responseCode")
                } else {
                    onResult(false, "Server Error: Code $responseCode")
                }
            } catch (e: Exception) {
                Log.e("StalkerClient", "Connection Exception", e)
                onResult(false, "Failed: ${e.localizedMessage}")
            }
        }.start()
    }
}

package com.stalker.iptvplayer

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class StalkerClient {

    fun authenticateAndFetchChannels(portalUrl: String, macAddress: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val cleanUrl = portalUrl.removeSuffix("/")
                
                // الخطوة 1: الـ Handshake والتحقق من البورتال
                val handshakeUrl = "$cleanUrl/c/"
                val url = URL(handshakeUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stb app")
                connection.setRequestProperty("Cookie", "mac=$macAddress")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode == 200 || responseCode == 302) {
                    
                    // الخطوة 2: بعد نجاح الاتصال، نقوم بطلب القنوات (Live TV)
                    val channelsUrl = "$cleanUrl/stalker_portal/server/load.php?type=itv&action=get_all_channels&mac=$macAddress"
                    val channelsConn = URL(channelsUrl).openConnection() as HttpURLConnection
                    channelsConn.requestMethod = "GET"
                    channelsConn.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stb app")
                    channelsConn.setRequestProperty("Cookie", "mac=$macAddress")
                    channelsConn.connectTimeout = 15000
                    channelsConn.readTimeout = 15000

                    val channelsCode = channelsConn.responseCode
                    if (channelsCode == 200) {
                        val response = channelsConn.inputStream.bufferedReader().use { it.readText() }
                        Log.d("StalkerChannels", response)
                        onResult(true, "Success! Channels fetched successfully.")
                    } else {
                        onResult(false, "Handshake OK, but failed to fetch channels: Code $channelsCode")
                    }

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

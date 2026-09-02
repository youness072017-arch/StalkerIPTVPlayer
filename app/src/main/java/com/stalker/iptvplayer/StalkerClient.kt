package com.stalker.iptvplayer

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class StalkerClient {

    fun authenticateAndFetchChannels(portalUrl: String, macAddress: String, onResult: (String) -> Unit) {
        Thread {
            try {
                // طريقة الاتصال ببوابة Stalker (Handshake / MAC Authentication)
                val handshakeUrl = "$portalUrl/stalker_portal/server/load.php?type=itv&action=handshake&mac=$macAddress"
                val url = URL(handshakeUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C)")
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    onResult("Connected! Data received successfully.")
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

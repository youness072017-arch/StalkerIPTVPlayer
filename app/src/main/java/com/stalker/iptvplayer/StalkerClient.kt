    private fun isValidMac(mac: String): Boolean {
        val clean = mac.replace(Regex("[^0-9a-fA-F]"), "")
        return clean.length == 12
    }

    private fun discoverLoadUrl(portal: String, mac: String): String {
        val candidates = listOf(
            "$portal/stalker_portal/server/load.php",
            "$portal/server/load.php",
            "$portal/c/portal.php"
        )
        for (url in candidates) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("X-User-Agent", X_USER_AGENT)
                conn.setRequestProperty("Cookie", "mac=$mac")
                if (conn.responseCode in 200..399) {
                    return url
                }
            } catch (_: Exception) {}
        }
        return candidates[0]
    }

    private fun performHandshake(loadUrl: String, mac: String): Session {
        val response = request(
            loadUrl,
            mapOf(
                "type" to "stb",
                "action" to "handshake",
                "preloaded" to "true",
                "JsHttpRequest" to "1-xml"
            ),
            mac,
            ""
        )
        val root = parseJson(response)
        val js = root.getAsJsonObject("js") ?: throw Exception("Invalid handshake response")
        val token = js.get("token")?.asString ?: ""
        val random = js.get("random")?.asString ?: ""
        return Session(token, random, loadUrl)
    }

    private fun getProfile(session: Session, mac: String) {
        request(
            session.loadUrl,
            mapOf(
                "type" to "stb",
                "action" to "get_profile",
                "hd" to "1",
                "ver" to "3.2.2",
                "stb_type" to "MAG250",
                "lang" to DEFAULT_LANGUAGE,
                "timezone" to DEFAULT_TIMEZONE,
                "JsHttpRequest" to "1-xml"
            ),
            mac,
            session.token
        )
    }

    private fun request(
        urlStr: String,
        params: Map<String, String>,
        mac: String,
        token: String
    ): String {
        val query = params.entries.joinToString("&") { 
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" 
        }
        val fullUrl = if (urlStr.contains("?")) "$urlStr&$query" else "$urlStr?$query"

        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("X-User-Agent", X_USER_AGENT)
        conn.setRequestProperty("Cookie", "mac=$mac; stb_lang=$DEFAULT_LANGUAGE; timezone=${URLEncoder.encode(DEFAULT_TIMEZONE, "UTF-8")}")
        if (token.isNotEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }

        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = reader.readLine() } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    private fun parseJson(jsonStr: String): JsonObject {
        val element = JsonParser.parseString(jsonStr)
        if (element.isJsonObject) {
            return element.asJsonObject
        }
        throw Exception("Invalid JSON format")
    }

    private fun extractPagination(root: JsonObject): Pair<Int, Int> {
        var total = -1
        var pageSize = 14
        try {
            val js = root.getAsJsonObject("js")
            if (js != null) {
                if (js.has("total_items")) total = js.get("total_items").asInt
                if (js.has("max_page_items")) pageSize = js.get("max_page_items").asInt
            }
        } catch (_: Exception) {}
        return Pair(total, pageSize)
    }

    private fun parseCategories(response: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        try {
            val root = parseJson(response)
            val js = root.get("js")
            val array = if (js != null && js.isJsonArray) js.asJsonArray else null
            if (array != null) {
                for (elem in array) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        val id = obj.get("id")?.asString ?: obj.get("alias")?.asString ?: continue
                        val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: "Category"
                        map[id] = title
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun parseChannels(response: String, categories: Map<String, String>): List<Channel> {
        val list = mutableListOf<Channel>()
        try {
            val root = parseJson(response)
            val js = root.get("js")
            val data = when {
                js != null && js.isJsonObject && js.asJsonObject.has("data") -> js.asJsonObject.getAsJsonArray("data")
                js != null && js.isJsonArray -> js.asJsonArray
                else -> null
            }

            if (data != null) {
                for (elem in data) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: obj.get("number")?.asString ?: ""
                    val name = obj.get("name")?.asString ?: "Unknown"
                    val logo = obj.get("logo")?.asString ?: ""
                    val cmd = obj.get("cmd")?.asString ?: ""
                    val catId = obj.get("tv_genre_id")?.asString ?: obj.get("genre_id")?.asString ?: ""
                    val catName = categories[catId] ?: ""

                    list.add(Channel(id, name, logo, cmd, catId, catName))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseVodItems(response: String, categories: Map<String, String>): List<VodItem> {
        val list = mutableListOf<VodItem>()
        try {
            val root = parseJson(response)
            val js = root.get("js")
            val data = when {
                js != null && js.isJsonObject && js.asJsonObject.has("data") -> js.asJsonObject.getAsJsonArray("data")
                js != null && js.isJsonArray -> js.asJsonArray
                else -> null
            }
            if (data != null) {
                for (elem in data) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: ""
                    val name = obj.get("name")?.asString ?: ""
                    val logo = obj.get("screenshot_uri")?.asString ?: obj.get("logo")?.asString ?: ""
                    val cmd = obj.get("cmd")?.asString ?: ""
                    val catId = obj.get("category_id")?.asString ?: ""
                    val year = obj.get("year")?.asString ?: ""
                    val desc = obj.get("description")?.asString ?: ""
                    list.add(VodItem(id, name, logo, cmd, catId, categories[catId] ?: "", year, desc))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseSeriesItems(response: String, categories: Map<String, String>): List<SeriesItem> {
        val list = mutableListOf<SeriesItem>()
        try {
            val root = parseJson(response)
            val js = root.get("js")
            val data = when {
                js != null && js.isJsonObject && js.asJsonObject.has("data") -> js.asJsonObject.getAsJsonArray("data")
                js != null && js.isJsonArray -> js.asJsonArray
                else -> null
            }
            if (data != null) {
                for (elem in data) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: ""
                    val name = obj.get("name")?.asString ?: ""
                    val logo = obj.get("screenshot_uri")?.asString ?: ""
                    val catId = obj.get("category_id")?.asString ?: ""
                    val year = obj.get("year")?.asString ?: ""
                    val desc = obj.get("description")?.asString ?: ""
                    list.add(SeriesItem(id, name, logo, catId, categories[catId] ?: "", year, desc))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun finishChannels(
        onResult: (Boolean, List<Channel>, String) -> Unit,
        success: Boolean,
        channels: List<Channel>,
        msg: String
    ) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onResult(success, channels, msg)
        }
    }

    private fun finishMovies(
        onResult: (Boolean, List<VodItem>, String) -> Unit,
        success: Boolean,
        items: List<VodItem>,
        msg: String
    ) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onResult(success, items, msg)
        }
    }

    private fun finishSeries(
        onResult: (Boolean, List<SeriesItem>, String) -> Unit,
        success: Boolean,
        items: List<SeriesItem>,
        msg: String
    ) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onResult(success, items, msg)
        }
    }
}

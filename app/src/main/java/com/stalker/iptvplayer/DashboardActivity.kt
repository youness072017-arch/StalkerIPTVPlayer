package com.stalker.iptvplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {

    private lateinit var itemList: ListView
    private lateinit var loading: ProgressBar
    private lateinit var statusText: TextView

    private val client = StalkerClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val portalUrl = intent.getStringExtra("PORTAL_URL") ?: ""
        val macAddress = intent.getStringExtra("MAC_ADDRESS") ?: ""

        val btnLiveTv = findViewById<Button>(R.id.btn_live_tv)
        val btnMovies = findViewById<Button>(R.id.btn_movies)
        val btnSeries = findViewById<Button>(R.id.btn_series)
        val btnSettings = findViewById<Button>(R.id.btn_settings)

        itemList = findViewById(R.id.channel_list)
        loading = findViewById(R.id.loading)
        statusText = findViewById(R.id.status_text)

        fun validLogin(): Boolean {
            if (portalUrl.isBlank() || macAddress.isBlank()) {
                Toast.makeText(
                    this,
                    "Missing portal details. Please re-login.",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }

            return true
        }

        fun showLoading(message: String) {
            loading.visibility = View.VISIBLE
            itemList.visibility = View.GONE
            statusText.visibility = View.VISIBLE
            statusText.text = message
        }

        fun showError(message: String) {
            loading.visibility = View.GONE
            itemList.visibility = View.GONE
            statusText.visibility = View.VISIBLE
            statusText.text = message

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
            ).show()
        }

        /*
         * ============================================================
         * OPEN PLAYER
         * ============================================================
         *
         * Stalker:
         *
         * item.cmd
         *     ↓
         * createLink()
         *     ↓
         * final stream URL
         *     ↓
         * PlayerActivity
         *     ↓
         * Media3 ExoPlayer
         */
        fun playStream(
            name: String,
            cmd: String,
            type: String
        ) {
            if (cmd.isBlank()) {
                Toast.makeText(
                    this,
                    "No playback command found for:\n$name",
                    Toast.LENGTH_LONG
                ).show()

                return
            }

            loading.visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            statusText.text = "Preparing: $name"

            client.createLink(
                portal = portalUrl,
                mac = macAddress,
                cmd = cmd,
                type = type,

                callback = { link ->

                    loading.visibility = View.GONE

                    if (link.isBlank()) {
                        Toast.makeText(
                            this,
                            "Playback link is empty.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@createLink
                    }

                    /*
                     * مهم:
                     *
                     * ما بقيناش نستعملو ACTION_VIEW.
                     *
                     * الرابط كيمشي مباشرة لـ PlayerActivity
                     * اللي غادي تستعمل Media3 ExoPlayer.
                     */
                    try {
                        val playerIntent = Intent(
                            this,
                            PlayerActivity::class.java
                        )

                        playerIntent.putExtra(
                            "STREAM_URL",
                            link
                        )

                        playerIntent.putExtra(
                            "TITLE",
                            name
                        )

                        startActivity(playerIntent)

                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Unable to open internal player: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },

                errorCallback = { message ->

                    loading.visibility = View.GONE
                    statusText.visibility = View.VISIBLE
                    statusText.text = "Playback error: $message"

                    Toast.makeText(
                        this,
                        "Playback error: $message",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }

        // ============================================================
        // LIVE TV
        // ============================================================

        btnLiveTv.setOnClickListener {

            if (!validLogin()) {
                return@setOnClickListener
            }

            showLoading("Loading Live TV...")

            client.fetchChannels(
                portal = portalUrl,
                mac = macAddress,

                callback = { channels ->

                    loading.visibility = View.GONE
                    statusText.visibility = View.VISIBLE

                    if (channels.isEmpty()) {

                        itemList.visibility = View.GONE

                        statusText.text =
                            "No Live TV channels were found."

                        Toast.makeText(
                            this,
                            "No Live TV channels were found.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@fetchChannels
                    }

                    statusText.text =
                        "Live TV — ${channels.size}"

                    val names = channels.map { it.name }

                    itemList.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        names
                    )

                    itemList.visibility = View.VISIBLE

                    itemList.setOnItemClickListener { _, _, position, _ ->

                        if (position < 0 || position >= channels.size) {
                            return@setOnItemClickListener
                        }

                        val channel = channels[position]

                        playStream(
                            name = channel.name,
                            cmd = channel.cmd,
                            type = "itv"
                        )
                    }
                },

                errorCallback = { message ->
                    showError("Live TV error: $message")
                }
            )
        }

        // ============================================================
        // MOVIES / VOD
        // ============================================================

        btnMovies.setOnClickListener {

            if (!validLogin()) {
                return@setOnClickListener
            }

            showLoading("Loading Movies...")

            client.fetchMovies(
                portal = portalUrl,
                mac = macAddress,

                callback = { movies ->

                    loading.visibility = View.GONE
                    statusText.visibility = View.VISIBLE

                    if (movies.isEmpty()) {

                        itemList.visibility = View.GONE

                        statusText.text =
                            "No Movies/VOD were found."

                        Toast.makeText(
                            this,
                            "No Movies/VOD were found.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@fetchMovies
                    }

                    statusText.text =
                        "Movies — ${movies.size}"

                    val names = movies.map { it.name }

                    itemList.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        names
                    )

                    itemList.visibility = View.VISIBLE

                    itemList.setOnItemClickListener { _, _, position, _ ->

                        if (position < 0 || position >= movies.size) {
                            return@setOnItemClickListener
                        }

                        val movie = movies[position]

                        playStream(
                            name = movie.name,
                            cmd = movie.cmd,
                            type = "vod"
                        )
                    }
                },

                errorCallback = { message ->
                    showError("Movies error: $message")
                }
            )
        }

        // ============================================================
        // SERIES
        // ============================================================

        btnSeries.setOnClickListener {

            if (!validLogin()) {
                return@setOnClickListener
            }

            showLoading("Loading Series...")

            client.fetchSeries(
                portal = portalUrl,
                mac = macAddress,

                callback = { series ->

                    loading.visibility = View.GONE
                    statusText.visibility = View.VISIBLE

                    if (series.isEmpty()) {

                        itemList.visibility = View.GONE

                        statusText.text =
                            "No Series were found."

                        Toast.makeText(
                            this,
                            "No Series were found.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@fetchSeries
                    }

                    statusText.text =
                        "Series — ${series.size}"

                    val names = series.map { it.name }

                    itemList.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        names
                    )

                    itemList.visibility = View.VISIBLE

                    /*
                     * Series ماشي تشغيل مباشر.
                     *
                     * Series
                     *   ↓
                     * Seasons
                     *   ↓
                     * Episodes
                     *   ↓
                     * createLink()
                     *   ↓
                     * PlayerActivity
                     *
                     * غادي نبنيو هاد المرحلة من بعد.
                     */
                    itemList.setOnItemClickListener { _, _, position, _ ->

                        if (position < 0 || position >= series.size) {
                            return@setOnItemClickListener
                        }

                        val selectedSeries = series[position]

                        Toast.makeText(
                            this,
                            "Selected: ${selectedSeries.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },

                errorCallback = { message ->
                    showError("Series error: $message")
                }
            )
        }

        // ============================================================
        // SETTINGS / LOGIN
        // ============================================================

        btnSettings.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                )
            )

            finish()
        }

        // ============================================================
        // INITIAL STATE
        // ============================================================

        statusText.visibility = View.GONE
        loading.visibility = View.GONE
        itemList.visibility = View.GONE
    }
}

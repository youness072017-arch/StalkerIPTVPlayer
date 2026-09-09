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

        fun showItems(
            title: String,
            names: List<String>,
            emptyMessage: String
        ) {
            loading.visibility = View.GONE
            statusText.visibility = View.VISIBLE

            if (names.isNotEmpty()) {

                statusText.text = "$title — ${names.size}"

                itemList.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    names
                )

                itemList.visibility = View.VISIBLE

            } else {

                itemList.visibility = View.GONE
                statusText.text = emptyMessage

                Toast.makeText(
                    this,
                    emptyMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
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
                portalUrl = portalUrl,
                macAddress = macAddress
            ) { success, channels, message ->

                runOnUiThread {

                    if (success) {

                        showItems(
                            title = "Live TV",
                            names = channels.map { it.name },
                            emptyMessage = "No Live TV channels were found."
                        )

                    } else {

                        showItems(
                            title = "Live TV",
                            names = emptyList(),
                            emptyMessage = message
                        )
                    }
                }
            }
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
                portalUrl = portalUrl,
                macAddress = macAddress
            ) { success, movies, message ->

                runOnUiThread {

                    if (success) {

                        showItems(
                            title = "Movies",
                            names = movies.map { it.name },
                            emptyMessage = "No Movies/VOD were found."
                        )

                    } else {

                        showItems(
                            title = "Movies",
                            names = emptyList(),
                            emptyMessage = message
                        )
                    }
                }
            }
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
                portalUrl = portalUrl,
                macAddress = macAddress
            ) { success, series, message ->

                runOnUiThread {

                    if (success) {

                        showItems(
                            title = "Series",
                            names = series.map { it.name },
                            emptyMessage = "No Series were found."
                        )

                    } else {

                        showItems(
                            title = "Series",
                            names = emptyList(),
                            emptyMessage = message
                        )
                    }
                }
            }
        }

        // ============================================================
        // SETTINGS
        // ============================================================

        btnSettings.setOnClickListener {

            startActivity(
                Intent(this, MainActivity::class.java)
            )

            finish()
        }

        // Initial state

        statusText.visibility = View.GONE
        loading.visibility = View.GONE
        itemList.visibility = View.GONE
    }
}

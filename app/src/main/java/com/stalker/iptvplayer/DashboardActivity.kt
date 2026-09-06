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

    private lateinit var channelList: ListView
    private lateinit var loading: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val portalUrl = intent.getStringExtra("PORTAL_URL") ?: ""
        val macAddress = intent.getStringExtra("MAC_ADDRESS") ?: ""

        val btnLiveTv = findViewById<Button>(R.id.btn_live_tv)
        val btnMovies = findViewById<Button>(R.id.btn_movies)
        val btnSeries = findViewById<Button>(R.id.btn_series)
        val btnSettings = findViewById<Button>(R.id.btn_settings)

        channelList = findViewById(R.id.channel_list)
        loading = findViewById(R.id.loading)
        statusText = findViewById(R.id.status_text)

        btnLiveTv.setOnClickListener {

            if (portalUrl.isEmpty() || macAddress.isEmpty()) {
                Toast.makeText(
                    this,
                    "Missing portal details. Please re-login.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            loading.visibility = View.VISIBLE
            channelList.visibility = View.GONE
            statusText.visibility = View.VISIBLE
            statusText.text = "Loading Live TV..."

            val client = StalkerClient()

            client.fetchChannels(
                portalUrl = portalUrl,
                macAddress = macAddress
            ) { success, channels, message ->

                runOnUiThread {

                    loading.visibility = View.GONE

                    if (success && channels.isNotEmpty()) {

                        statusText.text =
                            "Live TV — ${channels.size} channels"

                        val names = channels.map { channel ->
                            channel.name
                        }

                        val adapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_list_item_1,
                            names
                        )

                        channelList.adapter = adapter
                        channelList.visibility = View.VISIBLE

                    } else {

                        channelList.visibility = View.GONE
                        statusText.text = message

                        Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        btnMovies.setOnClickListener {
            Toast.makeText(
                this,
                "Movies module coming next.",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnSeries.setOnClickListener {
            Toast.makeText(
                this,
                "Series module coming next.",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnSettings.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
            )
            finish()
        }

        statusText.visibility = View.GONE
        loading.visibility = View.GONE
        channelList.visibility = View.GONE
    }
}

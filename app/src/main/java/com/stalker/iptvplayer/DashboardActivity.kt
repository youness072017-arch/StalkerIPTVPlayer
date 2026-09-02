package com.stalker.iptvplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // استقبال البيانات المرسلة من MainActivity
        val portalUrl = intent.getStringExtra("PORTAL_URL") ?: ""
        val macAddress = intent.getStringExtra("MAC_ADDRESS") ?: ""

        val btnLiveTv = findViewById<Button>(R.id.btn_live_tv)
        val btnMovies = findViewById<Button>(R.id.btn_movies)
        val btnSeries = findViewById<Button>(R.id.btn_series)
        val btnSettings = findViewById<Button>(R.id.btn_settings)

        btnLiveTv.setOnClickListener {
            if (portalUrl.isEmpty() || macAddress.isEmpty()) {
                Toast.makeText(this, "No portal or MAC address provided!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.init
            Toast.makeText(this, "Connecting to Stalker Portal...", Toast.LENGTH_SHORT).show()

            val client = StalkerClient()
            client.authenticateAndFetchChannels(portalUrl, macAddress) { result ->
                runOnUiThread {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                }
            }
        }

        btnMovies.setOnClickListener {
            Toast.makeText(this, "Movies clicked", Toast.LENGTH_SHORT).show()
        }

        btnSeries.setOnClickListener {
            Toast.makeText(this, "Series clicked", Toast.LENGTH_SHORT).show()
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}

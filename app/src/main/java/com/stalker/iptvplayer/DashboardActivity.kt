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

        val btnLiveTv = findViewById<Button>(R.id.btn_live_tv)
        val btnMovies = findViewById<Button>(R.id.btn_movies)
        val btnSeries = findViewById<Button>(R.id.btn_series)
        val btnSettings = findViewById<Button>(R.id.btn_settings)

        // الاتصال بسيرفر Stalker وجلب القنوات عند الضغط على Live TV
        btnLiveTv.setOnClickListener {
            Toast.makeText(this, "Connecting to Stalker Portal...", Toast.LENGTH_SHORT).show()
            
            val portalUrl = "http://portal.example.com" // رابط البورتال (يمكنك تعديله لاحقاً ليأخذ ما كتبه المستخدم)
            val macAddress = "00:1A:79:00:00:00" // الـ MAC Address
            
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

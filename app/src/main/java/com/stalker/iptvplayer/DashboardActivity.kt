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

        btnLiveTv.setOnClickListener {
            Toast.makeText(this, "Live TV clicked", Toast.LENGTH_SHORT).show()
        }

        btnMovies.setOnClickListener {
            Toast.makeText(this, "Movies clicked", Toast.LENGTH_SHORT).show()
        }

        btnSeries.setOnClickListener {
            Toast.makeText(this, "Series clicked", Toast.LENGTH_SHORT).show()
        }

        // زر الإعدادات كيرجعك لصفحة تسجيل الدخول باش تغير الماك أدرس
        btnSettings.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}

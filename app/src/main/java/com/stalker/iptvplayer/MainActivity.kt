package com.stalker.iptvplayer

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnLiveTv = findViewById<Button>(R.id.btn_live_tv)
        val btnMovies = findViewById<Button>(R.id.btn_movies)
        val btnSeries = findViewById<Button>(R.id.btn_series)

        btnLiveTv.setOnClickListener {
            Toast.makeText(this, "Live TV clicked", Toast.LENGTH_SHORT).show()
        }

        btnMovies.setOnClickListener {
            Toast.makeText(this, "Movies clicked", Toast.LENGTH_SHORT).show()
        }

        btnSeries.setOnClickListener {
            Toast.makeText(this, "Series clicked", Toast.LENGTH_SHORT).show()
        }
    }
}

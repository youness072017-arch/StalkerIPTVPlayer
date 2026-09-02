package com.stalker.iptvplayer

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etPortalUrl = findViewById<EditText>(R.id.et_portal_url)
        val etMacAddress = findViewById<EditText>(R.id.et_mac_address)
        val btnConnect = findViewById<Button>(R.id.btn_connect)

        btnConnect.setOnClickListener {
            val portalUrl = etPortalUrl.text.toString().trim()
            val macAddress = etMacAddress.text.toString().trim()

            if (portalUrl.isEmpty() || macAddress.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Connecting to Portal...", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

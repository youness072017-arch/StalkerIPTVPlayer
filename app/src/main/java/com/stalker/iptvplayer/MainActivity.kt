package com.stalker.iptvplayer

import android.content.Context
import android.content.Intent
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

        // استرجاع البيانات المحفوظة مسبقاً إن وجدت (Auto-fill)
        val sharedPrefs = getSharedPreferences("IPTV_Prefs", Context.MODE_PRIVATE)
        val savedPortal = sharedPrefs.getString("PORTAL_URL", "")
        val savedMac = sharedPrefs.getString("MAC_ADDRESS", "")

        if (!savedPortal.isNullOrEmpty()) etPortalUrl.setText(savedPortal)
        if (!savedMac.isNullOrEmpty()) etMacAddress.setText(savedMac)

        btnConnect.setOnClickListener {
            val portalUrl = etPortalUrl.text.toString().trim()
            val macAddress = etMacAddress.text.toString().trim()

            if (portalUrl.isEmpty() || macAddress.isEmpty()) {
                Toast.makeText(this, "Please enter Portal URL and MAC Address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // حفظ البيانات محلياً لتفادي إعادة كتابتها
            sharedPrefs.edit().apply {
                putString("PORTAL_URL", portalUrl)
                putString("MAC_ADDRESS", macAddress)
                apply()
            }

            val intent = Intent(this, DashboardActivity::class.java).apply {
                putExtra("PORTAL_URL", portalUrl)
                putExtra("MAC_ADDRESS", macAddress)
            }
            startActivity(intent)
            finish()
        }
    }
}

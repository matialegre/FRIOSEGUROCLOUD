package com.parametican.reeferserver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnOpenBrowser: Button
    
    private var serverService: ReeferServerService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        updateStatus()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvUrl = findViewById(R.id.tvUrl)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnOpenBrowser = findViewById(R.id.btnOpenBrowser)
        
        btnStart.setOnClickListener {
            startServer()
        }
        
        btnStop.setOnClickListener {
            stopServer()
        }
        
        btnOpenBrowser.setOnClickListener {
            openBrowser()
        }
    }

    private fun startServer() {
        val intent = Intent(this, ReeferServerService::class.java)
        startForegroundService(intent)
        updateStatus()
    }

    private fun stopServer() {
        val intent = Intent(this, ReeferServerService::class.java)
        stopService(intent)
        updateStatus()
    }

    private fun updateStatus() {
        if (ReeferServerService.isRunning) {
            val ip = getLocalIpAddress()
            val url = "http://$ip:8080"
            tvStatus.text = "🟢 SERVIDOR ACTIVO"
            tvUrl.text = "URL: $url\n\nLos ESP32 deben enviar datos a:\n$url/api/data"
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            btnOpenBrowser.isEnabled = true
        } else {
            tvStatus.text = "🔴 SERVIDOR DETENIDO"
            tvUrl.text = "Iniciá el servidor para obtener la URL"
            btnStart.isEnabled = true
            btnStop.isEnabled = false
            btnOpenBrowser.isEnabled = false
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            // Ignorar
        }
        return "0.0.0.0"
    }

    private fun openBrowser() {
        val ip = getLocalIpAddress()
        val url = "http://$ip:8080"
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        startActivity(intent)
    }
}

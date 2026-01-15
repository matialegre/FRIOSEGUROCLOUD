package com.parametican.alertarift

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class LoginActivity : AppCompatActivity() {
    
    companion object {
        // UDP Discovery - debe coincidir con el ESP32
        const val UDP_DISCOVERY_PORT = 5555
        const val UDP_DISCOVERY_MAGIC = "REEFER_DISCOVER"
        const val UDP_DISCOVERY_RESPONSE = "REEFER_HERE"
        
        // Supabase para obtener IP del ESP
        const val SUPABASE_URL = "https://zojiknjxaohxbetxnjhv.supabase.co"
        const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inpvamtrbmp4YW9oeGJldHhuamh2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcxNTg2MzMsImV4cCI6MjA2MjczNDYzM30.QhMQ7hXX7wFBeh0V-ykXhvLgXnJPOE9d3mFN3CdJqYo"
        
        // Device IDs conocidos para buscar
        val KNOWN_DEVICE_IDS = listOf("REEFER_01_SCZ", "REEFER_02_SCZ", "REEFER_DEV_BHI")
    }
    
    private var isSearching = AtomicBoolean(false)
    private var foundDevice = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Verificar si ya está configurado
        val prefs = getSharedPreferences("parametican", MODE_PRIVATE)
        if (prefs.getBoolean("logged_in", false)) {
            goToMain()
            return
        }
        
        setContentView(R.layout.activity_login)

        val etServerIp = findViewById<EditText>(R.id.etServerIp)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnAutoConnect = findViewById<Button>(R.id.btnAutoConnect)
        val cardLogin = findViewById<LinearLayout>(R.id.cardLogin)
        val tvStatus = findViewById<TextView>(R.id.tvConnectionStatus)

        // Animación de entrada
        val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        slideUp.duration = 500
        cardLogin.startAnimation(slideUp)

        // Mostrar reefer.local por defecto
        etServerIp.setText("reefer.local")

        // Auto-conectar: mDNS + UDP Discovery
        btnAutoConnect.setOnClickListener {
            if (isSearching.get()) {
                Toast.makeText(this, "Ya hay una búsqueda en progreso...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = "🔍 Buscando Reefer en la red..."
            searchForDevice(etServerIp, tvStatus)
        }

        btnConnect.setOnClickListener {
            val ip = etServerIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP del servidor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tvStatus.text = "🔄 Conectando..."
            testConnection(ip, tvStatus)
        }
        
        tvStatus.text = "Presiona el botón verde para buscar tu Reefer"
    }
    
    /**
     * Obtiene la IP del ESP desde Supabase (si hay internet)
     */
    private fun getIpFromSupabase(): String? {
        try {
            for (deviceId in KNOWN_DEVICE_IDS) {
                val url = URL("$SUPABASE_URL/rest/v1/devices?device_id=eq.$deviceId&select=ip_address,is_online")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    
                    // Parse simple JSON: [{"ip_address":"192.168.x.x","is_online":true}]
                    if (response.contains("\"is_online\":true") && response.contains("\"ip_address\":\"")) {
                        val ipStart = response.indexOf("\"ip_address\":\"") + 14
                        val ipEnd = response.indexOf("\"", ipStart)
                        if (ipStart > 14 && ipEnd > ipStart) {
                            val ip = response.substring(ipStart, ipEnd)
                            if (ip.isNotEmpty() && ip != "null") {
                                android.util.Log.d("LoginActivity", "Supabase: $deviceId está online en $ip")
                                return ip
                            }
                        }
                    }
                } else {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "Error consultando Supabase: ${e.message}")
        }
        return null
    }
    
    /**
     * Busca el dispositivo ESP32 en la red:
     * 1. Primero consulta Supabase para obtener la IP (si hay internet)
     * 2. Si falla, intenta mDNS (reefer.local)
     * 3. Si falla, usa UDP Broadcast Discovery
     */
    private fun searchForDevice(etServerIp: EditText, tvStatus: TextView) {
        isSearching.set(true)
        foundDevice.set(false)
        
        thread {
            // Paso 1: Consultar Supabase para obtener IP del ESP
            runOnUiThread { tvStatus.text = "☁️ Consultando nube..." }
            
            val cloudIp = getIpFromSupabase()
            if (cloudIp != null) {
                runOnUiThread { tvStatus.text = "🔍 Verificando $cloudIp (desde nube)..." }
                if (tryConnect(cloudIp)) {
                    onDeviceFound(cloudIp, etServerIp, tvStatus)
                    return@thread
                }
            }
            
            // Paso 2: Intentar mDNS
            runOnUiThread { tvStatus.text = "🔍 Intentando mDNS (reefer.local)..." }
            
            if (tryConnect("reefer.local")) {
                onDeviceFound("reefer.local", etServerIp, tvStatus)
                return@thread
            }
            
            // Paso 3: UDP Broadcast Discovery
            runOnUiThread { tvStatus.text = "🔍 Enviando broadcast UDP..." }
            
            val discoveredIp = udpDiscovery()
            if (discoveredIp != null) {
                // Verificar que responde HTTP
                runOnUiThread { tvStatus.text = "🔍 Verificando $discoveredIp..." }
                if (tryConnect(discoveredIp)) {
                    onDeviceFound(discoveredIp, etServerIp, tvStatus)
                    return@thread
                }
            }
            
            // No encontrado
            isSearching.set(false)
            runOnUiThread {
                tvStatus.text = "❌ No se encontró Reefer.\n\nOpciones:\n• Verifica que el ESP32 esté encendido\n• Asegurate de estar en la misma red WiFi\n• Ingresa la IP manualmente"
            }
        }
    }
    
    /**
     * UDP Broadcast Discovery
     * Envía un mensaje broadcast y espera respuesta del ESP32
     * Funciona en cualquier red, sin importar el rango de IPs
     */
    private fun udpDiscovery(): String? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 5000  // 5 segundos de timeout
            
            // Enviar broadcast
            val message = UDP_DISCOVERY_MAGIC.toByteArray()
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(message, message.size, broadcastAddress, UDP_DISCOVERY_PORT)
            
            // Enviar 3 veces para mayor confiabilidad
            repeat(3) {
                socket.send(sendPacket)
                Thread.sleep(100)
            }
            
            // Esperar respuesta
            val buffer = ByteArray(256)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            
            // Intentar recibir varias veces
            repeat(3) {
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    
                    // Formato esperado: "REEFER_HERE|192.168.1.100|REEFER-01|Reefer Principal"
                    if (response.startsWith(UDP_DISCOVERY_RESPONSE)) {
                        val parts = response.split("|")
                        if (parts.size >= 2) {
                            val ip = parts[1]
                            android.util.Log.d("LoginActivity", "UDP Discovery: encontrado en $ip")
                            return ip
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Timeout, intentar de nuevo
                }
            }
            
            return null
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "UDP Discovery error: ${e.message}")
            return null
        } finally {
            socket?.close()
        }
    }
    
    /**
     * Intenta conectar a una IP y verificar si es el ESP32
     */
    private fun tryConnect(address: String): Boolean {
        return try {
            val url = URL("http://$address/api/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            
            // Verificar que sea realmente nuestro dispositivo
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                // Verificar que tenga el formato esperado
                response.contains("sensor") && response.contains("system")
            } else {
                conn.disconnect()
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Llamado cuando se encuentra el dispositivo
     */
    private fun onDeviceFound(address: String, etServerIp: EditText, tvStatus: TextView) {
        if (foundDevice.getAndSet(true)) return  // Evitar duplicados
        isSearching.set(false)
        
        runOnUiThread {
            etServerIp.setText(address)
            tvStatus.text = "✅ Reefer encontrado en $address"
            
            // Guardar y conectar
            getSharedPreferences("parametican", MODE_PRIVATE).edit()
                .putString("server_ip", address)
                .putBoolean("logged_in", true)
                .apply()
            
            // Esperar 1 segundo y conectar
            etServerIp.postDelayed({ goToMain() }, 1000)
        }
    }
    
    private fun testConnection(ip: String, tvStatus: TextView) {
        thread {
            try {
                val url = URL("http://$ip/api/status")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                conn.disconnect()
                
                if (code == 200) {
                    runOnUiThread {
                        tvStatus.text = "✅ Conectado"
                        getSharedPreferences("parametican", MODE_PRIVATE).edit()
                            .putString("server_ip", ip)
                            .putBoolean("logged_in", true)
                            .apply()
                        goToMain()
                    }
                } else {
                    runOnUiThread { tvStatus.text = "❌ Error: código $code" }
                }
            } catch (e: Exception) {
                runOnUiThread { tvStatus.text = "❌ Error: ${e.message}" }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {
    
    companion object {
        // UDP Discovery - debe coincidir con el ESP32
        const val UDP_DISCOVERY_PORT = 5555
        const val UDP_DISCOVERY_MAGIC = "REEFER_DISCOVER"
        const val UDP_DISCOVERY_RESPONSE = "REEFER_HERE"
        
        // Supabase para obtener IP del ESP - DESARROLLO usa el mismo Supabase que CLOUD
        const val SUPABASE_URL = "https://xhdeacnwdzvkivfjzard.supabase.co"
        const val SUPABASE_ANON_KEY = "sb_publishable_JhTUv1X2LHMBVILUaysJ3g_Ho11zu-Q"
        
        // Device ID específico para DESARROLLO
        const val TARGET_DEVICE_ID = "REEFER_DEV_BHI"
        
        // Rango de IPs para escaneo directo (192.168.0.x)
        const val SCAN_IP_PREFIX = "192.168.0."
        const val SCAN_IP_START = 1
        const val SCAN_IP_END = 254
        
        // IP conocida del dispositivo de desarrollo
        const val KNOWN_DEV_IP = "192.168.0.104"
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
     * Obtiene la IP del ESP desde Supabase para REEFER_DEV_BHI
     */
    private fun getIpFromSupabase(): String? {
        try {
            val url = URL("$SUPABASE_URL/rest/v1/devices?device_id=eq.$TARGET_DEVICE_ID&select=ip_address,is_online")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                
                // Parse simple JSON: [{"ip_address":"192.168.x.x","is_online":true}]
                if (response.contains("\"ip_address\":\"")) {
                    val ipStart = response.indexOf("\"ip_address\":\"") + 14
                    val ipEnd = response.indexOf("\"", ipStart)
                    if (ipStart > 14 && ipEnd > ipStart) {
                        val ip = response.substring(ipStart, ipEnd)
                        if (ip.isNotEmpty() && ip != "null") {
                            android.util.Log.d("LoginActivity", "Supabase: $TARGET_DEVICE_ID IP = $ip")
                            return ip
                        }
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "Error consultando Supabase: ${e.message}")
        }
        return null
    }
    
    /**
     * BÚSQUEDA EN PARALELO:
     * - Thread 1: mDNS (reefer.local)
     * - Thread 2: IP conocida (192.168.0.104) + escaneo rango 192.168.0.x
     * - Thread 3: Supabase (REEFER_DEV_BHI)
     * - Thread 4: UDP Broadcast
     * 
     * El primero que encuentre el dispositivo gana.
     */
    private fun searchForDevice(etServerIp: EditText, tvStatus: TextView) {
        isSearching.set(true)
        foundDevice.set(false)
        
        runOnUiThread { tvStatus.text = "🔍 Buscando en PARALELO..." }
        
        // Thread 1: mDNS
        thread {
            android.util.Log.d("LoginActivity", "[PARALELO] Iniciando mDNS...")
            if (!foundDevice.get() && tryConnect("reefer.local")) {
                onDeviceFound("reefer.local", etServerIp, tvStatus)
            }
        }
        
        // Thread 2: IP conocida primero, luego escaneo de rango
        thread {
            android.util.Log.d("LoginActivity", "[PARALELO] Probando IP conocida $KNOWN_DEV_IP...")
            
            // Primero probar la IP conocida
            if (!foundDevice.get() && tryConnect(KNOWN_DEV_IP)) {
                onDeviceFound(KNOWN_DEV_IP, etServerIp, tvStatus)
                return@thread
            }
            
            // Si no funcionó, escanear el rango
            android.util.Log.d("LoginActivity", "[PARALELO] Escaneando rango ${SCAN_IP_PREFIX}x...")
            for (i in SCAN_IP_START..SCAN_IP_END) {
                if (foundDevice.get()) break
                val ip = "$SCAN_IP_PREFIX$i"
                if (ip != KNOWN_DEV_IP && tryConnectFast(ip)) {
                    onDeviceFound(ip, etServerIp, tvStatus)
                    break
                }
            }
        }
        
        // Thread 3: Supabase
        thread {
            android.util.Log.d("LoginActivity", "[PARALELO] Consultando Supabase para $TARGET_DEVICE_ID...")
            val cloudIp = getIpFromSupabase()
            if (cloudIp != null && !foundDevice.get()) {
                android.util.Log.d("LoginActivity", "[PARALELO] Supabase dice IP = $cloudIp, verificando...")
                if (tryConnect(cloudIp)) {
                    onDeviceFound(cloudIp, etServerIp, tvStatus)
                }
            }
        }
        
        // Thread 4: UDP Broadcast
        thread {
            android.util.Log.d("LoginActivity", "[PARALELO] Enviando UDP broadcast...")
            val discoveredIp = udpDiscovery()
            if (discoveredIp != null && !foundDevice.get()) {
                android.util.Log.d("LoginActivity", "[PARALELO] UDP encontró $discoveredIp, verificando...")
                if (tryConnect(discoveredIp)) {
                    onDeviceFound(discoveredIp, etServerIp, tvStatus)
                }
            }
        }
        
        // Thread de timeout: si después de 15 segundos no encontró nada
        thread {
            Thread.sleep(15000)
            if (!foundDevice.get()) {
                isSearching.set(false)
                runOnUiThread {
                    tvStatus.text = "❌ No se encontró Reefer DESARROLLO.\n\nOpciones:\n• Verifica que el ESP32 esté encendido\n• Asegurate de estar en la misma red WiFi\n• Ingresa la IP manualmente (ej: $KNOWN_DEV_IP)"
                }
            }
        }
    }
    
    /**
     * Conexión rápida para escaneo de rango (timeout corto)
     */
    private fun tryConnectFast(address: String): Boolean {
        return try {
            val url = URL("http://$address/api/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 500  // Solo 500ms para escaneo rápido
            conn.readTimeout = 500
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            false
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

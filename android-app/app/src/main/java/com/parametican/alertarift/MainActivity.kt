package com.parametican.alertarift

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var etServerIp: EditText
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvTemp1: TextView
    private lateinit var tvTemp2: TextView
    private lateinit var tvDoorStatus: TextView
    private lateinit var tvRelayStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvWifiSignal: TextView
    private lateinit var tvInternet: TextView
    private lateinit var tvAlertMessage: TextView
    private lateinit var tvDeviceIp: TextView
    private lateinit var tvConfigTempCritical: TextView
    private lateinit var tvConfigAlertDelay: TextView
    private lateinit var tvConfigDefrostCooldown: TextView
    private lateinit var tvConfigDefrostRelay: TextView
    private lateinit var tvDefrostSignal: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnConfig: Button
    private lateinit var btnSilence: Button
    private lateinit var btnRelayOff: Button
    private lateinit var btnLogout: Button
    private lateinit var alertBanner: LinearLayout
    private lateinit var simBadge: LinearLayout
    private lateinit var reefersContainer: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    
    // 6 Reefers data
    data class ReeferInfo(val id: String, val name: String, var online: Boolean, var temp: Float, var alertActive: Boolean)
    private val reefers = mutableListOf(
        ReeferInfo("REEFER-01", "Reefer Principal", true, -22.5f, false),
        ReeferInfo("REEFER-02", "Reefer Carnes", false, -20.0f, false),
        ReeferInfo("REEFER-03", "Reefer Lácteos", false, -18.5f, false),
        ReeferInfo("REEFER-04", "Reefer Verduras", false, -15.0f, false),
        ReeferInfo("REEFER-05", "Reefer Bebidas", false, -5.0f, false),
        ReeferInfo("REEFER-06", "Reefer Backup", false, -25.0f, false)
    )

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val temp = it.getFloatExtra("temperature", -999f)
                val temp1 = it.getFloatExtra("temp1", -999f)
                val temp2 = it.getFloatExtra("temp2", -999f)
                val doorOpen = it.getBooleanExtra("door_open", false)
                val relayOn = it.getBooleanExtra("relay_on", false)
                val alertActive = it.getBooleanExtra("alert_active", false)
                val alertAcknowledged = it.getBooleanExtra("alert_acknowledged", false)
                val alertMsg = it.getStringExtra("alert_message") ?: ""
                val uptime = it.getIntExtra("uptime", 0)
                val rssi = it.getIntExtra("rssi", 0)
                val internet = it.getBooleanExtra("internet", false)
                val location = it.getStringExtra("location") ?: ""
                val deviceIp = it.getStringExtra("device_ip") ?: ""
                val defrostMode = it.getBooleanExtra("defrost_mode", false)
                val cooldownMode = it.getBooleanExtra("cooldown_mode", false)
                val cooldownRemainingSec = it.getIntExtra("cooldown_remaining_sec", 0)

                updateDisplay(temp, temp1, temp2, doorOpen, relayOn, alertActive, alertAcknowledged, alertMsg, uptime, rssi, internet, location, deviceIp, defrostMode, cooldownMode, cooldownRemainingSec)
                
                // Actualizar temperatura del Reefer Principal con la real
                if (temp > -55 && temp < 125) {
                    reefers[0].temp = temp
                    reefers[0].alertActive = alertActive
                    populateReefers()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSavedIp()
        requestNotificationPermission()
        setupButtons()

        // Auto-iniciar monitoreo
        autoStartMonitoring()
    }
    
    private fun autoStartMonitoring() {
        val prefs = getSharedPreferences("parametican", MODE_PRIVATE)
        val ip = prefs.getString("server_ip", "reefer.local") ?: "reefer.local"
        
        val intent = Intent(this, MonitorService::class.java)
        intent.putExtra("server_ip", ip)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateUIState(true)
        
        // Cargar valores configurados
        loadConfigValues(ip)
    }
    
    private fun loadConfigValues(ip: String) {
        thread {
            try {
                val url = URL("http://$ip/api/config")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = org.json.JSONObject(response)
                runOnUiThread {
                    val tempCritical = json.optDouble("temp_critical", -10.0)
                    val alertDelaySec = json.optInt("alert_delay_sec", 300)
                    val defrostCooldownSec = json.optInt("defrost_cooldown_sec", 1800)
                    val defrostNC = json.optBoolean("defrost_relay_nc", false)
                    
                    tvConfigTempCritical.text = "🌡️ Temp. Crítica: ${tempCritical}°C"
                    tvConfigAlertDelay.text = "⏱️ Tiempo espera: ${alertDelaySec / 60} min"
                    tvConfigDefrostCooldown.text = "🧊 Post-descongelación: ${defrostCooldownSec / 60} min"
                    tvConfigDefrostRelay.text = if (defrostNC) 
                        "🔌 Relé descong.: Normal Cerrado (NC)" 
                    else 
                        "🔌 Relé descong.: Normal Abierto (NO)"
                }
            } catch (e: Exception) {
                // Silenciar error, se cargará cuando haya conexión
            }
        }
    }

    private fun initViews() {
        etServerIp = findViewById(R.id.etServerIp)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvTemp1 = findViewById(R.id.tvTemp1)
        tvTemp2 = findViewById(R.id.tvTemp2)
        tvDoorStatus = findViewById(R.id.tvDoorStatus)
        tvRelayStatus = findViewById(R.id.tvRelayStatus)
        tvLocation = findViewById(R.id.tvLocation)
        tvUptime = findViewById(R.id.tvUptime)
        tvWifiSignal = findViewById(R.id.tvWifiSignal)
        tvInternet = findViewById(R.id.tvInternet)
        tvAlertMessage = findViewById(R.id.tvAlertMessage)
        tvDeviceIp = findViewById(R.id.tvDeviceIp)
        tvConfigTempCritical = findViewById(R.id.tvConfigTempCritical)
        tvConfigAlertDelay = findViewById(R.id.tvConfigAlertDelay)
        tvConfigDefrostCooldown = findViewById(R.id.tvConfigDefrostCooldown)
        tvConfigDefrostRelay = findViewById(R.id.tvConfigDefrostRelay)
        tvDefrostSignal = findViewById(R.id.tvDefrostSignal)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnConfig = findViewById(R.id.btnConfig)
        btnSilence = findViewById(R.id.btnSilence)
        btnRelayOff = findViewById(R.id.btnRelayOff)
        btnLogout = findViewById(R.id.btnLogout)
        simBadge = findViewById(R.id.simBadge)
        reefersContainer = findViewById(R.id.reefersContainer)
        
        // Populate Reefers list
        populateReefers()
        alertBanner = findViewById(R.id.alertBanner)
    }

    private fun loadSavedIp() {
        val prefs = getSharedPreferences("parametican", MODE_PRIVATE)
        val savedIp = prefs.getString("server_ip", "")
        if (savedIp.isNullOrEmpty()) {
            // No hay IP guardada, intentar descubrir automáticamente
            etServerIp.setText("Buscando...")
            discoverEsp32()
        } else {
            etServerIp.setText(savedIp)
        }
    }
    
    // Device ID para buscar en Supabase (REEFER_01_SCZ para Santa Cruz)
    private val TARGET_DEVICE_ID = "REEFER_01_SCZ"
    
    @Volatile private var discoveredIp: String? = null
    
    private fun discoverEsp32() {
        Toast.makeText(this, "🔍 Buscando Reefer en la red (paralelo)...", Toast.LENGTH_SHORT).show()
        discoveredIp = null
        
        // Ejecutar 3 métodos en PARALELO
        val threads = mutableListOf<Thread>()
        
        // Método 1: mDNS (reefer.local)
        threads.add(thread {
            tryMdns()
        })
        
        // Método 2: Consultar Supabase por IP del dispositivo
        threads.add(thread {
            trySupabaseIp()
        })
        
        // Método 3: Escanear IPs conocidas y rangos Santa Cruz
        threads.add(thread {
            tryScanKnownIps()
        })
        
        // Esperar a que alguno encuentre o todos terminen
        thread {
            val startTime = System.currentTimeMillis()
            val timeout = 15000L // 15 segundos máximo
            
            while (discoveredIp == null && 
                   System.currentTimeMillis() - startTime < timeout &&
                   threads.any { it.isAlive }) {
                Thread.sleep(200)
            }
            
            // Cancelar threads restantes
            threads.forEach { if (it.isAlive) it.interrupt() }
            
            val finalIp = discoveredIp
            runOnUiThread {
                if (finalIp != null) {
                    etServerIp.setText(finalIp)
                    getSharedPreferences("parametican", MODE_PRIVATE)
                        .edit().putString("server_ip", finalIp).apply()
                    Toast.makeText(this, "✅ Reefer encontrado: $finalIp", Toast.LENGTH_SHORT).show()
                } else {
                    etServerIp.setText("")
                    etServerIp.hint = "IP no encontrada - ingresa manualmente"
                    Toast.makeText(this, "❌ No se encontró Reefer en la red", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun tryMdns() {
        if (discoveredIp != null) return
        try {
            val url = URL("http://reefer.local/api/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                if (response.contains("sensor") && response.contains("system")) {
                    discoveredIp = "reefer.local"
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            // mDNS no disponible
        }
    }
    
    private fun trySupabaseIp() {
        if (discoveredIp != null) return
        try {
            // Consultar Supabase para obtener la IP del dispositivo
            val supabaseUrl = "https://sxjmqxwdqdicxcoascks.supabase.co/rest/v1/device_status?device_id=eq.$TARGET_DEVICE_ID&select=local_ip"
            val url = URL(supabaseUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InN4am1xeHdkcWRpY3hjb2FzY2tzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcxNTMwNzcsImV4cCI6MjA2MjcyOTA3N30.lPvnJFxVWjU8CrVPSsNPPHxnBz2xRc8VLvXNfCPKbQU")
            conn.setRequestProperty("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InN4am1xeHdkcWRpY3hjb2FzY2tzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcxNTMwNzcsImV4cCI6MjA2MjcyOTA3N30.lPvnJFxVWjU8CrVPSsNPPHxnBz2xRc8VLvXNfCPKbQU")
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                // Parse JSON para obtener local_ip
                val ipMatch = Regex("\"local_ip\"\\s*:\\s*\"([^\"]+)\"").find(response)
                val ip = ipMatch?.groupValues?.get(1)
                if (!ip.isNullOrEmpty() && ip != "null") {
                    // Verificar que la IP responde
                    if (testReeferIp(ip)) {
                        discoveredIp = ip
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            // Error consultando Supabase
        }
    }
    
    private fun tryScanKnownIps() {
        if (discoveredIp != null) return
        
        // IPs conocidas + rangos Santa Cruz (192.168.224.0 - 192.168.227.255)
        val knownIps = mutableListOf(
            // IPs comunes
            "192.168.1.100", "192.168.1.101", "192.168.0.100", "192.168.0.101",
            "192.168.4.1",  // AP mode
            "10.0.0.100",
            // Santa Cruz específicas
            "192.168.225.200", "192.168.226.180"
        )
        
        // Agregar rangos Santa Cruz (192.168.224-227.x) - IPs más probables
        for (thirdOctet in 224..227) {
            for (lastOctet in listOf(1, 100, 101, 150, 180, 200, 254)) {
                knownIps.add("192.168.$thirdOctet.$lastOctet")
            }
        }
        
        // Probar en paralelo con threads
        val executor = java.util.concurrent.Executors.newFixedThreadPool(10)
        val futures = knownIps.map { ip ->
            executor.submit<String?> {
                if (discoveredIp != null) return@submit null
                if (testReeferIp(ip)) ip else null
            }
        }
        
        for (future in futures) {
            if (discoveredIp != null) break
            try {
                val result = future.get(2, java.util.concurrent.TimeUnit.SECONDS)
                if (result != null && discoveredIp == null) {
                    discoveredIp = result
                }
            } catch (e: Exception) {
                // Timeout o error
            }
        }
        
        executor.shutdownNow()
        
        // Si aún no encontramos, escanear subred actual
        if (discoveredIp == null) {
            scanCurrentSubnet()
        }
    }
    
    private fun testReeferIp(ip: String): Boolean {
        return try {
            val url = URL("http://$ip/api/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            val success = if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                response.contains("sensor") && response.contains("system")
            } else false
            conn.disconnect()
            success
        } catch (e: Exception) {
            false
        }
    }
    
    private fun scanCurrentSubnet() {
        if (discoveredIp != null) return
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            @Suppress("DEPRECATION")
            val ipInt = wifiInfo.ipAddress
            
            if (ipInt == 0) return
            
            val baseIp = String.format(
                "%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff
            )
            
            // Escanear rangos comunes en paralelo
            val executor = java.util.concurrent.Executors.newFixedThreadPool(20)
            val ipsToScan = mutableListOf<String>()
            
            for (lastOctet in listOf(1, 100, 101, 102, 150, 180, 200, 254)) {
                ipsToScan.add("$baseIp.$lastOctet")
            }
            // Agregar rango 100-120
            for (lastOctet in 100..120) {
                if (!ipsToScan.contains("$baseIp.$lastOctet")) {
                    ipsToScan.add("$baseIp.$lastOctet")
                }
            }
            
            val futures = ipsToScan.map { ip ->
                executor.submit<String?> {
                    if (discoveredIp != null) return@submit null
                    if (testReeferIp(ip)) ip else null
                }
            }
            
            for (future in futures) {
                if (discoveredIp != null) break
                try {
                    val result = future.get(1, java.util.concurrent.TimeUnit.SECONDS)
                    if (result != null && discoveredIp == null) {
                        discoveredIp = result
                    }
                } catch (e: Exception) {
                    // Continuar
                }
            }
            
            executor.shutdownNow()
        } catch (e: Exception) {
            // Error
        }
    }

    private fun setupButtons() {
        val prefs = getSharedPreferences("parametican", MODE_PRIVATE)

        btnStart.setOnClickListener {
            val ip = etServerIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP del servidor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString("server_ip", ip).apply()

            val intent = Intent(this, MonitorService::class.java)
            intent.putExtra("server_ip", ip)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            updateUIState(true)
            Toast.makeText(this, "✅ Monitoreo iniciado", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, MonitorService::class.java))
            updateUIState(false)
            Toast.makeText(this, "⏹ Monitoreo detenido", Toast.LENGTH_SHORT).show()
        }

        btnConfig.setOnClickListener {
            showConfigDialog()
        }

        btnSilence.setOnClickListener {
            silenceAlert()
        }
        
        btnRelayOff.setOnClickListener {
            turnOffRelay()
        }
        
        btnLogout.setOnClickListener {
            showSettingsMenu()
        }
        
        // Notifications buttons
        findViewById<Button>(R.id.btnConfigTelegram).setOnClickListener {
            showTelegramConfigDialog()
        }
        
        findViewById<Button>(R.id.btnConfigWhatsApp).setOnClickListener {
            showWhatsAppInfo()
        }
        
        // Services buttons
        findViewById<Button>(R.id.btnServiceMultiReefer).setOnClickListener {
            showServiceInfo("Multi-Reefer", "Conecta hasta 10 unidades Reefer a un solo panel de control.\n\nIdeal para:\n• Múltiples cámaras frigoríficas\n• Diferentes ubicaciones\n• Control centralizado\n\nPrecio: Consultar")
        }
        
        findViewById<Button>(R.id.btnServiceCloud).setOnClickListener {
            showServiceInfo("Dashboard en la Nube", "Accede a tus datos desde cualquier lugar del mundo.\n\n• Historial completo de temperaturas\n• Gráficos y estadísticas\n• Exportación de reportes\n• Acceso multi-usuario\n\nPrecio: USD 29/mes")
        }
        
        findViewById<Button>(R.id.btnServiceReports).setOnClickListener {
            showServiceInfo("Reportes Automáticos", "Recibe reportes diarios/semanales automáticos.\n\n• Resumen de temperaturas\n• Alertas del período\n• Tiempo de puerta abierta\n• Cumplimiento de normas\n\nPrecio: USD 15/mes")
        }
        
        findViewById<Button>(R.id.btnServiceSupport).setOnClickListener {
            showServiceInfo("Soporte 24/7", "Asistencia técnica las 24 horas.\n\n• Soporte telefónico\n• Monitoreo remoto\n• Mantenimiento preventivo\n• Respuesta en menos de 1 hora\n\nPrecio: USD 99/mes")
        }
        
        // Starlink button
        findViewById<Button>(R.id.btnStarlinkInfo).setOnClickListener {
            showStarlinkInfo()
        }
    }
    
    private fun showTelegramConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_telegram, null)
        val etChatId = dialogView.findViewById<EditText>(R.id.etTelegramChatId)
        
        val prefs = getSharedPreferences("parametican", MODE_PRIVATE)
        etChatId.setText(prefs.getString("telegram_chat_id", ""))
        
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("📲 Configurar Telegram")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val chatId = etChatId.text.toString().trim()
                prefs.edit().putString("telegram_chat_id", chatId).apply()
                Toast.makeText(this, "✅ Chat ID guardado", Toast.LENGTH_SHORT).show()
                
                // Enviar al servidor
                saveTelegramConfig(chatId)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun saveTelegramConfig(chatId: String) {
        val ip = etServerIp.text.toString().trim()
        thread {
            try {
                val url = URL("http://$ip/api/config")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.outputStream.write("""{"telegram_chat_id":"$chatId"}""".toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
    
    private fun showWhatsAppInfo() {
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("💬 WhatsApp Business API")
            .setMessage("Las notificaciones por WhatsApp requieren una suscripción adicional.\n\n" +
                    "Incluye:\n" +
                    "• Alertas instantáneas a WhatsApp\n" +
                    "• Múltiples destinatarios\n" +
                    "• Mensajes con imágenes\n" +
                    "• Confirmación de lectura\n\n" +
                    "Precio: USD 25/mes\n\n" +
                    "¿Deseas solicitar información?")
            .setPositiveButton("📞 Contactar") { _, _ ->
                Toast.makeText(this, "Contacto: info@parametican.com", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showServiceInfo(title: String, description: String) {
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("🛠️ $title")
            .setMessage(description)
            .setPositiveButton("📞 Solicitar") { _, _ ->
                Toast.makeText(this, "Contacto: info@parametican.com", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
    
    private fun showStarlinkInfo() {
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("🛰️ Starlink Integration")
            .setMessage("Internet satelital de alta velocidad para zonas remotas.\n\n" +
                    "INCLUYE:\n" +
                    "• Kit Starlink (antena + router)\n" +
                    "• Instalación profesional\n" +
                    "• Configuración del sistema Reefer\n" +
                    "• Soporte técnico dedicado\n\n" +
                    "BENEFICIOS:\n" +
                    "• Alertas en tiempo real desde cualquier lugar\n" +
                    "• Sin depender de redes locales\n" +
                    "• Velocidad de hasta 200 Mbps\n" +
                    "• Latencia baja (~20-40ms)\n\n" +
                    "INVERSIÓN:\n" +
                    "• Equipo: USD 599 (único pago)\n" +
                    "• Servicio: USD 120/mes\n" +
                    "• Instalación: USD 150\n\n" +
                    "¿Deseas que un asesor te contacte?")
            .setPositiveButton("📞 SÍ, CONTACTAR") { _, _ ->
                Toast.makeText(this, "Un asesor te contactará pronto.\nEmail: starlink@parametican.com", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }
    
    private fun showSettingsMenu() {
        val options = arrayOf("📱 Probar Telegram", "🚪 Cerrar sesión")
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("⚙️ Opciones")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> testTelegram()
                    1 -> logout()
                }
            }
            .show()
    }
    
    private fun toggleSimulation() {
        val ip = etServerIp.text.toString().trim()
        thread {
            try {
                val url = URL("http://$ip/api/simulation")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.outputStream.write("""{"enabled":true,"temp1":-22.0,"temp2":-22.0}""".toByteArray())
                conn.responseCode
                conn.disconnect()
                runOnUiThread {
                    simBadge.visibility = View.VISIBLE
                    Toast.makeText(this, "🧪 Simulación activada", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    
    private fun testTelegram() {
        val ip = etServerIp.text.toString().trim()
        thread {
            try {
                val url = URL("http://$ip/api/telegram/test")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                val code = conn.responseCode
                conn.disconnect()
                runOnUiThread {
                    if (code == 200) Toast.makeText(this, "✅ Telegram enviado", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this, "❌ Sin internet", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    
    private fun logout() {
        stopService(Intent(this, MonitorService::class.java))
        getSharedPreferences("parametican", MODE_PRIVATE).edit()
            .putBoolean("logged_in", false)
            .apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
    
    private fun populateReefers() {
        reefersContainer.removeAllViews()
        for (reefer in reefers) {
            val view = layoutInflater.inflate(R.layout.item_reefer, reefersContainer, false)
            
            view.findViewById<TextView>(R.id.tvReeferName).text = reefer.name
            view.findViewById<TextView>(R.id.tvReeferId).text = reefer.id
            view.findViewById<TextView>(R.id.tvReeferTemp).text = String.format("%.1f°C", reefer.temp)
            
            val statusText = view.findViewById<TextView>(R.id.tvReeferStatus)
            val statusIndicator = view.findViewById<View>(R.id.statusIndicator)
            
            if (reefer.online) {
                statusText.text = "Online"
                statusText.setTextColor(Color.parseColor("#22c55e"))
                statusIndicator.setBackgroundResource(R.drawable.bg_status_online)
            } else {
                statusText.text = "Offline"
                statusText.setTextColor(Color.parseColor("#ef4444"))  // Rojo para offline
                statusIndicator.setBackgroundResource(R.drawable.bg_status_offline)
                view.findViewById<TextView>(R.id.tvReeferTemp).text = "--.-°C"  // Sin temperatura
            }
            
            reefersContainer.addView(view)
        }
    }

    private fun updateUIState(running: Boolean) {
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
        btnStop.alpha = if (running) 1f else 0.5f
        btnStart.alpha = if (running) 0.5f else 1f
        etServerIp.isEnabled = !running
        tvConnectionStatus.text = if (running) "🟢 Conectado" else "⚪ Desconectado"
        tvConnectionStatus.setTextColor(if (running) Color.parseColor("#22c55e") else Color.parseColor("#64748b"))
    }

    private fun updateDisplay(
        temp: Float, temp1: Float, temp2: Float,
        doorOpen: Boolean, relayOn: Boolean,
        alertActive: Boolean, alertAcknowledged: Boolean, alertMsg: String,
        uptime: Int, rssi: Int, internet: Boolean, location: String, deviceIp: String, 
        defrostMode: Boolean, cooldownMode: Boolean = false, cooldownRemainingSec: Int = 0
    ) {
        // Temperature with color coding OR Defrost/Cooldown status
        if (defrostMode) {
            // Modo descongelamiento activo
            tvTemperature.text = "🧊 DESCONGELANDO"
            tvTemperature.setTextColor(Color.parseColor("#2196F3"))
            tvTemperature.textSize = 24f
        } else if (cooldownMode && cooldownRemainingSec > 0) {
            // Modo cooldown - mostrar tiempo restante
            val minR = cooldownRemainingSec / 60
            val secR = cooldownRemainingSec % 60
            tvTemperature.text = String.format("⏳ %d:%02d", minR, secR)
            tvTemperature.setTextColor(Color.parseColor("#FF9800"))
            tvTemperature.textSize = 32f
        } else if (temp > -55 && temp < 125) {
            tvTemperature.text = String.format("%.1f°C", temp)
            tvTemperature.textSize = 48f
            val tempColor = when {
                temp > -10 -> "#ef4444"  // Critical - red
                temp > -18 -> "#fbbf24"  // Warning - yellow
                else -> "#22d3ee"        // OK - cyan
            }
            tvTemperature.setTextColor(Color.parseColor(tempColor))
        } else {
            tvTemperature.text = "--.-°C"
            tvTemperature.textSize = 48f
            tvTemperature.setTextColor(Color.parseColor("#64748b"))
        }

        if (temp1 > -55 && temp1 < 125) tvTemp1.text = String.format("%.1f°C", temp1)
        if (temp2 > -55 && temp2 < 125) tvTemp2.text = String.format("%.1f°C", temp2)

        // Door status - siempre inhabilitado
        tvDoorStatus.text = "Inhabilitado"
        tvDoorStatus.setTextColor(Color.parseColor("#64748b"))

        // Relay/Sirena status - prendida solo si hay alerta Y no fue silenciada
        val sirenOn = alertActive && !alertAcknowledged
        tvRelayStatus.text = if (sirenOn) "PRENDIDA" else "Apagada"
        tvRelayStatus.setTextColor(Color.parseColor(if (sirenOn) "#ef4444" else "#94a3b8"))

        // Alert banner - mostrar si hay alerta (activa o silenciada)
        if (alertActive) {
            alertBanner.visibility = View.VISIBLE
            if (alertAcknowledged) {
                tvAlertMessage.text = "🔕 SILENCIADA - $alertMsg"
            } else {
                tvAlertMessage.text = alertMsg
            }
        } else {
            alertBanner.visibility = View.GONE
        }

        // Device IP
        if (deviceIp.isNotEmpty()) {
            tvDeviceIp.text = "IP: $deviceIp"
        }

        // Defrost / Cooldown signal status
        when {
            defrostMode -> {
                tvDefrostSignal.text = "📡 DESCONGELANDO"
                tvDefrostSignal.setTextColor(Color.parseColor("#2196F3"))
            }
            cooldownMode && cooldownRemainingSec > 0 -> {
                val minR = cooldownRemainingSec / 60
                val secR = cooldownRemainingSec % 60
                tvDefrostSignal.text = "⏳ Cooldown ${minR}:${String.format("%02d", secR)}"
                tvDefrostSignal.setTextColor(Color.parseColor("#FF9800"))
            }
            else -> {
                tvDefrostSignal.text = "📡 Señal Descong.: Normal"
                tvDefrostSignal.setTextColor(Color.parseColor("#22c55e"))
            }
        }

        // System info
        tvUptime.text = "Uptime: ${formatUptime(uptime)}"
        tvWifiSignal.text = "WiFi: ${rssi}dBm"
        tvInternet.text = if (internet) "Internet: ✓" else "Internet: ✗"

        if (location.isNotEmpty()) {
            tvLocation.text = "📍 $location"
        }

        // Last update
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        tvLastUpdate.text = "Última actualización: $time"
    }

    private fun formatUptime(seconds: Int): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    private fun silenceAlert() {
        // PRIMERO: Detener alarma local inmediatamente
        MonitorService.silenceAlarm()
        alertBanner.visibility = View.GONE
        Toast.makeText(this, "🛑 Alerta DETENIDA", Toast.LENGTH_SHORT).show()
        
        // SEGUNDO: Notificar al servidor para que DETENGA la alerta globalmente
        // Esto hace que todas las apps vean que la alerta ya no está activa
        val ip = etServerIp.text.toString().trim()
        thread {
            try {
                val url = URL("http://$ip/api/alert/ack")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.doOutput = true
                conn.outputStream.write("{}".toByteArray())
                val code = conn.responseCode
                conn.disconnect()
                
                // Forzar actualización inmediata del estado
                runOnUiThread {
                    if (code == 200) {
                        // La alerta fue detenida en el servidor
                        alertBanner.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                // Ignorar error del servidor, la alarma local ya se detuvo
            }
        }
    }

    private fun turnOffRelay() {
        val ip = etServerIp.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Primero ingresa la IP", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "🔌 Apagando relé...", Toast.LENGTH_SHORT).show()
        
        thread {
            try {
                val url = URL("http://$ip/api/relay")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write("{\"state\":false}".toByteArray())
                val code = conn.responseCode
                conn.disconnect()
                
                runOnUiThread {
                    if (code == 200) {
                        Toast.makeText(this, "✅ Relé APAGADO", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "❌ Error: código $code", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showConfigDialog() {
        val ip = etServerIp.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Primero ingresa la IP", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_config, null)
        val dialog = AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setView(dialogView)
            .setTitle("⚙️ Configuración")
            .setPositiveButton("Guardar") { _, _ ->
                saveConfig(dialogView, ip)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        // Load current config
        loadConfig(dialogView, ip)
        dialog.show()
    }

    private fun loadConfig(view: View, ip: String) {
        thread {
            try {
                val url = URL("http://$ip/api/config")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = org.json.JSONObject(response)
                runOnUiThread {
                    view.findViewById<EditText>(R.id.etTempCritical)?.setText(json.optDouble("temp_critical", -10.0).toString())
                    // Convertir segundos a minutos para mostrar
                    val alertDelaySec = json.optInt("alert_delay_sec", 300)
                    view.findViewById<EditText>(R.id.etAlertDelay)?.setText((alertDelaySec / 60).toString())
                    val defrostCooldownSec = json.optInt("defrost_cooldown_sec", 1800)
                    view.findViewById<EditText>(R.id.etDefrostCooldown)?.setText((defrostCooldownSec / 60).toString())
                    // Switch de relé NC
                    val defrostNC = json.optBoolean("defrost_relay_nc", false)
                    view.findViewById<android.widget.Switch>(R.id.switchDefrostNC)?.isChecked = defrostNC
                    view.findViewById<android.widget.TextView>(R.id.tvDefrostModeStatus)?.text = 
                        if (defrostNC) "Configurado: Normal Cerrado (NC)" else "Configurado: Normal Abierto (NO)"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error cargando config", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveConfig(view: View, ip: String) {
        val tempCritical = view.findViewById<EditText>(R.id.etTempCritical)?.text.toString().toDoubleOrNull() ?: -10.0
        // Convertir minutos a segundos para guardar
        val alertDelayMin = view.findViewById<EditText>(R.id.etAlertDelay)?.text.toString().toIntOrNull() ?: 5
        val alertDelaySec = alertDelayMin * 60
        val defrostCooldownMin = view.findViewById<EditText>(R.id.etDefrostCooldown)?.text.toString().toIntOrNull() ?: 30
        val defrostCooldownSec = defrostCooldownMin * 60
        val defrostNC = view.findViewById<android.widget.Switch>(R.id.switchDefrostNC)?.isChecked ?: false

        thread {
            try {
                val url = URL("http://$ip/api/config")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000

                val json = """{"temp_critical":$tempCritical,"alert_delay_sec":$alertDelaySec,"defrost_cooldown_sec":$defrostCooldownSec,"defrost_relay_nc":$defrostNC}"""
                conn.outputStream.write(json.toByteArray())
                conn.responseCode
                conn.disconnect()

                runOnUiThread {
                    Toast.makeText(this, "✅ Configuración guardada", Toast.LENGTH_SHORT).show()
                    // Recargar valores configurados en la pantalla principal
                    loadConfigValues(ip)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(dataReceiver, IntentFilter("REEFER_DATA_UPDATE"))

        if (MonitorService.isRunning) {
            updateUIState(true)
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataReceiver)
    }
}

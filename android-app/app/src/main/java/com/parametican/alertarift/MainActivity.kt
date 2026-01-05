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
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnConfig: Button
    private lateinit var btnSilence: Button
    private lateinit var btnLogout: Button
    private lateinit var alertBanner: LinearLayout
    private lateinit var simBadge: LinearLayout
    private lateinit var reefersContainer: LinearLayout
    private lateinit var connectionCard: LinearLayout
    private lateinit var controlButtons: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    
    // Reefers data
    data class ReeferInfo(
        val id: String, 
        val name: String, 
        var online: Boolean, 
        var temp: Float, 
        var alertActive: Boolean,
        var doorOpen: Boolean = false,
        var sirenOn: Boolean = false
    )
    private val reefers = mutableListOf<ReeferInfo>()

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val temp = it.getFloatExtra("temperature", -999f)
                val temp1 = it.getFloatExtra("temp1", -999f)
                val temp2 = it.getFloatExtra("temp2", -999f)
                val doorOpen = it.getBooleanExtra("door_open", false)
                val relayOn = it.getBooleanExtra("relay_on", false)
                val alertActive = it.getBooleanExtra("alert_active", false)
                val alertMsg = it.getStringExtra("alert_message") ?: ""
                val uptime = it.getIntExtra("uptime", 0)
                val rssi = it.getIntExtra("rssi", 0)
                val internet = it.getBooleanExtra("internet", false)
                val location = it.getStringExtra("location") ?: ""

                updateDisplay(temp, temp1, temp2, doorOpen, relayOn, alertActive, alertMsg, uptime, rssi, internet, location)
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

        if (MonitorService.isRunning) {
            updateUIState(true)
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
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnConfig = findViewById(R.id.btnConfig)
        btnSilence = findViewById(R.id.btnSilence)
        btnLogout = findViewById(R.id.btnLogout)
        simBadge = findViewById(R.id.simBadge)
        reefersContainer = findViewById(R.id.reefersContainer)
        
        // Populate Reefers list
        populateReefers()
        alertBanner = findViewById(R.id.alertBanner)
        connectionCard = findViewById(R.id.connectionCard)
        controlButtons = findViewById(R.id.controlButtons)
        
        // Ocultar conexión local si está en modo Internet
        setupConnectionMode()
    }
    
    private fun setupConnectionMode() {
        val prefs = getSharedPreferences("frioseguro", MODE_PRIVATE)
        val mode = prefs.getString("connection_mode", "local")
        
        // SIEMPRE ocultar campo IP y botones - conexión automática
        connectionCard.visibility = View.GONE
        controlButtons.visibility = View.GONE
        
        if (mode == "internet") {
            // Modo Internet: conectar a Supabase
            startInternetMonitoring()
        } else {
            // Modo Local: conectar automáticamente al ESP32 via NSD
            startLocalMonitoring()
        }
    }
    
    private fun startLocalMonitoring() {
        val prefs = getSharedPreferences("frioseguro", MODE_PRIVATE)
        val savedIp = prefs.getString("server_ip", null)
        
        tvConnectionStatus.text = "📡 Conectando..."
        tvConnectionStatus.setTextColor(Color.parseColor("#f59e0b"))
        
        if (savedIp != null) {
            // Usar IP guardada del ModeSelectActivity
            connectToLocalDevice(savedIp)
        } else {
            // Buscar con NSD
            discoverLocalDevice()
        }
    }
    
    private fun connectToLocalDevice(ip: String) {
        thread {
            try {
                val url = URL("http://$ip/api/status")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    
                    runOnUiThread {
                        tvConnectionStatus.text = "🟢 Conectado a $ip"
                        tvConnectionStatus.setTextColor(Color.parseColor("#22c55e"))
                        parseLocalResponse(response)
                    }
                    
                    // Iniciar polling cada 3 segundos
                    startLocalPolling(ip)
                } else {
                    conn.disconnect()
                    runOnUiThread {
                        tvConnectionStatus.text = "⚠️ ESP32 no responde"
                        tvConnectionStatus.setTextColor(Color.parseColor("#ef4444"))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvConnectionStatus.text = "❌ Error: ${e.message}"
                    tvConnectionStatus.setTextColor(Color.parseColor("#ef4444"))
                }
            }
        }
    }
    
    private fun startLocalPolling(ip: String) {
        updateRunnable = object : Runnable {
            override fun run() {
                fetchFromLocal(ip)
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(updateRunnable!!)
    }
    
    private fun fetchFromLocal(ip: String) {
        thread {
            try {
                // Hacer ping para registrar conexión Android
                try {
                    val pingUrl = URL("http://$ip/api/ping")
                    val pingConn = pingUrl.openConnection() as HttpURLConnection
                    pingConn.connectTimeout = 1000
                    pingConn.requestMethod = "POST"
                    pingConn.responseCode // trigger request
                    pingConn.disconnect()
                } catch (e: Exception) { }
                
                // Obtener status
                val url = URL("http://$ip/api/status")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    runOnUiThread {
                        parseLocalResponse(response)
                        tvLastUpdate.text = "Última actualización: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                // Silently fail, will retry
            }
        }
    }
    
    private fun parseLocalResponse(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            
            // El ESP32 devuelve: { sensor: {...}, system: {...}, device: {...}, location: {...} }
            val sensor = obj.optJSONObject("sensor")
            val system = obj.optJSONObject("system")
            val location = obj.optJSONObject("location")
            
            val temp = sensor?.optDouble("temp_avg", -999.0)?.toFloat() ?: -999f
            val temp1 = sensor?.optDouble("temp1", -999.0)?.toFloat() ?: -999f
            val temp2 = sensor?.optDouble("temp2", -999.0)?.toFloat() ?: -999f
            val doorOpen = sensor?.optBoolean("door_open", false) ?: false
            val relayOn = system?.optBoolean("relay_on", false) ?: false
            val alertActive = system?.optBoolean("alert_active", false) ?: false
            val alertMsg = system?.optString("alert_message", "") ?: ""
            val uptime = system?.optInt("uptime_sec", 0) ?: 0
            val rssi = system?.optInt("wifi_rssi", 0) ?: 0
            val internet = system?.optBoolean("internet", false) ?: false
            val locationName = location?.optString("detail", "") ?: ""
            val simulation = system?.optBoolean("simulation_mode", false) ?: false
            
            updateDisplay(temp, temp1, temp2, doorOpen, relayOn, alertActive, alertMsg, uptime, rssi, internet, locationName)
            
            // Mostrar badge de simulación si está activo
            simBadge.visibility = if (simulation) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }
    
    private fun discoverLocalDevice() {
        // Fallback: intentar IPs comunes
        val addresses = listOf("reefer.local", "192.168.4.1", "192.168.1.100", "192.168.0.100")
        
        thread {
            for (addr in addresses) {
                try {
                    val url = URL("http://$addr/api/status")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    
                    if (conn.responseCode == 200) {
                        conn.disconnect()
                        
                        // Guardar IP encontrada
                        getSharedPreferences("frioseguro", MODE_PRIVATE).edit()
                            .putString("server_ip", addr)
                            .apply()
                        
                        runOnUiThread {
                            connectToLocalDevice(addr)
                        }
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) { }
            }
            
            runOnUiThread {
                tvConnectionStatus.text = "❌ No se encontró ESP32"
                tvConnectionStatus.setTextColor(Color.parseColor("#ef4444"))
            }
        }
    }
    
    private fun startInternetMonitoring() {
        val prefs = getSharedPreferences("frioseguro", MODE_PRIVATE)
        val orgSlug = prefs.getString("org_slug", "parametican") ?: "parametican"
        
        tvConnectionStatus.text = "🌐 Conectado a la nube"
        tvConnectionStatus.setTextColor(Color.parseColor("#22c55e"))
        
        // Iniciar polling de Supabase
        startSupabasePolling(orgSlug)
    }
    
    private fun startSupabasePolling(orgSlug: String) {
        updateRunnable = object : Runnable {
            override fun run() {
                fetchFromSupabase(orgSlug)
                handler.postDelayed(this, 5000) // Cada 5 segundos
            }
        }
        handler.post(updateRunnable!!)
    }
    
    private fun fetchFromSupabase(orgSlug: String) {
        thread {
            try {
                val supabaseUrl = ModeSelectActivity.SUPABASE_URL
                val supabaseKey = ModeSelectActivity.SUPABASE_KEY
                
                // Obtener SOLO dispositivos REEFER (no CARNICERIA)
                val devicesUrl = URL("$supabaseUrl/rest/v1/devices?device_id=like.REEFER*&select=*&order=device_id")
                val devConn = devicesUrl.openConnection() as HttpURLConnection
                devConn.connectTimeout = 10000
                devConn.setRequestProperty("apikey", supabaseKey)
                devConn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                
                var devicesJson = "[]"
                if (devConn.responseCode == 200) {
                    devicesJson = devConn.inputStream.bufferedReader().readText()
                }
                devConn.disconnect()
                
                // Obtener últimas lecturas solo de REEFER
                val readingsUrl = URL("$supabaseUrl/rest/v1/readings?device_id=like.REEFER*&select=device_id,temp_avg,temp1,temp2,door_open,siren_on,alert_active,created_at&order=created_at.desc&limit=50")
                val readConn = readingsUrl.openConnection() as HttpURLConnection
                readConn.connectTimeout = 10000
                readConn.setRequestProperty("apikey", supabaseKey)
                readConn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                
                var readingsJson = "[]"
                if (readConn.responseCode == 200) {
                    readingsJson = readConn.inputStream.bufferedReader().readText()
                }
                readConn.disconnect()
                
                runOnUiThread {
                    parseSupabaseData(devicesJson, readingsJson)
                    tvLastUpdate.text = "Última actualización: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvConnectionStatus.text = "⚠️ Error: ${e.message}"
                    tvConnectionStatus.setTextColor(Color.parseColor("#f59e0b"))
                }
            }
        }
    }
    
    private fun parseSupabaseData(devicesJson: String, readingsJson: String) {
        try {
            val devices = org.json.JSONArray(devicesJson)
            val readings = org.json.JSONArray(readingsJson)
            
            // Crear mapa de últimas lecturas por device_id
            val latestReadings = mutableMapOf<String, org.json.JSONObject>()
            for (i in 0 until readings.length()) {
                val reading = readings.getJSONObject(i)
                val deviceId = reading.optString("device_id", "")
                if (deviceId.isNotEmpty() && !latestReadings.containsKey(deviceId)) {
                    latestReadings[deviceId] = reading
                }
            }
            
            // Limpiar y reconstruir lista de reefers
            reefers.clear()
            
            for (i in 0 until devices.length()) {
                val device = devices.getJSONObject(i)
                val deviceId = device.optString("device_id", "UNKNOWN")
                val name = device.optString("name", deviceId)
                val isOnline = device.optBoolean("is_online", false)
                
                // Buscar última lectura para este dispositivo
                val reading = latestReadings[deviceId]
                val temp = reading?.optDouble("temp_avg", -999.0)?.toFloat() ?: -999f
                val doorOpen = reading?.optBoolean("door_open", false) ?: false
                val sirenOn = reading?.optBoolean("siren_on", false) ?: false
                val alertActive = reading?.optBoolean("alert_active", false) ?: false
                
                reefers.add(ReeferInfo(
                    id = deviceId,
                    name = name,
                    online = isOnline,
                    temp = if (temp > -900) temp else -22.5f,
                    alertActive = alertActive,
                    doorOpen = doorOpen,
                    sirenOn = sirenOn
                ))
            }
            
            populateReefers()
            
            // Actualizar display principal con el primer dispositivo online o el primero disponible
            val mainDevice = reefers.firstOrNull { it.online } ?: reefers.firstOrNull()
            mainDevice?.let {
                tvTemperature.text = String.format("%.1f°C", it.temp)
                tvDoorStatus.text = if (it.doorOpen) "Abierta" else "Cerrada"
                tvDoorStatus.setTextColor(Color.parseColor(if (it.doorOpen) "#ef4444" else "#22c55e"))
                // Sirena status ya se maneja en updateDisplay
                
                if (it.alertActive) {
                    tvTemperature.setTextColor(Color.parseColor("#ef4444"))
                } else {
                    tvTemperature.setTextColor(Color.parseColor("#22d3ee"))
                }
            }
        } catch (e: Exception) {
            // Log error pero no crashear
            tvConnectionStatus.text = "⚠️ Error parseando datos"
        }
    }

    private fun loadSavedIp() {
        val prefs = getSharedPreferences("parametican", MODE_PRIVATE)
        etServerIp.setText(prefs.getString("server_ip", "192.168.1.100"))
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
        
        // Modo Descongelamiento
        findViewById<Button>(R.id.btnDefrost).setOnClickListener {
            showDefrostDialog()
        }
        
        // Cambiar modo conexión
        findViewById<Button>(R.id.btnChangeMode).setOnClickListener {
            changeConnectionMode()
        }
    }
    
    private fun showDefrostDialog() {
        val options = arrayOf("1 hora", "2 horas", "4 horas", "8 horas", "Hasta que lo reactive")
        val hours = arrayOf(1, 2, 4, 8, 0)
        
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("🧊 Modo Descongelamiento")
            .setMessage("Las alertas se desactivarán temporalmente.\n¿Por cuánto tiempo?")
            .setItems(options) { _, which ->
                val selectedHours = hours[which]
                activateDefrostMode(selectedHours)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun activateDefrostMode(hours: Int) {
        val ip = etServerIp.text.toString().trim()
        val prefs = getSharedPreferences("frioseguro", MODE_PRIVATE)
        val mode = prefs.getString("connection_mode", "local")
        
        thread {
            try {
                if (mode == "internet") {
                    // Enviar a Supabase
                    val supabaseUrl = prefs.getString("supabase_url", ModeSelectActivity.SUPABASE_URL)
                    val supabaseKey = prefs.getString("supabase_key", ModeSelectActivity.SUPABASE_KEY)
                    val deviceId = "REEFER-01"
                    
                    val url = URL("$supabaseUrl/rest/v1/devices?device_id=eq.$deviceId")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "PATCH"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("apikey", supabaseKey)
                    conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                    conn.doOutput = true
                    
                    val payload = if (hours == 0) {
                        """{"alerts_disabled":true,"alerts_disabled_until":null,"alerts_disabled_reason":"Descongelamiento manual"}"""
                    } else {
                        """{"alerts_disabled":true,"alerts_disabled_reason":"Descongelamiento $hours horas"}"""
                    }
                    conn.outputStream.write(payload.toByteArray())
                    conn.responseCode
                    conn.disconnect()
                } else {
                    // Enviar al ESP32 local
                    val url = URL("http://$ip/api/config")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 5000
                    
                    val payload = """{"alerts_disabled":true,"defrost_hours":$hours}"""
                    conn.outputStream.write(payload.toByteArray())
                    conn.responseCode
                    conn.disconnect()
                }
                
                runOnUiThread {
                    val msg = if (hours == 0) "Alertas desactivadas hasta que las reactives" else "Alertas desactivadas por $hours horas"
                    Toast.makeText(this, "🧊 $msg", Toast.LENGTH_LONG).show()
                    
                    // Cambiar botón
                    findViewById<Button>(R.id.btnDefrost).apply {
                        text = "✅ DESCONGELAMIENTO ACTIVO"
                        setBackgroundColor(Color.parseColor("#16a34a"))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun changeConnectionMode() {
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("🔄 Cambiar Modo")
            .setMessage("¿Querés cambiar el modo de conexión?\n\nEsto te llevará a la pantalla de selección.")
            .setPositiveButton("Sí, cambiar") { _, _ ->
                // Limpiar preferencias de modo
                getSharedPreferences("frioseguro", MODE_PRIVATE).edit()
                    .putBoolean("mode_selected", false)
                    .putBoolean("logged_in", false)
                    .apply()
                
                // Detener servicio
                stopService(Intent(this, MonitorService::class.java))
                
                // Ir a selector de modo
                startActivity(Intent(this, ModeSelectActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
        val options = arrayOf("📱 Probar Telegram", "🔄 Reconectar", "🚪 Cerrar sesión")
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("⚙️ Opciones")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> testTelegram()
                    1 -> reconnect()
                    2 -> logout()
                }
            }
            .show()
    }
    
    private fun reconnect() {
        handler.removeCallbacksAndMessages(null)
        updateRunnable = null
        
        val prefs = getSharedPreferences("frioseguro", MODE_PRIVATE)
        prefs.edit().remove("server_ip").apply()
        
        tvConnectionStatus.text = "🔄 Reconectando..."
        tvConnectionStatus.setTextColor(Color.parseColor("#f59e0b"))
        
        setupConnectionMode()
    }
    
    private fun showSensorConfigDialog() {
        val prefs = getSharedPreferences("frioseguro", MODE_PRIVATE)
        val ip = prefs.getString("server_ip", null)
        
        if (ip == null) {
            Toast.makeText(this, "❌ No hay conexión con ESP32", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_sensor_config, null)
        
        // Cargar configuración actual del ESP32
        thread {
            try {
                val url = URL("http://$ip/api/config")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                
                val json = org.json.JSONObject(response)
                runOnUiThread {
                    dialogView.findViewById<Switch>(R.id.switchSensor1)?.isChecked = json.optBoolean("sensor1_enabled", true)
                    dialogView.findViewById<Switch>(R.id.switchSensor2)?.isChecked = json.optBoolean("sensor2_enabled", false)
                    dialogView.findViewById<Switch>(R.id.switchSensor3)?.isChecked = json.optBoolean("sensor3_enabled", false)
                    dialogView.findViewById<Switch>(R.id.switchDoorSensor)?.isChecked = json.optBoolean("door_sensor_enabled", false)
                    dialogView.findViewById<Switch>(R.id.switchSimulation)?.isChecked = json.optBoolean("simulation", false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "⚠️ No se pudo cargar config", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("⚙️ Configurar Sensores")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                saveSensorConfig(dialogView, ip)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun saveSensorConfig(view: View, ip: String) {
        val sensor1 = view.findViewById<Switch>(R.id.switchSensor1)?.isChecked ?: true
        val sensor2 = view.findViewById<Switch>(R.id.switchSensor2)?.isChecked ?: false
        val sensor3 = view.findViewById<Switch>(R.id.switchSensor3)?.isChecked ?: false
        val doorSensor = view.findViewById<Switch>(R.id.switchDoorSensor)?.isChecked ?: false
        val simulation = view.findViewById<Switch>(R.id.switchSimulation)?.isChecked ?: false
        
        thread {
            try {
                val url = URL("http://$ip/api/config")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                
                val payload = """{
                    "sensor1_enabled": $sensor1,
                    "sensor2_enabled": $sensor2,
                    "sensor3_enabled": $sensor3,
                    "door_sensor_enabled": $doorSensor,
                    "simulation": $simulation
                }"""
                
                conn.outputStream.write(payload.toByteArray())
                val code = conn.responseCode
                conn.disconnect()
                
                runOnUiThread {
                    if (code == 200) {
                        Toast.makeText(this, "✅ Configuración guardada", Toast.LENGTH_SHORT).show()
                        simBadge.visibility = if (simulation) View.VISIBLE else View.GONE
                    } else {
                        Toast.makeText(this, "❌ Error al guardar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showSimulationDialog() {
        val prefs = getSharedPreferences("frioseguro", MODE_PRIVATE)
        val ip = prefs.getString("server_ip", null)
        
        if (ip == null) {
            Toast.makeText(this, "❌ No hay conexión con ESP32", Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = arrayOf(
            "🟢 Activar simulación (-22°C normal)",
            "🟡 Simular alerta de temperatura (-5°C)",
            "🔴 Simular temperatura crítica (0°C)",
            "🚪 Simular puerta abierta",
            "⏹️ Desactivar simulación"
        )
        
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("🧪 Modo Simulación")
            .setItems(options) { _, which ->
                val payload = when (which) {
                    0 -> """{"simulation":true,"sim_temp":-22.0}"""
                    1 -> """{"simulation":true,"sim_temp":-5.0}"""
                    2 -> """{"simulation":true,"sim_temp":0.0}"""
                    3 -> """{"simulation":true,"sim_door_open":true}"""
                    4 -> """{"simulation":false}"""
                    else -> return@setItems
                }
                sendSimulationCommand(ip, payload, which != 4)
            }
            .show()
    }
    
    private fun sendSimulationCommand(ip: String, payload: String, enableSim: Boolean) {
        thread {
            try {
                val url = URL("http://$ip/api/simulation")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.outputStream.write(payload.toByteArray())
                val code = conn.responseCode
                conn.disconnect()
                
                runOnUiThread {
                    if (code == 200) {
                        simBadge.visibility = if (enableSim) View.VISIBLE else View.GONE
                        Toast.makeText(this, if (enableSim) "🧪 Simulación activada" else "⏹️ Simulación desactivada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "❌ Error", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                statusText.setTextColor(Color.parseColor("#64748b"))
                statusIndicator.setBackgroundResource(R.drawable.bg_status_offline)
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
        alertActive: Boolean, alertMsg: String,
        uptime: Int, rssi: Int, internet: Boolean, location: String
    ) {
        // Temperature with color coding
        if (temp > -900) {
            tvTemperature.text = String.format("%.1f°C", temp)
            val tempColor = when {
                temp > -10 -> "#ef4444"  // Critical - red
                temp > -18 -> "#fbbf24"  // Warning - yellow
                else -> "#22d3ee"        // OK - cyan
            }
            tvTemperature.setTextColor(Color.parseColor(tempColor))
        }

        if (temp1 > -900) tvTemp1.text = String.format("%.1f°C", temp1)
        if (temp2 > -900) tvTemp2.text = String.format("%.1f°C", temp2)

        // Door status
        tvDoorStatus.text = if (doorOpen) "ABIERTA" else "Cerrada"
        tvDoorStatus.setTextColor(Color.parseColor(if (doorOpen) "#fbbf24" else "#22c55e"))

        // Relay status
        tvRelayStatus.text = if (relayOn) "ACTIVA" else "Apagada"
        tvRelayStatus.setTextColor(Color.parseColor(if (relayOn) "#ef4444" else "#94a3b8"))

        // Alert banner
        if (alertActive) {
            alertBanner.visibility = View.VISIBLE
            tvAlertMessage.text = alertMsg
        } else {
            alertBanner.visibility = View.GONE
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
                    view.findViewById<EditText>(R.id.etTempMax)?.setText(json.optDouble("temp_max", -18.0).toString())
                    view.findViewById<EditText>(R.id.etTempCritical)?.setText(json.optDouble("temp_critical", -10.0).toString())
                    view.findViewById<EditText>(R.id.etAlertDelay)?.setText(json.optInt("alert_delay_sec", 300).toString())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error cargando config", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveConfig(view: View, ip: String) {
        val tempMax = view.findViewById<EditText>(R.id.etTempMax)?.text.toString().toDoubleOrNull() ?: -18.0
        val tempCritical = view.findViewById<EditText>(R.id.etTempCritical)?.text.toString().toDoubleOrNull() ?: -10.0
        val alertDelay = view.findViewById<EditText>(R.id.etAlertDelay)?.text.toString().toIntOrNull() ?: 300

        thread {
            try {
                val url = URL("http://$ip/api/config")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000

                val json = """{"temp_max":$tempMax,"temp_critical":$tempCritical,"alert_delay_sec":$alertDelay}"""
                conn.outputStream.write(json.toByteArray())
                conn.responseCode
                conn.disconnect()

                runOnUiThread {
                    Toast.makeText(this, "✅ Configuración guardada", Toast.LENGTH_SHORT).show()
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

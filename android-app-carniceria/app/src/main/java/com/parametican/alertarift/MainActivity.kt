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
        val options = arrayOf("🔄 Cambiar servidor", "🧪 Modo simulación", "📱 Probar Telegram", "🚪 Cerrar sesión")
        AlertDialog.Builder(this, R.style.Theme_AlertaRift_Dialog)
            .setTitle("⚙️ Opciones")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Ya se puede editar la IP
                        etServerIp.requestFocus()
                    }
                    1 -> toggleSimulation()
                    2 -> testTelegram()
                    3 -> logout()
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

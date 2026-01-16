package com.parametican.alertarift

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * MainActivity para la versión CLOUD
 * Obtiene datos de Supabase en vez de conexión local al ESP32
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvReadingAge: TextView
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
    private lateinit var btnSilence: Button
    private lateinit var btnRelayOff: Button
    private lateinit var btnLogout: Button
    private lateinit var btnRefresh: Button
    private lateinit var alertBanner: LinearLayout
    private lateinit var simBadge: LinearLayout
    private lateinit var reefersContainer: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var selectedDeviceId: String = "REEFER-01"
    
    // Lista de dispositivos desde Supabase
    private var devices = mutableListOf<SupabaseClient.DeviceStatus>()
    
    // Configuración actual del dispositivo seleccionado
    private var currentConfig: SupabaseClient.DeviceConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupButtons()
        startAutoRefresh()
        
        // Iniciar MonitorService para polling y alarmas en background
        startMonitorService()
    }
    
    private fun startMonitorService() {
        AppLogger.info("SERVICE", "Iniciando MonitorService desde MainActivity")
        val serviceIntent = Intent(this, MonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
    }

    private fun initViews() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvReadingAge = findViewById(R.id.tvReadingAge)
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
        btnSilence = findViewById(R.id.btnSilence)
        btnRelayOff = findViewById(R.id.btnRelayOff)
        btnLogout = findViewById(R.id.btnLogout)
        simBadge = findViewById(R.id.simBadge)
        reefersContainer = findViewById(R.id.reefersContainer)
        alertBanner = findViewById(R.id.alertBanner)
        
        // Ocultar elementos no necesarios en cloud
        findViewById<EditText>(R.id.etServerIp).visibility = View.GONE
        findViewById<Button>(R.id.btnStart).visibility = View.GONE
        findViewById<Button>(R.id.btnStop).visibility = View.GONE
        
        // Agregar botón de refresh
        btnRefresh = findViewById(R.id.btnConfig)
        btnRefresh.text = "⚙️ Configurar"
        
        // Cambiar título de conexión
        tvConnectionStatus.text = "☁️ Modo Cloud"
        tvConnectionStatus.setTextColor(Color.parseColor("#4CAF50"))
    }

    private fun setupButtons() {
        btnRefresh.setOnClickListener {
            showConfigDialog()
        }

        btnSilence.setOnClickListener {
            silenceAlert()
        }
        
        btnRelayOff.setOnClickListener {
            turnOffRelay()
        }
        
        btnLogout.setOnClickListener {
            logout()
        }
        
        // Ocultar botones de servicios adicionales que no aplican
        try {
            findViewById<Button>(R.id.btnConfigTelegram).visibility = View.GONE
            findViewById<Button>(R.id.btnConfigWhatsApp).visibility = View.GONE
            findViewById<Button>(R.id.btnServiceMultiReefer).visibility = View.GONE
            findViewById<Button>(R.id.btnServiceCloud).visibility = View.GONE
            findViewById<Button>(R.id.btnServiceReports).visibility = View.GONE
            findViewById<Button>(R.id.btnServiceSupport).visibility = View.GONE
            findViewById<Button>(R.id.btnStarlinkInfo).visibility = View.GONE
        } catch (e: Exception) {
            // Algunos botones pueden no existir
        }
    }
    
    private fun showConfigDialog() {
        // Obtener configuración actual en background
        thread {
            val config = SupabaseClient.getDeviceConfig(selectedDeviceId)
            
            runOnUiThread {
                if (config == null) {
                    Toast.makeText(this, "❌ No se pudo cargar la configuración", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                
                // Crear diálogo con campos editables
                val dialogView = layoutInflater.inflate(R.layout.dialog_config, null)
                
                val etTempCritical = dialogView.findViewById<EditText>(R.id.etTempCritical)
                val etAlertDelay = dialogView.findViewById<EditText>(R.id.etAlertDelay)
                val etDefrostCooldown = dialogView.findViewById<EditText>(R.id.etDefrostCooldown)
                val etDoorMaxTime = dialogView.findViewById<EditText>(R.id.etDoorMaxTime)
                
                // Cargar valores actuales
                etTempCritical.setText(config.tempCritical.toString())
                etAlertDelay.setText((config.alertDelaySec / 60).toString())
                etDefrostCooldown.setText((config.defrostCooldownSec / 60).toString())
                etDoorMaxTime.setText(config.doorOpenMaxSec.toString())
                
                AlertDialog.Builder(this)
                    .setTitle("⚙️ Configuración - ${config.name}")
                    .setView(dialogView)
                    .setPositiveButton("💾 Guardar") { _, _ ->
                        saveConfig(
                            etTempCritical.text.toString().toFloatOrNull() ?: config.tempCritical,
                            (etAlertDelay.text.toString().toIntOrNull() ?: (config.alertDelaySec / 60)) * 60,
                            (etDefrostCooldown.text.toString().toIntOrNull() ?: (config.defrostCooldownSec / 60)) * 60,
                            etDoorMaxTime.text.toString().toIntOrNull() ?: config.doorOpenMaxSec
                        )
                    }
                    .setNeutralButton("📋 LOGS") { _, _ ->
                        startActivity(Intent(this, LogsActivity::class.java))
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }
    
    private fun saveConfig(tempCritical: Float, alertDelaySec: Int, defrostCooldownSec: Int, doorOpenMaxSec: Int) {
        thread {
            try {
                val config = JSONObject().apply {
                    put("temp_critical", tempCritical)
                    put("alert_delay_sec", alertDelaySec)
                    put("defrost_cooldown_sec", defrostCooldownSec)
                    put("door_open_max_sec", doorOpenMaxSec)
                }
                
                val success = SupabaseClient.updateDeviceConfig(selectedDeviceId, config)
                
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "✅ Configuración guardada", Toast.LENGTH_SHORT).show()
                        refreshData()
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
    
    private fun startAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                refreshData()
                handler.postDelayed(this, 5000) // Cada 5 segundos
            }
        }
        handler.post(refreshRunnable!!)
    }
    
    private fun stopAutoRefresh() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }
    
    private fun refreshData() {
        thread {
            try {
                // Obtener todos los dispositivos
                devices = SupabaseClient.getDevicesStatus().toMutableList()
                
                // Obtener el dispositivo seleccionado
                val device = devices.find { it.deviceId == selectedDeviceId } ?: devices.firstOrNull()
                
                // Obtener configuración del dispositivo
                val config = if (device != null) SupabaseClient.getDeviceConfig(device.deviceId) else null
                
                runOnUiThread {
                    if (device != null) {
                        // Guardar configuración actual para usar en updateDisplay
                        currentConfig = config
                        
                        updateDisplay(device)
                        populateReefers()
                        
                        // Mostrar configuración
                        if (config != null) {
                            updateConfigDisplay(config)
                        }
                    } else {
                        tvConnectionStatus.text = "⚠️ Sin dispositivos"
                    }
                    
                    tvLastUpdate.text = "Actualizado: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvConnectionStatus.text = "❌ Error: ${e.message}"
                }
            }
        }
    }
    
    private fun updateConfigDisplay(config: SupabaseClient.DeviceConfig) {
        tvConfigTempCritical.text = "🌡️ Temp. Crítica: ${config.tempCritical}°C"
        tvConfigAlertDelay.text = "⏱️ Tiempo espera: ${config.alertDelaySec / 60} min"
        tvConfigDefrostCooldown.text = "🧊 Post-descongelación: ${config.defrostCooldownSec / 60} min"
        tvConfigDefrostRelay.text = "🚪 Puerta máx: ${config.doorOpenMaxSec} seg"
    }
    
    private fun updateDisplay(device: SupabaseClient.DeviceStatus) {
        // Estado de conexión
        if (device.isOnline) {
            tvConnectionStatus.text = "☁️ ${device.name} - Online"
            tvConnectionStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            tvConnectionStatus.text = "☁️ ${device.name} - Offline"
            tvConnectionStatus.setTextColor(Color.parseColor("#F44336"))
        }
        
        // Temperatura o Estado de Descongelamiento
        val temp = device.tempAvg ?: -999f
        
        if (device.defrostMode) {
            // Modo descongelamiento activo
            tvTemperature.text = "🧊 DESCONGELANDO"
            tvTemperature.setTextColor(Color.parseColor("#2196F3"))
            tvTemperature.textSize = 24f
        } else if (device.cooldownMode && device.cooldownRemainingSec > 0) {
            // Modo cooldown - mostrar tiempo restante
            val minR = device.cooldownRemainingSec / 60
            val secR = device.cooldownRemainingSec % 60
            tvTemperature.text = String.format("⏳ %d:%02d", minR, secR)
            tvTemperature.setTextColor(Color.parseColor("#FF9800"))
            tvTemperature.textSize = 32f
        } else if (temp > -55 && temp < 125) {
            tvTemperature.text = String.format("%.1f°C", temp)
            tvTemperature.textSize = 48f
            
            // Color según temperatura
            val color = when {
                temp > 0 -> "#F44336"      // Rojo - muy caliente
                temp > -10 -> "#FF9800"    // Naranja - alerta
                temp > -25 -> "#4CAF50"    // Verde - normal
                else -> "#2196F3"          // Azul - muy frío
            }
            tvTemperature.setTextColor(Color.parseColor(color))
        } else {
            tvTemperature.text = "--.-°C"
            tvTemperature.textSize = 48f
        }
        
        // Temperaturas individuales
        val t1 = device.temp1 ?: -999f
        val t2 = device.temp2 ?: -999f
        tvTemp1.text = if (t1 > -55 && t1 < 125) String.format("T1: %.1f°C", t1) else "T1: --"
        tvTemp2.text = if (t2 > -55 && t2 < 125) String.format("T2: %.1f°C", t2) else "T2: --"
        
        // Puerta
        tvDoorStatus.text = if (device.door1Open) "🚪 ABIERTA" else "🚪 Cerrada"
        tvDoorStatus.setTextColor(if (device.door1Open) Color.parseColor("#FF9800") else Color.parseColor("#4CAF50"))
        
        // Relay/Sirena
        tvRelayStatus.text = if (device.relayOn) "🔔 SIRENA ACTIVA" else "🔕 Sirena apagada"
        tvRelayStatus.setTextColor(if (device.relayOn) Color.parseColor("#F44336") else Color.parseColor("#888888"))
        
        // Ubicación
        tvLocation.text = "📍 ${device.location}"
        
        // Uptime
        val uptimeSec = device.uptimeSec ?: 0
        val hours = uptimeSec / 3600
        val minutes = (uptimeSec % 3600) / 60
        tvUptime.text = "⏱️ ${hours}h ${minutes}m"
        
        // WiFi
        val rssi = device.wifiRssi ?: 0
        val wifiIcon = when {
            rssi > -50 -> "📶"
            rssi > -70 -> "📶"
            rssi > -85 -> "📶"
            else -> "📵"
        }
        tvWifiSignal.text = "$wifiIcon $rssi dBm"
        
        // Internet/Luz
        tvInternet.text = if (device.acPower) "⚡ Con luz" else "🔋 Sin luz"
        tvInternet.setTextColor(if (device.acPower) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
        
        // Device ID
        tvDeviceIp.text = "🆔 ${device.deviceId}"
        
        // Defrost / Cooldown
        when {
            device.defrostMode -> {
                tvDefrostSignal.text = "🧊 DESCONGELANDO"
                tvDefrostSignal.setTextColor(Color.parseColor("#2196F3"))
            }
            device.cooldownMode && device.cooldownRemainingSec > 0 -> {
                val minR = device.cooldownRemainingSec / 60
                val secR = device.cooldownRemainingSec % 60
                tvDefrostSignal.text = "⏳ Cooldown ${minR}:${String.format("%02d", secR)}"
                tvDefrostSignal.setTextColor(Color.parseColor("#FF9800"))
            }
            else -> {
                tvDefrostSignal.text = "🧊 Normal"
                tvDefrostSignal.setTextColor(Color.parseColor("#888888"))
            }
        }
        
        // Simulación
        simBadge.visibility = if (device.simulationMode) View.VISIBLE else View.GONE
        
        // Antigüedad de la lectura del ESP
        updateReadingAge(device.readingCreatedAt)
        
        // Cronómetro de cuenta regresiva ANTES de la alerta
        if (device.tempOverCritical && !device.alertActive && device.highTempElapsedSec > 0) {
            // Mostrar banner de advertencia con cronómetro
            alertBanner.visibility = View.VISIBLE
            alertBanner.setBackgroundColor(Color.parseColor("#FF9800")) // Naranja para advertencia
            
            // Calcular tiempo restante
            val alertDelaySec = currentConfig?.alertDelaySec ?: 60
            val elapsed = device.highTempElapsedSec
            val remaining = Math.max(0, alertDelaySec - elapsed)
            val minR = remaining / 60
            val secR = remaining % 60
            
            tvAlertMessage.text = "⏱️ ALERTA EN ${minR}:${String.format("%02d", secR)} (${elapsed}/${alertDelaySec}s)"
            btnSilence.visibility = View.GONE
            
            AppLogger.info("COUNTDOWN", "Temp sobre crítico - Tiempo: ${elapsed}/${alertDelaySec}s, Restante: ${remaining}s")
        }
        // Alerta ACTIVA
        else if (device.alertActive) {
            alertBanner.visibility = View.VISIBLE
            alertBanner.setBackgroundColor(Color.parseColor("#F44336")) // Rojo para alerta
            
            if (device.alertAcknowledged) {
                tvAlertMessage.text = "🔕 ALERTA SILENCIADA"
                btnSilence.visibility = View.GONE
            } else {
                tvAlertMessage.text = "⚠️ ALERTA ACTIVA"
                btnSilence.visibility = View.VISIBLE
            }
        } else {
            alertBanner.visibility = View.GONE
            btnSilence.visibility = View.GONE
        }
    }
    
    private fun populateReefers() {
        reefersContainer.removeAllViews()
        
        for (device in devices) {
            val itemView = layoutInflater.inflate(R.layout.item_reefer, reefersContainer, false)
            
            val tvName = itemView.findViewById<TextView>(R.id.tvReeferName)
            val tvTemp = itemView.findViewById<TextView>(R.id.tvReeferTemp)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvReeferStatus)
            
            tvName.text = device.name
            
            val temp = device.tempAvg ?: -999f
            tvTemp.text = if (temp > -55 && temp < 125) String.format("%.1f°C", temp) else "--°C"
            
            if (device.isOnline) {
                tvStatus.text = "🟢"
                itemView.alpha = 1.0f
            } else {
                tvStatus.text = "🔴"
                itemView.alpha = 0.5f
            }
            
            // Seleccionar dispositivo al hacer click
            itemView.setOnClickListener {
                selectedDeviceId = device.deviceId
                refreshData()
                Toast.makeText(this, "Seleccionado: ${device.name}", Toast.LENGTH_SHORT).show()
            }
            
            // Resaltar el seleccionado
            if (device.deviceId == selectedDeviceId) {
                itemView.setBackgroundColor(Color.parseColor("#1A4CAF50"))
            }
            
            reefersContainer.addView(itemView)
        }
    }
    
    private fun silenceAlert() {
        AppLogger.info("SILENCE", "🔕 Usuario presionó SILENCIAR", "Device: $selectedDeviceId")
        
        // Detener alarma local inmediatamente
        MonitorService.silenceAlarm()
        btnSilence.visibility = View.GONE
        tvAlertMessage.text = "🔕 SILENCIANDO..."
        
        thread {
            try {
                // 1. Enviar comando SILENCE al ESP32
                AppLogger.command("SILENCE", "Enviando comando SILENCE al ESP32", selectedDeviceId)
                val silenceResult = SupabaseClient.sendSilenceCommand(selectedDeviceId)
                
                if (silenceResult) {
                    AppLogger.success("SILENCE", "✓ Comando SILENCE enviado correctamente")
                } else {
                    AppLogger.error("SILENCE", "✗ Falló envío de comando SILENCE")
                }
                
                // 2. Obtener alertas activas del dispositivo seleccionado
                val alerts = SupabaseClient.getActiveAlerts()
                val deviceAlerts = alerts.filter { it.deviceId == selectedDeviceId }
                AppLogger.info("SILENCE", "Alertas activas encontradas: ${deviceAlerts.size}")
                
                for (alert in deviceAlerts) {
                    AppLogger.info("SILENCE", "Reconociendo alerta ID: ${alert.id}")
                    SupabaseClient.acknowledgeAlert(alert.id)
                }
                
                runOnUiThread {
                    tvAlertMessage.text = "🔕 ALERTA SILENCIADA"
                    Toast.makeText(this, "✅ Alertas silenciadas", Toast.LENGTH_SHORT).show()
                    AppLogger.success("SILENCE", "✓ Proceso de silenciado completado")
                    refreshData()
                }
            } catch (e: Exception) {
                AppLogger.error("SILENCE", "✗ Error en silenceAlert", e.message)
                runOnUiThread {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun turnOffRelay() {
        Toast.makeText(this, "🔌 Enviando RELAY_OFF a $selectedDeviceId...", Toast.LENGTH_SHORT).show()
        
        thread {
            try {
                // Enviar comando RELAY_OFF a través de Supabase
                AppLogger.command("RELAY", "Enviando RELAY_OFF", selectedDeviceId)
                val result = SupabaseClient.sendCommand(selectedDeviceId, "RELAY_OFF")
                
                runOnUiThread {
                    if (result) {
                        Toast.makeText(this, "✅ Comando enviado a $selectedDeviceId (esperar ~5s)", Toast.LENGTH_LONG).show()
                        AppLogger.success("RELAY", "✓ Comando RELAY_OFF enviado a $selectedDeviceId")
                    } else {
                        Toast.makeText(this, "❌ Error enviando comando", Toast.LENGTH_SHORT).show()
                        AppLogger.error("RELAY", "✗ Error enviando RELAY_OFF")
                    }
                }
            } catch (e: Exception) {
                AppLogger.error("RELAY", "✗ Excepción", e.message)
                runOnUiThread {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Deseas cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                getSharedPreferences("parametican_cloud", MODE_PRIVATE).edit()
                    .clear()
                    .apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    private fun updateReadingAge(createdAt: String?) {
        if (createdAt == null) {
            tvReadingAge.text = "📡 Lectura ESP: Sin datos"
            tvReadingAge.setTextColor(Color.parseColor("#F44336"))
            return
        }
        
        try {
            // Parse ISO 8601 timestamp from Supabase
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            
            // Handle different timestamp formats
            val cleanTimestamp = createdAt.replace("+00", "").split(".")[0]
            val readingTime = sdf.parse(cleanTimestamp)
            
            if (readingTime == null) {
                tvReadingAge.text = "📡 Lectura ESP: Error"
                tvReadingAge.setTextColor(Color.parseColor("#F44336"))
                return
            }
            
            val now = java.util.Date()
            val diffMs = now.time - readingTime.time
            val diffSec = diffMs / 1000
            val diffMin = diffSec / 60
            val diffHours = diffMin / 60
            val diffDays = diffHours / 24
            
            val text = when {
                diffDays > 0 -> "hace ${diffDays} día${if (diffDays > 1) "s" else ""}"
                diffHours > 0 -> "hace ${diffHours} hora${if (diffHours > 1) "s" else ""}"
                diffMin > 0 -> "hace ${diffMin} min"
                else -> "hace ${diffSec} seg"
            }
            
            // Color según antigüedad
            val color = when {
                diffMin >= 5 || diffHours > 0 || diffDays > 0 -> "#F44336"  // Rojo - muy viejo
                diffMin >= 1 -> "#FF9800"  // Naranja - algo viejo
                else -> "#22c55e"  // Verde - fresco
            }
            
            tvReadingAge.text = "📡 Lectura ESP: $text"
            tvReadingAge.setTextColor(Color.parseColor(color))
        } catch (e: Exception) {
            tvReadingAge.text = "📡 Lectura ESP: --"
            tvReadingAge.setTextColor(Color.parseColor("#888888"))
        }
    }
}

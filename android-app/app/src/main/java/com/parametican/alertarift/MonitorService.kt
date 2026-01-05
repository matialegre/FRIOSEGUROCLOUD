package com.parametican.alertarift

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MonitorService : Service() {

    companion object {
        const val TAG = "MonitorService"
        const val CHANNEL_ID = "parametican_monitor"
        const val CHANNEL_ALERT_ID = "parametican_alert"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        
        var isRunning = false
        var lastData: String? = null
        var currentAlert = false
        
        // Instancia para poder llamar stopAlarm desde afuera
        var instance: MonitorService? = null
        
        fun silenceAlarm() {
            instance?.forceStopAlarm()
        }
    }

    private var serverIp = "192.168.1.100"
    private var handler: Handler? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alertActive = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollServer()
            handler?.postDelayed(this, 5000) // Cada 5 segundos
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        // Wake lock para mantener el servicio activo
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlertaRift::MonitorWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverIp = intent?.getStringExtra("server_ip") ?: "192.168.1.100"
        
        // Iniciar como foreground service
        val notification = createMonitorNotification("Conectando a $serverIp...")
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        wakeLock?.acquire(10*60*60*1000L) // 10 horas máximo
        
        // Iniciar polling
        handler = Handler(Looper.getMainLooper())
        handler?.post(pollRunnable)
        
        Log.d(TAG, "Servicio iniciado, monitoreando $serverIp")
        
        return START_STICKY // Reiniciar si el sistema lo mata
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        handler?.removeCallbacks(pollRunnable)
        forceStopAlarm()
        wakeLock?.release()
        Log.d(TAG, "Servicio detenido")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para monitoreo normal
            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo RIFT",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Estado del monitoreo de temperatura"
            }

            // Canal para alertas (máxima prioridad)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Alertas Críticas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de temperatura crítica"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true) // Ignorar No Molestar
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(monitorChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createMonitorNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🏔️ Parametican Silver")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun pollServer() {
        Thread {
            try {
                val url = URL("http://$serverIp/api/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    processResponse(response)
                } else {
                    updateNotification("⚠️ Error: ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error polling: ${e.message}")
                updateNotification("❌ Sin conexión a $serverIp")
            }
        }.start()
    }

    private fun processResponse(json: String) {
        try {
            val data = JSONObject(json)
            
            // Nuevo formato: sistema unificado
            val sensor = data.getJSONObject("sensor")
            val system = data.getJSONObject("system")
            val location = data.optJSONObject("location")
            
            val temp = sensor.getDouble("temp_avg").toFloat()
            val temp1 = sensor.optDouble("temp1", -999.0).toFloat()
            val temp2 = sensor.optDouble("temp2", -999.0).toFloat()
            val doorOpen = sensor.getBoolean("door_open")
            val hasAlert = system.getBoolean("alert_active")
            val alertMessage = system.optString("alert_message", "Alerta de temperatura")
            val isCritical = system.optBoolean("critical", false)
            val relayOn = system.optBoolean("relay_on", false)
            val uptime = system.optInt("uptime_sec", 0)
            val rssi = system.optInt("wifi_rssi", 0)
            val internet = system.optBoolean("internet", false)
            val locationName = location?.optString("detail", "") ?: ""
            
            lastData = "Temp: ${String.format("%.1f", temp)}°C | Puerta: ${if (doorOpen) "ABIERTA" else "Cerrada"}"
            
            // Enviar broadcast a la UI
            val updateIntent = Intent("RIFT_DATA_UPDATE").apply {
                putExtra("temperature", temp)
                putExtra("temp1", temp1)
                putExtra("temp2", temp2)
                putExtra("door_open", doorOpen)
                putExtra("relay_on", relayOn)
                putExtra("alert_active", hasAlert)
                putExtra("alert_message", alertMessage)
                putExtra("uptime", uptime)
                putExtra("rssi", rssi)
                putExtra("internet", internet)
                putExtra("location", locationName)
            }
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(updateIntent)
            
            if (hasAlert) {
                if (!this.alertActive) {
                    triggerAlarm(alertMessage)
                }
            } else {
                if (this.alertActive) {
                    stopAlarm()
                }
                updateNotification("✅ $lastData")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON: ${e.message}")
        }
    }

    private fun triggerAlarm(message: String) {
        alertActive = true
        currentAlert = true
        Log.w(TAG, "🚨 ALERTA: $message")

        // Notificación de alerta
        showAlertNotification(message)

        // Abrir actividad de alerta (pantalla completa)
        val alertIntent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("message", message)
        }
        startActivity(alertIntent)

        // Reproducir alarma
        startAlarmSound()

        // Vibrar
        startVibration()
    }

    private fun showAlertNotification(message: String) {
        val intent = Intent(this, AlertActivity::class.java).apply {
            putExtra("message", message)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle("🚨 ALERTA CRÍTICA")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun startAlarmSound() {
        try {
            // Usar alarma del sistema al máximo volumen
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(
                    this@MonitorService,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reproduciendo alarma: ${e.message}")
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    fun stopAlarm() {
        alertActive = false
        currentAlert = false
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        vibrator?.cancel()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(ALERT_NOTIFICATION_ID)
        
        updateNotification("✅ Alerta resuelta")
        Log.d(TAG, "Alarma detenida")
    }
    
    // Forzar detener alarma (llamado desde UI)
    fun forceStopAlarm() {
        Log.d(TAG, "FORZANDO DETENER ALARMA")
        alertActive = false
        currentAlert = false
        
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) { }
        try {
            mediaPlayer?.release()
        } catch (e: Exception) { }
        mediaPlayer = null
        
        try {
            vibrator?.cancel()
        } catch (e: Exception) { }
        
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(ALERT_NOTIFICATION_ID)
        } catch (e: Exception) { }
        
        updateNotification("🔕 Alarma silenciada")
    }

    private fun updateNotification(text: String) {
        val notification = createMonitorNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}

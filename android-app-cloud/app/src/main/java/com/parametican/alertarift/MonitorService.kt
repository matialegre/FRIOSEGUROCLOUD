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

/**
 * MonitorService para la versión CLOUD
 * Hace polling a Supabase cada 5 segundos para detectar alertas
 * Dispara alarma con sonido y vibración cuando hay alerta activa
 */
class MonitorService : Service() {

    companion object {
        const val TAG = "MonitorService"
        private const val CHANNEL_ID = "reefer_cloud_monitor"
        private const val CHANNEL_ALERT_ID = "reefer_cloud_alert_v2"  // v2 para forzar recreación con IMPORTANCE_MAX
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 999
        
        var isRunning = false
        var instance: MonitorService? = null
        
        // Estado compartido
        var currentAlertActive = false
        var alertSilencedByUser = false
        var lastAlertDeviceId: String? = null
        
        fun silenceAlarm() {
            instance?.forceStopAlarm()
            alertSilencedByUser = true
        }
    }

    private var handler: Handler? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForAlerts()
            handler?.postDelayed(this, 5000L) // Cada 5 segundos
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlertaRift::CloudMonitorWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createMonitorNotification("☁️ Conectando a Supabase...")
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        wakeLock?.acquire(10*60*60*1000L) // 10 horas máximo
        
        handler = Handler(Looper.getMainLooper())
        handler?.post(pollRunnable)
        
        Log.d(TAG, "✓ Servicio Cloud iniciado - Polling a Supabase cada 5s")
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        handler?.removeCallbacks(pollRunnable)
        forceStopAlarm()
        wakeLock?.release()
        Log.d(TAG, "Servicio Cloud detenido")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para monitoreo normal
            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo Cloud",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Estado del monitoreo en la nube"
            }

            // Canal para alertas (máxima prioridad - HEADS UP)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Alertas Críticas Cloud",
                NotificationManager.IMPORTANCE_MAX  // IMPORTANTE: MAX para heads-up
            ).apply {
                description = "Alertas de temperatura crítica - URGENTE"
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
                setBypassDnd(true)
                enableLights(true)
                lightColor = 0xFFFF0000.toInt()  // Rojo
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(monitorChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun checkForAlerts() {
        Thread {
            try {
                val devices = SupabaseClient.getDevicesStatus()
                val hasAlert = devices.any { it.alertActive }
                AppLogger.pollingResult(devices.size, hasAlert)
                
                // Log detallado de cada dispositivo
                for (device in devices) {
                    if (device.tempOverCritical || device.alertActive) {
                        AppLogger.info("DEVICE_STATE", "${device.deviceId}: " +
                            "alertActive=${device.alertActive}, " +
                            "alertAcknowledged=${device.alertAcknowledged}, " +
                            "tempOverCritical=${device.tempOverCritical}, " +
                            "highTempElapsedSec=${device.highTempElapsedSec}")
                    }
                }
                
                val alertDevice = devices.find { it.alertActive }
                
                if (alertDevice != null) {
                    AppLogger.alert("POLLING", "🚨 DISPOSITIVO CON ALERTA: ${alertDevice.deviceId}", 
                        "alertActive=${alertDevice.alertActive}, alertAcknowledged=${alertDevice.alertAcknowledged}, " +
                        "currentAlertActive=$currentAlertActive, alertSilencedByUser=$alertSilencedByUser")
                    
                    // Verificar si la alerta fue silenciada (acknowledged) en Supabase
                    if (alertDevice.alertAcknowledged) {
                        // Alerta silenciada desde otro dispositivo o desde el ESP32
                        AppLogger.alertSilenced(alertDevice.deviceId, "Supabase (remoto)")
                        stopAlarmSound()
                        stopVibration()
                        alertSilencedByUser = true
                        updateNotification("🔕 Alerta silenciada - ${alertDevice.name}")
                    } else if (!alertSilencedByUser) {
                        // Alerta activa y no silenciada - DISPARAR ALARMA
                        if (!currentAlertActive) {
                            // Nueva alerta!
                            currentAlertActive = true
                            lastAlertDeviceId = alertDevice.deviceId
                            val temp = alertDevice.tempAvg ?: 0f
                            val message = "🌡️ ALERTA: ${String.format("%.1f", temp)}°C - ${alertDevice.name}"
                            
                            AppLogger.alert("ALARM", "🔊🔊🔊 DISPARANDO ALARMA AHORA 🔊🔊🔊", message)
                            
                            Handler(Looper.getMainLooper()).post {
                                triggerAlarm(message)
                            }
                        } else {
                            AppLogger.info("ALARM", "Alarma ya activa, no redisparar")
                        }
                    } else {
                        AppLogger.info("ALARM", "Alarma silenciada por usuario, no disparar")
                    }
                    
                    // Enviar broadcast a MainActivity
                    sendUpdateBroadcast(alertDevice, true)
                    
                } else {
                    // No hay alertas activas - AHORA sí resetear todo
                    if (currentAlertActive || alertSilencedByUser) {
                        AppLogger.alertResolved(lastAlertDeviceId ?: "unknown")
                        currentAlertActive = false
                        alertSilencedByUser = false
                        lastAlertDeviceId = null
                        stopAlarmSound()
                        stopVibration()
                        updateNotification("✅ Sin alertas activas")
                        Log.d(TAG, "✓ Alerta resuelta - estados reseteados")
                    }
                    
                    // Enviar broadcast con el primer dispositivo
                    if (devices.isNotEmpty()) {
                        sendUpdateBroadcast(devices.first(), false)
                    }
                }
                
            } catch (e: Exception) {
                AppLogger.pollingError(e.message ?: "Unknown error")
                Log.e(TAG, "Error checking alerts: ${e.message}")
            }
        }.start()
    }
    
    private fun sendUpdateBroadcast(device: SupabaseClient.DeviceStatus, hasAlert: Boolean) {
        val updateIntent = Intent("REEFER_CLOUD_UPDATE").apply {
            putExtra("device_id", device.deviceId)
            putExtra("device_name", device.name)
            putExtra("temperature", device.tempAvg ?: 0f)
            putExtra("temp1", device.temp1 ?: 0f)
            putExtra("temp2", device.temp2 ?: 0f)
            putExtra("alert_active", device.alertActive)
            putExtra("alert_acknowledged", device.alertAcknowledged)
            putExtra("temp_over_critical", device.tempOverCritical)
            putExtra("high_temp_elapsed_sec", device.highTempElapsedSec)
            putExtra("relay_on", device.relayOn)
            putExtra("door_open", device.doorOpen)
            putExtra("has_alert", hasAlert)
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .sendBroadcast(updateIntent)
    }

    private fun triggerAlarm(message: String) {
        Log.w(TAG, "🚨 DISPARANDO ALARMA: $message")

        // Notificación de alerta
        showAlertNotification(message)

        // Abrir actividad de alerta (pantalla completa)
        try {
            val alertIntent = Intent(this, AlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("message", message)
            }
            startActivity(alertIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo AlertActivity: ${e.message}")
        }

        // Reproducir alarma
        startAlarmSound()

        // Vibrar
        startVibration()
    }

    private fun showAlertNotification(message: String) {
        val intent = Intent(this, AlertActivity::class.java).apply {
            putExtra("message", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción de silenciar directamente desde la notificación (sin abrir app)
        val silenceActionIntent = Intent(this, SilenceReceiver::class.java)
        val silenceActionPendingIntent = PendingIntent.getBroadcast(
            this, 0, silenceActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción de abrir la app
        val openIntent = Intent(this, AlertActivity::class.java).apply {
            putExtra("message", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 3, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle("🚨🚨 ALERTA CRÍTICA 🚨🚨")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Visible en pantalla de bloqueo
            .setColor(0xFFFF0000.toInt())  // Rojo
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🔕 DETENER ALARMA", silenceActionPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "📱 ABRIR APP", openPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("🚨🚨 ALERTA CRÍTICA 🚨🚨")
                .bigText("$message\n\n⬇️ Desliza para ver opciones\n🔕 DETENER ALARMA - Silencia sin abrir\n📱 ABRIR APP - Ver detalles"))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, notification)
        
        AppLogger.info("NOTIFICATION", "Notificación de alerta mostrada", message)
    }

    private fun startAlarmSound() {
        try {
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
            Log.d(TAG, "✓ Alarma sonando")
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
        Log.d(TAG, "✓ Vibración activada")
    }

    private fun stopAlarmSound() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) { }
    }
    
    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) { }
    }
    
    fun forceStopAlarm() {
        Log.d(TAG, "FORZANDO DETENER ALARMA")
        currentAlertActive = false
        
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
    
    private fun createMonitorNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("☁️ FrioSeguro Cloud")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

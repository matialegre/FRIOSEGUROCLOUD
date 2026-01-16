package com.parametican.alertarift

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SilenceReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.info("SILENCE_RECEIVER", "🔕 Silenciando desde notificación (sin abrir app)")
        
        // Detener sonido y vibración
        MonitorService.silenceAlarm()
        
        // Cancelar la notificación de alerta
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(MonitorService.ALERT_NOTIFICATION_ID)
        
        // Silenciar en Supabase en background
        Thread {
            try {
                val alerts = SupabaseClient.getActiveAlerts()
                val deviceIds = alerts.map { it.deviceId }.distinct()
                
                for (deviceId in deviceIds) {
                    SupabaseClient.sendSilenceCommand(deviceId)
                    SupabaseClient.acknowledgeDeviceAlert(deviceId)
                }
                
                for (alert in alerts) {
                    SupabaseClient.acknowledgeAlert(alert.id)
                }
                
                AppLogger.success("SILENCE_RECEIVER", "Alertas silenciadas en Supabase", "${alerts.size} alertas")
            } catch (e: Exception) {
                AppLogger.error("SILENCE_RECEIVER", "Error silenciando en Supabase", e.message ?: "")
            }
        }.start()
    }
}

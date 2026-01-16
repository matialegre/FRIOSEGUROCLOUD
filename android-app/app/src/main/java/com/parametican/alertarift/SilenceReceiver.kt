package com.parametican.alertarift

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SilenceReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SilenceReceiver", "🔕 Silenciando desde notificación (sin abrir app)")
        
        // Detener sonido y vibración
        MonitorService.silenceAlarm()
        
        // Cancelar la notificación de alerta
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(MonitorService.ALERT_NOTIFICATION_ID)
        
        // Enviar comando de silencio al ESP32
        val prefs = context.getSharedPreferences("reefer_prefs", Context.MODE_PRIVATE)
        val serverIp = prefs.getString("server_ip", "") ?: ""
        
        if (serverIp.isNotEmpty()) {
            Thread {
                try {
                    val url = java.net.URL("http://$serverIp/api/silence")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.doOutput = true
                    connection.outputStream.write("{}".toByteArray())
                    
                    val responseCode = connection.responseCode
                    Log.d("SilenceReceiver", "Comando SILENCE enviado a ESP32: $responseCode")
                    connection.disconnect()
                } catch (e: Exception) {
                    Log.e("SilenceReceiver", "Error enviando SILENCE a ESP32: ${e.message}")
                }
            }.start()
        }
    }
}

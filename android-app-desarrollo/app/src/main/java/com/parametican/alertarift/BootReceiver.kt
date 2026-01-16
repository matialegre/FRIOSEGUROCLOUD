package com.parametican.alertarift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Obtener IP guardada
            val prefs = context.getSharedPreferences("parametican", Context.MODE_PRIVATE)
            val serverIp = prefs.getString("server_ip", null)
            
            // Solo iniciar si hay IP configurada
            if (!serverIp.isNullOrEmpty()) {
                val serviceIntent = Intent(context, MonitorService::class.java)
                serviceIntent.putExtra("server_ip", serverIp)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}

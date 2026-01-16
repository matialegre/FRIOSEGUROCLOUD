package com.parametican.alertarift

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mostrar sobre pantalla de bloqueo
        showOnLockScreen()
        
        setContentView(R.layout.activity_alert)

        val tvMessage = findViewById<TextView>(R.id.tvAlertMessage)
        val btnSilence = findViewById<Button>(R.id.btnSilence)

        // Mostrar mensaje de alerta
        val message = intent.getStringExtra("message") ?: "Alerta de temperatura!"
        tvMessage.text = message

        // Botón silenciar
        btnSilence.setOnClickListener {
            silenceAlarm()
        }
        
        // Si viene con auto_silence, silenciar automáticamente
        if (intent.getBooleanExtra("auto_silence", false)) {
            silenceAlarm()
        }
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Mantener pantalla encendida
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun silenceAlarm() {
        AppLogger.info("ALERT_ACTIVITY", "🔕 Usuario presionó DETENER ALERTA")
        
        // PRIMERO: Detener sonido y vibración inmediatamente
        MonitorService.silenceAlarm()
        
        // Versión cloud: silenciar alertas en Supabase
        Thread {
            try {
                // Obtener alertas activas y silenciarlas
                val alerts = SupabaseClient.getActiveAlerts()
                val deviceIds = alerts.map { it.deviceId }.distinct()
                
                // Enviar comando SILENCE a cada dispositivo
                for (deviceId in deviceIds) {
                    SupabaseClient.sendSilenceCommand(deviceId)
                    SupabaseClient.acknowledgeDeviceAlert(deviceId)
                }
                
                // Marcar alertas como acknowledged
                for (alert in alerts) {
                    SupabaseClient.acknowledgeAlert(alert.id)
                }
                
                AppLogger.success("ALERT_ACTIVITY", "Alertas silenciadas en Supabase", "${alerts.size} alertas")
            } catch (e: Exception) {
                AppLogger.error("ALERT_ACTIVITY", "Error silenciando en Supabase", e.message ?: "")
            }
        }.start()
        
        // Cerrar esta actividad
        finish()
        
        // Volver a la app principal
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    override fun onBackPressed() {
        // No permitir cerrar con back, debe silenciar
        // super.onBackPressed()
    }
}

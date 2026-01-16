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
        // PRIMERO: Detener alarma local INMEDIATAMENTE
        MonitorService.silenceAlarm()
        
        // Llamar al endpoint del servidor para silenciar (en background)
        Thread {
            try {
                val prefs = getSharedPreferences("parametican", MODE_PRIVATE)
                val ip = prefs.getString("server_ip", "192.168.1.100")
                val url = java.net.URL("http://$ip/api/alert/ack")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.doOutput = true
                conn.outputStream.write("{}".toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                // Ignorar - la alarma local ya se detuvo
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

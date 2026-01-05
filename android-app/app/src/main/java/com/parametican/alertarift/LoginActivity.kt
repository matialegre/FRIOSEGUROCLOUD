package com.parametican.alertarift

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Verificar si ya está configurado
        val prefs = getSharedPreferences("parametican", MODE_PRIVATE)
        if (prefs.getBoolean("logged_in", false)) {
            goToMain()
            return
        }
        
        setContentView(R.layout.activity_login)

        val etServerIp = findViewById<EditText>(R.id.etServerIp)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnAutoConnect = findViewById<Button>(R.id.btnAutoConnect)
        val cardLogin = findViewById<LinearLayout>(R.id.cardLogin)
        val tvStatus = findViewById<TextView>(R.id.tvConnectionStatus)

        // Animación de entrada
        val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        slideUp.duration = 500
        cardLogin.startAnimation(slideUp)

        // Siempre mostrar rift.local por defecto
        etServerIp.setText("rift.local")

        // Auto-conectar con rift.local
        btnAutoConnect.setOnClickListener {
            tvStatus.text = "🔍 Buscando RIFT en la red..."
            tryAutoConnect(etServerIp, tvStatus)
        }

        btnConnect.setOnClickListener {
            val ip = etServerIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP del servidor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tvStatus.text = "🔄 Conectando..."
            testConnection(ip, tvStatus)
        }
        
        // NO auto-conectar automáticamente, esperar que el usuario presione el botón
        tvStatus.text = "Presiona el botón verde para buscar tu RIFT"
    }
    
    private fun tryAutoConnect(etServerIp: EditText, tvStatus: TextView) {
        val addresses = listOf(
            "rift.local",
            "192.168.0.11:3000",  // Test server
            "192.168.1.100",
            "192.168.0.100",
            "192.168.4.1"  // AP mode
        )
        
        thread {
            for (addr in addresses) {
                try {
                    val url = URL("http://$addr/api/status")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    val code = conn.responseCode
                    conn.disconnect()
                    
                    if (code == 200) {
                        runOnUiThread {
                            etServerIp.setText(addr)
                            tvStatus.text = "✅ RIFT encontrado en $addr"
                            
                            // Guardar y conectar
                            getSharedPreferences("parametican", MODE_PRIVATE).edit()
                                .putString("server_ip", addr)
                                .putBoolean("logged_in", true)
                                .apply()
                            
                            // Esperar 1 segundo y conectar
                            etServerIp.postDelayed({ goToMain() }, 1000)
                        }
                        return@thread
                    }
                } catch (e: Exception) {
                    // Continuar con siguiente dirección
                }
            }
            
            runOnUiThread {
                tvStatus.text = "❌ No se encontró RIFT. Ingresa la IP manualmente."
            }
        }
    }
    
    private fun testConnection(ip: String, tvStatus: TextView) {
        thread {
            try {
                val url = URL("http://$ip/api/status")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                val code = conn.responseCode
                conn.disconnect()
                
                if (code == 200) {
                    runOnUiThread {
                        tvStatus.text = "✅ Conectado"
                        getSharedPreferences("parametican", MODE_PRIVATE).edit()
                            .putString("server_ip", ip)
                            .putBoolean("logged_in", true)
                            .apply()
                        goToMain()
                    }
                } else {
                    runOnUiThread { tvStatus.text = "❌ Error: código $code" }
                }
            } catch (e: Exception) {
                runOnUiThread { tvStatus.text = "❌ Error: ${e.message}" }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

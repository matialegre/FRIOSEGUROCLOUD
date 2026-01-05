package com.parametican.alertarift

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Animaciones
        val logo = findViewById<ImageView>(R.id.ivLogo)
        val title = findViewById<TextView>(R.id.tvTitle)
        val subtitle = findViewById<TextView>(R.id.tvSubtitle)

        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeIn.duration = 1000

        logo.startAnimation(fadeIn)
        
        Handler(Looper.getMainLooper()).postDelayed({
            title.alpha = 1f
            title.startAnimation(fadeIn)
        }, 500)

        Handler(Looper.getMainLooper()).postDelayed({
            subtitle.alpha = 1f
            subtitle.startAnimation(fadeIn)
        }, 1000)

        // Ir a login después de 2.5 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2500)
    }
}

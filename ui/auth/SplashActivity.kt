package com.fitnesslemon.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.fitnesslemon.app.R
import com.fitnesslemon.app.ui.main.MainActivity
import com.fitnesslemon.app.utils.PreferencesManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // ✅ ПРОВЕРЯЕМ ТОКЕН И СТАТУС
        val token = try {
            PreferencesManager.getToken()
        } catch (e: Exception) {
            null
        }

        val isLoggedIn = try {
            PreferencesManager.isLoggedIn()
        } catch (e: Exception) {
            false
        }

        // ✅ ЕСЛИ ЕСТЬ ТОКЕН И ВХОД ВЫПОЛНЕН - АВТОВХОД
        val hasValidSession = !token.isNullOrEmpty() && isLoggedIn

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = if (hasValidSession) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1000)
    }
}
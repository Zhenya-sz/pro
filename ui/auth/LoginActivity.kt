package com.fitnesslemon.app.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.fitnesslemon.app.databinding.ActivityLoginBinding
import com.fitnesslemon.app.ui.main.MainActivity
import com.fitnesslemon.app.utils.PreferencesManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ ПРОВЕРЯЕМ ТОКЕН И СТАТУС ПРИ ЗАПУСКЕ
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

        // ✅ ЕСЛИ ЕСТЬ ТОКЕН И ВХОД ВЫПОЛНЕН - ПЕРЕХОДИМ В MAIN
        if (!token.isNullOrEmpty() && isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        // Форматирование телефона
        binding.etPhone.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            private var lastFormatted = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return

                val raw = s?.toString() ?: ""
                val digits = raw.replace("[^0-9]".toRegex(), "")
                val limitedDigits = if (digits.length > 10) digits.substring(0, 10) else digits

                val formatted = when (limitedDigits.length) {
                    0 -> ""
                    1, 2, 3 -> limitedDigits
                    4, 5, 6 -> "(${limitedDigits.substring(0, 3)}) ${limitedDigits.substring(3)}"
                    7, 8 -> "(${limitedDigits.substring(0, 3)}) ${limitedDigits.substring(3, 6)}-${limitedDigits.substring(6)}"
                    else -> {
                        val part1 = limitedDigits.substring(0, 3)
                        val part2 = limitedDigits.substring(3, 6)
                        val part3 = limitedDigits.substring(6, 8)
                        val part4 = limitedDigits.substring(8, minOf(10, limitedDigits.length))
                        "($part1) $part2-$part3-$part4"
                    }
                }

                if (formatted != lastFormatted) {
                    lastFormatted = formatted
                    isUpdating = true
                    binding.etPhone.setText(formatted)
                    binding.etPhone.setSelection(formatted.length)
                    isUpdating = false
                }

                viewModel.onPhoneChanged(limitedDigits)
            }
        })

        binding.btnLogin.setOnClickListener {
            val password = binding.etPassword.text.toString()
            viewModel.onPasswordChanged(password)
            viewModel.login()
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.tvSupport.setOnClickListener {
            openSupportChat()
        }
    }

    private fun openSupportChat() {
        try {
            val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/79969518673"))
            startActivity(whatsappIntent)
        } catch (e: Exception) {
            try {
                val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/fitnesslemonstudiobot"))
                startActivity(telegramIntent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Установите WhatsApp или Telegram для связи с поддержкой", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this, Observer { state ->
            when (state) {
                is LoginState.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.btnLogin.isEnabled = false
                    binding.btnLogin.text = "ВХОД..."
                }
                is LoginState.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "ВОЙТИ"

                    // ✅ ПОКАЗЫВАЕМ СООБЩЕНИЕ
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()

                    // ✅ ПЕРЕХОД В MAIN
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
                is LoginState.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "ВОЙТИ"

                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // ✅ ПРОВЕРЯЕМ ТОКЕН И СТАТУС ПРИ ВОЗВРАТЕ
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

        if (!token.isNullOrEmpty() && isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
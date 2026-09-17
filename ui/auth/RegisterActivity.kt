package com.fitnesslemon.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.fitnesslemon.app.databinding.ActivityRegisterBinding
import com.fitnesslemon.app.ui.main.MainActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels {
        RegisterViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.etFullName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onFullNameChanged(s?.toString() ?: "")
            }
        })

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onPasswordChanged(s?.toString() ?: "")
            }
        })

        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onEmailChanged(s?.toString() ?: "")
            }
        })

        binding.btnRegister.setOnClickListener {
            viewModel.register()
        }

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.registerState.observe(this, Observer { state ->
            when (state) {
                is RegisterState.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.btnRegister.isEnabled = false
                    binding.btnRegister.text = "РЕГИСТРАЦИЯ..."
                }
                is RegisterState.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "ЗАРЕГИСТРИРОВАТЬСЯ"

                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
                is RegisterState.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "ЗАРЕГИСТРИРОВАТЬСЯ"

                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        })
    }
}
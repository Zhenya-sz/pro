package com.fitnesslemon.app.ui.auth

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.RegisterRequest
import com.fitnesslemon.app.utils.DeviceUtils
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.launch
import java.io.IOException

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    data class Success(val message: String) : RegisterState()
    data class Error(val message: String) : RegisterState()
}

class RegisterViewModel(private val context: Context) : ViewModel() {

    private val _registerState = MutableLiveData<RegisterState>(RegisterState.Idle)
    val registerState: LiveData<RegisterState> = _registerState

    private val _phone = MutableLiveData("")
    val phone: LiveData<String> = _phone

    private val _fullName = MutableLiveData("")
    val fullName: LiveData<String> = _fullName

    private val _password = MutableLiveData("")
    val password: LiveData<String> = _password

    private val _email = MutableLiveData("")
    val email: LiveData<String> = _email

    fun onPhoneChanged(phone: String) {
        val cleaned = phone.replace("[^0-9]".toRegex(), "")
        _phone.value = cleaned
    }

    fun onFullNameChanged(fullName: String) {
        _fullName.value = fullName
    }

    fun onPasswordChanged(password: String) {
        _password.value = password
    }

    fun onEmailChanged(email: String) {
        _email.value = email
    }

    fun register() {
        val phone = _phone.value ?: ""
        val fullName = _fullName.value ?: ""
        val password = _password.value ?: ""
        val email = _email.value ?: ""

        // Валидация
        if (phone.isBlank() || fullName.isBlank() || password.isBlank()) {
            _registerState.value = RegisterState.Error("Заполните все обязательные поля")
            return
        }

        if (phone.length != 10) {
            _registerState.value = RegisterState.Error("Номер телефона должен содержать 10 цифр")
            return
        }

        if (fullName.split(" ").size < 2) {
            _registerState.value = RegisterState.Error("Введите полное ФИО (Имя Фамилия)")
            return
        }

        if (password.length < 6) {
            _registerState.value = RegisterState.Error("Пароль должен быть минимум 6 символов")
            return
        }

        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _registerState.value = RegisterState.Error("Некорректный email")
            return
        }

        viewModelScope.launch {
            _registerState.value = RegisterState.Loading

            try {
                val deviceInfo = DeviceUtils.getDeviceInfo(context)

                val request = RegisterRequest(
                    fullName = fullName,
                    phone = phone,
                    email = if (email.isNotBlank()) email else null,
                    password = password,
                    deviceInfo = deviceInfo
                )

                println("🔍 Регистрация: phone=$phone, name=$fullName, email=$email")
                println("🔍 Device: IMEI=${deviceInfo.imei}, AndroidID=${deviceInfo.androidId}")

                val response = ApiClient.apiService.register(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        val token = body.data.token
                        val userId = body.data.userId
                        val userDisplayName = body.data.userDisplayName
                        val userEmail = body.data.userEmail
                        val userRole = body.data.userRole

                        PreferencesManager.saveUserData(
                            token = token,
                            userId = userId.toLong(),
                            userName = userDisplayName,
                            userEmail = userEmail,
                            userRole = userRole
                        )

                        _registerState.value = RegisterState.Success("Регистрация выполнена успешно!")
                    } else {
                        val errorMessage = body?.message ?: "Ошибка регистрации"
                        _registerState.value = RegisterState.Error(errorMessage)
                    }
                } else {
                    // Обрабатываем ошибку HTTP - извлекаем сообщение из ответа
                    val errorBody = response.errorBody()?.string()
                    val message = extractErrorMessage(errorBody)
                    _registerState.value = RegisterState.Error(message)
                }

            } catch (e: IOException) {
                println("❌ IOException: ${e.message}")
                _registerState.value = RegisterState.Error("Ошибка сети: ${e.message}")
            } catch (e: Exception) {
                println("❌ Exception: ${e.message}")
                e.printStackTrace()
                _registerState.value = RegisterState.Error("Ошибка: ${e.message}")
            }
        }
    }

    // Функция для извлечения сообщения из JSON ответа
    private fun extractErrorMessage(json: String?): String {
        if (json.isNullOrEmpty()) return "Ошибка сети"

        return try {
            // Ищем "message":"..." в строке
            val regex = "\"message\":\"([^\"]*)\"".toRegex()
            val match = regex.find(json)
            match?.groupValues?.get(1) ?: "Ошибка сети"
        } catch (e: Exception) {
            "Ошибка сети"
        }
    }

    fun resetState() {
        _registerState.value = RegisterState.Idle
    }
}
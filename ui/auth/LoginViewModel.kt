package com.fitnesslemon.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.LoginRequest
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.launch
import java.io.IOException

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val message: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>(LoginState.Idle)
    val loginState: LiveData<LoginState> = _loginState

    private val _phone = MutableLiveData("")
    val phone: LiveData<String> = _phone

    private val _password = MutableLiveData("")
    val password: LiveData<String> = _password

    fun onPhoneChanged(phone: String) {
        val cleaned = phone.replace("[^0-9]".toRegex(), "")
        _phone.value = cleaned
    }

    fun onPasswordChanged(password: String) {
        _password.value = password
    }

    fun login() {
        val phone = _phone.value ?: ""
        val password = _password.value ?: ""

        if (phone.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Заполните все поля")
            return
        }

        if (phone.length != 10) {
            _loginState.value = LoginState.Error("Номер телефона должен содержать 10 цифр")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            try {
                println("🔍 Пытаемся войти с телефоном: $phone")

                val request = LoginRequest(phone = phone, password = password)
                val response = ApiClient.apiService.login(request)

                println("🔍 Код ответа: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        val data = body.data
                        val token = data.token
                        val userId = data.userId
                        val userDisplayName = data.userDisplayName
                        val userEmail = data.userEmail
                        val userRole = data.userRole

                        println("✅ Получена роль: '$userRole'")

                        // ✅ СОХРАНЯЕМ ВСЕ ДАННЫЕ
                        PreferencesManager.saveToken(token)
                        PreferencesManager.saveUserId(userId)
                        PreferencesManager.saveUserName(userDisplayName)
                        PreferencesManager.saveUserRole(userRole)
                        PreferencesManager.saveIsLoggedIn(true) // ✅ ДОБАВЛЯЕМ

                        println("✅ Токен сохранен: ${token.take(20)}...")
                        println("✅ User ID: $userId")
                        println("✅ User Name: $userDisplayName")
                        println("✅ User Role: $userRole")
                        println("✅ Статус входа сохранен")

                        fetchUserProfile(token)

                        _loginState.value = LoginState.Success("Вход выполнен успешно")
                    } else {
                        val errorMessage = body?.message ?: "Ошибка входа"
                        _loginState.value = LoginState.Error(errorMessage)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val message = extractErrorMessage(errorBody)
                    _loginState.value = LoginState.Error(message)
                }
            } catch (e: IOException) {
                println("🔍 IOException: ${e.message}")
                _loginState.value = LoginState.Error("Ошибка сети: ${e.message}")
            } catch (e: Exception) {
                println("🔍 Exception: ${e.message}")
                e.printStackTrace()
                _loginState.value = LoginState.Error("Ошибка: ${e.message}")
            }
        }
    }

    private fun extractErrorMessage(json: String?): String {
        if (json.isNullOrEmpty()) return "Ошибка сети"
        return try {
            val regex = "\"message\":\"([^\"]*)\"".toRegex()
            val match = regex.find(json)
            match?.groupValues?.get(1) ?: "Ошибка сети"
        } catch (e: Exception) {
            "Ошибка сети"
        }
    }

    private suspend fun fetchUserProfile(token: String) {
        try {
            println("🔍 Получаем профиль пользователя")
            val response = ApiClient.apiService.getUserProfile("Bearer $token")

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    val user = body.data
                    if (!user.avatar.isNullOrEmpty()) {
                        PreferencesManager.saveUserAvatar(user.avatar)
                    }
                    PreferencesManager.saveRemainingWorkouts(user.remainingWorkouts)
                    println("✅ Профиль загружен: ${user.name} (${user.role})")
                    println("   - Аватар: ${user.avatar}")
                    println("   - Остаток тренировок: ${user.remainingWorkouts}")
                }
            } else {
                println("❌ Ошибка получения профиля: ${response.code()}")
            }
        } catch (e: Exception) {
            println("❌ Ошибка при получении профиля: ${e.message}")
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}
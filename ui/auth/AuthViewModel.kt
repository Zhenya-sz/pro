package com.fitnesslemon.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiService
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val token = PreferencesManager.getToken()
        _uiState.value = AuthUiState(
            isLoading = false,
            isLoggedIn = !token.isNullOrBlank()
        )
    }

    fun login(phone: String, password: String) {
        if (phone.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Телефон и пароль обязательны")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val body = mapOf(
                    "phone" to phone.trim(),
                    "password" to password
                )

                val response = withContext(Dispatchers.IO) {
                    api.login(body)
                }

                if (!response.isSuccessful || response.body() == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Ошибка авторизации"
                    )
                    return@launch
                }

                val data = response.body()!!
                val token = data.token ?: ""
                val refreshToken = data.refreshToken
                val expiresIn = data.expiresIn ?: 86400L

                if (token.isNotBlank()) {
                    PreferencesManager.saveToken(token)
                    PreferencesManager.saveRefreshToken(refreshToken ?: "")
                    PreferencesManager.saveExpiresAt(System.currentTimeMillis() + (expiresIn * 1000L))
                    PreferencesManager.setAuthToken(token, refreshToken, expiresIn)
                }

                _uiState.value = AuthUiState(
                    isLoading = false,
                    isLoggedIn = true,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = AuthUiState(
                    isLoading = false,
                    isLoggedIn = false,
                    error = e.message ?: "Не удалось войти"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.logout()
                }
            } catch (_: Exception) {
                // ignore network logout errors
            } finally {
                PreferencesManager.clearUserData()
                _uiState.value = AuthUiState(isLoading = false, isLoggedIn = false, error = null)
            }
        }
    }

    fun refreshSessionIfNeeded() {
        val token = PreferencesManager.getToken()
        if (token.isNullOrBlank()) return

        val expiresAt = PreferencesManager.getExpiresAt()
        if (expiresAt > 0L && System.currentTimeMillis() < expiresAt) return

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.refreshToken(
                        mapOf(
                            "refresh_token" to (PreferencesManager.getRefreshToken() ?: "")
                        )
                    )
                }
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    val newToken = data.token ?: return@launch
                    PreferencesManager.saveToken(newToken)
                    PreferencesManager.saveRefreshToken(data.refreshToken ?: "")
                    PreferencesManager.saveExpiresAt(System.currentTimeMillis() + ((data.expiresIn ?: 86400L) * 1000L))
                }
            } catch (_: Exception) {
                PreferencesManager.clearUserData()
            }
        }
    }
}

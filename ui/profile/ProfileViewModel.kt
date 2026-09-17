package com.fitnesslemon.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.Booking
import com.fitnesslemon.app.data.models.User
import com.fitnesslemon.app.data.models.UpdateProfileRequest
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import java.text.SimpleDateFormat
import java.util.*

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(
        val user: User,
        val history: List<Booking>
    ) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Loading : UpdateState()
    data class Success(val message: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class ProfileViewModel : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profileState: StateFlow<ProfileState> = _profileState

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val _totalVisits = MutableStateFlow(0)
    val totalVisits: StateFlow<Int> = _totalVisits

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _profileState.value = ProfileState.Error("Токен не найден")
                    return@launch
                }

                println("🔍 Загружаем профиль для экрана профиля")

                val profileResponse = ApiClient.apiService.getUserProfile("Bearer $token")

                if (profileResponse.isSuccessful) {
                    val response = profileResponse.body()!!
                    val user = response.data
                    println("✅ Профиль загружен: ${user.name}")
                    println("✅ Аватар URL: ${user.avatar}")
                    println("✅ ID пользователя: ${user.id}")
                    println("✅ Email: ${user.email}")
                    println("✅ Телефон: ${user.phone}")
                    println("✅ Остаток тренировок: ${user.remainingWorkouts}")

                    // Сохраняем остаток в Preferences
                    PreferencesManager.saveRemainingWorkouts(user.remainingWorkouts)

                    val historyResponse = ApiClient.apiService.getMyBookings("Bearer $token")

                    val history = if (historyResponse.isSuccessful) {
                        val responseBody = historyResponse.body()
                        if (responseBody != null && responseBody.success) {
                            filterPastBookings(responseBody.data)
                        } else {
                            emptyList()
                        }
                    } else {
                        println("❌ Ошибка загрузки истории: ${historyResponse.code()}")
                        emptyList()
                    }

                    println("✅ История загружена: ${history.size} записей")

                    val totalVisits = history.size
                    _totalVisits.value = totalVisits

                    _profileState.value = ProfileState.Success(
                        user = user,
                        history = history
                    )
                } else {
                    println("❌ Ошибка загрузки профиля: ${profileResponse.code()}")
                    val errorBody = profileResponse.errorBody()?.string()
                    println("❌ Ошибка: $errorBody")
                    _profileState.value = ProfileState.Error("Ошибка загрузки профиля: ${profileResponse.code()}")
                }
            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
                e.printStackTrace()
                _profileState.value = ProfileState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }

    fun updateProfile(birthDate: String, address: String) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _updateState.value = UpdateState.Error("Токен не найден")
                    return@launch
                }

                println("🔍 Обновляем профиль")
                println("📝 Дата рождения: $birthDate")
                println("📝 Адрес: $address")

                val request = UpdateProfileRequest(
                    birthDate = birthDate,
                    address = address
                )
                val response = ApiClient.apiService.updateProfile("Bearer $token", request)

                if (response.isSuccessful) {
                    response.body()?.let { apiResponse ->
                        if (apiResponse.success) {
                            println("✅ Профиль обновлен")
                            loadProfile()
                            _updateState.value = UpdateState.Success(apiResponse.message)
                        } else {
                            println("❌ Ошибка обновления: ${apiResponse.message}")
                            _updateState.value = UpdateState.Error(apiResponse.message)
                        }
                    } ?: run {
                        println("❌ Пустой ответ от сервера")
                        _updateState.value = UpdateState.Error("Пустой ответ от сервера")
                    }
                } else {
                    println("❌ Ошибка обновления: ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка: $errorBody")
                    _updateState.value = UpdateState.Error("Ошибка обновления: ${response.code()}")
                }

            } catch (e: Exception) {
                println("❌ Ошибка обновления: ${e.message}")
                e.printStackTrace()
                _updateState.value = UpdateState.Error(e.message ?: "Ошибка обновления")
            }
        }
    }

    fun uploadAvatar(part: MultipartBody.Part) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _updateState.value = UpdateState.Error("Токен не найден")
                    return@launch
                }

                println("🔍 Загружаем аватар")

                val response = ApiClient.apiService.uploadAvatar("Bearer $token", part)

                if (response.isSuccessful) {
                    response.body()?.let { avatarResponse ->
                        println("✅ Ответ от сервера:")
                        println("   - success: ${avatarResponse.success}")
                        println("   - message: ${avatarResponse.message}")
                        println("   - data.avatarUrl: ${avatarResponse.data?.avatarUrl}")

                        if (avatarResponse.success) {
                            val newAvatarUrl = avatarResponse.data?.avatarUrl
                            println("✅ Аватар успешно загружен на сервер")
                            println("✅ Новый URL аватара: $newAvatarUrl")

                            if (!newAvatarUrl.isNullOrEmpty()) {
                                updateAvatarInState(newAvatarUrl)
                                PreferencesManager.saveUserAvatar(newAvatarUrl)
                            }

                            loadProfile()
                            _updateState.value = UpdateState.Success(avatarResponse.message)
                        } else {
                            println("❌ Ошибка загрузки аватара: ${avatarResponse.message}")
                            _updateState.value = UpdateState.Error(avatarResponse.message)
                        }
                    } ?: run {
                        println("❌ Пустой ответ от сервера")
                        _updateState.value = UpdateState.Error("Пустой ответ от сервера")
                    }
                } else {
                    println("❌ Ошибка HTTP ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка: $errorBody")
                    _updateState.value = UpdateState.Error("Ошибка загрузки аватара: ${response.code()}")
                }

            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
                e.printStackTrace()
                _updateState.value = UpdateState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }

    private fun updateAvatarInState(newAvatarUrl: String?) {
        val currentState = _profileState.value
        if (currentState is ProfileState.Success) {
            val currentUser = currentState.user
            val updatedUser = currentUser.copy(avatar = newAvatarUrl)
            _profileState.value = currentState.copy(user = updatedUser)
            println("✅ Аватар обновлен в UI: $newAvatarUrl")
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _updateState.value = UpdateState.Error("Токен не найден")
                    return@launch
                }

                println("🔍 Удаляем аватар")

                val response = ApiClient.apiService.deleteAvatar("Bearer $token")

                if (response.isSuccessful) {
                    response.body()?.let { apiResponse ->
                        if (apiResponse.success) {
                            println("✅ Аватар удален")
                            updateAvatarInState(null)
                            PreferencesManager.saveUserAvatar("")
                            loadProfile()
                            _updateState.value = UpdateState.Success(apiResponse.message)
                        } else {
                            println("❌ Ошибка удаления: ${apiResponse.message}")
                            _updateState.value = UpdateState.Error(apiResponse.message)
                        }
                    } ?: run {
                        println("❌ Пустой ответ от сервера")
                        _updateState.value = UpdateState.Error("Пустой ответ от сервера")
                    }
                } else {
                    println("❌ Ошибка удаления аватара: ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка: $errorBody")
                    _updateState.value = UpdateState.Error("Ошибка удаления аватара: ${response.code()}")
                }

            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
                e.printStackTrace()
                _updateState.value = UpdateState.Error(e.message ?: "Ошибка удаления")
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    private fun filterPastBookings(bookings: List<Booking>): List<Booking> {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        return bookings.filter { booking ->
            try {
                val bookingDate = dateFormat.parse(booking.formattedDate)
                bookingDate != null && bookingDate.before(currentDate)
            } catch (e: Exception) {
                println("❌ Ошибка парсинга даты: ${booking.formattedDate}")
                false
            }
        }.sortedByDescending { booking ->
            try {
                dateFormat.parse(booking.formattedDate)?.time ?: 0
            } catch (e: Exception) {
                0L
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            println("🔍 Выход из аккаунта")
            PreferencesManager.clearUserData()
        }
    }
}
package com.fitnesslemon.app.ui.admin

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.CreateNewsRequest
import com.fitnesslemon.app.data.api.UpdateNewsRequest
import com.fitnesslemon.app.data.api.CreateSubscriptionRequest
import com.fitnesslemon.app.data.api.SellSubscriptionRequest
import com.fitnesslemon.app.data.models.*
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AdminViewModel : ViewModel() {

    private val _adminStats = MutableLiveData<AdminStats?>()
    val adminStats: LiveData<AdminStats?> = _adminStats

    private val _users = MutableLiveData<List<AdminUser>>(emptyList())
    val users: LiveData<List<AdminUser>> = _users

    private val _workouts = MutableLiveData<List<AdminWorkout>>(emptyList())
    val workouts: LiveData<List<AdminWorkout>> = _workouts

    private val _subscriptions = MutableLiveData<List<AdminSubscription>>(emptyList())
    val subscriptions: LiveData<List<AdminSubscription>> = _subscriptions

    private val _trainers = MutableLiveData<List<Trainer>>(emptyList())
    val trainers: LiveData<List<Trainer>> = _trainers

    private val _news = MutableLiveData<List<News>>(emptyList())
    val news: LiveData<List<News>> = _news

    private val _settings = MutableLiveData<Settings?>()
    val settings: LiveData<Settings?> = _settings

    private val _weeklySchedule = MutableLiveData<Map<Int, List<AdminWorkout>>>()
    val weeklySchedule: LiveData<Map<Int, List<AdminWorkout>>> = _weeklySchedule

    private val _weekRange = MutableLiveData<String>()
    val weekRange: LiveData<String> = _weekRange

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    private val _createdNewsId = MutableLiveData<Int?>()
    val createdNewsId: LiveData<Int?> = _createdNewsId

    // ============================================================
    // 🆕 ШАБЛОНЫ РАСПИСАНИЯ
    // ============================================================

    private val _templates = MutableLiveData<List<ScheduleTemplate>>(emptyList())
    val templates: LiveData<List<ScheduleTemplate>> = _templates

    private val _templateOperationState = MutableLiveData<TemplateOperationState>(TemplateOperationState.Idle)
    val templateOperationState: LiveData<TemplateOperationState> = _templateOperationState

    private val _scheduleHistory = MutableLiveData<List<ScheduleHistoryEntry>>(emptyList())
    val scheduleHistory: LiveData<List<ScheduleHistoryEntry>> = _scheduleHistory

    private val _scheduleVersions = MutableLiveData<List<ScheduleVersion>>(emptyList())
    val scheduleVersions: LiveData<List<ScheduleVersion>> = _scheduleVersions

    private var allUsers: List<AdminUser> = emptyList()
    private var allWorkouts: List<AdminWorkout> = emptyList()
    private var allSubscriptions: List<AdminSubscription> = emptyList()
    private var allNews: List<News> = emptyList()

    private var appContext: Context? = null

    // ============================================================
    // STATE SEALED CLASSES
    // ============================================================

    sealed class TemplateOperationState {
        object Idle : TemplateOperationState()
        object Loading : TemplateOperationState()
        data class Success(val message: String) : TemplateOperationState()
        data class Error(val message: String) : TemplateOperationState()
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun refreshAll() {
        loadAdminStats()
        loadUsers()
        loadWorkouts()
        loadSubscriptions()
        loadTrainers()
        loadSettings()
        loadWeeklyScheduleForDate(Date())
        loadNews()
        loadTemplates()
    }

    // ============================================================
    // СТАТИСТИКА
    // ============================================================

    fun loadAdminStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Загрузка статистики для админ-панели")

                val response = ApiClient.adminApiService.getAdminStats("Bearer $token")

                if (response.isSuccessful) {
                    response.body()?.let { stats ->
                        println("✅ Статистика загружена: сегодня ${stats.todayVisits} посещений")
                        _adminStats.value = stats
                    } ?: run {
                        _errorMessage.value = "Пустой ответ от сервера"
                        _adminStats.value = null
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка загрузки статистики: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка загрузки статистики: ${response.code()}"
                    _adminStats.value = null
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
                _adminStats.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============================================================
    // НАСТРОЙКИ
    // ============================================================

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Загрузка настроек")

                val response = ApiClient.adminApiService.getSettings("Bearer $token")

                if (response.isSuccessful) {
                    response.body()?.let { settings ->
                        println("✅ Настройки загружены")
                        _settings.value = settings
                    } ?: run {
                        println("⚠️ Пустой ответ от сервера, используем настройки по умолчанию")
                        _settings.value = getDefaultSettings()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка загрузки настроек: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка загрузки настроек: ${response.code()}"
                    _settings.value = getDefaultSettings()
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
                _settings.value = getDefaultSettings()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Сохранение настроек")

                val response = ApiClient.adminApiService.updateSettings("Bearer $token", settings)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Настройки сохранены")
                        _errorMessage.value = "Настройки сохранены"
                        _settings.value = settings
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка сохранения"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка сохранения настроек: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка сохранения настроек: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getDefaultSettings(): Settings {
        return Settings(
            clubName = "Fitness Lemon Studio",
            address = "ул. Ленина, 10, г. Москва",
            phone = "+7 (495) 123-45-67",
            email = "info@fitnesslemon.ru",
            maxBookingsPerDay = 2,
            cancellationHours = 2,
            autoCancelNoShow = true,
            autoCancelMinutes = 15,
            allowBookingWithoutSubscription = false,
            pushEnabled = true,
            emailConfirmations = true,
            reminderHours = 1,
            privacyPolicyUrl = "https://fitnesslemon.ru/privacy",
            termsUrl = "https://fitnesslemon.ru/terms",
            smsReminders = false,
            telegramBotActive = false,
            telegramBotToken = null
        )
    }

    // ============================================================
    // ТРЕНЕРЫ
    // ============================================================

    fun loadTrainers() {
        viewModelScope.launch {
            try {
                println("🔍 Загрузка списка тренеров")
                val response = ApiClient.apiService.getTrainers()
                if (response.isSuccessful) {
                    val trainersResponse = response.body()
                    if (trainersResponse != null && trainersResponse.success) {
                        val trainers = trainersResponse.data
                        println("✅ Загружено тренеров: ${trainers.size}")
                        _trainers.value = trainers
                    } else {
                        println("❌ Пустой ответ от сервера")
                        _trainers.value = emptyList()
                    }
                } else {
                    println("❌ Ошибка загрузки тренеров: ${response.code()}")
                    _trainers.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети при загрузке тренеров: ${e.message}")
                _trainers.value = emptyList()
            }
        }
    }

    // ============================================================
    // ПОЛЬЗОВАТЕЛИ
    // ============================================================

    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Загрузка списка пользователей")

                val response = ApiClient.adminApiService.getUsers(
                    token = "Bearer $token",
                    search = null,
                    role = null,
                    page = 1,
                    perPage = 100
                )

                if (response.isSuccessful) {
                    val adminResponse = response.body()
                    if (adminResponse != null && adminResponse.success) {
                        allUsers = adminResponse.data.map { apiUser ->
                            AdminUser(
                                id = apiUser.id,
                                name = apiUser.name,
                                email = apiUser.email,
                                phone = apiUser.phone,
                                role = apiUser.role,
                                subscriptionName = apiUser.subscriptionName,
                                subscriptionExpiry = apiUser.subscriptionExpiry,
                                visitsCount = 0,
                                registrationDate = apiUser.registrationDate,
                                avatar = apiUser.avatar,
                                isActive = apiUser.isActive
                            )
                        }
                        filterUsers()
                    } else {
                        allUsers = emptyList()
                        filterUsers()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка загрузки пользователей: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка загрузки пользователей: ${response.code()}"
                    _users.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
                _users.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchUsers(query: String) {
        _searchQuery.value = query
        filterUsers()
    }

    private fun filterUsers() {
        val query = _searchQuery.value?.lowercase() ?: ""
        val filtered = if (query.isEmpty()) {
            allUsers
        } else {
            allUsers.filter {
                it.name.lowercase().contains(query) ||
                        it.email.lowercase().contains(query) ||
                        (it.phone?.lowercase()?.contains(query) == true)
            }
        }
        _users.value = filtered
    }

    fun createUser(request: CreateUserRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Создание нового пользователя: ${request.name}")

                val response = ApiClient.adminApiService.createUser(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful) {
                    response.body()?.let { user ->
                        println("✅ Пользователь создан с ID: ${user.id}")
                        _errorMessage.value = "Пользователь успешно создан"
                        loadUsers()
                    } ?: run {
                        _errorMessage.value = "Пустой ответ от сервера"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка создания пользователя: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка создания пользователя: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUser(userId: Int, request: UpdateUserRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Обновление пользователя ID: $userId")

                val response = ApiClient.adminApiService.updateUser(
                    token = "Bearer $token",
                    userId = userId,
                    request = request
                )

                if (response.isSuccessful) {
                    response.body()?.let { user ->
                        println("✅ Пользователь обновлен")
                        _errorMessage.value = "Пользователь обновлен"
                        loadUsers()
                    } ?: run {
                        _errorMessage.value = "Пустой ответ от сервера"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка обновления пользователя: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка обновления пользователя: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteUser(userId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Удаление пользователя ID: $userId")

                val response = ApiClient.adminApiService.deleteUser(
                    token = "Bearer $token",
                    userId = userId
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Пользователь удален")
                        _errorMessage.value = "Пользователь удален"
                        loadUsers()
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка удаления"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка удаления пользователя: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка удаления пользователя: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetUserPassword(userId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Сброс пароля для пользователя ID: $userId")

                val response = ApiClient.adminApiService.resetUserPassword(
                    token = "Bearer $token",
                    userId = userId
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Пароль сброшен")
                        _errorMessage.value = "Новый пароль отправлен пользователю на email"
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка сброса"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка сброса пароля: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка сброса пароля: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============================================================
    // ТРЕНИРОВКИ
    // ============================================================

    fun loadWorkouts(status: String? = null, trainerId: Int? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Загрузка списка тренировок")

                val response = ApiClient.adminApiService.getAdminWorkouts(
                    token = "Bearer $token",
                    status = status,
                    trainerId = trainerId
                )

                if (response.isSuccessful) {
                    val adminResponse = response.body()
                    if (adminResponse != null && adminResponse.success) {
                        allWorkouts = adminResponse.data.map { apiWorkout ->
                            AdminWorkout(
                                id = apiWorkout.id,
                                title = apiWorkout.title,
                                description = apiWorkout.description ?: "",
                                date = apiWorkout.date,
                                formattedDate = apiWorkout.formattedDate,
                                formattedTime = apiWorkout.formattedTime,
                                duration = apiWorkout.duration,
                                trainerId = apiWorkout.trainerId,
                                trainerName = apiWorkout.trainerName ?: "",
                                currentParticipants = apiWorkout.currentParticipants,
                                maxParticipants = apiWorkout.maxParticipants,
                                workoutType = apiWorkout.workoutType,
                                workoutTypeId = apiWorkout.workoutTypeId,
                                ageCategory = apiWorkout.ageCategory,
                                thumbnail = apiWorkout.thumbnail,
                                status = apiWorkout.status,
                                room = apiWorkout.room
                            )
                        }
                        _workouts.value = allWorkouts
                    } else {
                        _workouts.value = emptyList()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка загрузки тренировок: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка загрузки тренировок: ${response.code()}"
                    _workouts.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
                _workouts.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============================================================
    // ✅ УДАЛЕНИЕ ТРЕНИРОВКИ - ДОБАВЛЕН МЕТОД
    // ============================================================

    fun deleteWorkout(workoutId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Удаление тренировки ID: $workoutId")

                val response = ApiClient.adminApiService.deleteWorkout(
                    token = "Bearer $token",
                    workoutId = workoutId
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Тренировка удалена")
                        _errorMessage.value = "Тренировка удалена"
                        // Обновляем списки
                        loadWorkouts()
                        loadWeeklyScheduleForDate(Date())
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка удаления"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка удаления тренировки: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка удаления тренировки: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============================================================
    // ✅ СОЗДАНИЕ ТРЕНИРОВКИ С ИЗОБРАЖЕНИЕМ
    // ============================================================

    fun createWorkoutWithImage(
        request: CreateWorkoutRequest,
        imageUri: Uri? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                val titlePart = RequestBody.create("text/plain".toMediaType(), request.title)
                val descriptionPart = RequestBody.create("text/plain".toMediaType(), request.description)
                val datePart = RequestBody.create("text/plain".toMediaType(), request.date)
                val durationPart = RequestBody.create("text/plain".toMediaType(), request.duration.toString())
                val trainerIdPart = RequestBody.create("text/plain".toMediaType(), request.trainerId.toString())
                val maxParticipantsPart = RequestBody.create("text/plain".toMediaType(), request.maxParticipants.toString())

                val workoutTypeIdPart = request.workoutTypeId?.let {
                    RequestBody.create("text/plain".toMediaType(), it.toString())
                }
                val ageCategoryPart = request.ageCategory?.let {
                    RequestBody.create("text/plain".toMediaType(), it)
                }
                val roomPart = request.room?.let {
                    RequestBody.create("text/plain".toMediaType(), it)
                }

                var imagePart: MultipartBody.Part? = null
                imageUri?.let { uri ->
                    val imageFile = createImageFileFromUri(uri)
                    if (imageFile != null && imageFile.exists()) {
                        val requestFile = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
                    }
                }

                val response = ApiClient.adminApiService.createWorkoutWithImage(
                    token = "Bearer $token",
                    title = titlePart,
                    description = descriptionPart,
                    date = datePart,
                    duration = durationPart,
                    trainerId = trainerIdPart,
                    maxParticipants = maxParticipantsPart,
                    workoutTypeId = workoutTypeIdPart,
                    ageCategory = ageCategoryPart,
                    room = roomPart,
                    image = imagePart
                )

                if (response.isSuccessful) {
                    val workout = response.body()
                    if (workout != null) {
                        println("✅ Тренировка создана с ID: ${workout.id}")
                        _errorMessage.value = "Тренировка успешно создана"
                        loadWorkouts()
                        loadWeeklyScheduleForDate(Date())
                    } else {
                        _errorMessage.value = "Пустой ответ от сервера"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка создания тренировки: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка создания тренировки: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============================================================
    // ✅ ОБНОВЛЕНИЕ ТРЕНИРОВКИ С ИЗОБРАЖЕНИЕМ
    // ============================================================

    fun updateWorkoutWithImage(
        workoutId: Int,
        request: UpdateWorkoutRequest,
        imageUri: Uri? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                val titlePart = request.title?.let { RequestBody.create("text/plain".toMediaType(), it) }
                val descriptionPart = request.description?.let { RequestBody.create("text/plain".toMediaType(), it) }
                val datePart = request.date?.let { RequestBody.create("text/plain".toMediaType(), it) }
                val durationPart = request.duration?.let { RequestBody.create("text/plain".toMediaType(), it.toString()) }
                val trainerIdPart = request.trainerId?.let { RequestBody.create("text/plain".toMediaType(), it.toString()) }
                val maxParticipantsPart = request.maxParticipants?.let { RequestBody.create("text/plain".toMediaType(), it.toString()) }
                val workoutTypeIdPart = request.workoutTypeId?.let { RequestBody.create("text/plain".toMediaType(), it.toString()) }
                val ageCategoryPart = request.ageCategory?.let { RequestBody.create("text/plain".toMediaType(), it) }
                val roomPart = request.room?.let { RequestBody.create("text/plain".toMediaType(), it) }

                var imagePart: MultipartBody.Part? = null
                imageUri?.let { uri ->
                    val imageFile = createImageFileFromUri(uri)
                    if (imageFile != null && imageFile.exists()) {
                        val requestFile = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
                    }
                }

                val response = ApiClient.adminApiService.updateWorkoutWithImage(
                    token = "Bearer $token",
                    workoutId = workoutId,
                    title = titlePart,
                    description = descriptionPart,
                    date = datePart,
                    duration = durationPart,
                    trainerId = trainerIdPart,
                    maxParticipants = maxParticipantsPart,
                    workoutTypeId = workoutTypeIdPart,
                    ageCategory = ageCategoryPart,
                    room = roomPart,
                    image = imagePart
                )

                if (response.isSuccessful) {
                    val workout = response.body()
                    if (workout != null) {
                        println("✅ Тренировка обновлена")
                        _errorMessage.value = "Тренировка обновлена"
                        loadWorkouts()
                        loadWeeklyScheduleForDate(Date())
                    } else {
                        _errorMessage.value = "Пустой ответ от сервера"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка обновления тренировки: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка обновления тренировки: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============================================================
    // ✅ УДАЛЕНИЕ ИЗОБРАЖЕНИЯ ТРЕНИРОВКИ
    // ============================================================

    fun deleteWorkoutImage(workoutId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                val response = ApiClient.adminApiService.deleteWorkoutImage("Bearer $token", workoutId)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Изображение удалено")
                        _errorMessage.value = "Изображение удалено"
                        loadWorkouts()
                        loadWeeklyScheduleForDate(Date())
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка удаления"
                    }
                } else {
                    _errorMessage.value = "Ошибка удаления: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ СОЗДАНИЯ ФАЙЛА ИЗ URI
    // ============================================================

    private suspend fun createImageFileFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val context = appContext ?: return@withContext null
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val file = File(context.cacheDir, "workout_image_${System.currentTimeMillis()}.jpg")

            FileOutputStream(file).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                val compressedFile = File(context.cacheDir, "workout_image_compressed_${System.currentTimeMillis()}.jpg")
                FileOutputStream(compressedFile).use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output)
                }
                bitmap.recycle()
                file.delete()
                return@withContext compressedFile
            }

            return@withContext file
        } catch (e: Exception) {
            println("❌ Ошибка создания файла изображения: ${e.message}")
            return@withContext null
        }
    }

    // ============================================================
    // АБОНЕМЕНТЫ
    // ============================================================

    fun loadSubscriptions(activeOnly: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Загрузка списка абонементов")

                val response = ApiClient.adminApiService.getAdminSubscriptions(
                    token = "Bearer $token",
                    activeOnly = activeOnly
                )

                if (response.isSuccessful) {
                    val adminResponse = response.body()
                    if (adminResponse != null && adminResponse.success) {
                        allSubscriptions = adminResponse.data.map { apiSub ->
                            AdminSubscription(
                                id = apiSub.id,
                                title = apiSub.title,
                                description = apiSub.description ?: "",
                                workoutsCount = apiSub.workoutsCount,
                                price = apiSub.price,
                                durationDays = apiSub.durationDays,
                                workoutType = apiSub.workoutType,
                                workoutTypeId = apiSub.workoutTypeId,
                                isActive = apiSub.isActive,
                                salesCount = apiSub.salesCount,
                                totalRevenue = apiSub.totalRevenue,
                                chatId = apiSub.chatId
                            )
                        }
                        _subscriptions.value = allSubscriptions
                    } else {
                        _subscriptions.value = emptyList()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка загрузки абонементов: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка загрузки абонементов: ${response.code()}"
                    _subscriptions.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
                _subscriptions.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createSubscription(request: CreateSubscriptionRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Создание абонемента: ${request.title}")

                val response = ApiClient.adminApiService.createSubscription(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful) {
                    response.body()?.let { subscription ->
                        println("✅ Абонемент создан с ID: ${subscription.id}, chat_id: ${subscription.chatId}")
                        _errorMessage.value = "Абонемент успешно создан"

                        if (subscription.chatId != null) {
                            _errorMessage.value = "Абонемент создан и создана группа для обсуждения"
                        }

                        loadSubscriptions()
                    } ?: run {
                        _errorMessage.value = "Пустой ответ от сервера"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка создания абонемента: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка создания абонемента: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSubscription(subscriptionId: Int, request: CreateSubscriptionRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Обновление абонемента ID: $subscriptionId")

                val response = ApiClient.adminApiService.updateSubscription(
                    token = "Bearer $token",
                    subscriptionId = subscriptionId,
                    request = request
                )

                if (response.isSuccessful) {
                    response.body()?.let { subscription ->
                        println("✅ Абонемент обновлен, chat_id: ${subscription.chatId}")
                        _errorMessage.value = "Абонемент обновлен"
                        loadSubscriptions()
                    } ?: run {
                        _errorMessage.value = "Пустой ответ от сервера"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка обновления абонемента: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка обновления абонемента: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSubscription(subscriptionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Удаление абонемента ID: $subscriptionId")

                val response = ApiClient.adminApiService.deleteSubscription(
                    token = "Bearer $token",
                    subscriptionId = subscriptionId
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Абонемент удален")
                        _errorMessage.value = "Абонемент удален"
                        loadSubscriptions()
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка удаления"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка удаления абонемента: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка удаления абонемента: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sellSubscription(request: SellSubscriptionRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                println("🔍 Продажа абонемента пользователю ID: ${request.userId}")

                val response = ApiClient.adminApiService.sellSubscription(
                    token = "Bearer $token",
                    request = request
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Абонемент продан")
                        _errorMessage.value = "Абонемент успешно продан"
                        loadSubscriptions()
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка продажи"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка продажи абонемента: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка продажи абонемента: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============================================================
    // РАСПИСАНИЕ
    // ============================================================

    fun loadWeeklyScheduleForDate(date: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = dateFormat.format(date)

                println("🔍 Загрузка расписания на неделю, начиная с: $dateStr")

                val response = ApiClient.adminApiService.getAdminWeeklySchedule(
                    token = "Bearer $token",
                    date = dateStr
                )

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null && responseBody.success) {
                        println("✅ Расписание загружено")

                        val data = responseBody.data
                        val week = data.week
                        val daysMap = data.days

                        if (week != null) {
                            println("   Неделя: ${week.display}")
                            _weekRange.value = week.display
                        } else {
                            println("   ⚠️ week = null")
                            _weekRange.value = "Неделя"
                        }

                        val convertedSchedule = mutableMapOf<Int, List<AdminWorkout>>()

                        for ((key, items) in daysMap) {
                            val dayKey = key.toIntOrNull() ?: 0
                            val adminWorkouts = mutableListOf<AdminWorkout>()
                            for (scheduleItem in items) {
                                adminWorkouts.add(convertToAdminWorkout(scheduleItem))
                            }
                            convertedSchedule[dayKey] = adminWorkouts
                        }

                        _weeklySchedule.value = convertedSchedule

                        println("   Дней с занятиями: ${convertedSchedule.size}")
                        println("   Всего занятий в неделе: ${convertedSchedule.values.flatten().size}")
                    } else {
                        _errorMessage.value = "Ошибка: пустой ответ или success=false"
                        _weeklySchedule.value = emptyMap()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка загрузки расписания: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка загрузки расписания: ${response.code()}"
                    _weeklySchedule.value = emptyMap()
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
                _weeklySchedule.value = emptyMap()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun copyScheduleToNextWeek(fromWeekDate: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val fromWeekStr = dateFormat.format(fromWeekDate)

                val calendar = Calendar.getInstance().apply {
                    time = fromWeekDate
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
                val toWeekStr = dateFormat.format(calendar.time)

                println("🔍 Копирование расписания: $fromWeekStr -> $toWeekStr")

                val response = ApiClient.adminApiService.copySchedule(
                    token = "Bearer $token",
                    fromWeek = fromWeekStr,
                    toWeek = toWeekStr
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Расписание скопировано")
                        _errorMessage.value = "Расписание успешно скопировано"
                        loadWeeklyScheduleForDate(calendar.time)
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка копирования"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка копирования расписания: ${response.code()} - $errorBody")
                    _errorMessage.value = "Ошибка копирования расписания: ${response.code()}"
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                _errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun convertToAdminWorkout(item: ScheduleItem): AdminWorkout {
        return AdminWorkout(
            id = item.id,
            title = item.title,
            description = item.description ?: "",
            date = item.date,
            formattedDate = item.formattedDate,
            formattedTime = item.formattedTime ?: "00:00",
            duration = item.duration ?: 60,
            trainerId = item.trainerId ?: 0,
            trainerName = item.trainerName ?: "Неизвестный тренер",
            currentParticipants = item.currentParticipants,
            maxParticipants = item.maxParticipants,
            workoutType = item.workoutType,
            workoutTypeId = null,
            ageCategory = item.ageCategory,
            thumbnail = item.thumbnail,
            status = determineWorkoutStatus(item.date),
            room = null
        )
    }

    private fun determineWorkoutStatus(dateStr: String): String {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val workoutDate = dateFormat.parse(dateStr)
            val now = Date()

            when {
                workoutDate == null -> "upcoming"
                workoutDate.after(now) -> "upcoming"
                else -> "completed"
            }
        } catch (e: Exception) {
            "upcoming"
        }
    }

    // ============================================================
    // НОВОСТИ
    // ============================================================

    fun loadNews() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = ApiClient.apiService.getNews(perPage = 100, page = 1)
                if (response.isSuccessful) {
                    val newsResponse = response.body()
                    if (newsResponse != null && newsResponse.success) {
                        allNews = newsResponse.data.news
                        filterNewsByQuery()
                    } else {
                        _errorMessage.value = "Ошибка загрузки новостей: пустой ответ"
                    }
                } else {
                    _errorMessage.value = "Ошибка загрузки новостей: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchNews(query: String) {
        _searchQuery.value = query
        filterNewsByQuery()
    }

    private fun filterNewsByQuery() {
        val q = _searchQuery.value?.lowercase() ?: ""
        val filtered = if (q.isEmpty()) {
            allNews
        } else {
            allNews.filter { news ->
                val titleMatches = news.title?.lowercase()?.contains(q) == true
                val excerptMatches = news.excerpt?.lowercase()?.contains(q) == true
                titleMatches || excerptMatches
            }
        }
        _news.value = filtered
    }

    fun createNews(
        title: String,
        content: String,
        shortDescription: String = "",
        importance: String = "normal",
        sendPush: Boolean = false,
        images: List<Uri> = emptyList(),
        videos: List<Uri> = emptyList()
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val token = PreferencesManager.getToken() ?: return@launch

                val response = ApiClient.adminApiService.createNews(
                    "Bearer $token",
                    CreateNewsRequest(
                        title = title,
                        content = content,
                        shortDescription = shortDescription.ifEmpty { null },
                        importance = importance,
                        sendPush = sendPush
                    )
                )

                if (response.isSuccessful) {
                    val createResponse = response.body()
                    val newsId = createResponse?.id

                    if (newsId != null) {
                        _createdNewsId.value = newsId
                        println("✅ Новость создана с ID: $newsId")

                        if (images.isNotEmpty() || videos.isNotEmpty()) {
                            val allParts = mutableListOf<MultipartBody.Part>()
                            images.forEach { uri ->
                                val part = createMediaPart(uri)
                                if (part != null) allParts.add(part)
                            }
                            videos.forEach { uri ->
                                val part = createMediaPart(uri)
                                if (part != null) allParts.add(part)
                            }
                            if (allParts.isNotEmpty()) {
                                ApiClient.adminApiService.uploadNewsMedia("Bearer $token", newsId, allParts)
                                println("✅ Медиа загружено для новости $newsId")
                            }
                        }

                        _errorMessage.value = "Новость создана|$newsId"
                        delay(500)
                        loadNews()
                    } else {
                        _errorMessage.value = "Ошибка: не получен ID новости"
                    }
                } else {
                    _errorMessage.value = "Ошибка создания: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateNews(
        newsId: Int,
        title: String,
        content: String,
        shortDescription: String = "",
        importance: String? = null,
        sendPush: Boolean? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.adminApiService.updateNews(
                    "Bearer $token",
                    newsId,
                    UpdateNewsRequest(
                        title = title,
                        content = content,
                        shortDescription = shortDescription.ifEmpty { null },
                        importance = importance,
                        sendPush = sendPush
                    )
                )
                if (response.isSuccessful) {
                    _errorMessage.value = "Новость обновлена"
                    loadNews()
                } else {
                    _errorMessage.value = "Ошибка обновления: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteNews(newsId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.adminApiService.deleteNews("Bearer $token", newsId)
                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        _errorMessage.value = "Новость удалена"
                        loadNews()
                    } else {
                        _errorMessage.value = result?.message ?: "Ошибка удаления"
                    }
                } else {
                    _errorMessage.value = "Ошибка удаления: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadNewsMedia(newsId: Int, parts: List<MultipartBody.Part>) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.adminApiService.uploadNewsMedia("Bearer $token", newsId, parts)
                if (response.isSuccessful) {
                    _errorMessage.value = "Медиа загружено"
                    loadNews()
                } else {
                    _errorMessage.value = "Ошибка загрузки медиа: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteNewsMedia(newsId: Int, url: String, type: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.adminApiService.deleteNewsMedia("Bearer $token", newsId, url, type)
                if (response.isSuccessful) {
                    _errorMessage.value = "Медиа удалено"
                    loadNews()
                } else {
                    _errorMessage.value = "Ошибка удаления медиа: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun createMediaPart(uri: Uri): MultipartBody.Part? {
        return try {
            val context = appContext ?: return null
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = mimeTypeToExtension(mimeType)
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.$extension")

            FileOutputStream(file).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("media", file.name, requestBody)
        } catch (e: Exception) {
            null
        }
    }

    private fun mimeTypeToExtension(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "video/x-msvideo" -> "avi"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            else -> mimeType.substringAfter("/")
        }
    }

    // ============================================================
    // 🆕 ШАБЛОНЫ РАСПИСАНИЯ - МЕТОДЫ
    // ============================================================

    fun loadTemplates() {
        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _templates.value = emptyList()
                    return@launch
                }

                println("📋 Загрузка списка шаблонов расписания")
                val response = ApiClient.adminApiService.getTemplates("Bearer $token", activeOnly = true)

                if (response.isSuccessful) {
                    val templatesList = response.body() ?: emptyList()
                    val convertedTemplates = templatesList.map { apiTemplate ->
                        ScheduleTemplate(
                            id = apiTemplate.id,
                            name = apiTemplate.name,
                            description = apiTemplate.description,
                            createdAt = apiTemplate.createdAt,
                            updatedAt = apiTemplate.updatedAt,
                            isActive = apiTemplate.isActive,
                            isDefault = apiTemplate.isDefault,
                            days = apiTemplate.days?.mapValues { entry ->
                                entry.value.map { apiSlot ->
                                    TemplateSlot(
                                        id = apiSlot.id,
                                        templateId = apiSlot.templateId,
                                        dayOfWeek = apiSlot.dayOfWeek,
                                        startTime = apiSlot.startTime,
                                        duration = apiSlot.duration,
                                        title = apiSlot.title,
                                        trainerId = apiSlot.trainerId,
                                        trainerName = apiSlot.trainerName,
                                        maxParticipants = apiSlot.maxParticipants,
                                        workoutTypeId = apiSlot.workoutTypeId,
                                        workoutTypeName = apiSlot.workoutTypeName,
                                        ageCategory = apiSlot.ageCategory,
                                        room = apiSlot.room,
                                        description = apiSlot.description,
                                        isActive = apiSlot.isActive
                                    )
                                }
                            } ?: emptyMap()
                        )
                    }
                    println("✅ Загружено шаблонов: ${convertedTemplates.size}")
                    _templates.value = convertedTemplates
                } else {
                    println("❌ Ошибка загрузки шаблонов: ${response.code()}")
                    _templates.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Ошибка загрузки шаблонов: ${e.message}")
                _templates.value = emptyList()
            }
        }
    }

    fun saveWeekAsTemplate(
        weekStartDate: Date,
        name: String,
        description: String? = null,
        isDefault: Boolean = false
    ) {
        viewModelScope.launch {
            _templateOperationState.value = TemplateOperationState.Loading
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _templateOperationState.value = TemplateOperationState.Error("Токен не найден")
                    return@launch
                }

                val currentSchedule = _weeklySchedule.value ?: emptyMap()
                if (currentSchedule.isEmpty()) {
                    _templateOperationState.value = TemplateOperationState.Error("Нет занятий для сохранения")
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val weekStartStr = dateFormat.format(weekStartDate)

                val slots = mutableListOf<com.fitnesslemon.app.data.api.TemplateSlotRequest>()
                currentSchedule.forEach { (dayOfWeek, workouts) ->
                    workouts.forEach { workout ->
                        slots.add(
                            com.fitnesslemon.app.data.api.TemplateSlotRequest(
                                dayOfWeek = dayOfWeek,
                                startTime = workout.formattedTime,
                                duration = workout.duration,
                                title = workout.title,
                                trainerId = workout.trainerId,
                                maxParticipants = workout.maxParticipants,
                                workoutTypeId = workout.workoutTypeId,
                                ageCategory = workout.ageCategory,
                                room = workout.room,
                                description = workout.description
                            )
                        )
                    }
                }

                if (slots.isEmpty()) {
                    _templateOperationState.value = TemplateOperationState.Error("Нет занятий для сохранения")
                    return@launch
                }

                val request = com.fitnesslemon.app.data.api.SaveTemplateRequest(
                    name = name,
                    description = description,
                    isDefault = isDefault,
                    weekStartDate = weekStartStr,
                    slots = slots
                )

                println("📋 Сохранение шаблона: $name (${slots.size} занятий)")
                val response = ApiClient.adminApiService.createTemplate("Bearer $token", request)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Шаблон сохранён")
                        _templateOperationState.value = TemplateOperationState.Success("Шаблон \"$name\" сохранён")
                        loadTemplates()
                    } else {
                        val errorMsg = result?.message ?: "Неизвестная ошибка"
                        _templateOperationState.value = TemplateOperationState.Error(errorMsg)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка сохранения шаблона: ${response.code()} - $errorBody")
                    _templateOperationState.value = TemplateOperationState.Error("Ошибка сохранения: ${response.code()}")
                }
            } catch (e: Exception) {
                println("❌ Ошибка сохранения шаблона: ${e.message}")
                _templateOperationState.value = TemplateOperationState.Error("Ошибка: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun applyTemplate(
        templateId: Int,
        targetWeekStart: Date,
        clearExisting: Boolean = true,
        mergeStrategy: String = "replace"
    ) {
        viewModelScope.launch {
            _templateOperationState.value = TemplateOperationState.Loading
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _templateOperationState.value = TemplateOperationState.Error("Токен не найден")
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val targetWeekStr = dateFormat.format(targetWeekStart)

                val request = com.fitnesslemon.app.data.api.ApplyTemplateRequest(
                    templateId = templateId,
                    targetWeekStart = targetWeekStr,
                    clearExisting = clearExisting,
                    mergeStrategy = mergeStrategy
                )

                println("📋 Применение шаблона ID $templateId к неделе $targetWeekStr")
                val response = ApiClient.adminApiService.applyTemplate("Bearer $token", templateId, request)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Шаблон применён")
                        _templateOperationState.value = TemplateOperationState.Success("Шаблон успешно применён")
                        loadWeeklyScheduleForDate(targetWeekStart)
                    } else {
                        val errorMsg = result?.message ?: "Неизвестная ошибка"
                        _templateOperationState.value = TemplateOperationState.Error(errorMsg)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка применения шаблона: ${response.code()} - $errorBody")
                    _templateOperationState.value = TemplateOperationState.Error("Ошибка применения: ${response.code()}")
                }
            } catch (e: Exception) {
                println("❌ Ошибка применения шаблона: ${e.message}")
                _templateOperationState.value = TemplateOperationState.Error("Ошибка: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTemplate(templateId: Int) {
        viewModelScope.launch {
            _templateOperationState.value = TemplateOperationState.Loading
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _templateOperationState.value = TemplateOperationState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.adminApiService.deleteTemplate("Bearer $token", templateId)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Шаблон удалён")
                        _templateOperationState.value = TemplateOperationState.Success("Шаблон удалён")
                        loadTemplates()
                    } else {
                        _templateOperationState.value = TemplateOperationState.Error(result?.message ?: "Ошибка удаления")
                    }
                } else {
                    _templateOperationState.value = TemplateOperationState.Error("Ошибка удаления: ${response.code()}")
                }
            } catch (e: Exception) {
                _templateOperationState.value = TemplateOperationState.Error("Ошибка: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setDefaultTemplate(templateId: Int) {
        viewModelScope.launch {
            _templateOperationState.value = TemplateOperationState.Loading
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _templateOperationState.value = TemplateOperationState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.adminApiService.setDefaultTemplate("Bearer $token", templateId)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Шаблон установлен как стандартный")
                        _templateOperationState.value = TemplateOperationState.Success("Шаблон установлен как стандартный")
                        loadTemplates()
                    } else {
                        _templateOperationState.value = TemplateOperationState.Error(result?.message ?: "Ошибка")
                    }
                } else {
                    _templateOperationState.value = TemplateOperationState.Error("Ошибка: ${response.code()}")
                }
            } catch (e: Exception) {
                _templateOperationState.value = TemplateOperationState.Error("Ошибка: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetTemplateOperationState() {
        _templateOperationState.value = TemplateOperationState.Idle
    }

    // ============================================================
    // 🆕 ИСТОРИЯ РАСПИСАНИЯ
    // ============================================================

    fun loadScheduleHistory(weekStart: Date? = null, limit: Int = 50, offset: Int = 0) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val weekStartStr = weekStart?.let { dateFormat.format(it) }

                val response = ApiClient.adminApiService.getScheduleHistory(
                    "Bearer $token",
                    weekStartStr,
                    limit,
                    offset
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        val convertedEntries = result.data.entries.map { apiEntry ->
                            ScheduleHistoryEntry(
                                id = apiEntry.id,
                                weekStart = apiEntry.weekStart,
                                weekEnd = apiEntry.weekEnd,
                                changedBy = apiEntry.changedBy,
                                changedById = apiEntry.changedById,
                                changeType = apiEntry.changeType,
                                description = apiEntry.description,
                                timestamp = apiEntry.timestamp,
                                workoutCount = apiEntry.workoutCount
                            )
                        }
                        _scheduleHistory.value = convertedEntries
                        println("✅ Загружено записей истории: ${convertedEntries.size}")
                    } else {
                        _scheduleHistory.value = emptyList()
                    }
                } else {
                    _scheduleHistory.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Ошибка загрузки истории: ${e.message}")
                _scheduleHistory.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadScheduleVersions(weekStart: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _errorMessage.value = "Токен не найден"
                    _isLoading.value = false
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val weekStartStr = dateFormat.format(weekStart)

                val response = ApiClient.adminApiService.getScheduleVersions(
                    "Bearer $token",
                    weekStartStr
                )

                if (response.isSuccessful) {
                    val versions = response.body() ?: emptyList()
                    val convertedVersions = versions.map { apiVersion ->
                        ScheduleVersion(
                            id = apiVersion.id,
                            weekStart = apiVersion.weekStart,
                            data = apiVersion.data.mapValues { entry ->
                                entry.value.map { apiWorkout ->
                                    AdminWorkout(
                                        id = apiWorkout.id,
                                        title = apiWorkout.title,
                                        description = apiWorkout.description ?: "",
                                        date = apiWorkout.date,
                                        formattedDate = apiWorkout.formattedDate,
                                        formattedTime = apiWorkout.formattedTime,
                                        duration = apiWorkout.duration,
                                        trainerId = apiWorkout.trainerId,
                                        trainerName = apiWorkout.trainerName ?: "",
                                        currentParticipants = apiWorkout.currentParticipants,
                                        maxParticipants = apiWorkout.maxParticipants,
                                        workoutType = apiWorkout.workoutType,
                                        workoutTypeId = apiWorkout.workoutTypeId,
                                        ageCategory = apiWorkout.ageCategory,
                                        thumbnail = apiWorkout.thumbnail,
                                        status = apiWorkout.status,
                                        room = apiWorkout.room
                                    )
                                }
                            },
                            createdAt = apiVersion.createdAt,
                            createdBy = apiVersion.createdBy,
                            versionNumber = apiVersion.versionNumber,
                            comment = apiVersion.comment
                        )
                    }
                    _scheduleVersions.value = convertedVersions
                    println("✅ Загружено версий: ${convertedVersions.size}")
                } else {
                    _scheduleVersions.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Ошибка загрузки версий: ${e.message}")
                _scheduleVersions.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreScheduleVersion(versionId: Int) {
        viewModelScope.launch {
            _templateOperationState.value = TemplateOperationState.Loading
            _errorMessage.value = null

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _templateOperationState.value = TemplateOperationState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.adminApiService.restoreScheduleVersion("Bearer $token", versionId)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true) {
                        println("✅ Версия восстановлена")
                        _templateOperationState.value = TemplateOperationState.Success("Версия восстановлена")
                        val currentWeek = _weekRange.value?.let { parseWeekStartFromRange(it) } ?: Date()
                        loadWeeklyScheduleForDate(currentWeek)
                    } else {
                        _templateOperationState.value = TemplateOperationState.Error(result?.message ?: "Ошибка")
                    }
                } else {
                    _templateOperationState.value = TemplateOperationState.Error("Ошибка: ${response.code()}")
                }
            } catch (e: Exception) {
                _templateOperationState.value = TemplateOperationState.Error("Ошибка: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseWeekStartFromRange(range: String): Date {
        return try {
            val parts = range.split(" — ")
            if (parts.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("d MMMM", Locale("ru"))
                dateFormat.parse(parts[0]) ?: Date()
            } else {
                Date()
            }
        } catch (e: Exception) {
            Date()
        }
    }

    fun autoApplyDefaultTemplate(weekStart: Date) {
        viewModelScope.launch {
            try {
                val defaultTemplate = _templates.value?.find { it.isDefault }
                if (defaultTemplate != null) {
                    val currentSchedule = _weeklySchedule.value ?: emptyMap()
                    val hasWorkouts = currentSchedule.values.any { it.isNotEmpty() }

                    if (!hasWorkouts) {
                        println("🔄 Автоматическое применение шаблона по умолчанию")
                        applyTemplate(defaultTemplate.id, weekStart, clearExisting = true, mergeStrategy = "replace")
                    }
                }
            } catch (e: Exception) {
                println("⚠️ Ошибка автоматического применения шаблона: ${e.message}")
            }
        }
    }
}
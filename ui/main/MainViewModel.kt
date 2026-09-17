package com.fitnesslemon.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.Booking
import com.fitnesslemon.app.data.models.News
import com.fitnesslemon.app.data.models.Story
import com.fitnesslemon.app.data.models.User
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class DashboardState {
    object Loading : DashboardState()
    data class Success(
        val user: User,
        val remainingWorkouts: Int,
        val upcomingBookings: List<Booking>
    ) : DashboardState()
    data class Error(val message: String) : DashboardState()
}

class MainViewModel : ViewModel() {

    private val _dashboardState = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val dashboardState: StateFlow<DashboardState> = _dashboardState

    private val _newsState = MutableStateFlow<List<News>>(emptyList())
    val newsState: StateFlow<List<News>> = _newsState

    private val _storiesState = MutableStateFlow<List<Story>>(emptyList())
    val storiesState: StateFlow<List<Story>> = _storiesState

    private var isLoading = false

    init {
        loadData()
    }

    /**
     * Основная загрузка данных (вызывается при инициализации)
     */
    fun loadData() {
        if (isLoading) {
            println("⚠️ Уже идет загрузка, пропускаем")
            return
        }
        isLoading = true

        viewModelScope.launch {
            _dashboardState.value = DashboardState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _dashboardState.value = DashboardState.Error("Токен не найден")
                    isLoading = false
                    return@launch
                }

                loadUserData(token)
                loadNews()
                loadStories()

                isLoading = false

            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
                _dashboardState.value = DashboardState.Error("Ошибка сети: ${e.message}")
                isLoading = false
            }
        }
    }

    /**
     * 🔄 Принудительное обновление данных (для pull-to-refresh и после публикации)
     */
    fun refreshData() {
        println("🔄 Принудительное обновление данных")
        isLoading = false
        loadData()
    }

    /**
     * 🔄 Обновление только сторис (без полной перезагрузки)
     */
    fun refreshStories() {
        println("🔄 Обновление сторис")
        viewModelScope.launch {
            loadStories()
        }
    }

    private suspend fun loadUserData(token: String) {
        try {
            val profileResponse = ApiClient.apiService.getUserProfile("Bearer $token")

            if (profileResponse.isSuccessful) {
                val response = profileResponse.body()
                if (response != null && response.success) {
                    val user = response.data

                    if (!user.avatar.isNullOrEmpty()) {
                        PreferencesManager.saveUserAvatar(user.avatar)
                    }

                    val bookingsResponse = ApiClient.apiService.getMyBookings("Bearer $token")
                    val allBookings = if (bookingsResponse.isSuccessful) {
                        bookingsResponse.body()?.data ?: emptyList()
                    } else {
                        emptyList()
                    }

                    val upcomingBookings = filterUpcomingBookings(allBookings)

                    _dashboardState.value = DashboardState.Success(
                        user = user,
                        remainingWorkouts = user.remainingWorkouts,
                        upcomingBookings = upcomingBookings
                    )
                }
            }
        } catch (e: Exception) {
            println("❌ Ошибка загрузки профиля: ${e.message}")
        }
    }

    private suspend fun loadNews() {
        try {
            val response = ApiClient.apiService.getNews(perPage = 20, page = 1)
            if (response.isSuccessful) {
                val newsResponse = response.body()
                if (newsResponse != null && newsResponse.success) {
                    _newsState.value = newsResponse.data.news
                }
            }
        } catch (e: Exception) {
            println("❌ Ошибка загрузки новостей: ${e.message}")
        }
    }

    private suspend fun loadStories() {
        try {
            val token = PreferencesManager.getToken()
            if (token.isNullOrEmpty()) {
                println("⚠️ Токен не найден, пропускаем загрузку сторис")
                _storiesState.value = emptyList()
                return
            }

            val response = ApiClient.apiService.getActiveStories("Bearer $token")
            if (response.isSuccessful) {
                val storiesResponse = response.body()
                if (storiesResponse != null && storiesResponse.success) {
                    val allStories = mutableListOf<Story>()
                    storiesResponse.data.stories.forEach { trainerStories ->
                        trainerStories.stories.forEach { story ->
                            val storyWithName = story.copy(
                                userName = trainerStories.userName,
                                userAvatar = trainerStories.userAvatar
                            )
                            allStories.add(storyWithName)
                        }
                    }
                    _storiesState.value = allStories
                    println("✅ Сторис загружены: ${allStories.size} шт.")
                } else {
                    _storiesState.value = emptyList()
                }
            } else {
                println("❌ Ошибка загрузки сторис: ${response.code()}")
                _storiesState.value = emptyList()
            }
        } catch (e: Exception) {
            println("❌ Ошибка загрузки сторис: ${e.message}")
            _storiesState.value = emptyList()
        }
    }

    private fun filterUpcomingBookings(bookings: List<Booking>): List<Booking> {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        return bookings.filter { booking ->
            try {
                val bookingDate = dateFormat.parse(booking.formattedDate)
                bookingDate != null && bookingDate.after(currentDate)
            } catch (e: Exception) {
                false
            }
        }.sortedBy { booking ->
            try {
                dateFormat.parse(booking.formattedDate)?.time ?: 0
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
        }
    }
}
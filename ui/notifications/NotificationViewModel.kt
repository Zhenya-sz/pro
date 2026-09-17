package com.fitnesslemon.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.MarkGroupAsReadRequest
import com.fitnesslemon.app.data.models.MarkNotificationReadRequest
import com.fitnesslemon.app.data.models.Notification
import com.fitnesslemon.app.data.models.NotificationGroup
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed class NotificationsState {
    object Loading : NotificationsState()
    data class Success(
        val chatGroups: List<NotificationGroup> = emptyList(),
        val bookingGroups: List<NotificationGroup> = emptyList(),
        val invitations: List<Notification> = emptyList(),
        val others: List<Notification> = emptyList()
    ) : NotificationsState()
    data class Error(val message: String) : NotificationsState()
}

sealed class UnreadCountState {
    object Loading : UnreadCountState()
    data class Success(val count: Int) : UnreadCountState()
    data class Error(val message: String) : UnreadCountState()
}

class NotificationViewModel : ViewModel() {

    private val _notificationsState = MutableStateFlow<NotificationsState>(NotificationsState.Loading)
    val notificationsState: StateFlow<NotificationsState> = _notificationsState

    private val _unreadCountState = MutableStateFlow<UnreadCountState>(UnreadCountState.Loading)
    val unreadCountState: StateFlow<UnreadCountState> = _unreadCountState

    private var isLoading = false
    private var lastLoadTime = 0L
    private val MIN_LOAD_INTERVAL = 3000L // 3 секунды между запросами

    private val gson = Gson()

    init {
        println("🔔 NotificationViewModel создан")
        loadUnreadCount()
    }

    fun loadNotifications() {
        // Предотвращаем частые запросы
        val now = System.currentTimeMillis()
        if (isLoading || (now - lastLoadTime) < MIN_LOAD_INTERVAL) {
            println("⚠️ Пропускаем загрузку, слишком часто или уже загружается")
            return
        }

        viewModelScope.launch {
            isLoading = true
            lastLoadTime = System.currentTimeMillis()
            println("🔄 Начинаем загрузку уведомлений...")
            _notificationsState.value = NotificationsState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    println("❌ Токен не найден")
                    _notificationsState.value = NotificationsState.Error("Токен не найден")
                    isLoading = false
                    return@launch
                }

                val response = ApiClient.apiService.getGroupedNotifications("Bearer $token")

                if (response.isSuccessful) {
                    val data = response.body()
                    if (data != null) {
                        val chats = data.chats ?: emptyList()
                        val bookings = data.bookings ?: emptyList()
                        val invitations = data.invitations ?: emptyList()
                        val others = data.others ?: emptyList()

                        val totalUnread = data.totalUnread

                        // Обновляем счетчик
                        _unreadCountState.value = UnreadCountState.Success(totalUnread)

                        println("✅ Данные обработаны:")
                        println("   - Чаты: ${chats.size}")
                        println("   - Тренировки: ${bookings.size}")
                        println("   - Приглашения: ${invitations.size}")
                        println("   - Другие: ${others.size}")
                        println("   - Всего непрочитанных: $totalUnread")

                        _notificationsState.value = NotificationsState.Success(
                            chatGroups = chats,
                            bookingGroups = bookings,
                            invitations = invitations,
                            others = others
                        )
                    } else {
                        println("❌ Тело ответа пустое")
                        _notificationsState.value = NotificationsState.Error("Пустой ответ от сервера")
                    }
                } else {
                    println("❌ Ошибка HTTP: ${response.code()}")
                    _notificationsState.value = NotificationsState.Error("Ошибка: ${response.code()}")
                }
            } catch (e: Exception) {
                println("❌ Исключение: ${e.message}")
                e.printStackTrace()
                _notificationsState.value = NotificationsState.Error(e.message ?: "Ошибка сети")
            } finally {
                isLoading = false
                println("🏁 Загрузка завершена")
            }
        }
    }

    fun markGroupAsRead(groupId: String, type: String) {
        println("📌 markGroupAsRead: groupId=$groupId, type=$type")
        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val request = MarkGroupAsReadRequest(groupId, type)
                val response = ApiClient.apiService.markGroupAsRead("Bearer $token", request)

                if (response.isSuccessful) {
                    println("✅ Группа отмечена как прочитанная")
                    updateUnreadCount()
                    // Обновляем список после отметки
                    refresh()
                }
            } catch (e: Exception) {
                println("❌ Исключение в markGroupAsRead: ${e.message}")
            }
        }
    }

    fun markAllAsRead() {
        println("📌 markAllAsRead")
        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.apiService.markAllNotificationsRead("Bearer $token")

                if (response.isSuccessful) {
                    println("✅ Все уведомления отмечены как прочитанные")
                    _unreadCountState.value = UnreadCountState.Success(0)
                    // Обновляем список
                    refresh()
                }
            } catch (e: Exception) {
                println("❌ Исключение в markAllAsRead: ${e.message}")
            }
        }
    }

    fun acceptInvitation(invitationId: Int) {
        println("📌 acceptInvitation: $invitationId")
        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.apiService.acceptInvitation("Bearer $token", invitationId)

                if (response.isSuccessful) {
                    println("✅ Приглашение принято")
                    updateUnreadCount()
                    refresh()
                }
            } catch (e: Exception) {
                println("❌ Исключение в acceptInvitation: ${e.message}")
                refresh()
            }
        }
    }

    fun declineInvitation(invitationId: Int) {
        println("📌 declineInvitation: $invitationId")
        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.apiService.declineInvitation("Bearer $token", invitationId)

                if (response.isSuccessful) {
                    println("✅ Приглашение отклонено")
                    updateUnreadCount()
                    refresh()
                }
            } catch (e: Exception) {
                println("❌ Исключение в declineInvitation: ${e.message}")
                refresh()
            }
        }
    }

    fun markAsRead(notificationId: Int) {
        println("📌 markAsRead: $notificationId")
        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val request = MarkNotificationReadRequest(notificationId)
                val response = ApiClient.apiService.markNotificationRead("Bearer $token", request)

                if (response.isSuccessful) {
                    println("✅ Уведомление отмечено как прочитанное")
                    updateUnreadCount()
                    refresh()
                }
            } catch (e: Exception) {
                println("❌ Исключение в markAsRead: ${e.message}")
            }
        }
    }

    fun deleteNotification(notificationId: Int) {
        println("📌 deleteNotification: $notificationId")
        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.apiService.deleteNotification("Bearer $token", notificationId)

                if (response.isSuccessful) {
                    println("✅ Уведомление удалено")
                    updateUnreadCount()
                    refresh()
                }
            } catch (e: Exception) {
                println("❌ Исключение в deleteNotification: ${e.message}")
            }
        }
    }

    private fun updateUnreadCount() {
        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.apiService.getUnreadNotificationsCount("Bearer $token")
                if (response.isSuccessful) {
                    val count = response.body()?.unreadCount ?: 0
                    _unreadCountState.value = UnreadCountState.Success(count)
                    println("📊 Обновлен счетчик: $count")
                }
            } catch (e: Exception) {
                println("❌ Ошибка обновления счетчика: ${e.message}")
            }
        }
    }

    fun loadUnreadCount() {
        // Предотвращаем частые запросы
        val now = System.currentTimeMillis()
        if ((now - lastLoadTime) < MIN_LOAD_INTERVAL && _unreadCountState.value is UnreadCountState.Success) {
            println("⚠️ Пропускаем загрузку счетчика, слишком часто")
            return
        }

        viewModelScope.launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _unreadCountState.value = UnreadCountState.Success(0)
                    return@launch
                }

                val response = ApiClient.apiService.getUnreadNotificationsCount("Bearer $token")
                if (response.isSuccessful) {
                    val count = response.body()?.unreadCount ?: 0
                    _unreadCountState.value = UnreadCountState.Success(count)
                    println("🔔 Непрочитанных уведомлений: $count")
                }
            } catch (e: Exception) {
                println("❌ Ошибка загрузки счетчика: ${e.message}")
                _unreadCountState.value = UnreadCountState.Error(e.message ?: "Ошибка")
            }
        }
    }

    fun refresh() {
        println("🔄 refresh вызван")
        loadNotifications()
    }
}
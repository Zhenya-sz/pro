package com.fitnesslemon.app.data.repository

import com.fitnesslemon.app.data.api.ApiService
import com.fitnesslemon.app.data.models.NotificationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class NotificationRepository(
    private val api: ApiService
) {
    private val _notifications = MutableStateFlow<List<NotificationResponse>>(emptyList())
    val notifications: StateFlow<List<NotificationResponse>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    suspend fun loadNotifications(): Result<List<NotificationResponse>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getNotifications()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки уведомлений"))
                }

                val list = response.body().orEmpty()
                _notifications.value = list
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loadUnreadCount(): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getUnreadNotificationsCount()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки unread count"))
                }
                val count = response.body()?.unread_count ?: 0
                _unreadCount.value = count
                Result.success(count)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun markAsRead(notificationId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.markNotificationRead(mapOf("id" to notificationId))
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка отметки уведомления"))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

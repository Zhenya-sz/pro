package com.fitnesslemon.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.ApiErrorHandler
import com.fitnesslemon.app.data.api.ApiResult
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NotificationsState {
    object Loading : NotificationsState()
    data class Success(
        val chatGroups: List<com.fitnesslemon.app.data.models.NotificationGroup> = emptyList(),
        val bookingGroups: List<com.fitnesslemon.app.data.models.NotificationGroup> = emptyList(),
        val invitations: List<com.fitnesslemon.app.data.models.Notification> = emptyList(),
        val others: List<com.fitnesslemon.app.data.models.Notification> = emptyList()
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
    val notificationsState: StateFlow<NotificationsState> = _notificationsState.asStateFlow()

    private val _unreadCountState = MutableStateFlow<UnreadCountState>(UnreadCountState.Loading)
    val unreadCountState: StateFlow<UnreadCountState> = _unreadCountState.asStateFlow()

    fun loadNotifications() {
        viewModelScope.launch {
            _notificationsState.value = NotificationsState.Loading
            val token = PreferencesManager.getToken() ?: run {
                _notificationsState.value = NotificationsState.Error("Token missing")
                return@launch
            }

            try {
                val response = ApiClient.apiService.getGroupedNotifications("Bearer $token")
                val result = ApiErrorHandler.fromResponse(response)
                when (result) {
                    is ApiResult.Success -> {
                        val data = result.data
                        _notificationsState.value = NotificationsState.Success(
                            chatGroups = data.chats ?: emptyList(),
                            bookingGroups = data.bookings ?: emptyList(),
                            invitations = data.invitations ?: emptyList(),
                            others = data.others ?: emptyList()
                        )
                        _unreadCountState.value = UnreadCountState.Success(data.totalUnread)
                    }
                    is ApiResult.Error -> _notificationsState.value = NotificationsState.Error(result.error.message)
                    is ApiResult.Loading -> Unit
                }
            } catch (t: Throwable) {
                _notificationsState.value = NotificationsState.Error(ApiErrorHandler.fromThrowable(t).message)
            }
        }
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            val token = PreferencesManager.getToken() ?: run {
                _unreadCountState.value = UnreadCountState.Success(0)
                return@launch
            }
            try {
                val response = ApiClient.apiService.getUnreadNotificationsCount("Bearer $token")
                val result = ApiErrorHandler.fromResponse(response)
                if (result is ApiResult.Success) {
                    _unreadCountState.value = UnreadCountState.Success(result.data.unreadCount)
                } else if (result is ApiResult.Error) {
                    _unreadCountState.value = UnreadCountState.Error(result.error.message)
                }
            } catch (t: Throwable) {
                _unreadCountState.value = UnreadCountState.Error(ApiErrorHandler.fromThrowable(t).message)
            }
        }
    }
}

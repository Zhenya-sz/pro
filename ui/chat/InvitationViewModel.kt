package com.fitnesslemon.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.CreateInvitationRequest
import com.fitnesslemon.app.data.models.Invitation
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class InvitationsState {
    object Loading : InvitationsState()
    data class Success(val invitations: List<Invitation>) : InvitationsState()
    data class Error(val message: String) : InvitationsState()
}

sealed class InvitationActionState {
    object Idle : InvitationActionState()
    object Loading : InvitationActionState()
    data class Success(val message: String, val chatId: Int? = null) : InvitationActionState()
    data class Error(val message: String) : InvitationActionState()
}

class InvitationViewModel : ViewModel() {

    private val _invitationsState = MutableStateFlow<InvitationsState>(InvitationsState.Loading)
    val invitationsState: StateFlow<InvitationsState> = _invitationsState

    private val _actionState = MutableStateFlow<InvitationActionState>(InvitationActionState.Idle)
    val actionState: StateFlow<InvitationActionState> = _actionState

    fun loadInvitations() {
        viewModelScope.launch {
            _invitationsState.value = InvitationsState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _invitationsState.value = InvitationsState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.apiService.getPendingInvitations("Bearer $token")

                if (response.isSuccessful) {
                    response.body()?.let { responseData ->
                        _invitationsState.value = InvitationsState.Success(responseData.invitations)
                    } ?: run {
                        _invitationsState.value = InvitationsState.Success(emptyList())
                    }
                } else {
                    _invitationsState.value = InvitationsState.Error("Ошибка загрузки: ${response.code()}")
                }
            } catch (e: Exception) {
                _invitationsState.value = InvitationsState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }

    fun createInvitation(phone: String) {
        viewModelScope.launch {
            _actionState.value = InvitationActionState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _actionState.value = InvitationActionState.Error("Токен не найден")
                    return@launch
                }

                val request = CreateInvitationRequest(inviteePhone = phone)
                val response = ApiClient.apiService.createInvitation("Bearer $token", request)

                if (response.isSuccessful) {
                    response.body()?.let { responseData ->
                        if (responseData.success) {
                            _actionState.value = InvitationActionState.Success(
                                "Приглашение отправлено пользователю $phone"
                            )
                        } else {
                            _actionState.value = InvitationActionState.Error(responseData.message)
                        }
                    } ?: run {
                        _actionState.value = InvitationActionState.Error("Пустой ответ от сервера")
                    }
                } else {
                    when (response.code()) {
                        400 -> {
                            _actionState.value = InvitationActionState.Error(
                                "Приглашение уже отправлено этому пользователю"
                            )
                        }
                        404 -> {
                            _actionState.value = InvitationActionState.Error(
                                "Пользователь с таким телефоном не найден"
                            )
                        }
                        else -> {
                            _actionState.value = InvitationActionState.Error("Ошибка: ${response.code()}")
                        }
                    }
                }
            } catch (e: Exception) {
                _actionState.value = InvitationActionState.Error(e.message ?: "Ошибка сети")
            }
        }
    }

    fun acceptInvitation(invitationId: Int) {
        viewModelScope.launch {
            _actionState.value = InvitationActionState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _actionState.value = InvitationActionState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.apiService.acceptInvitation("Bearer $token", invitationId)

                if (response.isSuccessful) {
                    response.body()?.let { responseData ->
                        if (responseData.success) {
                            _actionState.value = InvitationActionState.Success(
                                "Приглашение принято",
                                responseData.chatId
                            )
                        } else {
                            _actionState.value = InvitationActionState.Error(responseData.message)
                        }
                    } ?: run {
                        _actionState.value = InvitationActionState.Error("Пустой ответ от сервера")
                    }
                } else {
                    when (response.code()) {
                        400 -> {
                            _actionState.value = InvitationActionState.Error(
                                "Приглашение уже обработано или истекло"
                            )
                        }
                        404 -> {
                            _actionState.value = InvitationActionState.Error("Приглашение не найдено")
                        }
                        else -> {
                            _actionState.value = InvitationActionState.Error("Ошибка: ${response.code()}")
                        }
                    }
                }
            } catch (e: Exception) {
                _actionState.value = InvitationActionState.Error(e.message ?: "Ошибка сети")
            }
        }
    }

    fun declineInvitation(invitationId: Int) {
        viewModelScope.launch {
            _actionState.value = InvitationActionState.Loading

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _actionState.value = InvitationActionState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.apiService.declineInvitation("Bearer $token", invitationId)

                if (response.isSuccessful) {
                    response.body()?.let { responseData ->
                        if (responseData.success) {
                            _actionState.value = InvitationActionState.Success("Приглашение отклонено")
                            loadInvitations()
                        } else {
                            _actionState.value = InvitationActionState.Error(responseData.message)
                        }
                    } ?: run {
                        _actionState.value = InvitationActionState.Error("Пустой ответ от сервера")
                    }
                } else {
                    _actionState.value = InvitationActionState.Error("Ошибка: ${response.code()}")
                }
            } catch (e: Exception) {
                _actionState.value = InvitationActionState.Error(e.message ?: "Ошибка сети")
            }
        }
    }

    fun resetActionState() {
        _actionState.value = InvitationActionState.Idle
    }
}
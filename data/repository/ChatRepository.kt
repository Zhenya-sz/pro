package com.fitnesslemon.app.data.repository

import com.fitnesslemon.app.data.api.ApiService
import com.fitnesslemon.app.data.models.ChatResponse
import com.fitnesslemon.app.data.models.MessageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val api: ApiService
) {
    private val _chatList = MutableStateFlow<List<ChatResponse>>(emptyList())
    val chatList: StateFlow<List<ChatResponse>> = _chatList.asStateFlow()

    private val _messages = MutableStateFlow<Map<Int, List<MessageResponse>>>(emptyMap())
    val messages: StateFlow<Map<Int, List<MessageResponse>>> = _messages.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    suspend fun loadChats(force: Boolean = false): Result<List<ChatResponse>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getChats()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки чатов"))
                }

                val list = response.body().orEmpty()
                _chatList.value = list
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loadMessages(chatId: Int, force: Boolean = false): Result<List<MessageResponse>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getMessages(chatId)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки сообщений"))
                }

                val items = response.body()?.messages.orEmpty()
                val updated = _messages.value.toMutableMap()
                updated[chatId] = items
                _messages.value = updated

                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun sendText(chatId: Int, text: String): Result<MessageResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.sendMessage(
                    mapOf(
                        "chat_id" to chatId,
                        "content" to text,
                        "type" to "text"
                    )
                )
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Не удалось отправить сообщение"))
                }

                val message = response.body() ?: throw RuntimeException("Пустой ответ сервера")
                val current = (_messages.value[chatId] ?: emptyList()).toMutableList()
                current.add(0, message)
                val map = _messages.value.toMutableMap()
                map[chatId] = current
                _messages.value = map

                Result.success(message)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun markChatRead(chatId: Int, lastMessageId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.markChatRead(chatId, mapOf("last_read_message_id" to lastMessageId))
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Не удалось отметить сообщения прочитанными"))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loadUnreadCount(): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getUnreadChatsCount()
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

    suspend fun refreshAll(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                loadChats(force = true)
                loadUnreadCount()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

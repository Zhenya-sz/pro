package com.fitnesslemon.app.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.EncryptedTextRequest
import com.fitnesslemon.app.data.api.GroupEncryptedTextRequest
import com.fitnesslemon.app.data.api.SendMessageRequest
import com.fitnesslemon.app.data.api.MarkChatMessagesReadRequest
import com.fitnesslemon.app.data.models.*
import com.fitnesslemon.app.data.repository.ChatRepository
import com.fitnesslemon.app.utils.EncryptionHelper
import com.fitnesslemon.app.utils.PreferencesManager
import com.fitnesslemon.app.utils.ChatPollingManager
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ChatViewModel"

sealed class ChatListState {
    object Loading : ChatListState()
    data class Success(val chats: List<Chat>) : ChatListState()
    data class Error(val message: String) : ChatListState()
}

sealed class ChatDetailState {
    object Loading : ChatDetailState()
    data class Success(val messages: List<Message>) : ChatDetailState()
    data class Error(val message: String) : ChatDetailState()
}

sealed class SendMessageState {
    object Idle : SendMessageState()
    object Sending : SendMessageState()
    data class Success(val message: Message?) : SendMessageState()
    data class Error(val message: String) : SendMessageState()
}

sealed class DeleteMessageState {
    object Idle : DeleteMessageState()
    object Loading : DeleteMessageState()
    data class Success(val message: String) : DeleteMessageState()
    data class Error(val message: String) : DeleteMessageState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _chatListState = MutableStateFlow<ChatListState>(ChatListState.Loading)
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    private val _chatDetailState = MutableStateFlow<ChatDetailState>(ChatDetailState.Loading)
    val chatDetailState: StateFlow<ChatDetailState> = _chatDetailState.asStateFlow()

    private val _sendMessageState = MutableStateFlow<SendMessageState>(SendMessageState.Idle)
    val sendMessageState: StateFlow<SendMessageState> = _sendMessageState.asStateFlow()

    private val _deleteMessageState = MutableStateFlow<DeleteMessageState>(DeleteMessageState.Idle)
    val deleteMessageState: StateFlow<DeleteMessageState> = _deleteMessageState.asStateFlow()

    private val _currentUserId = MutableLiveData(0)
    val currentUserId: LiveData<Int> = _currentUserId

    private val _unreadCount = MutableLiveData(0)
    val unreadCount: LiveData<Int> = _unreadCount

    private val _currentUserRoleInGroup = MutableStateFlow<String?>(null)
    val currentUserRoleInGroup: StateFlow<String?> = _currentUserRoleInGroup.asStateFlow()

    private var currentChatId: Int = 0
    private lateinit var repository: ChatRepository
    private var messageFlowJob: kotlinx.coroutines.Job? = null
    private val isRefreshing = AtomicBoolean(false)
    private var currentChatType: String = "private"
    private var isLoadingMessages = false
    private var lastLoadTime = 0L
    private val MIN_LOAD_INTERVAL = 2000L

    private val chatListPollingManager = ChatPollingManager(
        onPoll = { refreshChatList() },
        intervalMs = 15000
    )

    private val chatDetailPollingManager = ChatPollingManager(
        onPoll = { refreshMessages() },
        intervalMs = 8000
    )

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    init {
        repository = ChatRepository(appContext)
        loadCurrentUserId()
        Log.d(TAG, "✅ ChatViewModel инициализирован")
    }

    private fun loadCurrentUserId() {
        val userId = PreferencesManager.getUserId()
        _currentUserId.postValue(userId)
        Log.d(TAG, "👤 Текущий пользователь: $userId")
    }

    suspend fun loadCurrentUserRole(chatId: Int) {
        val token = PreferencesManager.getToken() ?: return
        try {
            val response = ApiClient.adminApiService.getChatParticipants("Bearer $token", chatId)
            if (response.isSuccessful) {
                val currentUserId = _currentUserId.value ?: return
                val participant = response.body()?.find { it.id == currentUserId }
                _currentUserRoleInGroup.value = participant?.role
                Log.d(TAG, "Роль пользователя в группе $chatId: ${participant?.role}")
            } else {
                _currentUserRoleInGroup.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки роли: ${e.message}")
            _currentUserRoleInGroup.value = null
        }
    }

    fun loadChats() {
        viewModelScope.launch {
            _chatListState.value = ChatListState.Loading
            Log.d(TAG, "🔄 Загрузка списка чатов")

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    Log.e(TAG, "❌ Токен не найден")
                    _chatListState.value = ChatListState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.apiService.getChats("Bearer $token")

                if (response.isSuccessful) {
                    val chatsResponse = response.body()
                    if (chatsResponse != null && chatsResponse.success) {
                        val chats = chatsResponse.data
                        Log.d(TAG, "✅ Загружено ${chats.size} чатов")
                        _chatListState.value = ChatListState.Success(chats)
                        val totalUnread = chats.sumOf { it.unreadCount }
                        _unreadCount.postValue(totalUnread)
                    } else {
                        Log.w(TAG, "⚠️ Пустой ответ от сервера")
                        _chatListState.value = ChatListState.Error("Пустой ответ от сервера")
                    }
                } else {
                    Log.e(TAG, "❌ Ошибка загрузки: ${response.code()}")
                    _chatListState.value = ChatListState.Error("Ошибка загрузки: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Исключение: ${e.message}", e)
                _chatListState.value = ChatListState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }

    private suspend fun refreshChatList() {
        try {
            val token = PreferencesManager.getToken() ?: return
            val response = ApiClient.apiService.getChats("Bearer $token")

            if (response.isSuccessful) {
                val chatsResponse = response.body()
                if (chatsResponse != null && chatsResponse.success) {
                    val chats = chatsResponse.data
                    _chatListState.value = ChatListState.Success(chats)
                    val totalUnread = chats.sumOf { it.unreadCount }
                    _unreadCount.postValue(totalUnread)
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки при polling
        }
    }

    fun loadChatMessages(chatId: Int, chatName: String, chatType: String = "private") {
        if (isLoadingMessages) {
            Log.d(TAG, "⏳ Загрузка уже выполняется, пропускаем")
            return
        }

        currentChatId = chatId
        this.currentChatType = chatType
        lastLoadTime = System.currentTimeMillis()
        Log.d(TAG, "🔄 loadChatMessages START для чата $chatId ($chatName), тип: $chatType")

        messageFlowJob?.cancel()

        messageFlowJob = viewModelScope.launch {
            repository.getMessagesForChat(chatId).collect { messages ->
                Log.d(TAG, "📊 Flow получил ${messages.size} сообщений для чата $chatId")
                val uniqueMessages = messages.distinctBy { it.id }
                _chatDetailState.value = ChatDetailState.Success(uniqueMessages)
            }
        }

        viewModelScope.launch {
            isLoadingMessages = true

            try {
                val currentState = _chatDetailState.value
                if (currentState is ChatDetailState.Success && currentState.messages.isEmpty()) {
                    _chatDetailState.value = ChatDetailState.Loading
                    Log.d(TAG, "⏳ Нет сообщений в кэше, показываем загрузку")
                }

                val decryptedMessages = repository.loadAndDecryptMessages(chatId, chatType)
                Log.d(TAG, "📡 Загружено с сервера ${decryptedMessages.size} сообщений")

                if (decryptedMessages.isNotEmpty()) {
                    decryptedMessages.lastOrNull()?.let { lastMessage ->
                        if (lastMessage.senderId != _currentUserId.value) {
                            markMessagesAsRead(chatId, lastMessage.id)
                            Log.d(TAG, "📖 Отмечено как прочитанное сообщение ${lastMessage.id}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка загрузки: ${e.message}", e)
                val currentState = _chatDetailState.value
                if (currentState is ChatDetailState.Success && currentState.messages.isEmpty()) {
                    _chatDetailState.value = ChatDetailState.Error(e.message ?: "Ошибка загрузки")
                }
            } finally {
                isLoadingMessages = false
            }
        }
    }

    fun forceRefreshChat(chatId: Int) {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Принудительное обновление чата $chatId")
            try {
                repository.deleteMessagesForChat(chatId)
                repository.clearCache(chatId)
                lastLoadTime = 0
                loadChatMessages(chatId, "", currentChatType)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка принудительного обновления: ${e.message}", e)
            }
        }
    }

    fun clearChatMessages() {
        Log.d(TAG, "🧹 Очищаем сообщения в ViewModel")
        _chatDetailState.value = ChatDetailState.Success(emptyList())

        messageFlowJob?.cancel()
        messageFlowJob = null

        isLoadingMessages = false
        currentChatId = 0
        lastLoadTime = 0
    }

    fun updateMessagesWithParticipants(participantsMap: Map<Int, Pair<String, String?>>) {
        viewModelScope.launch {
            try {
                repository.updateMessagesWithParticipants(participantsMap)
                Log.d(TAG, "✅ Массовое обновление сообщений выполнено")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка массового обновления: ${e.message}", e)
            }
        }
    }

    private suspend fun refreshMessages() {
        if (currentChatId == 0) return
        if (!isRefreshing.compareAndSet(false, true)) {
            Log.d(TAG, "⚠️ Обновление уже выполняется, пропускаем")
            return
        }

        try {
            val cachedMessages = repository.getCachedMessages(currentChatId)
            if (cachedMessages.isNotEmpty()) {
                Log.d(TAG, "📡 Используем кэш, пропускаем запрос к серверу")
                return
            }

            Log.d(TAG, "🔄 Polling: обновление сообщений чата $currentChatId")
            repository.loadAndDecryptMessages(currentChatId, currentChatType)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка при polling: ${e.message}", e)
        } finally {
            isRefreshing.set(false)
        }
    }

    // ===== ОТПРАВКА ТЕКСТОВОГО СООБЩЕНИЯ =====

    fun sendMessage(chatId: Int, text: String, recipientPublicKey: String?, chatType: String) {
        viewModelScope.launch {
            val currentUserId = PreferencesManager.getUserId()
            val tempId = -System.currentTimeMillis().toInt()

            val tempMessage = Message(
                id = tempId,
                chatId = chatId,
                senderId = currentUserId,
                senderName = "Вы",
                senderAvatar = null,
                type = "text",
                content = text,
                fileName = null,
                fileSize = null,
                mimeType = null,
                createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                isRead = true,
                isMe = true,
                duration = null,
                localUri = null,
                fileToken = null
            )

            val currentState = _chatDetailState.value
            if (currentState is ChatDetailState.Success) {
                val newMessages = currentState.messages + tempMessage
                _chatDetailState.value = ChatDetailState.Success(newMessages)
            }

            _sendMessageState.value = SendMessageState.Sending

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    removeTempMessageFromUI(tempId)
                    _sendMessageState.value = SendMessageState.Error("Токен не найден")
                    return@launch
                }

                val request = SendMessageRequest(chatId = chatId, type = "text", content = text)
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.sendMessage("Bearer $token", request)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    val realId = response.body()!!.messageId
                    Log.d(TAG, "✅ Сообщение отправлено, ID: $realId")

                    removeTempMessageFromUI(tempId)

                    repository.updateMessageId(tempId, realId)

                    repository.loadAndDecryptMessages(chatId, chatType)

                    refreshChatList()
                    _sendMessageState.value = SendMessageState.Success(null)
                } else {
                    Log.e(TAG, "❌ Ошибка отправки: ${response.code()}")
                    removeTempMessageFromUI(tempId)
                    _sendMessageState.value = SendMessageState.Error("Ошибка отправки")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка отправки: ${e.message}")
                removeTempMessageFromUI(tempId)
                _sendMessageState.value = SendMessageState.Error(e.message ?: "Ошибка отправки")
            }
        }
    }

    private fun updateMessageIdInUI(tempId: Int, realId: Int) {
        val currentState = _chatDetailState.value
        if (currentState is ChatDetailState.Success) {
            val withoutTemp = currentState.messages.filter { it.id != tempId }

            val updatedMessages = withoutTemp.map { message ->
                if (message.id == realId) {
                    message.copy(id = realId)
                } else {
                    message
                }
            }
            _chatDetailState.value = ChatDetailState.Success(updatedMessages)
        }
    }

    private fun removeTempMessageFromUI(tempId: Int) {
        val currentState = _chatDetailState.value
        if (currentState is ChatDetailState.Success) {
            val updatedMessages = currentState.messages.filter { it.id != tempId }
            _chatDetailState.value = ChatDetailState.Success(updatedMessages)
            Log.d(TAG, "🗑️ Временное сообщение $tempId удалено из UI")
        }
    }

    // ===== ОТПРАВКА ИЗОБРАЖЕНИЙ =====

    fun sendImageMessage(chatId: Int, imageFile: File, recipientPublicKey: String?, chatType: String) {
        viewModelScope.launch {
            _sendMessageState.value = SendMessageState.Sending
            Log.d(TAG, "📸 Отправка изображения в чат $chatId: ${imageFile.name}, тип: $chatType")

            try {
                val currentUserId = PreferencesManager.getUserId()
                val tempMessage = Message(
                    id = -System.currentTimeMillis().toInt(),
                    chatId = chatId,
                    senderId = currentUserId,
                    senderName = "Вы",
                    senderAvatar = null,
                    type = "image",
                    content = imageFile.absolutePath,
                    fileName = imageFile.name,
                    fileSize = imageFile.length().toInt(),
                    mimeType = "image/jpeg",
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    isRead = true,
                    isMe = true,
                    duration = null,
                    localUri = imageFile.absolutePath,
                    fileToken = null
                )

                val currentState = _chatDetailState.value
                if (currentState is ChatDetailState.Success) {
                    val newMessages = currentState.messages + tempMessage
                    _chatDetailState.value = ChatDetailState.Success(newMessages)
                }

                if (chatType == "group") {
                    sendEncryptedGroupImage(chatId, imageFile, tempMessage.id)
                } else if (recipientPublicKey.isNullOrEmpty()) {
                    sendPlainImageMessage(chatId, imageFile, tempMessage.id)
                } else {
                    sendEncryptedImage(chatId, imageFile, recipientPublicKey, tempMessage.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка отправки изображения: ${e.message}", e)
                _sendMessageState.value = SendMessageState.Error(e.message ?: "Ошибка отправки")
            }
        }
    }

    private suspend fun sendPlainImageMessage(chatId: Int, imageFile: File, tempId: Int) {
        val token = PreferencesManager.getToken() ?: return

        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val requestFile = imageFile.asRequestBody("image/jpeg".toMediaType())
        val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)

        val response = ApiClient.apiService.sendImageMessage(
            token = "Bearer $token",
            chatId = chatIdPart,
            image = imagePart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Изображение отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            Log.e(TAG, "❌ Ошибка отправки изображения: ${response.code()}")
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "❌ Тело ошибки: $errorBody")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }
    }

    private suspend fun sendEncryptedImage(
        chatId: Int,
        imageFile: File,
        recipientPublicKey: String,
        tempId: Int
    ) {
        val aesKey = EncryptionHelper.generateAESKey()
        val (encryptedFile, _) = EncryptionHelper.encryptFile(imageFile, aesKey)
        val encryptedKey = EncryptionHelper.encryptAESKeyWithRSA(aesKey, recipientPublicKey)

        if (encryptedKey.isEmpty()) {
            Log.e(TAG, "❌ Ошибка шифрования ключа изображения")
            encryptedFile.delete()
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка шифрования")
            return
        }

        val token = PreferencesManager.getToken() ?: return
        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val encryptedKeyPart = encryptedKey.toRequestBody(MultipartBody.FORM)
        val requestFile = encryptedFile.asRequestBody("image/jpeg".toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", encryptedFile.name, requestFile)

        val response = ApiClient.apiService.sendEncryptedImage(
            token = "Bearer $token",
            chatId = chatIdPart,
            encryptedKey = encryptedKeyPart,
            file = filePart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Зашифрованное изображение отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            Log.e(TAG, "❌ Ошибка отправки зашифрованного изображения: ${response.code()}")
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "❌ Тело ошибки: $errorBody")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }

        encryptedFile.delete()
        Log.d(TAG, "🗑️ Зашифрованный временный файл удален: ${encryptedFile.absolutePath}")
    }

    private suspend fun sendEncryptedGroupImage(
        chatId: Int,
        imageFile: File,
        tempId: Int
    ) {
        val token = PreferencesManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "❌ Токен не найден")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Токен не найден")
            return
        }

        val participants = try {
            val response = ApiClient.adminApiService.getChatParticipants("Bearer $token", chatId)
            if (response.isSuccessful) {
                response.body()?.filter { it.id != _currentUserId.value } ?: emptyList()
            } else {
                Log.e(TAG, "❌ Ошибка получения участников: ${response.code()}")
                removeTempMessageFromUI(tempId)
                _sendMessageState.value = SendMessageState.Error("Ошибка получения участников")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка: ${e.message}")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error(e.message ?: "Ошибка")
            return
        }

        if (participants.isEmpty()) {
            Log.e(TAG, "❌ Нет других участников в группе")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Нет участников")
            return
        }

        val aesKey = EncryptionHelper.generateAESKey()
        val (encryptedFile, _) = EncryptionHelper.encryptFile(imageFile, aesKey)

        val encryptedKeys = mutableMapOf<Int, String>()

        for (participant in participants) {
            try {
                val keyResponse = ApiClient.apiService.getUserPublicKey("Bearer $token", participant.id)
                if (keyResponse.isSuccessful && !keyResponse.body()?.public_key.isNullOrEmpty()) {
                    val publicKey = keyResponse.body()!!.public_key
                    val encryptedKey = EncryptionHelper.encryptAESKeyWithRSA(aesKey, publicKey!!)
                    if (encryptedKey.isNotEmpty()) {
                        encryptedKeys[participant.id] = encryptedKey
                        Log.d(TAG, "✅ Ключ для участника ${participant.id} зашифрован")
                    } else {
                        Log.w(TAG, "⚠️ Не удалось зашифровать ключ для ${participant.id}")
                    }
                } else {
                    Log.w(TAG, "⚠️ Нет публичного ключа у участника ${participant.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка для участника ${participant.id}: ${e.message}")
            }
        }

        if (encryptedKeys.isEmpty()) {
            Log.e(TAG, "❌ Не удалось зашифровать ключи")
            encryptedFile.delete()
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Не удалось зашифровать ключи")
            return
        }

        val encryptedKeysJson = gson.toJson(encryptedKeys)
        Log.d(TAG, "📤 Отправляем encrypted_keys JSON: $encryptedKeysJson")

        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val encryptedKeysPart = encryptedKeysJson.toRequestBody(MultipartBody.FORM)
        val requestFile = encryptedFile.asRequestBody("image/jpeg".toMediaType())
        val imagePart = MultipartBody.Part.createFormData("image", encryptedFile.name, requestFile)

        val response = ApiClient.apiService.sendGroupEncryptedImage(
            token = "Bearer $token",
            chatId = chatIdPart,
            encryptedKeys = encryptedKeysPart,
            image = imagePart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Групповое зашифрованное изображение отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "❌ Ошибка отправки группового изображения: ${response.code()}, тело: $errorBody")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }

        encryptedFile.delete()
    }

    // ===== ОТПРАВКА АУДИО =====

    fun sendAudioMessage(chatId: Int, audioFile: File, duration: Int, recipientPublicKey: String?, chatType: String) {
        viewModelScope.launch {
            _sendMessageState.value = SendMessageState.Sending
            val mimeType = getMimeType(audioFile)
            Log.d(TAG, "🎵 Отправка аудио в чат $chatId: ${audioFile.name}, длительность: $duration сек, тип: $chatType")

            try {
                val currentUserId = PreferencesManager.getUserId()
                val tempMessage = Message(
                    id = -System.currentTimeMillis().toInt(),
                    chatId = chatId,
                    senderId = currentUserId,
                    senderName = "Вы",
                    senderAvatar = null,
                    type = "audio",
                    content = audioFile.absolutePath,
                    fileName = audioFile.name,
                    fileSize = audioFile.length().toInt(),
                    mimeType = mimeType,
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    isRead = true,
                    isMe = true,
                    duration = duration,
                    localUri = audioFile.absolutePath,
                    fileToken = null
                )

                val currentState = _chatDetailState.value
                if (currentState is ChatDetailState.Success) {
                    val newMessages = currentState.messages + tempMessage
                    _chatDetailState.value = ChatDetailState.Success(newMessages)
                }

                if (chatType == "group") {
                    sendEncryptedGroupAudio(chatId, audioFile, duration, tempMessage.id)
                } else if (recipientPublicKey.isNullOrEmpty()) {
                    sendPlainAudioMessage(chatId, audioFile, duration, tempMessage.id)
                } else {
                    sendEncryptedAudio(chatId, audioFile, duration, recipientPublicKey, tempMessage.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка отправки аудио: ${e.message}", e)
                _sendMessageState.value = SendMessageState.Error(e.message ?: "Ошибка отправки")
            }
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "m4a" -> "audio/mp4"
            "mp4" -> "audio/mp4"
            "3gp", "3gpp" -> "audio/3gpp"
            "amr" -> "audio/amr"
            "mp3" -> "audio/mpeg"
            else -> "audio/mp4"
        }
    }

    private suspend fun sendPlainAudioMessage(chatId: Int, audioFile: File, duration: Int, tempId: Int) {
        val token = PreferencesManager.getToken() ?: return
        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val durationPart = duration.toString().toRequestBody(MultipartBody.FORM)
        val mimeType = getMimeType(audioFile)
        val requestFile = audioFile.asRequestBody(mimeType.toMediaType())
        val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)

        val response = ApiClient.apiService.sendAudioMessage(
            token = "Bearer $token",
            chatId = chatIdPart,
            audio = audioPart,
            duration = durationPart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Аудио отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            Log.e(TAG, "❌ Ошибка отправки аудио: ${response.code()}")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }
    }

    private suspend fun sendEncryptedAudio(
        chatId: Int,
        audioFile: File,
        duration: Int,
        recipientPublicKey: String,
        tempId: Int
    ) {
        val aesKey = EncryptionHelper.generateAESKey()
        val (encryptedFile, _) = EncryptionHelper.encryptFile(audioFile, aesKey)
        val encryptedKey = EncryptionHelper.encryptAESKeyWithRSA(aesKey, recipientPublicKey)

        if (encryptedKey.isEmpty()) {
            Log.e(TAG, "❌ Ошибка шифрования ключа аудио")
            encryptedFile.delete()
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка шифрования")
            return
        }

        val token = PreferencesManager.getToken() ?: return
        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val durationPart = duration.toString().toRequestBody(MultipartBody.FORM)
        val encryptedKeyPart = encryptedKey.toRequestBody(MultipartBody.FORM)
        val mimeType = getMimeType(audioFile)
        val requestFile = encryptedFile.asRequestBody(mimeType.toMediaType())
        val audioPart = MultipartBody.Part.createFormData("audio", encryptedFile.name, requestFile)

        val response = ApiClient.apiService.sendEncryptedAudio(
            token = "Bearer $token",
            chatId = chatIdPart,
            encryptedKey = encryptedKeyPart,
            duration = durationPart,
            audio = audioPart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Зашифрованное аудио отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            Log.e(TAG, "❌ Ошибка отправки зашифрованного аудио: ${response.code()}")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }

        encryptedFile.delete()
    }

    private suspend fun sendEncryptedGroupAudio(
        chatId: Int,
        audioFile: File,
        duration: Int,
        tempId: Int
    ) {
        val token = PreferencesManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "❌ Токен не найден")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Токен не найден")
            return
        }

        val participants = try {
            val response = ApiClient.adminApiService.getChatParticipants("Bearer $token", chatId)
            if (response.isSuccessful) {
                response.body()?.filter { it.id != _currentUserId.value } ?: emptyList()
            } else {
                Log.e(TAG, "❌ Ошибка получения участников: ${response.code()}")
                removeTempMessageFromUI(tempId)
                _sendMessageState.value = SendMessageState.Error("Ошибка получения участников")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка: ${e.message}")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error(e.message ?: "Ошибка")
            return
        }

        if (participants.isEmpty()) {
            Log.e(TAG, "❌ Нет других участников в группе")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Нет участников")
            return
        }

        val aesKey = EncryptionHelper.generateAESKey()
        val (encryptedFile, _) = EncryptionHelper.encryptFile(audioFile, aesKey)

        val encryptedKeys = mutableMapOf<Int, String>()

        for (participant in participants) {
            try {
                val keyResponse = ApiClient.apiService.getUserPublicKey("Bearer $token", participant.id)
                if (keyResponse.isSuccessful && !keyResponse.body()?.public_key.isNullOrEmpty()) {
                    val publicKey = keyResponse.body()!!.public_key
                    val encryptedKey = EncryptionHelper.encryptAESKeyWithRSA(aesKey, publicKey!!)
                    if (encryptedKey.isNotEmpty()) {
                        encryptedKeys[participant.id] = encryptedKey
                        Log.d(TAG, "✅ Ключ для участника ${participant.id} зашифрован")
                    } else {
                        Log.w(TAG, "⚠️ Не удалось зашифровать ключ для ${participant.id}")
                    }
                } else {
                    Log.w(TAG, "⚠️ Нет публичного ключа у участника ${participant.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка для участника ${participant.id}: ${e.message}")
            }
        }

        if (encryptedKeys.isEmpty()) {
            Log.e(TAG, "❌ Не удалось зашифровать ключи")
            encryptedFile.delete()
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Не удалось зашифровать ключи")
            return
        }

        val encryptedKeysJson = gson.toJson(encryptedKeys)
        Log.d(TAG, "📤 Отправляем encrypted_keys JSON: $encryptedKeysJson")

        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val durationPart = duration.toString().toRequestBody(MultipartBody.FORM)
        val encryptedKeysPart = encryptedKeysJson.toRequestBody(MultipartBody.FORM)
        val mimeType = getMimeType(audioFile)
        val requestFile = encryptedFile.asRequestBody(mimeType.toMediaType())
        val audioPart = MultipartBody.Part.createFormData("audio", encryptedFile.name, requestFile)

        val response = ApiClient.apiService.sendGroupEncryptedAudio(
            token = "Bearer $token",
            chatId = chatIdPart,
            encryptedKeys = encryptedKeysPart,
            duration = durationPart,
            audio = audioPart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Групповое зашифрованное аудио отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "❌ Ошибка отправки группового аудио: ${response.code()}, тело: $errorBody")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }

        encryptedFile.delete()
    }

    // =============================================
    // ===== ОТПРАВКА ВИДЕО =====
    // =============================================

    fun sendVideoMessage(chatId: Int, videoFile: File, duration: Int, recipientPublicKey: String?, chatType: String) {
        viewModelScope.launch {
            _sendMessageState.value = SendMessageState.Sending
            Log.d(TAG, "🎥 Отправка видео в чат $chatId: ${videoFile.name}, длительность: $duration сек, тип: $chatType")

            try {
                val currentUserId = PreferencesManager.getUserId()
                val tempMessage = Message(
                    id = -System.currentTimeMillis().toInt(),
                    chatId = chatId,
                    senderId = currentUserId,
                    senderName = "Вы",
                    senderAvatar = null,
                    type = "video",
                    content = videoFile.absolutePath,
                    fileName = videoFile.name,
                    fileSize = videoFile.length().toInt(),
                    mimeType = "video/mp4",
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    isRead = true,
                    isMe = true,
                    duration = duration,
                    localUri = videoFile.absolutePath,
                    fileToken = null
                )

                val currentState = _chatDetailState.value
                if (currentState is ChatDetailState.Success) {
                    val newMessages = currentState.messages + tempMessage
                    _chatDetailState.value = ChatDetailState.Success(newMessages)
                }

                if (chatType == "group") {
                    sendEncryptedGroupVideo(chatId, videoFile, duration, tempMessage.id)
                } else if (recipientPublicKey.isNullOrEmpty()) {
                    sendPlainVideoMessage(chatId, videoFile, duration, tempMessage.id)
                } else {
                    sendEncryptedVideo(chatId, videoFile, duration, recipientPublicKey, tempMessage.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка отправки видео: ${e.message}", e)
                _sendMessageState.value = SendMessageState.Error(e.message ?: "Ошибка отправки")
            }
        }
    }

    private suspend fun sendPlainVideoMessage(chatId: Int, videoFile: File, duration: Int, tempId: Int) {
        val token = PreferencesManager.getToken() ?: return

        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val durationPart = duration.toString().toRequestBody(MultipartBody.FORM)
        val requestFile = videoFile.asRequestBody("video/mp4".toMediaType())
        val videoPart = MultipartBody.Part.createFormData("video", videoFile.name, requestFile)

        val response = ApiClient.apiService.sendVideoMessage(
            token = "Bearer $token",
            chatId = chatIdPart,
            video = videoPart,
            duration = durationPart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Видео отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            Log.e(TAG, "❌ Ошибка отправки видео: ${response.code()}")
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "❌ Тело ошибки: $errorBody")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }
    }

    private suspend fun sendEncryptedVideo(
        chatId: Int,
        videoFile: File,
        duration: Int,
        recipientPublicKey: String,
        tempId: Int
    ) {
        val aesKey = EncryptionHelper.generateAESKey()
        val (encryptedFile, _) = EncryptionHelper.encryptFile(videoFile, aesKey)
        val encryptedKey = EncryptionHelper.encryptAESKeyWithRSA(aesKey, recipientPublicKey)

        if (encryptedKey.isEmpty()) {
            Log.e(TAG, "❌ Ошибка шифрования ключа видео")
            encryptedFile.delete()
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка шифрования")
            return
        }

        val token = PreferencesManager.getToken() ?: return
        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val durationPart = duration.toString().toRequestBody(MultipartBody.FORM)
        val encryptedKeyPart = encryptedKey.toRequestBody(MultipartBody.FORM)
        val requestFile = encryptedFile.asRequestBody("video/mp4".toMediaType())
        val videoPart = MultipartBody.Part.createFormData("video", encryptedFile.name, requestFile)

        val response = ApiClient.apiService.sendEncryptedVideo(
            token = "Bearer $token",
            chatId = chatIdPart,
            encryptedKey = encryptedKeyPart,
            duration = durationPart,
            video = videoPart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Зашифрованное видео отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            Log.e(TAG, "❌ Ошибка отправки зашифрованного видео: ${response.code()}")
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "❌ Тело ошибки: $errorBody")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }

        encryptedFile.delete()
        Log.d(TAG, "🗑️ Зашифрованный временный видеофайл удален: ${encryptedFile.absolutePath}")
    }

    private suspend fun sendEncryptedGroupVideo(
        chatId: Int,
        videoFile: File,
        duration: Int,
        tempId: Int
    ) {
        val token = PreferencesManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "❌ Токен не найден")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Токен не найден")
            return
        }

        val participants = try {
            val response = ApiClient.adminApiService.getChatParticipants("Bearer $token", chatId)
            if (response.isSuccessful) {
                response.body()?.filter { it.id != _currentUserId.value } ?: emptyList()
            } else {
                Log.e(TAG, "❌ Ошибка получения участников: ${response.code()}")
                removeTempMessageFromUI(tempId)
                _sendMessageState.value = SendMessageState.Error("Ошибка получения участников")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка: ${e.message}")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error(e.message ?: "Ошибка")
            return
        }

        if (participants.isEmpty()) {
            Log.e(TAG, "❌ Нет других участников в группе")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Нет участников")
            return
        }

        val aesKey = EncryptionHelper.generateAESKey()
        val (encryptedFile, _) = EncryptionHelper.encryptFile(videoFile, aesKey)

        val encryptedKeys = mutableMapOf<Int, String>()

        for (participant in participants) {
            try {
                val keyResponse = ApiClient.apiService.getUserPublicKey("Bearer $token", participant.id)
                if (keyResponse.isSuccessful && !keyResponse.body()?.public_key.isNullOrEmpty()) {
                    val publicKey = keyResponse.body()!!.public_key
                    val encryptedKey = EncryptionHelper.encryptAESKeyWithRSA(aesKey, publicKey!!)
                    if (encryptedKey.isNotEmpty()) {
                        encryptedKeys[participant.id] = encryptedKey
                        Log.d(TAG, "✅ Ключ для участника ${participant.id} зашифрован")
                    } else {
                        Log.w(TAG, "⚠️ Не удалось зашифровать ключ для ${participant.id}")
                    }
                } else {
                    Log.w(TAG, "⚠️ Нет публичного ключа у участника ${participant.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка для участника ${participant.id}: ${e.message}")
            }
        }

        if (encryptedKeys.isEmpty()) {
            Log.e(TAG, "❌ Не удалось зашифровать ключи")
            encryptedFile.delete()
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Не удалось зашифровать ключи")
            return
        }

        val encryptedKeysJson = gson.toJson(encryptedKeys)
        Log.d(TAG, "📤 Отправляем encrypted_keys JSON: $encryptedKeysJson")

        val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)
        val durationPart = duration.toString().toRequestBody(MultipartBody.FORM)
        val encryptedKeysPart = encryptedKeysJson.toRequestBody(MultipartBody.FORM)
        val requestFile = encryptedFile.asRequestBody("video/mp4".toMediaType())
        val videoPart = MultipartBody.Part.createFormData("video", encryptedFile.name, requestFile)

        val response = ApiClient.apiService.sendGroupEncryptedVideo(
            token = "Bearer $token",
            chatId = chatIdPart,
            encryptedKeys = encryptedKeysPart,
            duration = durationPart,
            video = videoPart
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val realId = response.body()!!.messageId
            Log.d(TAG, "✅ Групповое зашифрованное видео отправлено, ID: $realId")
            updateMessageIdInUI(tempId, realId)
            repository.updateMessageId(tempId, realId)

            repository.clearCache(chatId)
            repository.loadAndDecryptMessages(chatId, currentChatType)

            refreshChatList()
            _sendMessageState.value = SendMessageState.Success(null)
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "❌ Ошибка отправки группового видео: ${response.code()}, тело: $errorBody")
            removeTempMessageFromUI(tempId)
            _sendMessageState.value = SendMessageState.Error("Ошибка отправки: ${response.code()}")
        }

        encryptedFile.delete()
        Log.d(TAG, "🗑️ Зашифрованный временный видеофайл удален: ${encryptedFile.absolutePath}")
    }

    // ===== УДАЛЕНИЕ СООБЩЕНИЙ =====

    fun deleteMessageForMe(messageId: Int) {
        viewModelScope.launch {
            _deleteMessageState.value = DeleteMessageState.Loading
            Log.d(TAG, "🗑️ Удаление сообщения $messageId у себя")

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _deleteMessageState.value = DeleteMessageState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.apiService.deleteMessageForMe(
                    token = "Bearer $token",
                    chatId = currentChatId,
                    messageId = messageId
                )

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Сообщение $messageId удалено у себя")
                    removeMessageFromUI(messageId)
                    _deleteMessageState.value = DeleteMessageState.Success("Сообщение удалено")
                    refreshMessages()
                } else {
                    Log.e(TAG, "❌ Ошибка удаления: ${response.code()}")
                    _deleteMessageState.value = DeleteMessageState.Error("Ошибка удаления: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка удаления: ${e.message}", e)
                _deleteMessageState.value = DeleteMessageState.Error(e.message ?: "Ошибка удаления")
            }
        }
    }

    fun deleteMessageForEveryone(messageId: Int) {
        viewModelScope.launch {
            _deleteMessageState.value = DeleteMessageState.Loading
            Log.d(TAG, "🗑️ Удаление сообщения $messageId у всех")

            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    _deleteMessageState.value = DeleteMessageState.Error("Токен не найден")
                    return@launch
                }

                val response = ApiClient.apiService.deleteMessageForEveryone(
                    token = "Bearer $token",
                    chatId = currentChatId,
                    messageId = messageId
                )

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Сообщение $messageId удалено у всех")
                    removeMessageFromUI(messageId)
                    _deleteMessageState.value = DeleteMessageState.Success("Сообщение удалено у всех")
                    refreshMessages()
                } else {
                    Log.e(TAG, "❌ Ошибка удаления: ${response.code()}")
                    _deleteMessageState.value = DeleteMessageState.Error("Ошибка удаления: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка удаления: ${e.message}", e)
                _deleteMessageState.value = DeleteMessageState.Error(e.message ?: "Ошибка удаления")
            }
        }
    }

    private fun removeMessageFromUI(messageId: Int) {
        val currentState = _chatDetailState.value
        if (currentState is ChatDetailState.Success) {
            val updatedMessages = currentState.messages.filter { it.id != messageId }
            _chatDetailState.value = ChatDetailState.Success(updatedMessages)
        }
    }

    fun updateMessagesWithSenderInfo(senderId: Int, senderName: String, senderAvatar: String?) {
        viewModelScope.launch {
            try {
                repository.updateMessageWithSenderInfo(senderId, senderName, senderAvatar)
                Log.d(TAG, "✅ Обновлены сообщения для отправителя $senderId: имя=$senderName")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка обновления: ${e.message}", e)
            }
        }
    }

    private suspend fun markMessagesAsRead(chatId: Int, lastMessageId: Int) {
        try {
            val token = PreferencesManager.getToken() ?: return
            val request = MarkChatMessagesReadRequest(lastReadMessageId = lastMessageId)

            ApiClient.apiService.markMessagesAsRead(
                token = "Bearer $token",
                chatId = chatId,
                request = request
            )
            Log.d(TAG, "📖 Сообщения отмечены как прочитанные до $lastMessageId")
        } catch (e: Exception) {
            // Игнорируем ошибки
        }
    }

    fun startChatListPolling() {
        chatListPollingManager.startPolling(viewModelScope)
        Log.d(TAG, "▶️ Запущен polling списка чатов")
    }

    fun stopChatListPolling() {
        chatListPollingManager.stopPolling()
        Log.d(TAG, "⏹️ Остановлен polling списка чатов")
    }

    fun startChatDetailPolling() {
        chatDetailPollingManager.startPolling(viewModelScope)
        Log.d(TAG, "▶️ Запущен polling деталей чата")
    }

    fun stopChatDetailPolling() {
        chatDetailPollingManager.stopPolling()
        Log.d(TAG, "⏹️ Остановлен polling деталей чата")
    }

    fun clearSendState() {
        _sendMessageState.value = SendMessageState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 ViewModel очищен")
        stopChatListPolling()
        stopChatDetailPolling()
        messageFlowJob?.cancel()
    }
}
package com.fitnesslemon.app.data.repository

import android.content.Context
import android.util.Log
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.local.AppDatabase
import com.fitnesslemon.app.data.local.MessageEntity
import com.fitnesslemon.app.data.models.Message
import com.fitnesslemon.app.utils.EncryptionHelper
import com.fitnesslemon.app.utils.KeyStoreManager
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.PrivateKey

private const val TAG = "ChatRepository"

class ChatRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val messageDao = database.messageDao()
    private lateinit var keyStoreManager: KeyStoreManager

    // ===== КЭШИРОВАНИЕ В ПАМЯТИ =====
    private val messageCache = mutableMapOf<Int, List<Message>>()
    private val cacheTime = mutableMapOf<Int, Long>()
    private val CACHE_DURATION_MS = 30000L // 30 секунд

    private val decryptedFilesDir: File by lazy {
        val dir = File(context.filesDir, "decrypted_files")
        if (!dir.exists()) {
            dir.mkdirs()
            Log.d(TAG, "📁 Создана директория: ${dir.absolutePath}")
        }
        dir
    }

    init {
        keyStoreManager = KeyStoreManager(context)
        Log.d(TAG, "ChatRepository инициализирован")
    }

    // ===== МЕТОДЫ ДЛЯ КЭШИРОВАНИЯ =====

    /**
     * Получить кэшированные сообщения для чата
     */
    suspend fun getCachedMessages(chatId: Int): List<Message> {
        val cached = messageCache[chatId]
        val time = cacheTime[chatId] ?: 0

        if (cached != null && System.currentTimeMillis() - time < CACHE_DURATION_MS) {
            Log.d(TAG, "✅ Используем кэш для чата $chatId: ${cached.size} сообщений")
            return cached
        }

        // Загружаем из БД
        val messages = getMessagesFromDatabase(chatId)
        messageCache[chatId] = messages
        cacheTime[chatId] = System.currentTimeMillis()
        return messages
    }

    /**
     * Получить сообщения из БД синхронно
     */
    private suspend fun getMessagesFromDatabase(chatId: Int): List<Message> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = messageDao.getMessagesForChatSync(chatId)
                entities.map { entity ->
                    entity.toMessage()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка получения сообщений из БД: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Очистить кэш для чата
     */
    suspend fun clearCache(chatId: Int) {
        messageCache.remove(chatId)
        cacheTime.remove(chatId)
        Log.d(TAG, "✅ Кэш очищен для чата $chatId")
    }

    /**
     * Очистить весь кэш
     */
    suspend fun clearAllCache() {
        messageCache.clear()
        cacheTime.clear()
        Log.d(TAG, "✅ Весь кэш очищен")
    }

    // ===== ОСНОВНЫЕ МЕТОДЫ =====

    fun getMessagesForChat(chatId: Int): Flow<List<Message>> {
        return messageDao.getMessagesForChat(chatId).map { entities ->
            entities.map { entity ->
                entity.toMessage()
            }.filter { it.id > 0 }.distinctBy { it.id }
        }
    }

    suspend fun loadAndDecryptMessages(chatId: Int, chatType: String = "private"): List<Message> = withContext(Dispatchers.IO) {
        try {
            val token = PreferencesManager.getToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "❌ Токен не найден")
                return@withContext emptyList()
            }

            // ✅ Убираем проверку кэша - ВСЕГДА загружаем с сервера
            // val cached = getCachedMessages(chatId)
            // if (cached.isNotEmpty()) { return@withContext cached }

            val allMessages = mutableListOf<Message>()
            var page = 1
            var hasMore = true

            Log.d(TAG, "🔄 Загружаем с сервера сообщения для чата $chatId, тип: $chatType")

            while (hasMore) {
                val response = ApiClient.apiService.getChatMessages(
                    token = "Bearer $token",
                    chatId = chatId,
                    page = page,
                    perPage = 100
                )

                if (!response.isSuccessful) {
                    Log.e(TAG, "❌ Ошибка загрузки страницы $page: ${response.code()}")
                    break
                }

                val messagesResponse = response.body()
                if (messagesResponse != null && messagesResponse.success) {
                    val serverMessages = messagesResponse.data?.messages ?: emptyList()
                    Log.d(TAG, "📥 Загружена страница $page: ${serverMessages.size} сообщений")

                    if (serverMessages.isEmpty()) {
                        hasMore = false
                        break
                    }

                    allMessages.addAll(serverMessages)

                    if (serverMessages.size < 100) {
                        hasMore = false
                    } else {
                        page++
                    }
                } else {
                    Log.e(TAG, "❌ Ошибка: success=false или тело пустое")
                    hasMore = false
                }
            }

            Log.d(TAG, "✅ Всего загружено сообщений: ${allMessages.size}")

            val privateKey = keyStoreManager.getPrivateKey()
            val decryptedMessages = mutableListOf<Message>()

            for (serverMessage in allMessages) {
                val existingMessage = messageDao.getMessageById(serverMessage.id)

                if (existingMessage != null && existingMessage.type != "encrypted_text"
                    && existingMessage.type != "encrypted_image" && existingMessage.type != "encrypted_audio") {
                    decryptedMessages.add(existingMessage.toMessage())
                    continue
                }

                val decryptedMessage = when (serverMessage.type) {
                    "encrypted_text" -> {
                        if (privateKey != null && !serverMessage.fileToken.isNullOrEmpty()) {
                            decryptTextMessage(serverMessage, privateKey)
                        } else {
                            serverMessage.copy(type = "text", content = "🔒 Сообщение зашифровано")
                        }
                    }
                    "encrypted_image" -> {
                        if (privateKey != null && !serverMessage.fileToken.isNullOrEmpty()) {
                            decryptAndSaveImageMessage(serverMessage, privateKey)
                        } else {
                            serverMessage
                        }
                    }
                    "encrypted_audio" -> {
                        if (privateKey != null && !serverMessage.fileToken.isNullOrEmpty()) {
                            decryptAndSaveAudioMessage(serverMessage, privateKey)
                        } else {
                            serverMessage
                        }
                    }
                    else -> serverMessage
                }

                if (decryptedMessage.type != "encrypted_text" &&
                    decryptedMessage.type != "encrypted_image" &&
                    decryptedMessage.type != "encrypted_audio") {
                    saveMessageToLocal(decryptedMessage)
                }
                decryptedMessages.add(decryptedMessage)
            }

            // ✅ Обновляем кэш свежими данными
            messageCache[chatId] = decryptedMessages
            cacheTime[chatId] = System.currentTimeMillis()

            return@withContext decryptedMessages.sortedBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка: ${e.message}", e)
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun updateMessageWithSenderInfo(messageId: Int, senderName: String, senderAvatar: String?) {
        try {
            val existing = messageDao.getMessageById(messageId)
            if (existing != null) {
                val updated = existing.copy(
                    senderName = senderName,
                    senderAvatar = senderAvatar
                )
                messageDao.insertMessage(updated)
                Log.d(TAG, "✅ Обновлено сообщение $messageId: имя=$senderName, аватар=$senderAvatar")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления сообщения: ${e.message}", e)
        }
    }

    suspend fun updateMessagesWithParticipants(participantsMap: Map<Int, Pair<String, String?>>) {
        try {
            participantsMap.forEach { (senderId, info) ->
                val (name, avatar) = info
                messageDao.updateMessagesBySender(senderId, name, avatar)
                Log.d(TAG, "✅ Обновлены сообщения для отправителя $senderId: имя=$name")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка массового обновления: ${e.message}", e)
        }
    }

    suspend fun deleteMessagesForChat(chatId: Int) {
        try {
            messageDao.deleteMessagesForChat(chatId)
            messageCache.remove(chatId)
            cacheTime.remove(chatId)
            Log.d(TAG, "🗑️ Удалены все сообщения чата $chatId")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления сообщений чата: ${e.message}", e)
        }
    }

    // ===== МЕТОДЫ РАСШИФРОВКИ =====

    private suspend fun decryptTextMessage(
        serverMessage: Message,
        privateKey: PrivateKey
    ): Message = withContext(Dispatchers.IO) {
        try {
            val fileToken = serverMessage.fileToken ?: return@withContext serverMessage.copy(
                type = "text", content = "🔒 Ошибка: токен не найден"
            )

            val token = PreferencesManager.getToken() ?: return@withContext serverMessage.copy(
                type = "text", content = "🔒 Ошибка: токен авторизации не найден"
            )

            val response = ApiClient.apiService.getEncryptedFile(
                token = "Bearer $token",
                fileToken = fileToken
            )

            if (response.isSuccessful) {
                val fileData = response.body()
                if (fileData != null && fileData.success) {
                    val aesKey = EncryptionHelper.decryptAESKeyWithRSA(
                        fileData.encrypted_key,
                        privateKey
                    )

                    if (aesKey != null) {
                        val decryptedText = EncryptionHelper.decryptText(
                            serverMessage.content,
                            aesKey
                        )

                        if (decryptedText != null) {
                            Log.d(TAG, "✅ Текст расшифрован для сообщения ${serverMessage.id}")
                            return@withContext serverMessage.copy(
                                type = "text",
                                content = decryptedText,
                                fileToken = null
                            )
                        }
                    }
                }
            }

            return@withContext serverMessage.copy(
                type = "text",
                content = "🔒 Не удалось расшифровать"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка расшифровки текста: ${e.message}", e)
            return@withContext serverMessage.copy(
                type = "text",
                content = "🔒 Ошибка: ${e.message}"
            )
        }
    }

    private suspend fun decryptAndSaveImageMessage(
        serverMessage: Message,
        privateKey: PrivateKey
    ): Message = withContext(Dispatchers.IO) {
        try {
            val fileToken = serverMessage.fileToken ?: return@withContext serverMessage
            val token = PreferencesManager.getToken() ?: return@withContext serverMessage

            val response = ApiClient.apiService.getEncryptedFile(
                token = "Bearer $token",
                fileToken = fileToken
            )

            if (response.isSuccessful) {
                val fileData = response.body()
                if (fileData != null && fileData.success && fileData.file.isNotEmpty()) {
                    val aesKey = EncryptionHelper.decryptAESKeyWithRSA(
                        fileData.encrypted_key,
                        privateKey
                    )

                    if (aesKey != null) {
                        val tempEncryptedFile = File(context.cacheDir, "temp_enc_${System.currentTimeMillis()}")
                        val fileBytes = android.util.Base64.decode(fileData.file, android.util.Base64.DEFAULT)
                        tempEncryptedFile.writeBytes(fileBytes)

                        val decryptedFile = EncryptionHelper.decryptFile(tempEncryptedFile, aesKey)
                        tempEncryptedFile.delete()

                        if (decryptedFile != null && decryptedFile.exists()) {
                            val fileName = "img_${serverMessage.id}_${System.currentTimeMillis()}.jpg"
                            val savedFile = File(decryptedFilesDir, fileName)

                            copyFileComplete(decryptedFile, savedFile)
                            decryptedFile.delete()

                            Log.d(TAG, "✅ Изображение сохранено: ${savedFile.absolutePath}")

                            return@withContext serverMessage.copy(
                                type = "image",
                                content = savedFile.absolutePath,
                                localUri = savedFile.absolutePath,
                                fileToken = null
                            )
                        }
                    }
                }
            }
            return@withContext serverMessage
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка расшифровки изображения: ${e.message}", e)
            return@withContext serverMessage
        }
    }

    private suspend fun decryptAndSaveAudioMessage(
        serverMessage: Message,
        privateKey: PrivateKey
    ): Message = withContext(Dispatchers.IO) {
        try {
            val fileToken = serverMessage.fileToken ?: return@withContext serverMessage
            val token = PreferencesManager.getToken() ?: return@withContext serverMessage

            val response = ApiClient.apiService.getEncryptedFile(
                token = "Bearer $token",
                fileToken = fileToken
            )

            if (response.isSuccessful) {
                val fileData = response.body()
                if (fileData != null && fileData.success && fileData.file.isNotEmpty()) {
                    val aesKey = EncryptionHelper.decryptAESKeyWithRSA(
                        fileData.encrypted_key,
                        privateKey
                    )

                    if (aesKey != null) {
                        val tempEncryptedFile = File(context.cacheDir, "temp_enc_${System.currentTimeMillis()}")
                        val fileBytes = android.util.Base64.decode(fileData.file, android.util.Base64.DEFAULT)
                        tempEncryptedFile.writeBytes(fileBytes)

                        val decryptedFile = EncryptionHelper.decryptFile(tempEncryptedFile, aesKey)
                        tempEncryptedFile.delete()

                        if (decryptedFile != null && decryptedFile.exists()) {
                            val fileName = "audio_${serverMessage.id}_${System.currentTimeMillis()}.m4a"
                            val savedFile = File(decryptedFilesDir, fileName)

                            copyFileComplete(decryptedFile, savedFile)
                            decryptedFile.delete()

                            Log.d(TAG, "✅ Аудио сохранено: ${savedFile.absolutePath}")

                            return@withContext serverMessage.copy(
                                type = "audio",
                                content = savedFile.absolutePath,
                                localUri = savedFile.absolutePath,
                                mimeType = "audio/mp4",
                                duration = serverMessage.duration,
                                fileToken = null
                            )
                        }
                    }
                }
            }
            return@withContext serverMessage
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка расшифровки аудио: ${e.message}", e)
            return@withContext serverMessage
        }
    }

    private fun copyFileComplete(source: File, dest: File): Boolean {
        return try {
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    output.flush()
                    Log.d(TAG, "📦 Скопировано $totalBytes байт")
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка копирования файла: ${e.message}", e)
            false
        }
    }

    // ===== СОХРАНЕНИЕ В БД =====

    suspend fun saveMessageToLocal(message: Message) {
        try {
            val existing = messageDao.getMessageById(message.id)
            if (existing != null) {
                if (existing.senderName != message.senderName ||
                    existing.senderAvatar != message.senderAvatar ||
                    existing.content != message.content ||
                    existing.type != message.type) {
                    val updated = existing.copy(
                        senderName = message.senderName,
                        senderAvatar = message.senderAvatar,
                        content = message.content,
                        type = message.type,
                        localUri = message.localUri,
                        duration = message.duration
                    )
                    messageDao.insertMessage(updated)
                    Log.d(TAG, "🔄 Обновлено сообщение ${message.id}: имя=${message.senderName}")
                }
                return
            }

            val entity = MessageEntity(
                id = message.id,
                chatId = message.chatId,
                senderId = message.senderId,
                senderName = message.senderName,
                senderAvatar = message.senderAvatar,
                type = message.type,
                content = message.content,
                fileName = message.fileName,
                fileSize = message.fileSize,
                mimeType = message.mimeType,
                createdAt = message.createdAt,
                isRead = message.isRead,
                isMe = message.isMe,
                duration = message.duration,
                localUri = message.localUri,
                isDecrypted = true,
                createdAtTimestamp = try {
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    format.parse(message.createdAt)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            )
            messageDao.insertMessage(entity)
            Log.d(TAG, "💾 Сохранено сообщение ${message.id}: тип=${message.type}, имя=${message.senderName}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения: ${e.message}", e)
        }
    }

    suspend fun saveLocalMessage(
        chatId: Int,
        text: String,
        type: String = "text"
    ): Message {
        val tempId = -System.currentTimeMillis().toInt()
        val currentUserId = PreferencesManager.getUserId()
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        val tempMessage = Message(
            id = tempId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = "Вы",
            senderAvatar = null,
            type = type,
            content = text,
            fileName = null,
            fileSize = null,
            mimeType = null,
            createdAt = currentTime,
            isRead = true,
            isMe = true,
            duration = null,
            localUri = null,
            fileToken = null
        )

        saveMessageToLocal(tempMessage)
        return tempMessage
    }

    suspend fun saveLocalImageMessage(
        chatId: Int,
        imageFile: File,
        type: String = "image"
    ): Message {
        if (!imageFile.exists()) {
            throw IOException("Изображение не существует")
        }

        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val savedFile = File(decryptedFilesDir, fileName)

        copyFileComplete(imageFile, savedFile)

        val tempId = -System.currentTimeMillis().toInt()
        val currentUserId = PreferencesManager.getUserId()
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        val tempMessage = Message(
            id = tempId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = "Вы",
            senderAvatar = null,
            type = type,
            content = savedFile.absolutePath,
            fileName = savedFile.name,
            fileSize = savedFile.length().toInt(),
            mimeType = "image/jpeg",
            createdAt = currentTime,
            isRead = true,
            isMe = true,
            duration = null,
            localUri = savedFile.absolutePath,
            fileToken = null
        )

        saveMessageToLocal(tempMessage)
        return tempMessage
    }

    suspend fun saveLocalAudioMessage(
        chatId: Int,
        audioFile: File,
        duration: Int,
        type: String = "audio"
    ): Message {
        if (!audioFile.exists()) {
            throw IOException("Аудиофайл не существует")
        }

        val fileName = "audio_${System.currentTimeMillis()}.m4a"
        val savedFile = File(decryptedFilesDir, fileName)

        val copySuccess = copyFileComplete(audioFile, savedFile)

        if (!copySuccess) {
            throw IOException("Не удалось скопировать аудиофайл")
        }

        val tempId = -System.currentTimeMillis().toInt()
        val currentUserId = PreferencesManager.getUserId()
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        val tempMessage = Message(
            id = tempId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = "Вы",
            senderAvatar = null,
            type = type,
            content = savedFile.absolutePath,
            fileName = savedFile.name,
            fileSize = savedFile.length().toInt(),
            mimeType = "audio/mp4",
            createdAt = currentTime,
            isRead = true,
            isMe = true,
            duration = duration,
            localUri = savedFile.absolutePath,
            fileToken = null
        )

        saveMessageToLocal(tempMessage)
        return tempMessage
    }

    suspend fun updateMessageId(tempId: Int, realId: Int) {
        try {
            val existingMessage = messageDao.getMessageById(realId)
            if (existingMessage != null) {
                val tempMessage = messageDao.getMessageById(tempId)
                tempMessage?.let { messageDao.deleteMessage(it) }
                return
            }

            val tempMessage = messageDao.getMessageById(tempId)
            tempMessage?.let {
                val updated = it.copy(id = realId)
                messageDao.insertMessage(updated)
                messageDao.deleteMessage(it)
                Log.d(TAG, "🔄 ID сообщения обновлен: $tempId -> $realId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления ID: ${e.message}", e)
        }
    }
}

fun MessageEntity.toMessage(): Message {
    return Message(
        id = id,
        chatId = chatId,
        senderId = senderId,
        senderName = senderName,
        senderAvatar = senderAvatar,
        type = type,
        content = content,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        createdAt = createdAt,
        isRead = isRead,
        isMe = isMe,
        duration = duration,
        localUri = localUri,
        fileToken = null
    )
}
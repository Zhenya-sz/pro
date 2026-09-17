package com.fitnesslemon.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: Int,
    val chatId: Int,
    val senderId: Int,
    val senderName: String,
    val senderAvatar: String? = null,
    val type: String, // text, image, audio, encrypted_text, encrypted_image, encrypted_audio
    val content: String, // Храним в открытом виде!
    val fileName: String? = null,
    val fileSize: Int? = null,
    val mimeType: String? = null,
    val createdAt: String,
    val isRead: Boolean = false,
    val isMe: Boolean = false,
    val duration: Int? = null,
    val localUri: String? = null, // локальный путь к файлу (изображение/аудио)
    val isDecrypted: Boolean = true, // помечаем, что сообщение уже расшифровано
    val createdAtTimestamp: Long = System.currentTimeMillis()
)
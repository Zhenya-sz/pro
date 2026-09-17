package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ============== ЧАТЫ ==============

@Parcelize
data class Chat(
    val id: Int,
    val type: String, // "private" или "group"
    val name: String? = null, // для групповых чатов
    val avatar: String? = null, // аватар группы
    val description: String? = null, // описание группы
    @SerializedName("is_subscription_group")
    val isSubscriptionGroup: Int = 0,
    @SerializedName("subscription_id")
    val subscriptionId: Int? = null,
    @SerializedName("participants_count")
    val participantsCount: Int = 0,
    @SerializedName("participant")
    val participant: ChatParticipant? = null,
    @SerializedName("last_message")
    val lastMessage: LastMessage? = null,
    @SerializedName("unread_count")
    val unreadCount: Int = 0,
    @SerializedName("participant_role")
    val participantRole: String? = null,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
) : Parcelable {
    val isSubscriptionGroupBoolean: Boolean
        get() = isSubscriptionGroup == 1

    val displayName: String
        get() = name ?: "Чат #$id"
}

@Parcelize
data class ChatParticipant(
    val id: Int,
    val name: String,
    val role: String,
    val avatar: String? = null,
    val email: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("photo")
    val photo: String? = null,
    @SerializedName("joined_at")
    val joinedAt: String? = null,
    @SerializedName("last_read_at")
    val lastReadAt: String? = null
) : Parcelable {
    fun getAvatarUrlValue(): String? {
        return avatar ?: avatarUrl ?: photo
    }
}

@Parcelize
data class LastMessage(
    val text: String,
    val time: String,
    @SerializedName("sender_id")
    val senderId: Int,
    @SerializedName("sender_name")
    val senderName: String,
    @SerializedName("sender_avatar")
    val senderAvatar: String? = null
) : Parcelable

// ============== СООБЩЕНИЯ ==============

@Parcelize
data class Message(
    val id: Int,
    @SerializedName("chat_id")
    val chatId: Int,
    @SerializedName("sender_id")
    val senderId: Int,
    @SerializedName("sender_name")
    val senderName: String,
    @SerializedName("sender_role")
    val senderRole: String? = null,
    @SerializedName("sender_avatar")
    val senderAvatar: String? = null,
    val type: String,
    val content: String,
    @SerializedName("file_name")
    val fileName: String? = null,
    @SerializedName("file_size")
    val fileSize: Int? = null,
    @SerializedName("mime_type")
    val mimeType: String? = null,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("is_read")
    val isRead: Boolean = false,
    @SerializedName("is_me")
    val isMe: Boolean = false,
    @SerializedName("is_pinned")
    val isPinned: Boolean = false,
    val duration: Int? = null,
    @SerializedName("local_uri")
    val localUri: String? = null,
    @SerializedName("token")
    val fileToken: String? = null
) : Parcelable

// ============== ЗАПРОСЫ/ОТВЕТЫ ==============

data class SendMessageRequest(
    @SerializedName("chat_id")
    val chatId: Int,
    val type: String = "text",
    val content: String
)

data class SendMessageResponse(
    val success: Boolean,
    @SerializedName("message_id")
    val messageId: Int,
    @SerializedName("created_at")
    val createdAt: String
)

data class MarkChatMessagesReadRequest(
    @SerializedName("last_read_message_id")
    val lastReadMessageId: Int
)

data class UnreadCountResponse(
    @SerializedName("total_unread")
    val totalUnread: Int,
    @SerializedName("chats")
    val chats: List<ChatUnread>
)

data class ChatUnread(
    @SerializedName("chat_id")
    val chatId: Int,
    @SerializedName("unread_count")
    val unreadCount: Int
)

/**
 * ✅ ИСПРАВЛЕНО: name сделан nullable с дефолтным значением null
 */
data class CreateChatRequest(
    val type: String,
    val name: String? = null,  // ⬅️ Сделано nullable
    val participants: List<Int>
)

// ============== КЛАСС ДЛЯ БРОНИРОВАНИЯ ==============
data class BookClassRequest(
    @SerializedName("class_id")
    val classId: Int
)
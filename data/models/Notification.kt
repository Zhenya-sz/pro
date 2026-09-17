package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ==========================================
// ОСНОВНЫЕ МОДЕЛИ УВЕДОМЛЕНИЙ
// ==========================================

@Parcelize
data class Notification(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("type")
    val type: String = "system",

    @SerializedName("title")
    val title: String = "Уведомление",

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: NotificationData? = null,

    @SerializedName("group_id")
    val groupId: String? = null,

    @SerializedName("group_count")
    val groupCount: Int = 1,

    @SerializedName("is_read")
    val isRead: Boolean = false,

    @SerializedName("is_group")
    val isGroup: Boolean = false,

    @SerializedName("expires_at")
    val expiresAt: String? = null,

    @SerializedName("created_at")
    val createdAt: String = "",

    @SerializedName("formatted_date")
    val formattedDate: String = "",

    @SerializedName("action_buttons")
    val actionButtons: List<NotificationAction>? = null
) : Parcelable

@Parcelize
data class NotificationAction(
    @SerializedName("text")
    val text: String = "",

    @SerializedName("action")
    val action: String = "",

    @SerializedName("color")
    val color: String? = null
) : Parcelable

@Parcelize
data class NotificationData(
    @SerializedName("chat_id")
    val chatId: Int? = null,

    @SerializedName("sender_id")
    val senderId: Int? = null,

    @SerializedName("sender_name")
    val senderName: String? = null,

    @SerializedName("sender_avatar")
    val senderAvatar: String? = null,

    @SerializedName("message_preview")
    val messagePreview: String? = null,

    @SerializedName("unread_count")
    val unreadCount: Int? = null,

    @SerializedName("class_id")
    val classId: Int? = null,

    @SerializedName("class_date")
    val classDate: String? = null,

    @SerializedName("class_title")
    val classTitle: String? = null,

    @SerializedName("trainer_name")
    val trainerName: String? = null,

    @SerializedName("invitation_id")
    val invitationId: Int? = null,

    @SerializedName("inviter_id")
    val inviterId: Int? = null,

    @SerializedName("inviter_name")
    val inviterName: String? = null,

    @SerializedName("news_id")
    val newsId: Int? = null,

    @SerializedName("url")
    val url: String? = null
) : Parcelable

// ==========================================
// ГРУППОВОЙ ОТВЕТ ОТ СЕРВЕРА
// ==========================================
data class GroupedNotificationsResponse(
    @SerializedName("chats")
    val chats: List<NotificationGroup> = emptyList(),

    @SerializedName("bookings")
    val bookings: List<NotificationGroup> = emptyList(),

    @SerializedName("invitations")
    val invitations: List<Notification> = emptyList(),

    @SerializedName("others")
    val others: List<Notification> = emptyList(),

    @SerializedName("total_unread")
    val totalUnread: Int = 0
)

// ==========================================
// ГРУППА УВЕДОМЛЕНИЙ
// ==========================================
@Parcelize
data class NotificationGroup(
    @SerializedName("group_id")
    val groupId: String = "",

    @SerializedName("type")
    val type: String = "system",

    @SerializedName("title")
    val title: String = "Группа уведомлений",

    @SerializedName("message")
    val message: String = "",

    @SerializedName("count")
    val count: Int = 1,

    @SerializedName("last_notification")
    val lastNotification: Notification? = null,

    @SerializedName("is_read")
    val isRead: Boolean = false,

    @SerializedName("data")
    val data: NotificationData? = null
) : Parcelable

// ==========================================
// КЛАССЫ ДЛЯ ЗАПРОСОВ
// ==========================================
data class MarkNotificationReadRequest(
    @SerializedName("notification_id")
    val notificationId: Int
)

data class MarkGroupAsReadRequest(
    @SerializedName("group_id")
    val groupId: String,

    @SerializedName("type")
    val type: String
)

data class NotificationsResponse(
    @SerializedName("notifications")
    val notifications: List<Notification> = emptyList(),

    @SerializedName("total")
    val total: Int = 0,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("per_page")
    val perPage: Int = 20,

    @SerializedName("total_pages")
    val totalPages: Int = 0
)

data class UnreadNotificationsResponse(
    @SerializedName("unread_count")
    val unreadCount: Int = 0
)
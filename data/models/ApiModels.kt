package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

// BASE RESPONSE
data class BaseResponse(
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("data")
    val data: Map<String, Any>? = null
)

// AUTH
data class AuthResponse(
    @SerializedName("token")
    val token: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("expires_in")
    val expiresIn: Long? = 86400L,
    @SerializedName("user")
    val user: UserResponse? = null
)

data class UserResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("phone")
    val phone: String? = null,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("role")
    val role: String? = null
)

data class UserProfileResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("phone")
    val phone: String? = null,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("birth_date")
    val birthDate: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int = 0,
    @SerializedName("registered")
    val registered: String? = null
)

// NEWS
data class NewsResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("content")
    val content: String? = null,
    @SerializedName("excerpt")
    val excerpt: String? = null,
    @SerializedName("thumbnail")
    val thumbnail: String? = null,
    @SerializedName("author_name")
    val authorName: String? = null,
    @SerializedName("date")
    val date: String? = null,
    @SerializedName("likes_count")
    val likesCount: Int = 0,
    @SerializedName("is_liked")
    val isLiked: Boolean = false,
    @SerializedName("is_saved")
    val isSaved: Boolean = false
)

// WORKOUTS
data class WorkoutResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("date")
    val date: String,
    @SerializedName("duration")
    val duration: Int,
    @SerializedName("trainer_name")
    val trainerName: String? = null,
    @SerializedName("current_participants")
    val currentParticipants: Int = 0,
    @SerializedName("max_participants")
    val maxParticipants: Int = 10,
    @SerializedName("available_spots")
    val availableSpots: Int = 0,
    @SerializedName("thumbnail")
    val thumbnail: String? = null,
    @SerializedName("room")
    val room: String? = null,
    @SerializedName("age_category")
    val ageCategory: String? = null
)

// CHATS
data class ChatResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("type")
    val type: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("unread_count")
    val unreadCount: Int = 0,
    @SerializedName("last_message")
    val lastMessage: MessageResponse? = null,
    @SerializedName("participants_count")
    val participantsCount: Int = 0,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

data class MessageResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("chat_id")
    val chatId: Int,
    @SerializedName("sender_id")
    val senderId: Int,
    @SerializedName("sender_name")
    val senderName: String? = null,
    @SerializedName("sender_avatar")
    val senderAvatar: String? = null,
    @SerializedName("type")
    val type: String,
    @SerializedName("content")
    val content: String? = null,
    @SerializedName("file_name")
    val fileName: String? = null,
    @SerializedName("file_size")
    val fileSize: Long = 0,
    @SerializedName("mime_type")
    val mimeType: String? = null,
    @SerializedName("duration")
    val duration: Int? = null,
    @SerializedName("is_read")
    val isRead: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("is_me")
    val isMe: Boolean = false
)

data class MessagePageResponse(
    @SerializedName("messages")
    val messages: List<MessageResponse> = emptyList(),
    @SerializedName("page")
    val page: Int = 1,
    @SerializedName("per_page")
    val perPage: Int = 100,
    @SerializedName("total")
    val total: Int = 0
)

// NOTIFICATIONS
data class NotificationResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("is_read")
    val isRead: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String? = null
)

data class UnreadCountResponse(
    @SerializedName("unread_count")
    val unread_count: Int = 0
)

// TRAINERS
data class TrainerResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("specialization")
    val specialization: String? = null,
    @SerializedName("bio")
    val bio: String? = null
)

// BOOKINGS
data class BookingResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("workout_id")
    val workoutId: Int,
    @SerializedName("workout_title")
    val workoutTitle: String? = null,
    @SerializedName("date")
    val date: String? = null,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)

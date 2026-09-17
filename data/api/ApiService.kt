package com.fitnesslemon.app.data.api

import com.fitnesslemon.app.data.models.*
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ========== АУТЕНТИФИКАЦИЯ ==========
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<LoginResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Header("Authorization") token: String
    ): Response<RefreshTokenResponse>

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    // ========== ПУБЛИЧНЫЕ ЭНДПОИНТЫ ==========

    @GET("trainers")
    suspend fun getTrainers(): Response<TrainersResponse>

    @GET("workouts")
    suspend fun getWorkouts(): Response<WorkoutsResponse>

    @GET("news")
    suspend fun getNews(
        @Query("per_page") perPage: Int = 200,
        @Query("page") page: Int = 1,
        @Query("category") category: String? = null,
        @Query("importance") importance: String? = null,
        @Query("search") search: String? = null
    ): Response<NewsResponse>

    @GET("news/{id}")
    suspend fun getNewsById(
        @Path("id") id: Int
    ): Response<NewsDetailResponse>

    @GET("schedule/week")
    suspend fun getWeeklySchedule(
        @Query("date") date: String? = null,
        @Query("week_offset") weekOffset: Int = 0
    ): Response<ScheduleWeekResponse>

    @GET("schedule/day")
    suspend fun getDaySchedule(
        @Query("date") date: String
    ): Response<List<ScheduleItem>>

    // ========== НОВЫЕ ЭНДПОИНТЫ ДЛЯ ЛАЙКОВ И ЗАКЛАДОК ==========

    @POST("news/{id}/like")
    suspend fun likeNews(
        @Header("Authorization") token: String,
        @Path("id") newsId: Int
    ): Response<ApiResponse>

    @DELETE("news/{id}/like")
    suspend fun unlikeNews(
        @Header("Authorization") token: String,
        @Path("id") newsId: Int
    ): Response<ApiResponse>

    @POST("news/{id}/save")
    suspend fun saveNews(
        @Header("Authorization") token: String,
        @Path("id") newsId: Int
    ): Response<ApiResponse>

    @DELETE("news/{id}/save")
    suspend fun unsaveNews(
        @Header("Authorization") token: String,
        @Path("id") newsId: Int
    ): Response<ApiResponse>

    @GET("news/saved")
    suspend fun getSavedNews(
        @Header("Authorization") token: String
    ): Response<NewsResponse>

    @GET("news/liked")
    suspend fun getLikedNews(
        @Header("Authorization") token: String
    ): Response<NewsResponse>

    // ========== АВТОРИЗОВАННЫЕ ПОЛЬЗОВАТЕЛИ ==========
    @GET("users/profile")
    suspend fun getUserProfile(
        @Header("Authorization") token: String
    ): Response<UserResponse>

    @GET("my-bookings")
    suspend fun getMyBookings(
        @Header("Authorization") token: String
    ): Response<MyBookingsResponse>

    @GET("remaining-workouts")
    suspend fun getRemainingWorkouts(
        @Header("Authorization") token: String
    ): Response<RemainingWorkoutsResponse>

    @GET("dashboard")
    suspend fun getDashboard(
        @Header("Authorization") token: String
    ): Response<DashboardResponse>

    @GET("user-history")
    suspend fun getUserHistory(
        @Header("Authorization") token: String,
        @Query("period") period: String = "30days"
    ): Response<HistoryResponse>

    // ========== ЗАПИСЬ НА ТРЕНИРОВКИ ==========
    @POST("book-class")
    suspend fun bookClassJson(
        @Header("Authorization") token: String,
        @Body request: BookClassRequest
    ): Response<BookClassResponse>

    @POST("cancel-booking")
    suspend fun cancelBooking(
        @Header("Authorization") token: String,
        @Body request: BookClassRequest
    ): Response<ApiResponse>

    // ========== ПРОФИЛЬ ==========
    @POST("users/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse>

    @Multipart
    @POST("users/upload-avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") token: String,
        @Part avatar: MultipartBody.Part
    ): Response<UpdateAvatarResponse>

    @POST("users/delete-avatar")
    suspend fun deleteAvatar(
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    // ========== СТОРИС (STORIES) ==========
    @GET("stories/active")
    suspend fun getActiveStories(
        @Header("Authorization") token: String
    ): Response<StoriesResponse>

    @GET("stories/trainer/{trainerId}")
    suspend fun getTrainerStories(
        @Header("Authorization") token: String,
        @Path("trainerId") trainerId: Int
    ): Response<List<Story>>

    @Multipart
    @POST("stories/create")
    suspend fun createStory(
        @Header("Authorization") token: String,
        @Part("text") text: RequestBody? = null,
        @Part("title") title: RequestBody? = null,
        @Part("duration") duration: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<CreateStoryResponse>

    @POST("stories/{storyId}/view")
    suspend fun markStoryViewed(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int
    ): Response<ApiResponse>

    @DELETE("stories/{storyId}")
    suspend fun deleteStory(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int
    ): Response<ApiResponse>

    @GET("stories/{storyId}/views")
    suspend fun getStoryViews(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int
    ): Response<StoryViewsResponse>

    // ========== ОТВЕТЫ И ЛАЙКИ НА СТОРИС ==========
    @POST("stories/{storyId}/reply")
    suspend fun sendStoryReply(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int,
        @Body request: StoryReplyRequest
    ): Response<ApiResponse>

    @POST("stories/{storyId}/like")
    suspend fun likeStory(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int
    ): Response<ApiResponse>

    @DELETE("stories/{storyId}/like")
    suspend fun unlikeStory(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int
    ): Response<ApiResponse>

    @GET("stories/{storyId}/replies")
    suspend fun getStoryReplies(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int
    ): Response<List<StoryReply>>

    // ========== ГРУППОВЫЕ ЧАТЫ ДЛЯ СТОРИС ==========
    @GET("stories/groups")
    suspend fun getStoryGroups(
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    @GET("stories/{storyId}/messages")
    suspend fun getStoryMessages(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int
    ): Response<ApiResponse>

    @POST("stories/{storyId}/message")
    suspend fun sendStoryMessage(
        @Header("Authorization") token: String,
        @Path("storyId") storyId: Int,
        @Body request: SendStoryMessageRequest
    ): Response<ApiResponse>

    // ========== ЧАТЫ ==========
    @GET("chats")
    suspend fun getChats(
        @Header("Authorization") token: String
    ): Response<ChatsResponse>

    @GET("chats/{chatId}")
    suspend fun getChatDetails(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int
    ): Response<Chat>

    @GET("chats/{chatId}/participants")
    suspend fun getChatParticipants(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int
    ): Response<List<ChatParticipant>>

    @GET("chats/{chatId}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): Response<ChatMessagesResponse>

    @POST("chats/send")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: SendMessageRequest
    ): Response<SendMessageResponse>

    @Multipart
    @POST("chats/send-image")
    suspend fun sendImageMessage(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("message_id") messageId: RequestBody? = null,
        @Part image: MultipartBody.Part
    ): Response<SendMessageResponse>

    @Multipart
    @POST("chats/send-audio")
    suspend fun sendAudioMessage(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part audio: MultipartBody.Part,
        @Part("duration") duration: RequestBody
    ): Response<SendMessageResponse>

    // ✅ ========== ОТПРАВКА ВИДЕО ==========

    @Multipart
    @POST("chats/send-video")
    suspend fun sendVideoMessage(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("message_id") messageId: RequestBody? = null,
        @Part video: MultipartBody.Part,
        @Part("duration") duration: RequestBody
    ): Response<SendMessageResponse>

    @Multipart
    @POST("chats/send-encrypted-video")
    suspend fun sendEncryptedVideo(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("encrypted_key") encryptedKey: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part video: MultipartBody.Part
    ): Response<SendMessageResponse>

    @Multipart
    @POST("chats/send-group-encrypted-video")
    suspend fun sendGroupEncryptedVideo(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("encrypted_keys") encryptedKeys: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part video: MultipartBody.Part
    ): Response<SendMessageResponse>

    @POST("chats/create")
    suspend fun createChat(
        @Header("Authorization") token: String,
        @Body request: CreateChatRequest
    ): Response<CreateChatResponse>

    @POST("chats/{chatId}/read")
    suspend fun markMessagesAsRead(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Body request: MarkChatMessagesReadRequest
    ): Response<ApiResponse>

    @DELETE("chats/{chatId}/messages/{messageId}")
    suspend fun deleteMessageForMe(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Path("messageId") messageId: Int
    ): Response<ApiResponse>

    @DELETE("chats/{chatId}/messages/{messageId}/everyone")
    suspend fun deleteMessageForEveryone(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Path("messageId") messageId: Int
    ): Response<ApiResponse>

    // ========== ЗАШИФРОВАННЫЕ СООБЩЕНИЯ ==========
    @POST("chats/send-encrypted-text")
    suspend fun sendEncryptedTextMessage(
        @Header("Authorization") token: String,
        @Body request: EncryptedTextRequest
    ): Response<SendMessageResponse>

    @POST("chats/send-group-encrypted-text")
    suspend fun sendGroupEncryptedTextMessage(
        @Header("Authorization") token: String,
        @Body request: GroupEncryptedTextRequest
    ): Response<SendMessageResponse>

    @Multipart
    @POST("chats/send-encrypted-image")
    suspend fun sendEncryptedImage(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("encrypted_key") encryptedKey: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<SendMessageResponse>

    @Multipart
    @POST("chats/send-group-encrypted-image")
    suspend fun sendGroupEncryptedImage(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("encrypted_keys") encryptedKeys: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<SendMessageResponse>

    @Multipart
    @POST("chats/send-encrypted-audio")
    suspend fun sendEncryptedAudio(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("encrypted_key") encryptedKey: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part audio: MultipartBody.Part
    ): Response<SendMessageResponse>

    @Multipart
    @POST("chats/send-group-encrypted-audio")
    suspend fun sendGroupEncryptedAudio(
        @Header("Authorization") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("encrypted_keys") encryptedKeys: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part audio: MultipartBody.Part
    ): Response<SendMessageResponse>

    @GET("chats/get-file/{token}")
    suspend fun getEncryptedFile(
        @Header("Authorization") token: String,
        @Path("token") fileToken: String
    ): Response<EncryptedFileResponse>

    // ========== УВЕДОМЛЕНИЯ ==========
    @GET("notifications/grouped")
    suspend fun getGroupedNotifications(
        @Header("Authorization") token: String
    ): Response<GroupedNotificationsResponse>

    @POST("notifications/mark-read")
    suspend fun markNotificationRead(
        @Header("Authorization") token: String,
        @Body request: MarkNotificationReadRequest
    ): Response<ApiResponse>

    @POST("notifications/mark-group-read")
    suspend fun markGroupAsRead(
        @Header("Authorization") token: String,
        @Body request: MarkGroupAsReadRequest
    ): Response<ApiResponse>

    @POST("notifications/mark-all-read")
    suspend fun markAllNotificationsRead(
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    @DELETE("notifications/{id}")
    suspend fun deleteNotification(
        @Header("Authorization") token: String,
        @Path("id") notificationId: Int
    ): Response<ApiResponse>

    @GET("notifications/unread")
    suspend fun getUnreadNotificationsCount(
        @Header("Authorization") token: String
    ): Response<UnreadNotificationsResponse>

    // ========== ПРИГЛАШЕНИЯ ==========
    @GET("invitations/pending")
    suspend fun getPendingInvitations(
        @Header("Authorization") token: String
    ): Response<InvitationsListResponse>

    @POST("invitations/create")
    suspend fun createInvitation(
        @Header("Authorization") token: String,
        @Body request: CreateInvitationRequest
    ): Response<InvitationResponse>

    @POST("invitations/{id}/accept")
    suspend fun acceptInvitation(
        @Header("Authorization") token: String,
        @Path("id") invitationId: Int
    ): Response<InvitationResponse>

    @POST("invitations/{id}/decline")
    suspend fun declineInvitation(
        @Header("Authorization") token: String,
        @Path("id") invitationId: Int
    ): Response<InvitationResponse>

    // ========== АБОНЕМЕНТЫ ==========
    @GET("subscriptions")
    suspend fun getSubscriptions(
        @Header("Authorization") token: String? = null
    ): Response<List<AdminSubscription>>

    // ========== ПОИСК ПОЛЬЗОВАТЕЛЯ ==========
    @GET("users/find-by-phone")
    suspend fun findUserByPhone(
        @Header("Authorization") token: String,
        @Query("phone") phone: String
    ): Response<FindUserResponse>

    // ========== ПУБЛИЧНЫЕ КЛЮЧИ ==========
    @GET("users/{id}/public-key")
    suspend fun getUserPublicKey(
        @Header("Authorization") token: String,
        @Path("id") userId: Int
    ): Response<PublicKeyResponse>

    @POST("users/{id}/create-public-key")
    suspend fun createPublicKey(
        @Header("Authorization") token: String,
        @Path("id") userId: Int
    ): Response<PublicKeyResponse>

    @POST("users/upload-public-key")
    suspend fun uploadPublicKey(
        @Header("Authorization") token: String,
        @Body request: UploadPublicKeyRequest
    ): Response<ApiResponse>

    @GET("users/check-public-key")
    suspend fun checkPublicKey(
        @Header("Authorization") token: String
    ): Response<PublicKeyResponse>

    // ========== АДМИН ==========
    @GET("admin/stats")
    suspend fun getAdminStats(
        @Header("Authorization") token: String
    ): Response<AdminStatsResponse>

    @GET("admin/users")
    suspend fun getAdminUsers(
        @Header("Authorization") token: String,
        @Query("search") search: String? = null,
        @Query("role") role: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<AdminUsersResponse>

    @GET("admin/workouts")
    suspend fun getAdminWorkouts(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("trainer_id") trainerId: Int? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null
    ): Response<List<AdminWorkout>>

    @GET("admin/subscriptions")
    suspend fun getAdminSubscriptions(
        @Header("Authorization") token: String,
        @Query("active_only") activeOnly: Boolean = false
    ): Response<List<AdminSubscription>>

    @POST("admin/subscriptions")
    suspend fun createSubscription(
        @Header("Authorization") token: String,
        @Body request: CreateSubscriptionRequest
    ): Response<ApiResponse>

    @POST("admin/subscriptions/sell")
    suspend fun sellSubscription(
        @Header("Authorization") token: String,
        @Body request: SellSubscriptionRequest
    ): Response<SellSubscriptionResponse>
}

// ==========================================
// DATA КЛАССЫ ДЛЯ СТОРИС
// ==========================================

data class StoryReplyRequest(
    val text: String
)

data class StoryGroupChat(
    val id: Int,
    @SerializedName("story_id")
    val storyId: Int,
    @SerializedName("chat_id")
    val chatId: Int,
    @SerializedName("chat_name")
    val chatName: String,
    @SerializedName("chat_avatar")
    val chatAvatar: String? = null,
    @SerializedName("participants_count")
    val participantsCount: Int,
    @SerializedName("last_message")
    val lastMessage: String? = null,
    @SerializedName("last_message_time")
    val lastMessageTime: String? = null,
    @SerializedName("unread_count")
    val unreadCount: Int = 0
)

data class SendStoryMessageRequest(
    val text: String
)

// ==========================================
// ОСТАЛЬНЫЕ DATA КЛАССЫ
// ==========================================

// Для /workouts
data class WorkoutsResponse(
    val success: Boolean,
    val message: String,
    val data: WorkoutsData
)

data class WorkoutsData(
    val workouts: List<Workout>,
    val total: Int,
    val page: Int,
    @SerializedName("per_page")
    val perPage: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)

// Для /news
data class NewsResponse(
    val success: Boolean,
    val message: String,
    val data: NewsData
)

data class NewsData(
    val news: List<News>,
    val total: Int,
    val page: Int,
    @SerializedName("per_page")
    val perPage: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)

// Для получения одной новости
data class NewsDetailResponse(
    val success: Boolean,
    val message: String,
    val data: News
)

// Для /my-bookings
data class MyBookingsResponse(
    val success: Boolean,
    val message: String,
    val data: List<Booking>
)

// Для /remaining-workouts
data class RemainingWorkoutsResponse(
    val success: Boolean,
    val message: String,
    val data: RemainingWorkoutsData
)

data class RemainingWorkoutsData(
    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int
)

// Для /dashboard
data class DashboardResponse(
    val success: Boolean,
    val message: String,
    val data: DashboardData
)

// Для /user-history
data class HistoryResponse(
    val success: Boolean,
    val message: String,
    val data: List<Booking>
)

// Для /dashboard
data class DashboardData(
    val user: User,
    val stats: Stats,
    @SerializedName("upcoming_bookings")
    val upcomingBookings: List<Booking>
)

data class Stats(
    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int,
    @SerializedName("upcoming_count")
    val upcomingCount: Int,
    @SerializedName("unread_notifications")
    val unreadNotifications: Int
)

// Для /users/find-by-phone
data class UserIdResponse(
    val userId: Int
)

// Для общих API ответов
data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val id: Int? = null,
    @SerializedName("chat_id")
    val chatId: Int? = null
)

// Для публичных ключей
data class PublicKeyResponse(
    val success: Boolean,
    @SerializedName("public_key")
    val public_key: String? = null,
    val message: String? = null,
    val pending: Boolean? = null
)

data class UploadPublicKeyRequest(
    @SerializedName("public_key")
    val public_key: String
)

// ==========================================
// BOOK CLASS RESPONSE
// ==========================================

data class BookClassResponse(
    val success: Boolean,
    val message: String? = null,
    val data: BookClassData? = null
)

data class BookClassData(
    @SerializedName("booking_id")
    val bookingId: Int = 0,
    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int? = null,
    @SerializedName("current_participants")
    val currentParticipants: Int? = null,
    @SerializedName("booked_class_ids")
    val bookedClassIds: List<Int>? = null,
    @SerializedName("is_booked")
    val isBooked: Boolean? = null,
    @SerializedName("class_id")
    val classId: Int? = null
)

// Для запросов
data class BookClassRequest(
    @SerializedName("class_id")
    val classId: Int
)

// Для отправки сообщений
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
    val createdAt: String,
    @SerializedName("audio_url")
    val audioUrl: String? = null,
    val message: String? = null
)

data class CreateChatRequest(
    val type: String,
    val name: String? = null,
    val participants: List<Int>
)

data class MarkChatMessagesReadRequest(
    @SerializedName("last_read_message_id")
    val lastReadMessageId: Int
)

// Для зашифрованных сообщений
data class EncryptedTextRequest(
    @SerializedName("chat_id")
    val chatId: Int,
    val content: String,
    @SerializedName("encrypted_key")
    val encryptedKey: String
)

data class GroupEncryptedTextRequest(
    @SerializedName("chat_id")
    val chatId: Int,
    val content: String,
    @SerializedName("encrypted_keys")
    val encryptedKeys: Map<Int, String>
)

data class EncryptedFileResponse(
    val success: Boolean,
    val file: String,
    @SerializedName("encrypted_key")
    val encrypted_key: String,
    @SerializedName("mime_type")
    val mime_type: String,
    @SerializedName("file_name")
    val file_name: String,
    @SerializedName("message_id")
    val message_id: Int
)

// ==========================================
// ПРИГЛАШЕНИЯ
// ==========================================

data class InvitationsListResponse(
    val invitations: List<Invitation>,
    val total: Int
)

data class InvitationResponse(
    val success: Boolean,
    val message: String,
    val invitation: Invitation? = null,
    @SerializedName("chat_id")
    val chatId: Int? = null
)

data class CreateInvitationRequest(
    @SerializedName("invitee_phone")
    val inviteePhone: String
)

// ==========================================
// TRAINERS RESPONSE
// ==========================================

data class TrainersResponse(
    val success: Boolean,
    val message: String,
    val data: List<Trainer>
)

// ==========================================
// CHATS RESPONSE
// ==========================================

data class ChatsResponse(
    val success: Boolean,
    val message: String,
    val data: List<Chat>
)

data class UserResponse(
    val success: Boolean,
    val message: String,
    val data: User
)

// ==========================================
// АВАТАР
// ==========================================

data class AvatarResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("data")
    val data: AvatarData? = null,
    @SerializedName("message")
    val message: String? = null
)

data class AvatarData(
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
)

// ==========================================
// УВЕДОМЛЕНИЯ
// ==========================================

data class UnreadNotificationsResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("unread_count")
    val unreadCount: Int
)

// ==========================================
// FIND USER BY PHONE RESPONSE
// ==========================================

data class FindUserResponse(
    val success: Boolean,
    val message: String,
    val data: FindUserData? = null
)

data class FindUserData(
    @SerializedName("userId")
    val userId: Int = 0
)

// ==========================================
// CREATE CHAT RESPONSE
// ==========================================

data class CreateChatResponse(
    val success: Boolean,
    val message: String,
    val data: Chat? = null
)

// ==========================================
// SCHEDULE RESPONSE
// ==========================================

data class ScheduleWeekResponse(
    val success: Boolean,
    val message: String,
    val data: ScheduleWeekData
)

data class ScheduleWeekData(
    val week: ScheduleWeekInfo,
    val days: Map<Int, List<ScheduleItem>>
)

// ==========================================
// СТОРИС (STORIES) DATA CLASSES
// ==========================================

data class StoriesResponse(
    val success: Boolean,
    val message: String,
    val data: StoriesData
)

data class StoriesData(
    val stories: List<TrainerStories>,
    val total: Int
)

data class TrainerStories(
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("user_name")
    val userName: String,
    @SerializedName("user_avatar")
    val userAvatar: String? = null,
    @SerializedName("user_role")
    val userRole: String,
    val stories: List<Story>
)

data class CreateStoryResponse(
    val success: Boolean,
    val message: String,
    val data: CreateStoryData
)

data class CreateStoryData(
    val story: Story
)

data class StoryViewsResponse(
    val success: Boolean,
    val message: String,
    val data: StoryViewsData
)

data class StoryViewsData(
    val views: List<StoryView>,
    val total: Int
)

data class StoryView(
    val id: Int,
    val name: String,
    val avatar: String? = null,
    @SerializedName("viewed_at")
    val viewedAt: String
)

data class StoryReply(
    val id: Int,
    @SerializedName("story_id")
    val storyId: Int,
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("user_name")
    val userName: String,
    @SerializedName("user_avatar")
    val userAvatar: String? = null,
    val text: String,
    @SerializedName("is_like")
    val isLike: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String
)

// ==========================================
// АДМИНСКИЕ RESPONSE
// ==========================================

data class AdminStatsResponse(
    val success: Boolean,
    val message: String,
    val data: AdminStatsData
)

data class AdminStatsData(
    @SerializedName("today_visits")
    val todayVisits: Int,
    @SerializedName("today_visits_change")
    val todayVisitsChange: Int,
    @SerializedName("week_visits")
    val weekVisits: Int,
    @SerializedName("week_visits_change")
    val weekVisitsChange: Int,
    @SerializedName("month_visits")
    val monthVisits: Int,
    @SerializedName("month_visits_change")
    val monthVisitsChange: Int,
    @SerializedName("popular_workouts")
    val popularWorkouts: List<PopularWorkout>,
    @SerializedName("revenue_month")
    val revenueMonth: Double,
    @SerializedName("subscriptions_sold")
    val subscriptionsSold: Int,
    @SerializedName("new_users")
    val newUsers: Int,
    @SerializedName("total_users")
    val totalUsers: Int,
    @SerializedName("active_users")
    val activeUsers: Int
)

data class PopularWorkout(
    val id: Int,
    val title: String,
    @SerializedName("bookings_count")
    val bookingsCount: Int,
    val rank: Int
)

data class AdminUsersResponse(
    val success: Boolean,
    val message: String,
    val data: AdminUsersData
)

data class AdminUsersData(
    val users: List<AdminUser>,
    val total: Int,
    val page: Int,
    @SerializedName("per_page")
    val perPage: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)

data class AdminUser(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val avatar: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("registration_date")
    val registrationDate: String,
    @SerializedName("subscription_name")
    val subscriptionName: String? = null,
    @SerializedName("subscription_expiry")
    val subscriptionExpiry: String? = null,
    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int = 0
)

data class AdminWorkout(
    val id: Int,
    val title: String,
    val description: String? = null,
    val date: String,
    val duration: Int,
    @SerializedName("trainer_id")
    val trainerId: Int,
    @SerializedName("trainer_name")
    val trainerName: String? = null,
    @SerializedName("current_participants")
    val currentParticipants: Int,
    @SerializedName("max_participants")
    val maxParticipants: Int,
    val thumbnail: String? = null,
    val status: String,
    val room: String? = null,
    @SerializedName("workout_type")
    val workoutType: String? = null,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int? = null,
    @SerializedName("age_category")
    val ageCategory: String? = null,
    @SerializedName("formatted_date")
    val formattedDate: String,
    @SerializedName("formatted_time")
    val formattedTime: String
)

data class AdminSubscription(
    val id: Int,
    val title: String,
    val description: String? = null,
    @SerializedName("workouts_count")
    val workoutsCount: Int,
    val price: Double,
    @SerializedName("duration_days")
    val durationDays: Int,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("sales_count")
    val salesCount: Int,
    @SerializedName("total_revenue")
    val totalRevenue: Double,
    @SerializedName("chat_id")
    val chatId: Int? = null,
    @SerializedName("workout_type")
    val workoutType: String? = null,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int? = null
)

data class CreateSubscriptionRequest(
    val title: String,
    val description: String? = null,
    @SerializedName("workouts_count")
    val workoutsCount: Int,
    val price: Double,
    @SerializedName("duration_days")
    val durationDays: Int,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int? = null
)

data class SellSubscriptionRequest(
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("subscription_id")
    val subscriptionId: Int
)

data class SellSubscriptionResponse(
    val success: Boolean,
    val message: String,
    val data: SellSubscriptionData
)

data class SellSubscriptionData(
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int,
    @SerializedName("workouts_added")
    val workoutsAdded: Int,
    @SerializedName("booked_count")
    val bookedCount: Int
)

// ==========================================
// REFRESH TOKEN RESPONSE
// ==========================================

data class RefreshTokenResponse(
    val success: Boolean,
    val message: String,
    val data: RefreshTokenData? = null
)

data class RefreshTokenData(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String
)
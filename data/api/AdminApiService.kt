package com.fitnesslemon.app.data.api

import com.fitnesslemon.app.data.models.*
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface AdminApiService {

    // ========== СТАТИСТИКА ==========
    @GET("admin/stats")
    suspend fun getAdminStats(
        @Header("Authorization") token: String
    ): Response<AdminStats>

    // ========== УПРАВЛЕНИЕ ПОЛЬЗОВАТЕЛЯМИ ==========
    @GET("admin/users")
    suspend fun getUsers(
        @Header("Authorization") token: String,
        @Query("search") search: String? = null,
        @Query("role") role: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<AdminResponse<List<AdminUser>>>

    @GET("admin/users/{id}")
    suspend fun getUser(
        @Header("Authorization") token: String,
        @Path("id") userId: Int
    ): Response<AdminUser>

    @POST("admin/users")
    suspend fun createUser(
        @Header("Authorization") token: String,
        @Body request: CreateUserRequest
    ): Response<AdminUser>

    @PUT("admin/users/{id}")
    suspend fun updateUser(
        @Header("Authorization") token: String,
        @Path("id") userId: Int,
        @Body request: UpdateUserRequest
    ): Response<AdminUser>

    @DELETE("admin/users/{id}")
    suspend fun deleteUser(
        @Header("Authorization") token: String,
        @Path("id") userId: Int
    ): Response<AdminApiResponse<Nothing>>

    @POST("admin/users/{id}/reset-password")
    suspend fun resetUserPassword(
        @Header("Authorization") token: String,
        @Path("id") userId: Int
    ): Response<AdminApiResponse<Nothing>>

    // ========== УПРАВЛЕНИЕ ТРЕНИРОВКАМИ ==========
    @GET("admin/workouts")
    suspend fun getAdminWorkouts(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("trainer_id") trainerId: Int? = null
    ): Response<AdminResponse<List<AdminWorkout>>>

    @GET("admin/workouts/{id}")
    suspend fun getAdminWorkout(
        @Header("Authorization") token: String,
        @Path("id") workoutId: Int
    ): Response<AdminWorkout>

    // ============================================================
    // ✅ СОЗДАНИЕ ТРЕНИРОВКИ С ИЗОБРАЖЕНИЕМ (MULTIPART)
    // ============================================================
    @Multipart
    @POST("admin/workouts")
    suspend fun createWorkoutWithImage(
        @Header("Authorization") token: String,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("date") date: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part("trainer_id") trainerId: RequestBody,
        @Part("max_participants") maxParticipants: RequestBody,
        @Part("workout_type_id") workoutTypeId: RequestBody? = null,
        @Part("age_category") ageCategory: RequestBody? = null,
        @Part("room") room: RequestBody? = null,
        @Part image: MultipartBody.Part? = null
    ): Response<AdminWorkout>

    // ============================================================
    // ✅ ОБНОВЛЕНИЕ ТРЕНИРОВКИ С ИЗОБРАЖЕНИЕМ (MULTIPART)
    // ============================================================
    @Multipart
    @PUT("admin/workouts/{id}")
    suspend fun updateWorkoutWithImage(
        @Header("Authorization") token: String,
        @Path("id") workoutId: Int,
        @Part("title") title: RequestBody? = null,
        @Part("description") description: RequestBody? = null,
        @Part("date") date: RequestBody? = null,
        @Part("duration") duration: RequestBody? = null,
        @Part("trainer_id") trainerId: RequestBody? = null,
        @Part("max_participants") maxParticipants: RequestBody? = null,
        @Part("workout_type_id") workoutTypeId: RequestBody? = null,
        @Part("age_category") ageCategory: RequestBody? = null,
        @Part("room") room: RequestBody? = null,
        @Part image: MultipartBody.Part? = null
    ): Response<AdminWorkout>

    // ============================================================
    // ✅ УДАЛЕНИЕ ИЗОБРАЖЕНИЯ ТРЕНИРОВКИ
    // ============================================================
    @DELETE("admin/workouts/{id}/image")
    suspend fun deleteWorkoutImage(
        @Header("Authorization") token: String,
        @Path("id") workoutId: Int
    ): Response<AdminApiResponse<Nothing>>

    @DELETE("admin/workouts/{id}/delete")
    suspend fun deleteWorkout(
        @Header("Authorization") token: String,
        @Path("id") workoutId: Int
    ): Response<AdminApiResponse<Nothing>>

    @GET("admin/workout-types")
    suspend fun getWorkoutTypes(
        @Header("Authorization") token: String
    ): Response<AdminResponse<List<WorkoutType>>>

    // ========== УПРАВЛЕНИЕ АБОНЕМЕНТАМИ ==========
    @GET("admin/subscriptions")
    suspend fun getAdminSubscriptions(
        @Header("Authorization") token: String,
        @Query("active_only") activeOnly: Boolean = false
    ): Response<AdminResponse<List<AdminSubscription>>>

    @GET("admin/subscriptions/{id}")
    suspend fun getAdminSubscription(
        @Header("Authorization") token: String,
        @Path("id") subscriptionId: Int
    ): Response<AdminSubscription>

    @POST("admin/subscriptions")
    suspend fun createSubscription(
        @Header("Authorization") token: String,
        @Body request: CreateSubscriptionRequest
    ): Response<AdminSubscription>

    @PUT("admin/subscriptions/{id}")
    suspend fun updateSubscription(
        @Header("Authorization") token: String,
        @Path("id") subscriptionId: Int,
        @Body request: CreateSubscriptionRequest
    ): Response<AdminSubscription>

    @DELETE("admin/subscriptions/{id}/delete")
    suspend fun deleteSubscription(
        @Header("Authorization") token: String,
        @Path("id") subscriptionId: Int
    ): Response<AdminApiResponse<Nothing>>

    @POST("admin/subscriptions/sell")
    suspend fun sellSubscription(
        @Header("Authorization") token: String,
        @Body request: SellSubscriptionRequest
    ): Response<AdminApiResponse<Nothing>>

    // ========== РАСПИСАНИЕ ==========
    @GET("admin/schedule/week")
    suspend fun getAdminWeeklySchedule(
        @Header("Authorization") token: String,
        @Query("date") date: String
    ): Response<AdminScheduleWeekResponse>

    @POST("admin/schedule/copy")
    suspend fun copySchedule(
        @Header("Authorization") token: String,
        @Query("from_week") fromWeek: String,
        @Query("to_week") toWeek: String
    ): Response<AdminApiResponse<Nothing>>

    // ========== НАСТРОЙКИ ==========
    @GET("admin/settings")
    suspend fun getSettings(
        @Header("Authorization") token: String
    ): Response<Settings>

    @POST("admin/settings")
    suspend fun updateSettings(
        @Header("Authorization") token: String,
        @Body settings: Settings
    ): Response<AdminApiResponse<Nothing>>

    // ========== ГРУППЫ ==========
    @GET("chats/{chatId}")
    suspend fun getChatDetails(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int
    ): Response<Chat>

    @POST("chats/{chatId}")
    suspend fun updateChat(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Body request: UpdateChatRequest
    ): Response<Chat>

    @Multipart
    @POST("chats/{chatId}/avatar")
    suspend fun uploadChatAvatar(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Part avatar: MultipartBody.Part
    ): Response<ChatAvatarResponse>

    @GET("chats/{chatId}/participants")
    suspend fun getChatParticipants(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int
    ): Response<List<ChatParticipant>>

    @POST("chats/{chatId}/participants/{userId}")
    suspend fun updateParticipantRole(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Path("userId") userId: Int,
        @Body request: UpdateParticipantRoleRequest
    ): Response<AdminApiResponse<Nothing>>

    @DELETE("chats/{chatId}/participants/{userId}")
    suspend fun removeParticipant(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Path("userId") userId: Int
    ): Response<AdminApiResponse<Nothing>>

    @POST("chats/{chatId}/participants")
    suspend fun addParticipant(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int,
        @Body request: AddParticipantRequest
    ): Response<AdminApiResponse<Nothing>>

    @GET("chats/{chatId}/statistics")
    suspend fun getChatStatistics(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int
    ): Response<ChatStatistics>

    @DELETE("admin/chats/{chatId}")
    suspend fun deleteChat(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: Int
    ): Response<AdminApiResponse<Nothing>>

    // ========== НОВОСТИ ==========
    @POST("admin/news")
    suspend fun createNews(
        @Header("Authorization") token: String,
        @Body request: CreateNewsRequest
    ): Response<NewsCreateResponse>

    @PUT("admin/news/{id}")
    suspend fun updateNews(
        @Header("Authorization") token: String,
        @Path("id") newsId: Int,
        @Body request: UpdateNewsRequest
    ): Response<NewsUpdateResponse>

    @DELETE("admin/news/{id}")
    suspend fun deleteNews(
        @Header("Authorization") token: String,
        @Path("id") newsId: Int
    ): Response<AdminApiResponse<Nothing>>

    @Multipart
    @POST("admin/news/{id}/upload-media")
    suspend fun uploadNewsMedia(
        @Header("Authorization") token: String,
        @Path("id") newsId: Int,
        @Part media: List<MultipartBody.Part>
    ): Response<AdminApiResponse<Nothing>>

    @DELETE("admin/news/{id}/media")
    suspend fun deleteNewsMedia(
        @Header("Authorization") token: String,
        @Path("id") newsId: Int,
        @Query("url") url: String,
        @Query("type") type: String
    ): Response<AdminApiResponse<Nothing>>

    // ========== ОТЧЕТЫ ==========
    @GET("admin/reports/attendance")
    suspend fun getAttendanceReport(
        @Header("Authorization") token: String,
        @Query("period") period: String,
        @Query("date") date: String? = null
    ): Response<AttendanceReport>

    @GET("admin/reports/financial")
    suspend fun getFinancialReport(
        @Header("Authorization") token: String,
        @Query("period") period: String,
        @Query("date") date: String? = null
    ): Response<FinancialReport>

    @GET("admin/reports/export")
    suspend fun exportReport(
        @Header("Authorization") token: String,
        @Query("type") type: String,
        @Query("period") period: String,
        @Query("format") format: String
    ): Response<Any>

    // ============================================================
    // ========== 🆕 ШАБЛОНЫ РАСПИСАНИЯ ==========
    // ============================================================

    @GET("admin/templates")
    suspend fun getTemplates(
        @Header("Authorization") token: String,
        @Query("active_only") activeOnly: Boolean = true
    ): Response<List<ScheduleTemplate>>

    @POST("admin/templates")
    suspend fun createTemplate(
        @Header("Authorization") token: String,
        @Body request: SaveTemplateRequest
    ): Response<AdminApiResponse<ScheduleTemplate>>

    @PUT("admin/templates/{templateId}")
    suspend fun updateTemplate(
        @Header("Authorization") token: String,
        @Path("templateId") templateId: Int,
        @Body request: SaveTemplateRequest
    ): Response<AdminApiResponse<ScheduleTemplate>>

    @DELETE("admin/templates/{templateId}")
    suspend fun deleteTemplate(
        @Header("Authorization") token: String,
        @Path("templateId") templateId: Int
    ): Response<AdminApiResponse<Nothing>>

    @POST("admin/templates/{templateId}/apply")
    suspend fun applyTemplate(
        @Header("Authorization") token: String,
        @Path("templateId") templateId: Int,
        @Body request: ApplyTemplateRequest
    ): Response<AdminApiResponse<Nothing>>

    @POST("admin/templates/{templateId}/set-default")
    suspend fun setDefaultTemplate(
        @Header("Authorization") token: String,
        @Path("templateId") templateId: Int
    ): Response<AdminApiResponse<Nothing>>

    // ============================================================
    // ========== 🆕 ИСТОРИЯ РАСПИСАНИЯ ==========
    // ============================================================

    @GET("admin/schedule/history")
    suspend fun getScheduleHistory(
        @Header("Authorization") token: String,
        @Query("week_start") weekStart: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ScheduleHistoryResponse>

    @GET("admin/schedule/versions")
    suspend fun getScheduleVersions(
        @Header("Authorization") token: String,
        @Query("week_start") weekStart: String
    ): Response<List<ScheduleVersion>>

    @POST("admin/schedule/versions/{versionId}/restore")
    suspend fun restoreScheduleVersion(
        @Header("Authorization") token: String,
        @Path("versionId") versionId: Int
    ): Response<AdminApiResponse<Nothing>>
}

// ==========================================
// ОБЩИЙ КЛАСС ДЛЯ ADMIN ОТВЕТОВ
// ==========================================

data class AdminResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T
)

// ==========================================
// 🆕 ОБЪЕДИНЁННЫЙ ОТВЕТ ДЛЯ API (GENERIC)
// ==========================================

data class AdminApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val id: Int? = null
)

// ==========================================
// DATA КЛАССЫ ДЛЯ РАСПИСАНИЯ
// ==========================================

data class AdminScheduleWeekResponse(
    val success: Boolean,
    val message: String,
    val data: AdminScheduleWeekData
)

data class AdminScheduleWeekData(
    val week: ScheduleWeekInfo?,
    val days: Map<String, List<ScheduleItem>>
)

data class ScheduleWeekInfo(
    val start: String,
    val end: String,
    val display: String,
    @SerializedName("week_number")
    val weekNumber: Int? = null,
    val year: Int? = null
)

// ==========================================
// DATA КЛАССЫ ДЛЯ НОВОСТЕЙ
// ==========================================

data class CreateNewsRequest(
    val title: String,
    val content: String,
    @SerializedName("short_description")
    val shortDescription: String? = null,
    val importance: String? = null,
    @SerializedName("category_id")
    val categoryId: Int? = null,
    @SerializedName("send_push")
    val sendPush: Boolean = false
)

data class UpdateNewsRequest(
    val title: String? = null,
    val content: String? = null,
    @SerializedName("short_description")
    val shortDescription: String? = null,
    val importance: String? = null,
    @SerializedName("category_id")
    val categoryId: Int? = null,
    @SerializedName("send_push")
    val sendPush: Boolean? = null
)

data class NewsCreateResponse(
    val success: Boolean,
    val message: String,
    val id: Int? = null,
    val data: Any? = null
)

data class NewsUpdateResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

// ==========================================
// DATA КЛАССЫ ДЛЯ ЧАТОВ
// ==========================================

data class UpdateChatRequest(
    val name: String? = null,
    val description: String? = null,
    val avatar: String? = null
)

data class ChatAvatarResponse(
    val success: Boolean,
    val message: String,
    val avatar: String? = null
)

data class UpdateParticipantRoleRequest(
    val role: String
)

data class AddParticipantRequest(
    @SerializedName("user_id")
    val userId: Int
)

data class ChatStatistics(
    @SerializedName("total_messages")
    val totalMessages: Int,
    @SerializedName("participants_count")
    val participantsCount: Int,
    @SerializedName("active_participants")
    val activeParticipants: Int,
    @SerializedName("daily_stats")
    val dailyStats: List<DailyStat>,
    @SerializedName("top_users")
    val topUsers: List<TopUser>
)

data class DailyStat(
    val date: String,
    val count: Int
)

data class TopUser(
    @SerializedName("user_id")
    val userId: Int,
    val name: String,
    @SerializedName("message_count")
    val messageCount: Int
)

// ==========================================
// 🆕 ШАБЛОНЫ РАСПИСАНИЯ - ЗАПРОСЫ И ОТВЕТЫ
// ==========================================

data class SaveTemplateRequest(
    val name: String,
    val description: String? = null,
    @SerializedName("is_default")
    val isDefault: Boolean = false,
    @SerializedName("week_start_date")
    val weekStartDate: String,
    val slots: List<TemplateSlotRequest>
)

data class TemplateSlotRequest(
    @SerializedName("day_of_week")
    val dayOfWeek: Int,
    @SerializedName("start_time")
    val startTime: String,
    val duration: Int,
    val title: String,
    @SerializedName("trainer_id")
    val trainerId: Int,
    @SerializedName("max_participants")
    val maxParticipants: Int,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int? = null,
    @SerializedName("age_category")
    val ageCategory: String? = null,
    val room: String? = null,
    val description: String? = null
)

data class ApplyTemplateRequest(
    @SerializedName("template_id")
    val templateId: Int,
    @SerializedName("target_week_start")
    val targetWeekStart: String,
    @SerializedName("clear_existing")
    val clearExisting: Boolean = true,
    @SerializedName("merge_strategy")
    val mergeStrategy: String = "replace"
)

// ==========================================
// 🆕 ИСТОРИЯ РАСПИСАНИЯ
// ==========================================

data class ScheduleHistoryResponse(
    val success: Boolean,
    val message: String,
    val data: ScheduleHistoryData
)

data class ScheduleHistoryData(
    val entries: List<ScheduleHistoryEntry>,
    val total: Int,
    val page: Int,
    @SerializedName("per_page")
    val perPage: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)

// ==========================================
// 🆕 МОДЕЛИ ДЛЯ ШАБЛОНОВ И ИСТОРИИ
// ==========================================

data class ScheduleTemplate(
    val id: Int,
    val name: String,
    val description: String? = null,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("is_default")
    val isDefault: Boolean = false,
    @SerializedName("days")
    val days: Map<Int, List<TemplateSlot>> = emptyMap()
)

data class TemplateSlot(
    val id: Int? = null,
    @SerializedName("template_id")
    val templateId: Int? = null,
    @SerializedName("day_of_week")
    val dayOfWeek: Int,
    @SerializedName("start_time")
    val startTime: String,
    val duration: Int,
    val title: String,
    @SerializedName("trainer_id")
    val trainerId: Int,
    @SerializedName("trainer_name")
    val trainerName: String? = null,
    @SerializedName("max_participants")
    val maxParticipants: Int = 10,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int? = null,
    @SerializedName("workout_type_name")
    val workoutTypeName: String? = null,
    @SerializedName("age_category")
    val ageCategory: String? = null,
    val room: String? = null,
    val description: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

data class ScheduleHistoryEntry(
    val id: Int,
    @SerializedName("week_start")
    val weekStart: String,
    @SerializedName("week_end")
    val weekEnd: String? = null,
    @SerializedName("changed_by")
    val changedBy: String,
    @SerializedName("changed_by_id")
    val changedById: Int,
    @SerializedName("change_type")
    val changeType: String,
    val description: String? = null,
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("workout_count")
    val workoutCount: Int = 0
)

data class ScheduleVersion(
    val id: Int,
    @SerializedName("week_start")
    val weekStart: String,
    val data: Map<Int, List<AdminWorkout>>,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("created_by")
    val createdBy: String,
    @SerializedName("version_number")
    val versionNumber: Int,
    val comment: String? = null
)
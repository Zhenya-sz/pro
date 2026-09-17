package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ========== СТАТИСТИКА ==========

@Parcelize
data class AdminStats(
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
) : Parcelable

@Parcelize
data class PopularWorkout(
    val id: Int,
    val title: String,
    @SerializedName("bookings_count")
    val bookingsCount: Int,
    val rank: Int
) : Parcelable

// ========== ПОЛЬЗОВАТЕЛИ ==========

@Parcelize
data class AdminUser(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String?,
    val role: String,
    @SerializedName("subscription_name")
    val subscriptionName: String?,
    @SerializedName("subscription_expiry")
    val subscriptionExpiry: String?,
    @SerializedName("visits_count")
    val visitsCount: Int,
    @SerializedName("registration_date")
    val registrationDate: String,
    val avatar: String?,
    @SerializedName("is_active")
    val isActive: Boolean
) : Parcelable

// ========== ТРЕНИРОВКИ ==========

@Parcelize
data class AdminWorkout(
    val id: Int,
    val title: String,
    val description: String,
    val date: String,
    @SerializedName("formatted_date")
    val formattedDate: String,
    @SerializedName("formatted_time")
    val formattedTime: String,
    val duration: Int,
    @SerializedName("trainer_id")
    val trainerId: Int,
    @SerializedName("trainer_name")
    val trainerName: String,
    @SerializedName("current_participants")
    val currentParticipants: Int,
    @SerializedName("max_participants")
    val maxParticipants: Int,
    @SerializedName("workout_type")
    val workoutType: String?,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int?,
    @SerializedName("age_category")
    val ageCategory: String?,
    val thumbnail: String?,
    val status: String,
    @SerializedName("room")
    val room: String? = null
) : Parcelable

// ========== АБОНЕМЕНТЫ ==========

@Parcelize
data class AdminSubscription(
    val id: Int,
    val title: String,
    val description: String,
    @SerializedName("workouts_count")
    val workoutsCount: Int,
    val price: Double,
    @SerializedName("duration_days")
    val durationDays: Int,
    @SerializedName("workout_type")
    val workoutType: String?,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int?,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("sales_count")
    val salesCount: Int,
    @SerializedName("total_revenue")
    val totalRevenue: Double,
    @SerializedName("chat_id")
    val chatId: Int? = null
) : Parcelable

// ========== ЗАПРОСЫ ==========

data class CreateWorkoutRequest(
    val title: String,
    val description: String,
    val date: String,
    val duration: Int,
    @SerializedName("trainer_id")
    val trainerId: Int,
    @SerializedName("max_participants")
    val maxParticipants: Int,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int?,
    @SerializedName("age_category")
    val ageCategory: String?,
    @SerializedName("room")
    val room: String? = null
)

data class UpdateWorkoutRequest(
    val id: Int,
    val title: String? = null,
    val description: String? = null,
    val date: String? = null,
    val duration: Int? = null,
    @SerializedName("trainer_id")
    val trainerId: Int? = null,
    @SerializedName("max_participants")
    val maxParticipants: Int? = null,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int? = null,
    @SerializedName("age_category")
    val ageCategory: String? = null,
    @SerializedName("room")
    val room: String? = null
)

data class CreateUserRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: String = "subscriber"
)

data class UpdateUserRequest(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    @SerializedName("reset_password")
    val resetPassword: Boolean = false
)

data class CreateSubscriptionRequest(
    val title: String,
    val description: String,
    @SerializedName("workouts_count")
    val workoutsCount: Int,
    val price: Double,
    @SerializedName("duration_days")
    val durationDays: Int,
    @SerializedName("workout_type_id")
    val workoutTypeId: Int? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

data class SellSubscriptionRequest(
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("subscription_id")
    val subscriptionId: Int,
    @SerializedName("payment_method")
    val paymentMethod: String = "cash"
)

// ==========================================
// УДАЛИ ЭТИ КЛАССЫ ОТСЮДА (они уже есть в AdminApiService.kt)
// ==========================================
//
// data class AttendanceReport(...)
// data class ReportDataset(...)
// data class FinancialReport(...)

// ==========================================
// ДОБАВИТЬ В КОНЕЦ ФАЙЛА AdminModels.kt
// ==========================================

// ========== ШАБЛОНЫ РАСПИСАНИЯ ==========

@Parcelize
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
) : Parcelable

@Parcelize
data class TemplateSlot(
    val id: Int? = null,
    @SerializedName("template_id")
    val templateId: Int? = null,
    @SerializedName("day_of_week")
    val dayOfWeek: Int,              // 1-7 (пн-вс)
    @SerializedName("start_time")
    val startTime: String,           // "09:00"
    val duration: Int,               // минуты
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
) : Parcelable

// ========== ЗАПРОСЫ ДЛЯ ШАБЛОНОВ ==========

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
    val mergeStrategy: String = "replace" // "replace" | "merge" | "append"
)

// ========== ИСТОРИЯ ИЗМЕНЕНИЙ РАСПИСАНИЯ ==========

@Parcelize
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
    val changeType: String,          // "created" | "updated" | "deleted" | "template_applied"
    val description: String? = null,
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("workout_count")
    val workoutCount: Int = 0
) : Parcelable

// ========== ВЕРСИЯ РАСПИСАНИЯ ==========

@Parcelize
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
) : Parcelable
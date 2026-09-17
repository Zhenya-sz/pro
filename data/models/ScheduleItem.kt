package com.fitnesslemon.app.data.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScheduleItem(
    // ===== ОСНОВНЫЕ ПОЛЯ =====
    val id: Int,
    val title: String,
    val description: String? = null,

    // ===== ДАТА И ВРЕМЯ =====
    val date: String,                          // "2026-08-01 10:00:00"
    @SerializedName("formatted_date")
    val formattedDate: String,                 // "2026-08-01" или "01.08.2026"
    @SerializedName("formatted_time")
    val formattedTime: String? = null,         // "10:00"
    @SerializedName("day_of_week")
    val dayOfWeek: Int? = null,                // 1-7 (Пн-Вс)
    @SerializedName("week_number")
    val weekNumber: Int? = null,               // Номер недели

    // ===== ДЛИТЕЛЬНОСТЬ =====
    val duration: Int? = null,                 // в минутах

    // ===== ТРЕНЕР =====
    @SerializedName("trainer_id")
    val trainerId: Int? = null,
    @SerializedName("trainer_name")
    val trainerName: String? = null,

    // ⭐ НОВОЕ ПОЛЕ: фото тренера
    @SerializedName("trainer_photo")
    val trainerPhoto: String? = null,          // URL аватарки тренера

    // ===== УЧАСТНИКИ =====
    @SerializedName("current_participants")
    val currentParticipants: Int = 0,
    @SerializedName("max_participants")
    val maxParticipants: Int = 0,
    @SerializedName("available_spots")
    val availableSpots: Int = 0,

    // ===== ТИПЫ ТРЕНИРОВОК =====
    @SerializedName("workout_type")
    val workoutType: String? = null,           // "yoga"
    @SerializedName("workout_type_slug")
    val workoutTypeSlug: String? = null,       // "yoga"
    @SerializedName("workout_types")
    val workoutTypes: List<String>? = null,    // ["Йога", "Растяжка"]
    @SerializedName("workout_type_slugs")
    val workoutTypeSlugs: List<String>? = null,// ["yoga", "stretching"]

    // ===== ВОЗРАСТНАЯ КАТЕГОРИЯ =====
    @SerializedName("age_category")
    val ageCategory: String? = null,           // "adult", "kids"
    @SerializedName("age_category_display")
    val ageCategoryDisplay: String? = null,    // "Взрослые", "Дети"

    // ===== ЛОКАЦИЯ =====
    val room: String? = null,                  // "Зал 1", "Студия"

    // ===== ИЗОБРАЖЕНИЯ =====
    val thumbnail: String? = null,             // URL превью

    // ===== СТАТУС =====
    val status: String? = null,                // "upcoming", "active", "completed", "cancelled"

    // ===== ДОПОЛНИТЕЛЬНАЯ ИНФОРМАЦИЯ =====
    @SerializedName("difficulty_level")
    val difficultyLevel: String? = null,       // "beginner", "intermediate", "advanced"
    @SerializedName("difficulty_level_display")
    val difficultyLevelDisplay: String? = null,// "Начинающий", "Средний", "Продвинутый"

    @SerializedName("is_booked")
    val isBooked: Boolean? = null,             // Записан ли пользователь

    @SerializedName("is_past")
    val isPast: Boolean? = null,               // Прошла ли тренировка

    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int? = null         // Остаток тренировок у пользователя

) : Parcelable {

    // ===== ВСПОМОГАТЕЛЬНЫЕ СВОЙСТВА =====

    /** Проверяет, прошла ли тренировка */
    val isWorkoutPast: Boolean
        get() = isPast ?: false

    /** Проверяет, записан ли пользователь */
    val isUserBooked: Boolean
        get() = isBooked ?: false

    /** Полное время в формате "HH:MM" */
    val timeDisplay: String
        get() = formattedTime ?: "00:00"

    /** Короткое название типа тренировки */
    val primaryWorkoutType: String
        get() = workoutTypes?.firstOrNull() ?: workoutType ?: "Тренировка"

    /** Цвет для типа тренировки (возвращает ресурс цвета) */
    fun getWorkoutColorRes(): Int {
        return when (primaryWorkoutType.lowercase()) {
            "йога" -> com.fitnesslemon.app.R.color.workout_yoga
            "силовая" -> com.fitnesslemon.app.R.color.workout_strength
            "кардио" -> com.fitnesslemon.app.R.color.workout_cardio
            "пилатес" -> com.fitnesslemon.app.R.color.workout_pilates
            "медитация" -> com.fitnesslemon.app.R.color.workout_meditation
            "растяжка" -> com.fitnesslemon.app.R.color.workout_stretching
            "функциональная" -> com.fitnesslemon.app.R.color.workout_functional
            "танцы" -> com.fitnesslemon.app.R.color.workout_dance
            "бокс" -> com.fitnesslemon.app.R.color.workout_boxing
            else -> com.fitnesslemon.app.R.color.workout_default
        }
    }

    /** Эмодзи для типа тренировки */
    val workoutEmoji: String
        get() = when (primaryWorkoutType.lowercase()) {
            "йога" -> "🧘"
            "силовая" -> "💪"
            "кардио" -> "🏃"
            "пилатес" -> "🤸"
            "медитация" -> "🧘‍♀️"
            "растяжка" -> "🧘‍♂️"
            "функциональная" -> "🏋️"
            "танцы" -> "💃"
            "бокс" -> "🥊"
            else -> "🏋️"
        }

    /** Статус записи для отображения */
    val bookingStatusText: String
        get() = when {
            isWorkoutPast -> "Прошла"
            isUserBooked -> "Записан"
            availableSpots <= 0 -> "Мест нет"
            else -> "Доступно"
        }

    /** Цвет статуса записи */
    fun getBookingStatusColorRes(): Int {
        return when {
            isWorkoutPast -> com.fitnesslemon.app.R.color.lemon_text_secondary
            isUserBooked -> com.fitnesslemon.app.R.color.lemon_success
            availableSpots <= 0 -> com.fitnesslemon.app.R.color.lemon_error
            else -> com.fitnesslemon.app.R.color.lemon_primary
        }
    }

    /** Проверяет, есть ли свободные места */
    val hasAvailableSpots: Boolean
        get() = availableSpots > 0

    /** Проверяет, можно ли записаться */
    fun canBook(remainingWorkoutsCount: Int): Boolean {
        return !isWorkoutPast &&
                !isUserBooked &&
                hasAvailableSpots &&
                remainingWorkoutsCount > 0
    }
}
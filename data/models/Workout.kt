package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Workout(
    val id: Int,
    val title: String,
    val description: String,
    val date: String,
    @SerializedName("formatted_date")
    val formattedDate: String,
    val duration: Int? = null,
    @SerializedName("trainer_id")
    val trainerId: Int? = null,
    @SerializedName("trainer_name")
    val trainerName: String,
    @SerializedName("current_participants")
    val currentParticipants: Int,
    @SerializedName("max_participants")
    val maxParticipants: Int,
    @SerializedName("available_spots")
    val availableSpots: Int,
    @SerializedName("workout_types")
    val workoutTypes: List<String>,
    @SerializedName("difficulty_levels")
    val difficultyLevels: List<String>,
    val thumbnail: String?,
    val room: String? = null,
    @SerializedName("age_category")
    val ageCategory: String? = null,
    val status: String = "upcoming"
) : Parcelable {

    // Вспомогательные свойства
    val isFull: Boolean get() = availableSpots <= 0

    val isPast: Boolean get() {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val workoutDate = format.parse(date)
            val currentTime = java.util.Date()
            workoutDate?.before(currentTime) ?: false
        } catch (e: Exception) {
            false
        }
    }

    val isBooked: Boolean get() = false // Устанавливается извне

    val formattedShortDate: String get() {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val dateObj = format.parse(date)
            val outputFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale("ru"))
            dateObj?.let { outputFormat.format(it) } ?: formattedDate
        } catch (e: Exception) {
            formattedDate
        }
    }

    val formattedTime: String get() {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val dateObj = format.parse(date)
            val outputFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            dateObj?.let { outputFormat.format(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
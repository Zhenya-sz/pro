package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Booking(
    val id: Int,
    @SerializedName("workout_id")
    val workoutId: Int? = null,
    val title: String,
    val date: String,
    @SerializedName("formatted_date")
    val formattedDate: String,
    val duration: Int? = null,
    @SerializedName("trainer_id")
    val trainerId: Int? = null,
    @SerializedName("trainer_name")
    val trainerName: String,
    @SerializedName("current_participants")
    val currentParticipants: Int? = null,
    @SerializedName("max_participants")
    val maxParticipants: Int? = null,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("booking_date")
    val bookingDate: String? = null,
    @SerializedName("user_id")
    val userId: Int? = null,
    @SerializedName("data")
    val data: @RawValue Map<String, Any>? = null  // Добавлено @RawValue
) : Parcelable
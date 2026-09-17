package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Trainer(
    val id: Int,
    val name: String,
    val photo: String?,
    val specialization: String,
    val experience: String,
    val bio: String,
    val email: String,
    @SerializedName("workout_types")
    val workoutTypes: List<String>,
    val rating: Double
) : Parcelable
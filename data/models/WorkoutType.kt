package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class WorkoutType(
    val id: Int,
    val name: String,
    val slug: String,
    @SerializedName("description")
    val description: String? = null
)
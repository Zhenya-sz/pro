package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val id: Int,
    val name: String,
    @SerializedName("first_name")
    val firstName: String = "",
    @SerializedName("last_name")
    val lastName: String = "",
    @SerializedName("middle_name")
    val middleName: String? = null,
    val email: String,
    val phone: String? = null,
    val avatar: String? = null,
    val role: String,
    @SerializedName("birth_date")
    val birthDate: String? = null,
    val address: String? = null,
    val registered: String? = null,
    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int = 0
) : Parcelable {
    val fullName: String
        get() = listOfNotNull(lastName, firstName, middleName).joinToString(" ").ifEmpty { name }
}
package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("name")
    val fullName: String,

    @SerializedName("phone")
    val phone: String,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("password")
    val password: String,

    @SerializedName("device_info")
    val deviceInfo: DeviceInfo
)
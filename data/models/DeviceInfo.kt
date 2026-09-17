package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class DeviceInfo(
    @SerializedName("imei")
    val imei: String? = null,

    @SerializedName("android_id")
    val androidId: String? = null,

    @SerializedName("model")
    val model: String,

    @SerializedName("android_version")
    val androidVersion: String,

    @SerializedName("app_version")
    val appVersion: String
)
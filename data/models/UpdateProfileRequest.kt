package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class UpdateProfileRequest(
    @SerializedName("birth_date")
    val birthDate: String = "",
    val address: String = ""
)

data class UpdateAvatarResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: AvatarData? = null
)

data class AvatarData(
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,

    @SerializedName("attachment_id")
    val attachmentId: Int? = null,

    @SerializedName("attachment_url")
    val attachmentUrl: String? = null
)
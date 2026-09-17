package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class UpdateChatRequest(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("avatar")
    val avatar: String? = null
)

data class ChatAvatarResponse(
    val success: Boolean,
    val message: String,
    val avatar: String? = null
)

data class UpdateParticipantRoleRequest(
    val role: String
)

data class AddParticipantRequest(
    @SerializedName("user_id")
    val userId: Int
)
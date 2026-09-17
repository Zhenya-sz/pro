package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class StoryReply(
    val id: Int,
    @SerializedName("story_id")
    val storyId: Int,
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("user_name")
    val userName: String,
    @SerializedName("user_avatar")
    val userAvatar: String? = null,
    val text: String,
    @SerializedName("is_like")
    val isLike: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String
)
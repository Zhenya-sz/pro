// StoryModels.kt
package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class SendStoryMessageRequest(
    val text: String
)

data class StoryLikeResponse(
    @SerializedName("like_count")
    val likeCount: Int,
    @SerializedName("liked")
    val liked: Boolean
)
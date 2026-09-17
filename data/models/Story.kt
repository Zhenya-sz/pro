package com.fitnesslemon.app.data.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Story(
    val id: Int,
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("user_name")
    val userName: String = "",
    @SerializedName("user_avatar")
    val userAvatar: String? = null,
    @SerializedName("media_url")
    val mediaUrl: String = "",
    val type: String = "image",
    val title: String? = null,
    val text: String? = null,
    val duration: Int = 5,
    @SerializedName("created_at")
    val createdAt: String = "",
    @SerializedName("is_viewed")
    val isViewed: Int = 0,
    @SerializedName("is_own")
    val isOwn: Boolean = false,
    @SerializedName("like_count")
    val likeCount: Int = 0,
    @SerializedName("is_liked")
    val isLiked: Boolean = false
) : Parcelable {
    val isViewedBoolean: Boolean
        get() = isViewed == 1
}
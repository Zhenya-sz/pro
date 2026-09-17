package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class News(
    val id: Int,
    val title: String?,
    val excerpt: String?,
    val content: String?,
    val date: String?,
    @SerializedName("formatted_date")
    val formattedDate: String?,
    val thumbnail: String?,
    @SerializedName("author_id")
    val authorId: Int?,
    @SerializedName("author_name")
    val authorName: String?,
    val categories: List<String>?,
    val link: String?,
    val images: List<String>? = null,
    val videos: List<String>? = null,
    @SerializedName("short_description")
    val shortDescription: String? = null,
    @SerializedName("importance")
    val importance: String? = null,
    @SerializedName("category_id")
    val categoryId: Int? = null,
    @SerializedName("category_name")
    val categoryName: String? = null,
    @SerializedName("send_push")
    val sendPush: Int = 0,
    @SerializedName("views_count")
    val viewsCount: Int = 0,

    // ========== НОВЫЕ ПОЛЯ ДЛЯ INSTAGRAM-СТИЛЯ ==========
    @SerializedName("likes_count")
    val likesCount: Int = 0,

    @SerializedName("comments_count")
    val commentsCount: Int = 0,

    @SerializedName("author_avatar")
    val authorAvatar: String? = null,

    @SerializedName("is_liked")
    val isLiked: Boolean = false,

    @SerializedName("is_saved")
    val isSaved: Boolean = false,

    @SerializedName("media_count")
    val mediaCount: Int = 0,

    @SerializedName("is_pinned")
    val isPinned: Boolean = false
)
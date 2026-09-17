package com.fitnesslemon.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news")
data class NewsEntity(
    @PrimaryKey
    val id: Int,
    val title: String?,
    val excerpt: String?,
    val content: String?,
    val date: String?,
    val formattedDate: String?,
    val thumbnail: String?,
    val authorId: Int?,
    val authorName: String?,
    val categories: String?,
    val link: String?,
    val images: String?,
    val videos: String?,
    val shortDescription: String?,
    val importance: String?,
    val categoryId: Int?,
    val categoryName: String?,
    val sendPush: Int,
    val viewsCount: Int,
    val likesCount: Int? = 0,
    val commentsCount: Int? = 0,
    val authorAvatar: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
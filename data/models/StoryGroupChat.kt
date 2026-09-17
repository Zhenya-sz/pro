package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class StoryGroupChat(
    val id: Int,
    @SerializedName("story_id")
    val storyId: Int,
    @SerializedName("chat_id")
    val chatId: Int,
    @SerializedName("chat_name")
    val chatName: String,
    @SerializedName("chat_avatar")
    val chatAvatar: String? = null,
    @SerializedName("participants_count")
    val participantsCount: Int,
    @SerializedName("last_message")
    val lastMessage: String? = null,
    @SerializedName("last_message_time")
    val lastMessageTime: String? = null,
    @SerializedName("unread_count")
    val unreadCount: Int = 0
)
package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class ChatMessagesResponse(
    val success: Boolean,
    val message: String,
    val data: ChatMessagesData? = null
)

data class ChatMessagesData(
    val id: Int,
    val type: String,
    val name: String? = null,
    val avatar: String? = null,
    val description: String? = null,
    @SerializedName("is_subscription_group")
    val isSubscriptionGroup: Int = 0,
    @SerializedName("subscription_id")
    val subscriptionId: Int? = null,
    @SerializedName("participants_count")
    val participantsCount: Int = 0,
    @SerializedName("participant")
    val participant: ChatParticipant? = null,
    @SerializedName("last_message")
    val lastMessage: LastMessage? = null,
    @SerializedName("unread_count")
    val unreadCount: Int = 0,
    @SerializedName("participant_role")
    val participantRole: String? = null,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    val messages: List<Message> = emptyList()  // ← Список сообщений
)
package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class ChatStatistics(
    @SerializedName("total_messages")
    val totalMessages: Int,
    @SerializedName("participants_count")
    val participantsCount: Int,
    @SerializedName("active_participants")
    val activeParticipants: Int,
    @SerializedName("daily_stats")
    val dailyStats: List<DailyStat>,
    @SerializedName("top_users")
    val topUsers: List<TopUser>
)

data class DailyStat(
    val date: String,
    val count: Int
)

data class TopUser(
    @SerializedName("user_id")
    val userId: Int,
    val name: String,
    @SerializedName("message_count")
    val messageCount: Int
)
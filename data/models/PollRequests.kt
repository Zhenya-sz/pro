package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class CreatePollRequest(
    val question: String,
    val options: List<String>,
    @SerializedName("expires_days")
    val expiresDays: Int = 7
)

data class CreatePollResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("poll_id")
    val pollId: Int
)

data class VotePollRequest(
    @SerializedName("option_index")
    val optionIndex: Int
)

data class PollResultsResponse(
    val question: String,
    @SerializedName("total_votes")
    val totalVotes: Int,
    val results: List<PollOptionResult>,
    @SerializedName("user_vote")
    val userVote: Int? = null,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("expires_at")
    val expiresAt: String? = null
)

data class PollOptionResult(
    val option: String,
    val votes: Int,
    val percentage: Int
)
package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Invitation(
    val id: Int,
    @SerializedName("inviter_id")
    val inviterId: Int,
    @SerializedName("inviter_name")
    val inviterName: String,
    @SerializedName("inviter_avatar")
    val inviterAvatar: String? = null,
    @SerializedName("invitee_phone")
    val inviteePhone: String,
    @SerializedName("invitee_id")
    val inviteeId: Int? = null,
    @SerializedName("chat_id")
    val chatId: Int? = null,
    val status: String, // pending, accepted, declined
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("expires_at")
    val expiresAt: String? = null,
    @SerializedName("formatted_date")
    val formattedDate: String
) : Parcelable

data class CreateInvitationRequest(
    @SerializedName("invitee_phone")
    val inviteePhone: String
)

data class InvitationResponse(
    val success: Boolean,
    val message: String,
    val invitation: Invitation? = null
)

data class InvitationsListResponse(
    val invitations: List<Invitation>,
    val total: Int
)
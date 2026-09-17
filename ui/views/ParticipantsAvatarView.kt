package com.fitnesslemon.app.ui.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import de.hdodenhof.circleimageview.CircleImageView

class ParticipantsAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val maxAvatars = 5
    private val avatarSize = 40

    init {
        orientation = HORIZONTAL
    }

    fun setParticipants(participants: List<Participant>) {
        removeAllViews()

        val displayParticipants = participants.take(maxAvatars)
        val remaining = participants.size - displayParticipants.size

        displayParticipants.forEach { participant ->
            addAvatar(participant)
        }

        if (remaining > 0) {
            addRemainingBadge(remaining)
        }
    }

    private fun addAvatar(participant: Participant) {
        val avatar = CircleImageView(context).apply {
            layoutParams = LayoutParams(
                dpToPx(avatarSize),
                dpToPx(avatarSize)
            ).apply {
                marginEnd = dpToPx(4)
            }

            if (!participant.avatarUrl.isNullOrEmpty()) {
                Glide.with(context)
                    .load(participant.avatarUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(this)
            } else {
                setImageResource(R.drawable.ic_profile)
            }
        }
        addView(avatar)
    }

    private fun addRemainingBadge(count: Int) {
        val badge = TextView(context).apply {
            layoutParams = LayoutParams(
                dpToPx(avatarSize),
                dpToPx(avatarSize)
            ).apply {
                marginEnd = dpToPx(4)
            }

            background = ContextCompat.getDrawable(context, R.drawable.bg_circle_primary)
            gravity = android.view.Gravity.CENTER
            text = "+$count"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        addView(badge)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    data class Participant(
        val id: Int,
        val name: String,
        val avatarUrl: String? = null
    )
}
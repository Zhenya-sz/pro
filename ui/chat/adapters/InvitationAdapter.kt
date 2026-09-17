package com.fitnesslemon.app.ui.chat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Invitation
import com.fitnesslemon.app.databinding.ItemInvitationBinding

class InvitationAdapter(
    private val invitations: List<Invitation>,
    private val onAcceptClick: (Invitation) -> Unit,
    private val onDeclineClick: (Invitation) -> Unit
) : RecyclerView.Adapter<InvitationAdapter.InvitationViewHolder>() {

    class InvitationViewHolder(val binding: ItemInvitationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvitationViewHolder {
        val binding = ItemInvitationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InvitationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InvitationViewHolder, position: Int) {
        val invitation = invitations[position]
        holder.binding.apply {
            tvInviterName.text = invitation.inviterName
            tvInviterPhone.text = "Приглашает вас в чат"
            tvInvitationDate.text = invitation.formattedDate

            // Загружаем аватар приглашающего
            if (!invitation.inviterAvatar.isNullOrEmpty()) {
                Glide.with(root.context)
                    .load(invitation.inviterAvatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(ivInviterAvatar)
            } else {
                ivInviterAvatar.setImageResource(R.drawable.ic_profile)
            }

            // Кнопки действий
            btnAccept.setOnClickListener {
                onAcceptClick(invitation)
            }

            btnDecline.setOnClickListener {
                onDeclineClick(invitation)
            }

            // Если статус не pending, скрываем кнопки
            if (invitation.status != "pending") {
                btnAccept.visibility = android.view.View.GONE
                btnDecline.visibility = android.view.View.GONE
                
                tvStatus.visibility = android.view.View.VISIBLE
                tvStatus.text = when (invitation.status) {
                    "accepted" -> "✓ Принято"
                    "declined" -> "✗ Отклонено"
                    else -> ""
                }
            } else {
                btnAccept.visibility = android.view.View.VISIBLE
                btnDecline.visibility = android.view.View.VISIBLE
                tvStatus.visibility = android.view.View.GONE
            }
        }
    }

    override fun getItemCount() = invitations.size
}
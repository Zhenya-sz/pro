package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Chat
import com.fitnesslemon.app.databinding.ItemAdminGroupBinding
import android.view.View

class AdminGroupsAdapter(
    private val onGroupClick: (Chat) -> Unit,
    private val onManageClick: (Chat) -> Unit,
    private val onEditClick: (Chat) -> Unit,
    private val onDeleteClick: (Chat) -> Unit
) : ListAdapter<Chat, AdminGroupsAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemAdminGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding, onGroupClick, onManageClick, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GroupViewHolder(
        private val binding: ItemAdminGroupBinding,
        private val onGroupClick: (Chat) -> Unit,
        private val onManageClick: (Chat) -> Unit,
        private val onEditClick: (Chat) -> Unit,
        private val onDeleteClick: (Chat) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: Chat) {
            binding.apply {
                tvGroupName.text = group.name ?: "Без названия"
                tvDescription.text = group.description ?: ""
                tvParticipantsCount.text = "${group.participantsCount} участников"
                tvUnreadCount.text = if (group.unreadCount > 0) group.unreadCount.toString() else ""
                tvUnreadCount.visibility = if (group.unreadCount > 0) View.VISIBLE else View.GONE

                if (!group.avatar.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(group.avatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_group)
                        .into(ivAvatar)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_group)
                }

                // Тип группы (абонемент или обычная)
                // ИСПРАВЛЕНО: isSubscriptionGroup теперь Int, сравниваем с 1
                if (group.isSubscriptionGroup == 1) {
                    tvSubscriptionName.text = "Группа абонемента"
                    tvSubscriptionName.visibility = View.VISIBLE
                } else {
                    tvSubscriptionName.visibility = View.GONE
                }

                btnOpenChat.setOnClickListener { onGroupClick(group) }
                btnManage.setOnClickListener { onManageClick(group) }
                btnEdit.setOnClickListener { onEditClick(group) }
                // Кнопка удаления может быть в диалоге управления, но можно добавить отдельно
            }
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean = oldItem == newItem
    }
}
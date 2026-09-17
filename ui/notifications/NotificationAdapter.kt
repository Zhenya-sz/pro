package com.fitnesslemon.app.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Notification
import com.fitnesslemon.app.data.models.NotificationGroup
import com.fitnesslemon.app.databinding.ItemNotificationBinding
import com.fitnesslemon.app.databinding.ItemNotificationGroupBinding
import com.fitnesslemon.app.databinding.ItemNotificationSectionBinding

sealed class NotificationItem {
    data class Section(val title: String) : NotificationItem()
    data class ChatGroup(val group: NotificationGroup) : NotificationItem()
    data class BookingGroup(val group: NotificationGroup) : NotificationItem()
    data class Invitation(val notification: Notification) : NotificationItem()
    data class Other(val notification: Notification) : NotificationItem()
}

class NotificationAdapter(
    private val onChatClick: (NotificationGroup) -> Unit,
    private val onBookingClick: (NotificationGroup) -> Unit,
    private val onInvitationAction: (Notification, String) -> Unit,
    private val onItemLongClick: (Notification) -> Unit
) : ListAdapter<NotificationItem, RecyclerView.ViewHolder>(NotificationItemDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is NotificationItem.Section -> TYPE_SECTION
            is NotificationItem.ChatGroup -> TYPE_CHAT_GROUP
            is NotificationItem.BookingGroup -> TYPE_BOOKING_GROUP
            is NotificationItem.Invitation -> TYPE_INVITATION
            is NotificationItem.Other -> TYPE_OTHER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION -> {
                val binding = ItemNotificationSectionBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SectionViewHolder(binding)
            }
            TYPE_CHAT_GROUP, TYPE_BOOKING_GROUP -> {
                val binding = ItemNotificationGroupBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                GroupViewHolder(binding)
            }
            TYPE_INVITATION, TYPE_OTHER -> {
                val binding = ItemNotificationBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                NotificationViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)

        when (holder) {
            is SectionViewHolder -> {
                holder.bind((item as NotificationItem.Section).title)
            }
            is GroupViewHolder -> {
                when (item) {
                    is NotificationItem.ChatGroup -> {
                        holder.bind(item.group) { group ->
                            onChatClick(group)
                        }
                    }
                    is NotificationItem.BookingGroup -> {
                        holder.bind(item.group) { group ->
                            onBookingClick(group)
                        }
                    }
                    else -> throw IllegalArgumentException("Invalid item for GroupViewHolder")
                }
            }
            is NotificationViewHolder -> {
                val notification = when (item) {
                    is NotificationItem.Invitation -> item.notification
                    is NotificationItem.Other -> item.notification
                    else -> throw IllegalArgumentException("Invalid item for NotificationViewHolder")
                }
                holder.bind(
                    notification = notification,
                    onLongClick = onItemLongClick,
                    onActionClick = { action -> onInvitationAction(notification, action) }
                )
            }
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount()
    }

    class SectionViewHolder(private val binding: ItemNotificationSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvSectionTitle.text = title
        }
    }

    class GroupViewHolder(private val binding: ItemNotificationGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(group: NotificationGroup, onClick: (NotificationGroup) -> Unit) {
            binding.apply {
                tvGroupTitle.text = group.title
                tvGroupMessage.text = group.message
                tvGroupTime.text = group.lastNotification?.formattedDate ?: ""

                if (group.count > 1) {
                    tvGroupCount.text = group.count.toString()
                    tvGroupCount.visibility = android.view.View.VISIBLE
                } else {
                    tvGroupCount.visibility = android.view.View.GONE
                }

                when (group.type) {
                    "chat" -> ivGroupIcon.setImageResource(R.drawable.ic_chat)
                    "booking", "reminder" -> ivGroupIcon.setImageResource(R.drawable.ic_workout)
                    else -> ivGroupIcon.setImageResource(R.drawable.ic_notification_default)
                }

                root.setOnClickListener { onClick(group) }
            }
        }
    }

    class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            notification: Notification,
            onLongClick: (Notification) -> Unit,
            onActionClick: (String) -> Unit
        ) {
            binding.apply {
                tvNotificationTitle.text = notification.title
                tvNotificationMessage.text = notification.message
                tvNotificationTime.text = notification.formattedDate

                when (notification.type) {
                    "invitation" -> ivNotificationIcon.setImageResource(R.drawable.ic_invite)
                    "chat" -> ivNotificationIcon.setImageResource(R.drawable.ic_chat)
                    "booking", "reminder" -> ivNotificationIcon.setImageResource(R.drawable.ic_workout)
                    "news" -> ivNotificationIcon.setImageResource(R.drawable.ic_news)
                    else -> ivNotificationIcon.setImageResource(R.drawable.ic_notification_default)
                }

                if (notification.type == "invitation") {
                    actionButtonsContainer.visibility = android.view.View.VISIBLE
                    btnAccept.setOnClickListener { onActionClick("accept") }
                    btnDecline.setOnClickListener { onActionClick("decline") }
                } else {
                    actionButtonsContainer.visibility = android.view.View.GONE
                }

                vUnreadDot.visibility = if (!notification.isRead) android.view.View.VISIBLE else android.view.View.GONE

                root.setOnLongClickListener {
                    onLongClick(notification)
                    true
                }
            }
        }
    }

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_CHAT_GROUP = 1
        private const val TYPE_BOOKING_GROUP = 2
        private const val TYPE_INVITATION = 3
        private const val TYPE_OTHER = 4
    }
}

class NotificationItemDiffCallback : DiffUtil.ItemCallback<NotificationItem>() {
    override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean {
        return when {
            oldItem is NotificationItem.Section && newItem is NotificationItem.Section -> oldItem.title == newItem.title
            oldItem is NotificationItem.ChatGroup && newItem is NotificationItem.ChatGroup -> oldItem.group.groupId == newItem.group.groupId
            oldItem is NotificationItem.BookingGroup && newItem is NotificationItem.BookingGroup -> oldItem.group.groupId == newItem.group.groupId
            oldItem is NotificationItem.Invitation && newItem is NotificationItem.Invitation -> oldItem.notification.id == newItem.notification.id
            oldItem is NotificationItem.Other && newItem is NotificationItem.Other -> oldItem.notification.id == newItem.notification.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean {
        return oldItem == newItem
    }
}
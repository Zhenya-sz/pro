package com.fitnesslemon.app.ui.chat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Chat
import com.fitnesslemon.app.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatListAdapter(
    private val chats: List<Chat>,
    private val onChatClick: (Chat) -> Unit,
    private val onChatLongClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.apply {
                // Название чата
                tvChatName.text = when {
                    chat.type == "group" -> chat.name ?: "Групповой чат"
                    chat.participant != null -> chat.participant?.name ?: "Пользователь"
                    else -> "Чат"
                }

                // Отображение количества участников для групповых чатов
                if (chat.type == "group" && chat.participantsCount > 0) {
                    tvParticipantsCount.text = formatParticipantsCount(chat.participantsCount)
                    tvParticipantsCount.visibility = android.view.View.VISIBLE
                } else {
                    tvParticipantsCount.visibility = android.view.View.GONE
                }

                // Последнее сообщение
                tvLastMessage.text = chat.lastMessage?.text ?: "Нет сообщений"

                // Время последнего сообщения
                tvLastMessageTime.text = chat.lastMessage?.time?.let { formatTime(it) } ?: ""

                // Количество непрочитанных
                if (chat.unreadCount > 0) {
                    tvUnreadCount.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
                    tvUnreadCount.visibility = android.view.View.VISIBLE
                } else {
                    tvUnreadCount.visibility = android.view.View.GONE
                }

                // Аватар
                val avatarUrl = if (chat.type == "group") {
                    chat.avatar
                } else {
                    chat.participant?.getAvatarUrlValue()
                }

                if (!avatarUrl.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(ivAvatar)
                } else {
                    ivAvatar.setImageResource(
                        if (chat.type == "group") R.drawable.ic_group else R.drawable.ic_profile
                    )
                }

                // Иконка группы (для групповых чатов)
                ivGroupIcon.visibility = if (chat.type == "group") android.view.View.VISIBLE else android.view.View.GONE

                // Обработка кликов
                root.setOnClickListener {
                    onChatClick(chat)
                }

                root.setOnLongClickListener {
                    onChatLongClick(chat)
                    true
                }
            }
        }

        private fun formatParticipantsCount(count: Int): String {
            val lastDigit = count % 10
            val lastTwoDigits = count % 100
            return when {
                lastTwoDigits in 11..19 -> "$count участников"
                lastDigit == 1 -> "$count участник"
                lastDigit in 2..4 -> "$count участника"
                else -> "$count участников"
            }
        }

        private fun formatTime(dateStr: String): String {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = format.parse(dateStr)
                val now = Date()
                val diff = now.time - (date?.time ?: 0)

                when {
                    diff < 60000 -> "только что"
                    diff < 3600000 -> "${diff / 60000} мин"
                    diff < 86400000 -> "${diff / 3600000} ч"
                    else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
                }
            } catch (e: Exception) {
                ""
            }
        }
    }
}
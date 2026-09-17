package com.fitnesslemon.app.ui.chat.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.ChatParticipant
import com.fitnesslemon.app.databinding.ItemParticipantHorizontalBinding

class ParticipantsHorizontalAdapter(
    private val participants: List<ChatParticipant>,
    private val onParticipantClick: (ChatParticipant) -> Unit
) : RecyclerView.Adapter<ParticipantsHorizontalAdapter.ViewHolder>() {

    private val TAG = "ParticipantsHorizontalAdapter"

    init {
        Log.d(TAG, "========== Адаптер создан ==========")
        Log.d(TAG, "Количество участников: ${participants.size}")
        participants.forEachIndexed { index, participant ->
            Log.d(TAG, "  [$index] ${participant.name} (${participant.role}) - аватар: ${participant.getAvatarUrlValue()}")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.d(TAG, "onCreateViewHolder - создаем ViewHolder")
        val binding = ItemParticipantHorizontalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder - позиция $position из ${participants.size}")
        holder.bind(participants[position])
    }

    override fun getItemCount(): Int {
        Log.d(TAG, "getItemCount = ${participants.size}")
        return participants.size
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        Log.d(TAG, "onAttachedToRecyclerView - адаптер прикреплен к RecyclerView")
    }

    inner class ViewHolder(private val binding: ItemParticipantHorizontalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(participant: ChatParticipant) {
            Log.d(TAG, "bind() - Участник: ${participant.name}")

            binding.tvParticipantName.text = participant.name
            Log.d(TAG, "  Имя установлено: ${participant.name}")
            Log.d(TAG, "  Длина имени: ${participant.name.length} символов")

            val avatarUrl = participant.getAvatarUrlValue()
            Log.d(TAG, "  URL аватара: $avatarUrl")

            if (!avatarUrl.isNullOrEmpty()) {
                Log.d(TAG, "  Загружаем аватар по URL: $avatarUrl")
                Glide.with(binding.root.context)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(binding.ivParticipantAvatar)
            } else {
                Log.d(TAG, "  URL аватара пустой, используем иконку по умолчанию")
                binding.ivParticipantAvatar.setImageResource(R.drawable.ic_profile)
            }

            binding.root.setOnClickListener {
                Log.d(TAG, "👆 Клик по участнику: ${participant.name} (ID: ${participant.id})")
                onParticipantClick(participant)
            }
        }
    }
}
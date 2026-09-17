package com.fitnesslemon.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Trainer
import com.fitnesslemon.app.databinding.ItemTrainerBinding

class TrainerAdapter(
    private val trainers: List<Trainer>,
    private val onItemClick: (Trainer) -> Unit,
    private val onChatClick: (Trainer) -> Unit
) : RecyclerView.Adapter<TrainerAdapter.TrainerViewHolder>() {

    class TrainerViewHolder(val binding: ItemTrainerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrainerViewHolder {
        val binding = ItemTrainerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrainerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrainerViewHolder, position: Int) {
        val trainer = trainers[position]
        holder.binding.apply {
            tvTrainerName.text = trainer.name
            tvSpecialization.text = trainer.specialization
            tvExperience.text = "Опыт: ${trainer.experience}"

            // Показываем рейтинг если есть
            if (trainer.rating > 0) {
                tvRating.text = "⭐ ${String.format("%.1f", trainer.rating)}"
                tvRating.visibility = android.view.View.VISIBLE
            } else {
                tvRating.visibility = android.view.View.GONE
            }

            // ИСПРАВЛЕНО: Загружаем фото с отключением кэша
            if (!trainer.photo.isNullOrEmpty()) {
                // Добавляем timestamp к URL для обхода кэша
                val photoUrlWithTimestamp = if (trainer.photo.contains("?")) {
                    "${trainer.photo}&t=${System.currentTimeMillis()}"
                } else {
                    "${trainer.photo}?t=${System.currentTimeMillis()}"
                }

                Glide.with(root.context)
                    .load(photoUrlWithTimestamp)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(ivTrainerPhoto)
            } else {
                ivTrainerPhoto.setImageResource(R.drawable.ic_profile)
            }

            // Обработка клика по карточке
            root.setOnClickListener {
                onItemClick(trainer)
            }

            // Обработка клика по кнопке "Написать тренеру"
            btnChatWithTrainer.setOnClickListener {
                onChatClick(trainer)
            }
        }
    }

    override fun getItemCount() = trainers.size
}
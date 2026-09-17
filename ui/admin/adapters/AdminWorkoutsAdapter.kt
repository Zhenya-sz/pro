package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.AdminWorkout
import com.fitnesslemon.app.databinding.ItemAdminWorkoutBinding

class AdminWorkoutsAdapter(
    private val workouts: List<AdminWorkout>,
    private val onItemClick: (AdminWorkout) -> Unit,
    private val onEditClick: (AdminWorkout) -> Unit,
    private val onDeleteClick: (AdminWorkout) -> Unit
) : RecyclerView.Adapter<AdminWorkoutsAdapter.WorkoutViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val binding = ItemAdminWorkoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return WorkoutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        holder.bind(workouts[position])
    }

    override fun getItemCount() = workouts.size

    inner class WorkoutViewHolder(
        private val binding: ItemAdminWorkoutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(workout: AdminWorkout) {
            binding.apply {
                tvTitle.text = workout.title
                tvDateTime.text = "${workout.formattedDate} ${workout.formattedTime}"
                tvTrainer.text = "Тренер: ${workout.trainerName}"
                tvDuration.text = "${workout.duration} мин"
                tvParticipants.text = "${workout.currentParticipants}/${workout.maxParticipants}"

                // Статус
                when (workout.status) {
                    "upcoming" -> {
                        tvStatus.text = "Предстоит"
                        tvStatus.setTextColor(root.context.getColor(R.color.lemon_primary))
                    }
                    "ongoing" -> {
                        tvStatus.text = "Идет"
                        tvStatus.setTextColor(root.context.getColor(R.color.lemon_accent))
                    }
                    "completed" -> {
                        tvStatus.text = "Завершено"
                        tvStatus.setTextColor(root.context.getColor(R.color.lemon_text_secondary))
                    }
                }

                // Прогресс-бар заполненности
                val percentage = if (workout.maxParticipants > 0) {
                    (workout.currentParticipants * 100) / workout.maxParticipants
                } else 0
                progressBar.progress = percentage

                // Клики
                root.setOnClickListener {
                    onItemClick(workout)
                }

                btnEdit.setOnClickListener {
                    onEditClick(workout)
                }

                btnDelete.setOnClickListener {
                    onDeleteClick(workout)
                }
            }
        }
    }
}
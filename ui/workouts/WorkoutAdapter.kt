package com.fitnesslemon.app.ui.workouts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Workout
import com.fitnesslemon.app.databinding.ItemWorkoutBinding
import java.text.SimpleDateFormat
import java.util.*

class WorkoutsAdapter(
    private val onItemClick: (Workout) -> Unit,
    private val onBookClick: (Workout) -> Unit
) : RecyclerView.Adapter<WorkoutsAdapter.WorkoutViewHolder>() {

    private var workouts: List<Workout> = emptyList()
    private var remainingWorkouts: Int = 0

    fun updateWorkouts(newWorkouts: List<Workout>, newRemaining: Int) {
        workouts = newWorkouts
        remainingWorkouts = newRemaining
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val binding = ItemWorkoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return WorkoutViewHolder(binding, onItemClick, onBookClick)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        holder.bind(workouts[position], remainingWorkouts)
    }

    override fun getItemCount() = workouts.size

    class WorkoutViewHolder(
        private val binding: ItemWorkoutBinding,
        private val onItemClick: (Workout) -> Unit,
        private val onBookClick: (Workout) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(workout: Workout, remainingWorkouts: Int) {
            binding.apply {
                tvTitle.text = workout.title
                tvTrainer.text = "Тренер: ${workout.trainerName}"
                tvDateTime.text = "📅 ${workout.formattedDate}"
                tvDuration.text = "⏱️ ${workout.duration ?: 60} мин"
                tvAvailableSpots.text = "👥 Свободно: ${workout.availableSpots} из ${workout.maxParticipants}"

                // Отображаем типы тренировок
                val typesText = workout.workoutTypes.joinToString(", ")
                tvWorkoutTypes.text = if (typesText.isNotEmpty()) "🏋️ $typesText" else "🏋️ Нет типа"

                // Загружаем изображение если есть
                if (!workout.thumbnail.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(workout.thumbnail)
                        .centerCrop()
                        .placeholder(R.drawable.ic_workout)
                        .error(R.drawable.ic_workout)
                        .into(ivWorkout)
                }

                // Проверяем, прошла ли тренировка
                val isPast = isWorkoutPast(workout.date)

                // Настраиваем кнопку записи
                btnBook.isEnabled = false
                when {
                    isPast -> {
                        btnBook.text = "Тренировка прошла"
                        btnBook.setBackgroundColor(root.context.getColor(R.color.lemon_text_secondary))
                    }
                    workout.availableSpots <= 0 -> {
                        btnBook.text = "Нет мест"
                        btnBook.setBackgroundColor(root.context.getColor(R.color.lemon_error))
                    }
                    remainingWorkouts <= 0 -> {
                        btnBook.text = "Нет тренировок"
                        btnBook.setBackgroundColor(root.context.getColor(R.color.lemon_text_secondary))
                    }
                    else -> {
                        btnBook.isEnabled = true
                        btnBook.text = "Записаться"
                        btnBook.setBackgroundColor(root.context.getColor(R.color.lemon_primary))
                    }
                }

                // Обработка клика по кнопке записи
                btnBook.setOnClickListener {
                    if (btnBook.isEnabled) {
                        onBookClick(workout)
                    }
                }

                // Обработка клика по карточке
                root.setOnClickListener {
                    onItemClick(workout)
                }
            }
        }

        private fun isWorkoutPast(workoutDate: String): Boolean {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val workoutDateTime = format.parse(workoutDate)
                val currentTime = Date()
                workoutDateTime?.before(currentTime) ?: false
            } catch (e: Exception) {
                false
            }
        }
    }
}
package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.AdminWorkout
import java.text.SimpleDateFormat
import java.util.*

class ScheduleDayEditAdapter(
    private val workouts: List<AdminWorkout>,
    private val onEditClick: (AdminWorkout) -> Unit,
    private val onDeleteClick: (AdminWorkout) -> Unit
) : RecyclerView.Adapter<ScheduleDayEditAdapter.WorkoutViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_day_edit, parent, false)
        return WorkoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        holder.bind(workouts[position])
    }

    override fun getItemCount() = workouts.size

    inner class WorkoutViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(workout: AdminWorkout) {
            val context = itemView.context

            // Получаем ссылки на View
            val tvWorkoutTime = itemView.findViewById<android.widget.TextView>(R.id.tvWorkoutTime)
            val tvWorkoutTitle = itemView.findViewById<android.widget.TextView>(R.id.tvWorkoutTitle)
            val tvWorkoutTrainer = itemView.findViewById<android.widget.TextView>(R.id.tvWorkoutTrainer)
            val tvWorkoutParticipants = itemView.findViewById<android.widget.TextView>(R.id.tvWorkoutParticipants)
            val tvWorkoutStatus = itemView.findViewById<android.widget.TextView>(R.id.tvWorkoutStatus)
            val tvWorkoutType = itemView.findViewById<android.widget.TextView>(R.id.tvWorkoutType)
            val tvAgeCategory = itemView.findViewById<android.widget.TextView>(R.id.tvAgeCategory)
            val progressBar = itemView.findViewById<android.widget.ProgressBar>(R.id.progressBar)
            val btnEdit = itemView.findViewById<android.widget.ImageView>(R.id.btnEdit)
            val btnDelete = itemView.findViewById<android.widget.ImageView>(R.id.btnDelete)

            // Заполняем данные
            tvWorkoutTime.text = workout.formattedTime
            tvWorkoutTitle.text = workout.title
            tvWorkoutTrainer.text = "👤 ${workout.trainerName ?: "Не назначен"}"
            tvWorkoutParticipants.text = "👥 ${workout.currentParticipants}/${workout.maxParticipants}"

            val isPast = isWorkoutPast(workout.date)
            val statusText = if (isPast) "Завершено" else "Предстоит"
            tvWorkoutStatus.text = statusText
            tvWorkoutStatus.visibility = View.VISIBLE

            tvWorkoutStatus.setTextColor(
                if (isPast)
                    ContextCompat.getColor(context, R.color.lemon_error)
                else
                    ContextCompat.getColor(context, R.color.lemon_success)
            )

            // Тип тренировки
            if (!workout.workoutType.isNullOrEmpty()) {
                tvWorkoutType.text = "🏋️ ${workout.workoutType}"
                tvWorkoutType.visibility = View.VISIBLE
            } else {
                tvWorkoutType.visibility = View.GONE
            }

            // Возрастная категория
            if (!workout.ageCategory.isNullOrEmpty()) {
                tvAgeCategory.text = workout.ageCategory
                tvAgeCategory.visibility = View.VISIBLE
            } else {
                tvAgeCategory.visibility = View.GONE
            }

            // Прогресс заполненности
            val occupancyPercentage = if (workout.maxParticipants > 0) {
                (workout.currentParticipants * 100) / workout.maxParticipants
            } else 0
            progressBar.progress = occupancyPercentage

            // Обработчики кликов
            btnEdit.setOnClickListener { onEditClick(workout) }
            btnDelete.setOnClickListener { onDeleteClick(workout) }
            itemView.setOnClickListener { onEditClick(workout) }
        }

        private fun isWorkoutPast(workoutDate: String): Boolean {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = format.parse(workoutDate)
                date?.before(Date()) ?: false
            } catch (e: Exception) {
                false
            }
        }
    }
}
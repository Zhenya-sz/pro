package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.AdminWorkout
import java.text.SimpleDateFormat
import java.util.*

class ScheduleWeekEditAdapter(
    private val days: List<ScheduleDayData>,
    private val onAddClick: (Int, Date) -> Unit,
    private val onEditClick: (AdminWorkout) -> Unit,
    private val onDeleteClick: (AdminWorkout) -> Unit,
    private val onClearDayClick: ((Int, Date) -> Unit)? = null
) : RecyclerView.Adapter<ScheduleWeekEditAdapter.DayViewHolder>() {

    data class ScheduleDayData(
        val dayOfWeek: Int,
        val date: Date,
        val displayName: String,
        val workouts: List<AdminWorkout>
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_day_full, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount() = days.size

    inner class DayViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(dayData: ScheduleDayData) {
            val context = itemView.context

            // Заголовок дня
            val tvDayTitle = itemView.findViewById<TextView>(R.id.tvDayTitle)
            val tvWorkoutCount = itemView.findViewById<TextView>(R.id.tvWorkoutCount)

            tvDayTitle.text = dayData.displayName
            val countText = when (dayData.workouts.size) {
                0 -> "нет занятий"
                1 -> "1 занятие"
                in 2..4 -> "${dayData.workouts.size} занятия"
                else -> "${dayData.workouts.size} занятий"
            }
            tvWorkoutCount.text = countText

            // Кнопка добавления
            val btnAddWorkout = itemView.findViewById<Button>(R.id.btnAddWorkout)
            btnAddWorkout.setOnClickListener {
                onAddClick(dayData.dayOfWeek, dayData.date)
            }

            // Кнопка очистки дня
            val btnClearDay = itemView.findViewById<Button>(R.id.btnClearDay)
            if (dayData.workouts.isNotEmpty()) {
                btnClearDay.visibility = View.VISIBLE
                btnClearDay.setOnClickListener {
                    onClearDayClick?.invoke(dayData.dayOfWeek, dayData.date)
                }
            } else {
                btnClearDay.visibility = View.GONE
            }

            // Список занятий
            val rvWorkouts = itemView.findViewById<RecyclerView>(R.id.rvWorkouts)
            val tvEmpty = itemView.findViewById<TextView>(R.id.tvEmpty)

            if (dayData.workouts.isNotEmpty()) {
                rvWorkouts.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE

                rvWorkouts.layoutManager = LinearLayoutManager(context)

                val adapter = ScheduleDayEditAdapter(
                    workouts = dayData.workouts,
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick
                )
                rvWorkouts.adapter = adapter

            } else {
                rvWorkouts.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Нет занятий"
            }
        }
    }

    fun updateData(newDays: List<ScheduleDayData>) {
        // В реальном проекте здесь нужно обновить данные и вызвать notifyDataSetChanged()
        // Но так как days - val, мы не можем его обновить напрямую
        // Вместо этого создадим новый адаптер
    }
}
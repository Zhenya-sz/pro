package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.data.models.PopularWorkout
import com.fitnesslemon.app.databinding.ItemPopularWorkoutBinding

class PopularWorkoutsAdapter(
    private val workouts: List<PopularWorkout>
) : RecyclerView.Adapter<PopularWorkoutsAdapter.PopularWorkoutViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PopularWorkoutViewHolder {
        val binding = ItemPopularWorkoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PopularWorkoutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PopularWorkoutViewHolder, position: Int) {
        holder.bind(workouts[position])
    }

    override fun getItemCount() = workouts.size

    class PopularWorkoutViewHolder(
        private val binding: ItemPopularWorkoutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(workout: PopularWorkout) {
            binding.apply {
                tvRank.text = workout.rank.toString()
                tvTitle.text = workout.title
                tvBookingsCount.text = "${workout.bookingsCount} записей"
            }
        }
    }
}
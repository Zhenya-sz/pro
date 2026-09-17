package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Trainer
import com.fitnesslemon.app.databinding.ItemTrainerSelectionBinding

class TrainerSelectionAdapter(
    private val trainers: List<Trainer>,
    private val onTrainerSelected: (Trainer) -> Unit
) : RecyclerView.Adapter<TrainerSelectionAdapter.TrainerViewHolder>(), Filterable {

    private var filteredList: List<Trainer> = trainers
    private var originalList: List<Trainer> = trainers

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrainerViewHolder {
        val binding = ItemTrainerSelectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrainerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrainerViewHolder, position: Int) {
        holder.bind(filteredList[position])
    }

    override fun getItemCount() = filteredList.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchQuery = constraint?.toString()?.lowercase() ?: ""
                filteredList = if (searchQuery.isEmpty()) {
                    originalList
                } else {
                    originalList.filter {
                        it.name.lowercase().contains(searchQuery)
                    }
                }
                val results = FilterResults()
                results.values = filteredList
                results.count = filteredList.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as List<Trainer>
                notifyDataSetChanged()
            }
        }
    }

    inner class TrainerViewHolder(
        private val binding: ItemTrainerSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(trainer: Trainer) {
            binding.apply {
                tvTrainerName.text = trainer.name
                tvSpecialization.text = trainer.specialization

                if (!trainer.photo.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(trainer.photo)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .into(ivTrainerPhoto)
                } else {
                    ivTrainerPhoto.setImageResource(R.drawable.ic_profile)
                }

                root.setOnClickListener {
                    onTrainerSelected(trainer)
                }
            }
        }
    }
}
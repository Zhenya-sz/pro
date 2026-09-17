package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.data.models.Rating
import com.fitnesslemon.app.databinding.ItemRatingBinding
import android.view.View
class RatingsAdapter(
    private val onApprove: (Rating) -> Unit,
    private val onDelete: (Rating) -> Unit
) : ListAdapter<Rating, RatingsAdapter.RatingViewHolder>(RatingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RatingViewHolder {
        val binding = ItemRatingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RatingViewHolder(binding, onApprove, onDelete)
    }

    override fun onBindViewHolder(holder: RatingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RatingViewHolder(
        private val binding: ItemRatingBinding,
        private val onApprove: (Rating) -> Unit,
        private val onDelete: (Rating) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rating: Rating) {
            binding.apply {
                tvUserName.text = rating.userName
                tvRating.text = "⭐".repeat(rating.rating) + " (${rating.rating})"
                tvComment.text = rating.comment
                tvDate.text = rating.date

                btnApprove.visibility = if (rating.isApproved) View.GONE else View.VISIBLE
                btnApprove.setOnClickListener { onApprove(rating) }
                btnDelete.setOnClickListener { onDelete(rating) }
            }
        }
    }

    class RatingDiffCallback : DiffUtil.ItemCallback<Rating>() {
        override fun areItemsTheSame(oldItem: Rating, newItem: Rating): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Rating, newItem: Rating): Boolean = oldItem == newItem
    }
}
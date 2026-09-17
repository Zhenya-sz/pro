package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.News
import com.fitnesslemon.app.databinding.ItemAdminNewsBinding

class AdminNewsAdapter(
    private val onEditClick: (News) -> Unit,
    private val onDeleteClick: (News) -> Unit
) : ListAdapter<News, AdminNewsAdapter.ViewHolder>(NewsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onEditClick, onDeleteClick)
    }

    class ViewHolder(private val binding: ItemAdminNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(news: News, onEdit: (News) -> Unit, onDelete: (News) -> Unit) {
            binding.apply {
                tvTitle.text = news.title
                tvDate.text = news.formattedDate ?: ""
                tvExcerpt.text = news.excerpt ?: ""

                if (!news.thumbnail.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(news.thumbnail)
                        .placeholder(R.drawable.ic_news)
                        .into(ivThumbnail)
                } else {
                    ivThumbnail.setImageResource(R.drawable.ic_news)
                }

                btnEdit.setOnClickListener { onEdit(news) }
                btnDelete.setOnClickListener { onDelete(news) }
            }
        }
    }

    class NewsDiffCallback : DiffUtil.ItemCallback<News>() {
        override fun areItemsTheSame(oldItem: News, newItem: News) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: News, newItem: News) = oldItem == newItem
    }
}
package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.ItemNewsMediaBinding

class NewsMediaAdapter(
    private val items: List<MediaItem>,
    private val onDelete: (MediaItem) -> Unit,
    private val onPlay: (MediaItem) -> Unit
) : RecyclerView.Adapter<NewsMediaAdapter.ViewHolder>() {

    data class MediaItem(val url: String, val type: String) // "image" или "video"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNewsMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onDelete, onPlay)
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemNewsMediaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MediaItem, onDelete: (MediaItem) -> Unit, onPlay: (MediaItem) -> Unit) {
            if (item.type == "image") {
                Glide.with(binding.root).load(item.url).centerCrop().into(binding.ivThumb)
                binding.btnPlay.visibility = android.view.View.GONE
            } else {
                // Для видео показываем иконку play или первый кадр (пока просто иконка)
                binding.ivThumb.setImageResource(R.drawable.ic_news) // placeholder
                binding.btnPlay.visibility = android.view.View.VISIBLE
            }

            binding.btnDelete.setOnClickListener { onDelete(item) }
            binding.btnPlay.setOnClickListener { onPlay(item) }
        }
    }
}
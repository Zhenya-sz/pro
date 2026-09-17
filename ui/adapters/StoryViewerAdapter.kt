package com.fitnesslemon.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.databinding.ItemStoryViewerBinding
import com.fitnesslemon.app.data.models.Story

class StoryViewerAdapter(
    private val stories: List<Story>,
    private val onStoryClick: (Story) -> Unit
) : RecyclerView.Adapter<StoryViewerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStoryViewerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onStoryClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(stories[position])
    }

    override fun getItemCount() = stories.size

    class ViewHolder(
        private val binding: ItemStoryViewerBinding,
        private val onStoryClick: (Story) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(story: Story) {
            binding.apply {
                // Загружаем изображение
                if (!story.mediaUrl.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(story.mediaUrl)
                        .into(ivStory)
                }
                
                // Имя пользователя
                tvUserName.text = story.userName
                
                // Время создания
                tvTime.text = story.createdAt // Можно отформатировать
                
                root.setOnClickListener {
                    onStoryClick(story)
                }
            }
        }
    }
}
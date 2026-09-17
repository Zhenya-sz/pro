package com.fitnesslemon.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Story
import com.fitnesslemon.app.databinding.ItemStoryBinding

class StoryAdapter(
    private val stories: List<Story>,
    private val onStoryClick: (Story) -> Unit
) : RecyclerView.Adapter<StoryAdapter.StoryViewHolder>() {

    // ✅ Кэш для подсчета непросмотренных сторис по пользователям
    private val unviewedCountCache = mutableMapOf<Int, Int>()

    // Оптимизированные настройки для аватаров
    private val avatarOptions = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .format(DecodeFormat.PREFER_RGB_565)
        .override(64, 64)
        .circleCrop()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryViewHolder {
        val binding = ItemStoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StoryViewHolder(binding, avatarOptions, onStoryClick, unviewedCountCache)
    }

    override fun onBindViewHolder(holder: StoryViewHolder, position: Int) {
        holder.bind(stories[position])
    }

    override fun getItemCount() = stories.size

    /**
     * ✅ Обновляет кэш количества непросмотренных сторис для каждого пользователя
     * Вызывается из HomeFragment при обновлении списка
     */
    fun updateStoryCounts(allStories: List<Story>) {
        unviewedCountCache.clear()

        // Группируем по пользователю и считаем непросмотренные
        val grouped = allStories.groupBy { it.userId }
        grouped.forEach { (userId, userStories) ->
            val unviewedCount = userStories.count { it.isViewed == 0 }
            if (unviewedCount > 0) {
                unviewedCountCache[userId] = unviewedCount
            }
        }
        notifyDataSetChanged()
    }

    class StoryViewHolder(
        private val binding: ItemStoryBinding,
        private val avatarOptions: RequestOptions,
        private val onStoryClick: (Story) -> Unit,
        private val unviewedCountCache: MutableMap<Int, Int>
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(story: Story) {
            binding.apply {
                val userName = story.userName
                tvTrainerName.text = if (userName.length > 12) {
                    userName.take(10) + "..."
                } else {
                    userName
                }

                val avatarUrl = story.userAvatar
                if (!avatarUrl.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(avatarUrl)
                        .apply(avatarOptions)
                        .placeholder(R.drawable.ic_trainer)
                        .error(R.drawable.ic_trainer)
                        .into(ivAvatar)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_trainer)
                }

                val isViewed = story.isViewed == 1

                // ✅ Обновляем границу аватара
                if (!isViewed) {
                    ivAvatar.setBorderColor(root.context.getColor(R.color.lemon_primary))
                    ivAvatar.setBorderWidth(3)
                } else {
                    ivAvatar.setBorderColor(root.context.getColor(R.color.lemon_divider))
                    ivAvatar.setBorderWidth(2)
                }

                // ✅ Показываем количество НЕПРОСМОТРЕННЫХ сторис
                val userId = story.userId
                val count = unviewedCountCache[userId] ?: 0

                // ✅ Проверяем, есть ли у нас TextView для бейджа
                // Если в item_story.xml нет tvStoryCount, показываем только цветную точку
                try {
                    if (count > 0) {
                        tvStoryCount.text = count.toString()
                        tvStoryCount.visibility = View.VISIBLE
                    } else {
                        tvStoryCount.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    // Если tvStoryCount нет в layout, игнорируем
                }

                root.setOnClickListener {
                    onStoryClick(story)
                }
            }
        }
    }
}
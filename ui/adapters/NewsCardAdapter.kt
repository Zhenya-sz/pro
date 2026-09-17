package com.fitnesslemon.app.ui.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.News
import com.fitnesslemon.app.databinding.ItemNewsCardBinding
import com.google.android.material.tabs.TabLayoutMediator

class NewsCardAdapter(
    private val onLikeClick: (News) -> Unit,
    private val onCommentClick: (News) -> Unit,
    private val onShareClick: (News) -> Unit,
    private val onSaveClick: (News) -> Unit,
    private val onReadMoreClick: (News) -> Unit,
    private val onMediaClick: (News, Int) -> Unit
) : ListAdapter<News, NewsCardAdapter.NewsCardViewHolder>(NewsDiffCallback()) {

    companion object {
        private const val TAG = "NewsCardAdapter"
    }

    private var likedNewsIds = mutableSetOf<Int>()
    private var savedNewsIds = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsCardViewHolder {
        val binding = ItemNewsCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NewsCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsCardViewHolder, position: Int) {
        val news = getItem(position)
        val isLiked = likedNewsIds.contains(news.id)
        val isSaved = savedNewsIds.contains(news.id)
        holder.bind(news, isLiked, isSaved)
    }

    fun setLikedNews(ids: Set<Int>) {
        likedNewsIds = ids.toMutableSet()
        notifyDataSetChanged()
    }

    fun setSavedNews(ids: Set<Int>) {
        savedNewsIds = ids.toMutableSet()
        notifyDataSetChanged()
    }

    fun toggleLike(newsId: Int) {
        if (likedNewsIds.contains(newsId)) {
            likedNewsIds.remove(newsId)
        } else {
            likedNewsIds.add(newsId)
        }
        notifyItemChanged(getPosition(newsId))
    }

    fun toggleSave(newsId: Int) {
        if (savedNewsIds.contains(newsId)) {
            savedNewsIds.remove(newsId)
        } else {
            savedNewsIds.add(newsId)
        }
        notifyItemChanged(getPosition(newsId))
    }

    private fun getPosition(newsId: Int): Int {
        for (i in 0 until itemCount) {
            if (getItem(i).id == newsId) {
                return i
            }
        }
        return 0
    }

    inner class NewsCardViewHolder(
        private val binding: ItemNewsCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentNews: News? = null
        private var mediaAdapter: NewsMediaPagerAdapter? = null

        fun bind(news: News, isLiked: Boolean, isSaved: Boolean) {
            currentNews = news

            Log.d(TAG, "========== bind ==========")
            Log.d(TAG, "News ID: ${news.id}")
            Log.d(TAG, "Title: ${news.title}")
            Log.d(TAG, "Images: ${news.images}")
            Log.d(TAG, "Videos: ${news.videos}")
            Log.d(TAG, "Liked: $isLiked, Saved: $isSaved")

            // Заголовок
            binding.tvTitle.text = news.title ?: "Без названия"

            // Контент (первые 3 строки)
            val content = news.content ?: ""
            binding.tvContent.text = content
            binding.tvContent.maxLines = 3
            binding.tvContent.ellipsize = android.text.TextUtils.TruncateAt.END

            // "Читать далее" если текст длинный
            binding.tvReadMore.visibility = if (content.length > 200) {
                View.VISIBLE
            } else {
                View.GONE
            }

            binding.tvReadMore.setOnClickListener {
                news.let { onReadMoreClick(it) }
            }

            // Автор
            binding.tvAuthorName.text = news.authorName ?: "Администратор"

            // Дата
            binding.tvDate.text = news.formattedDate ?: ""

            // Просмотры
            binding.tvViewsCount.text = "👁 ${news.viewsCount}"

            // Лайки
            binding.tvLikeCount.text = news.likesCount.toString()

            // Комментарии
            binding.tvCommentCount.text = news.commentsCount.toString()

            // Кнопка лайка
            binding.btnLike.setImageResource(
                if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like
            )
            binding.btnLike.setOnClickListener {
                Log.d(TAG, "❤️ Лайк нажат для новости ID: ${news.id}")
                news.let { onLikeClick(it) }
            }

            // Кнопка комментариев
            binding.btnComment.setOnClickListener {
                Log.d(TAG, "💬 Комментарии для новости ID: ${news.id}")
                news.let { onCommentClick(it) }
            }

            // Кнопка поделиться
            binding.btnShare.setOnClickListener {
                Log.d(TAG, "↗️ Поделиться новостью ID: ${news.id}")
                news.let { onShareClick(it) }
            }

            // Кнопка сохранить
            binding.btnSave.setImageResource(
                if (isSaved) R.drawable.ic_save_filled else R.drawable.ic_save
            )
            binding.btnSave.setOnClickListener {
                Log.d(TAG, "🔖 Сохранить новость ID: ${news.id}")
                news.let { onSaveClick(it) }
            }

            // Аватар автора
            val avatarUrl = news.authorAvatar
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(binding.ivAuthorAvatar.context)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(binding.ivAuthorAvatar)
            } else {
                binding.ivAuthorAvatar.setImageResource(R.drawable.ic_profile)
            }

            // Настройка карусели медиа
            setupMediaCarousel(news)
        }

        private fun setupMediaCarousel(news: News) {
            Log.d(TAG, "========== setupMediaCarousel ==========")
            Log.d(TAG, "News ID: ${news.id}")
            Log.d(TAG, "Images: ${news.images}")
            Log.d(TAG, "Videos: ${news.videos}")

            // Собираем все медиа
            val mediaItems = mutableListOf<NewsMediaPagerAdapter.MediaItem>()

            news.images?.forEach { url ->
                Log.d(TAG, "📷 Добавляем изображение: $url")
                mediaItems.add(
                    NewsMediaPagerAdapter.MediaItem(
                        url = url,
                        type = "image"
                    )
                )
            }

            news.videos?.forEach { url ->
                Log.d(TAG, "🎬 Добавляем видео: $url")
                mediaItems.add(
                    NewsMediaPagerAdapter.MediaItem(
                        url = url,
                        type = "video",
                        duration = 0
                    )
                )
            }

            Log.d(TAG, "Всего медиа: ${mediaItems.size}")

            if (mediaItems.isEmpty()) {
                Log.w(TAG, "⚠️ Нет медиа для новости ID: ${news.id}")
                binding.viewPagerMedia.visibility = View.GONE
                binding.tabLayoutIndicator.visibility = View.GONE
                return
            }

            binding.viewPagerMedia.visibility = View.VISIBLE
            binding.tabLayoutIndicator.visibility = View.VISIBLE

            mediaAdapter = NewsMediaPagerAdapter(
                mediaItems = mediaItems,
                onMediaClick = { item ->
                    Log.d(TAG, "🖱️ Клик по медиа: ${item.url}")
                    currentNews?.let { onMediaClick(it, 0) }
                },
                onDoubleTap = { item ->
                    Log.d(TAG, "🔄 Двойной тап по медиа: ${item.url}")
                    currentNews?.let { onLikeClick(it) }
                }
            )

            binding.viewPagerMedia.adapter = mediaAdapter
            Log.d(TAG, "✅ Адаптер установлен")

            // Настройка индикатора через TabLayoutMediator
            try {
                TabLayoutMediator(binding.tabLayoutIndicator, binding.viewPagerMedia) { _, _ ->
                    // Можно установить иконку или текст, но оставляем пустым для точечек
                }.attach()
                Log.d(TAG, "✅ TabLayoutMediator прикреплен")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка прикрепления TabLayoutMediator: ${e.message}")
            }

            // Если одно медиа - скрываем индикатор
            if (mediaItems.size <= 1) {
                binding.tabLayoutIndicator.visibility = View.GONE
                Log.d(TAG, "👁️ Одно медиа - скрываем индикатор")
            }

            // ✅ ИСПРАВЛЕНО: Обработка смены страницы с post()
            binding.viewPagerMedia.registerOnPageChangeCallback(
                object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        Log.d(TAG, "📄 Страница изменена: $position")
                        // Отложенное выполнение для избежания конфликтов
                        binding.viewPagerMedia.post {
                            mediaAdapter?.setCurrentItem(position)
                        }
                    }
                }
            )
        }

        fun pauseVideo() {
            mediaAdapter?.pauseAll()
            Log.d(TAG, "⏸️ Видео на паузу")
        }

        fun releaseMedia() {
            mediaAdapter?.releaseAll()
            mediaAdapter = null
            Log.d(TAG, "🗑️ Медиа освобождены")
        }
    }

    class NewsDiffCallback : DiffUtil.ItemCallback<News>() {
        override fun areItemsTheSame(oldItem: News, newItem: News): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: News, newItem: News): Boolean {
            return oldItem == newItem
        }
    }
}
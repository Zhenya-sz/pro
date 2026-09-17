package com.fitnesslemon.app.ui.adapters

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.fitnesslemon.app.R
import com.fitnesslemon.app.utils.VideoPlayerManager
import com.google.android.exoplayer2.ui.StyledPlayerView

class NewsMediaPagerAdapter(
    private val mediaItems: List<MediaItem>,
    private val onMediaClick: (MediaItem) -> Unit,
    private val onDoubleTap: (MediaItem) -> Unit
) : RecyclerView.Adapter<NewsMediaPagerAdapter.MediaViewHolder>() {

    companion object {
        private const val TAG = "NewsMediaPagerAdapter"
    }

    data class MediaItem(
        val url: String,
        val type: String, // "image" или "video"
        val duration: Int = 0 // длительность в секундах
    )

    private var currentPlayingPosition = -1
    private var currentViewHolder: MediaViewHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_media_pager, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = mediaItems[position]
        Log.d(TAG, "📄 bind: position=$position, type=${item.type}, url=${item.url}")
        holder.bind(item, position)
    }

    override fun getItemCount(): Int = mediaItems.size

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivMedia: ImageView = itemView.findViewById(R.id.ivMedia)
        private val playerView: StyledPlayerView = itemView.findViewById(R.id.playerView)
        private val btnPlayPause: ImageButton = itemView.findViewById(R.id.btnPlayPause)
        private val progressLoading: ProgressBar = itemView.findViewById(R.id.progressLoading)
        private val tvVideoIndicator: TextView = itemView.findViewById(R.id.tvVideoIndicator)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)

        private var currentItem: MediaItem? = null
        private var isPlaying = false
        private var lastClickTime = 0L

        init {
            itemView.setOnClickListener {
                Log.d(TAG, "🖱️ Клик по ViewHolder")
                currentItem?.let { onMediaClick(it) }
            }

            itemView.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    val time = System.currentTimeMillis()
                    if (time - lastClickTime < 300) {
                        Log.d(TAG, "🔄 Двойной тап!")
                        currentItem?.let { onDoubleTap(it) }
                        lastClickTime = 0
                        return@setOnTouchListener true
                    }
                    lastClickTime = time
                }
                false
            }

            btnPlayPause.setOnClickListener {
                Log.d(TAG, "▶️ Кнопка Play/Pause нажата")
                togglePlayback()
            }
        }

        fun bind(item: MediaItem, position: Int) {
            currentItem = item
            val context = itemView.context

            Log.d(TAG, "📄 bind: position=$position, type=${item.type}, url=${item.url}")

            tvVideoIndicator.visibility = View.GONE
            tvDuration.visibility = View.GONE
            btnPlayPause.visibility = View.GONE
            progressLoading.visibility = View.GONE

            if (item.type == "video") {
                Log.d(TAG, "🎬 Загружаем видео: ${item.url}")
                tvVideoIndicator.visibility = View.VISIBLE
                if (item.duration > 0) {
                    tvDuration.visibility = View.VISIBLE
                    tvDuration.text = formatDuration(item.duration)
                }

                // Показываем превью видео через Glide
                Glide.with(context)
                    .load(item.url)
                    .apply(RequestOptions.centerCropTransform())
                    .thumbnail(0.25f)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(ivMedia)

                playerView.visibility = View.VISIBLE
                playerView.player = null

                if (position != currentPlayingPosition) {
                    stopPlayback()
                }

                btnPlayPause.visibility = View.VISIBLE
                btnPlayPause.setImageResource(R.drawable.ic_play_circle)

                if (position == currentPlayingPosition && isPlaying) {
                    startPlayback()
                }

                try {
                    val uri = Uri.parse(item.url)
                    Log.d(TAG, "🎬 Инициализируем плеер для: $uri")
                    VideoPlayerManager.initializePlayer(context, playerView, uri.toString())
                    VideoPlayerManager.setPlayerListener(
                        onBuffering = { isBuffering ->
                            Log.d(TAG, "⏳ Буферизация: $isBuffering")
                            progressLoading.visibility = if (isBuffering) View.VISIBLE else View.GONE
                        },
                        onReady = {
                            Log.d(TAG, "✅ Плеер готов")
                            if (position == currentPlayingPosition && isPlaying) {
                                playerView.player?.play()
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка инициализации плеера: ${e.message}", e)
                }

            } else {
                // ИЗОБРАЖЕНИЕ
                Log.d(TAG, "📷 Загружаем изображение: ${item.url}")
                ivMedia.visibility = View.VISIBLE
                playerView.visibility = View.GONE
                btnPlayPause.visibility = View.GONE

                Glide.with(context)
                    .load(item.url)
                    .apply(RequestOptions.centerCropTransform())
                    .thumbnail(0.25f)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(ivMedia)
            }
        }

        private fun formatDuration(seconds: Int): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return String.format("%d:%02d", mins, secs)
        }

        fun startPlayback() {
            if (currentItem?.type == "video") {
                Log.d(TAG, "▶️ Запуск воспроизведения")
                isPlaying = true
                btnPlayPause.setImageResource(R.drawable.ic_pause_circle)
                VideoPlayerManager.play()
                if (adapterPosition == currentPlayingPosition) {
                    currentViewHolder = this
                }
            }
        }

        fun stopPlayback() {
            Log.d(TAG, "⏸️ Остановка воспроизведения")
            isPlaying = false
            btnPlayPause.setImageResource(R.drawable.ic_play_circle)
            VideoPlayerManager.pause()
            if (this == currentViewHolder) {
                currentViewHolder = null
            }
        }

        fun togglePlayback() {
            if (currentItem?.type == "video") {
                if (isPlaying) {
                    stopPlayback()
                } else {
                    startPlayback()
                }
            }
        }

        fun setCurrentPlaying(position: Int) {
            Log.d(TAG, "🎯 setCurrentPlaying: position=$position, currentAdapterPosition=${adapterPosition}")
            if (position == adapterPosition && currentItem?.type == "video") {
                currentPlayingPosition = position
                currentViewHolder = this
                if (!isPlaying) {
                    startPlayback()
                }
            } else if (adapterPosition == currentPlayingPosition && position != adapterPosition) {
                stopPlayback()
                currentPlayingPosition = -1
                currentViewHolder = null
            }
        }

        fun releasePlayer() {
            Log.d(TAG, "🗑️ Освобождение плеера")
            VideoPlayerManager.release()
            playerView.player = null
        }

        fun isVideo(): Boolean = currentItem?.type == "video"
        fun isCurrentlyPlaying(): Boolean = isPlaying
    }

    fun setCurrentItem(position: Int) {
        Log.d(TAG, "🎯 setCurrentItem: position=$position, currentPlayingPosition=$currentPlayingPosition")
        if (position != currentPlayingPosition) {
            currentViewHolder?.stopPlayback()
            currentViewHolder = null
            currentPlayingPosition = position
            notifyItemChanged(position)
        }
    }

    fun pauseAll() {
        Log.d(TAG, "⏸️ Пауза всех видео")
        currentViewHolder?.stopPlayback()
        currentPlayingPosition = -1
        currentViewHolder = null
    }

    fun releaseAll() {
        Log.d(TAG, "🗑️ Освобождение всех видео")
        pauseAll()
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        Log.d(TAG, "♻️ ViewHolder переработан: position=${holder.adapterPosition}")
        if (holder.isCurrentlyPlaying()) {
            holder.stopPlayback()
        }
        if (holder == currentViewHolder) {
            currentViewHolder = null
            currentPlayingPosition = -1
        }
        holder.releasePlayer()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        Log.d(TAG, "🗑️ Adapter отсоединен от RecyclerView")
        releaseAll()
    }
}
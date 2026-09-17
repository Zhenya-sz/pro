package com.fitnesslemon.app.ui.stories

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.StoryReplyRequest
import com.fitnesslemon.app.databinding.ActivityStoryViewerBinding
import com.fitnesslemon.app.data.models.Story
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class StoryViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryViewerBinding

    private var stories: List<Story> = emptyList()
    private var currentIndex = 0
    private var isPaused = false
    private var isLiked = false
    private var likeCount = 0
    private var currentProgress = 0
    private var isTransitioning = false

    private var exoPlayer: ExoPlayer? = null
    private var isPlayerReady = false
    private var videoDurationMs: Long = 0
    private var isAutoPlayRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null

    private val videoCache = mutableMapOf<Int, File>()
    private var videoCacheDir: File? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> showLoading(true)
                Player.STATE_READY -> {
                    isPlayerReady = true
                    showLoading(false)
                    binding.ivPause.visibility = View.GONE
                    videoDurationMs = exoPlayer?.duration ?: 0
                    isTransitioning = false
                    if (!isPaused) {
                        exoPlayer?.playWhenReady = true
                        if (!isAutoPlayRunning) startAutoPlay()
                    }
                    preloadNextVideos()
                }
                Player.STATE_ENDED -> {
                    if (!isTransitioning) navigateToNext()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            isPlayerReady = false
            isTransitioning = false
            showLoading(false)
            Toast.makeText(this@StoryViewerActivity, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
            navigateToNext()
        }
    }

    companion object {
        const val EXTRA_STORIES = "stories"
        const val EXTRA_START_INDEX = "start_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoryViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        videoCacheDir = File(applicationContext.cacheDir, "story_videos")
        if (videoCacheDir?.exists() == false) {
            videoCacheDir?.mkdirs()
        }

        stories = intent.getParcelableArrayListExtra(EXTRA_STORIES) ?: emptyList()
        currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        if (stories.isEmpty()) {
            finish()
            return
        }

        setupUI()
        initPlayer()
        showStory(currentIndex)
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playWhenReady = false
            addListener(playerListener)
        }
        binding.playerView.player = exoPlayer
    }

    private fun setupUI() {
        binding.apply {
            btnClose.setOnClickListener { finish() }
            ivPrevArea.setOnClickListener { navigateToPrevious() }
            ivNextArea.setOnClickListener { navigateToNext() }
            ivPrevArea.setOnLongClickListener { togglePause(); true }
            ivNextArea.setOnLongClickListener { togglePause(); true }
            btnLike.setOnClickListener { toggleLike() }
            btnSend.setOnClickListener { sendReply() }
            etReply.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { sendReply(); true } else false
            }
            etReply.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) pauseAutoPlay() else resumeAutoPlay()
            }
            etReply.addTextChangedListener {
                if (etReply.hasFocus() && !isPaused) pauseAutoPlay()
            }
            etReply.setOnClickListener { pauseAutoPlay() }
            tvExpandText.setOnClickListener {
                if (binding.tvStoryText.maxLines == 3) {
                    binding.tvStoryText.maxLines = Int.MAX_VALUE
                    binding.tvStoryText.ellipsize = null
                    binding.tvExpandText.text = "Свернуть"
                    pauseAutoPlay()
                } else {
                    binding.tvStoryText.maxLines = 3
                    binding.tvStoryText.ellipsize = android.text.TextUtils.TruncateAt.END
                    binding.tvExpandText.text = "Еще"
                    resumeAutoPlay()
                }
            }
        }
    }

    private fun showStory(position: Int) {
        if (position < 0 || position >= stories.size || isTransitioning) return

        isTransitioning = true
        isAutoPlayRunning = false
        stopAutoPlay()
        isPlayerReady = false
        videoDurationMs = 0
        currentIndex = position
        currentProgress = 0

        updateProgressIndicators()
        showLoading(true)

        val story = stories[position]

        binding.apply {
            tvStoryTitle.text = story.title ?: ""
            tvStoryTitle.visibility = if (!story.title.isNullOrEmpty()) View.VISIBLE else View.GONE

            val text = story.text ?: ""
            if (text.isNotEmpty()) {
                tvStoryText.text = text
                tvStoryText.visibility = View.VISIBLE
                tvStoryText.maxLines = Int.MAX_VALUE
                tvStoryText.ellipsize = null
                tvStoryText.post {
                    if (tvStoryText.lineCount > 3) {
                        tvStoryText.maxLines = 3
                        tvStoryText.ellipsize = android.text.TextUtils.TruncateAt.END
                        tvExpandText.visibility = View.VISIBLE
                        tvExpandText.text = "Еще"
                    } else {
                        tvExpandText.visibility = View.GONE
                    }
                }
            } else {
                tvStoryText.visibility = View.GONE
                tvExpandText.visibility = View.GONE
            }

            tvUserName.text = story.userName
            if (!story.userAvatar.isNullOrEmpty()) {
                Glide.with(this@StoryViewerActivity)
                    .load(story.userAvatar).circleCrop()
                    .placeholder(R.drawable.ic_profile).error(R.drawable.ic_profile)
                    .into(ivUserAvatar)
            } else {
                ivUserAvatar.setImageResource(R.drawable.ic_profile)
            }

            tvTime.text = formatTime(story.createdAt)
            likeCount = story.likeCount
            isLiked = story.isLiked
            btnLike.setImageResource(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            tvLikeCount.text = likeCount.toString()
            etReply.setText("")
            etReply.clearFocus()

            if (story.type == "video") {
                ivStory.visibility = View.GONE
                playerView.visibility = View.VISIBLE

                val cachedFile = videoCache[story.id]
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 10240) {
                    exoPlayer?.apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(cachedFile)))
                        prepare()
                        playWhenReady = !isPaused
                    }
                } else {
                    story.mediaUrl?.let { url ->
                        exoPlayer?.apply {
                            setMediaItem(MediaItem.fromUri(url))
                            prepare()
                            playWhenReady = !isPaused
                        }
                        downloadToCache(story)
                    } ?: run {
                        isTransitioning = false
                        navigateToNext()
                    }
                }
            } else {
                playerView.visibility = View.GONE
                ivStory.visibility = View.VISIBLE
                if (!story.mediaUrl.isNullOrEmpty()) {
                    Glide.with(this@StoryViewerActivity).load(story.mediaUrl).into(ivStory)
                }
                isTransitioning = false
                showLoading(false)
                videoDurationMs = (story.duration ?: 5) * 1000L
                if (!isPaused && !isAutoPlayRunning) startAutoPlay()
            }
        }
    }

    private fun downloadToCache(story: Story) {
        val cacheDir = videoCacheDir ?: return
        val file = File(cacheDir, "video_${story.id}.mp4")
        if (file.exists() && file.length() > 10240) {
            videoCache[story.id] = file
            return
        }

        val mediaUrl = story.mediaUrl ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(mediaUrl).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful && response.body != null) {
                    val body = response.body!!
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(file)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }

                    outputStream.close()
                    inputStream.close()
                    response.close()

                    if (file.exists() && file.length() > 10240) {
                        videoCache[story.id] = file
                        cleanCache(200L * 1024L * 1024L)
                    }
                }
            } catch (_: Exception) {
                file.delete()
            }
        }
    }

    private fun preloadNextVideos() {
        for (i in 1..2) {
            val nextIndex = currentIndex + i
            if (nextIndex < stories.size) {
                val story = stories[nextIndex]
                if (story.type == "video") {
                    downloadToCache(story)
                }
            }
        }
    }

    private fun cleanCache(maxSize: Long) {
        try {
            val cacheDir = videoCacheDir ?: return
            val files = cacheDir.listFiles() ?: return
            if (files.isEmpty()) return

            var totalSize = 0L
            for (f in files) {
                totalSize += f.length()
            }

            val sorted = files.sortedBy { it.lastModified() }
            for (file in sorted) {
                if (totalSize <= maxSize) break
                totalSize -= file.length()
                file.delete()
            }
        } catch (_: Exception) {}
    }

    private fun showLoading(show: Boolean) {
        binding.loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateProgressIndicators() {
        binding.progressContainer.removeAllViews()
        stories.forEachIndexed { index, _ ->
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                max = 100
                progress = when {
                    index < currentIndex -> 100
                    index == currentIndex -> currentProgress
                    else -> 0
                }
                progressTintList = ContextCompat.getColorStateList(
                    this@StoryViewerActivity,
                    if (index <= currentIndex) R.color.lemon_primary else R.color.lemon_divider
                )
                progressBackgroundTintList = ContextCompat.getColorStateList(
                    this@StoryViewerActivity,
                    R.color.lemon_divider
                )
                setPadding(0, 0, if (index < stories.size - 1) 4 else 0, 0)
            }
            binding.progressContainer.addView(pb)
        }
    }

    private fun updateProgress(progress: Int) {
        currentProgress = progress.coerceIn(0, 100)
        if (currentIndex < binding.progressContainer.childCount) {
            (binding.progressContainer.getChildAt(currentIndex) as ProgressBar).progress = currentProgress
        }
    }

    private fun startAutoPlay() {
        if (isAutoPlayRunning || isPaused || !isPlayerReady || videoDurationMs <= 0) return
        stopAutoPlay()
        isAutoPlayRunning = true
        val interval = 50L
        val totalSteps = videoDurationMs / interval
        var step = 0L

        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (isPaused) {
                    isAutoPlayRunning = false
                    return
                }
                if (step >= totalSteps) {
                    isAutoPlayRunning = false
                    navigateToNext()
                    return
                }
                step++
                updateProgress(((step.toFloat() / totalSteps.toFloat()) * 100).toInt())
                handler.postDelayed(this, interval)
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun stopAutoPlay() {
        progressUpdateRunnable?.let { handler.removeCallbacks(it) }
        progressUpdateRunnable = null
        isAutoPlayRunning = false
    }

    private fun togglePause(): Boolean {
        isPaused = !isPaused
        if (isPaused) pauseAutoPlay() else resumeAutoPlay()
        return true
    }

    private fun pauseAutoPlay() {
        isPaused = true
        isAutoPlayRunning = false
        stopAutoPlay()
        exoPlayer?.playWhenReady = false
        binding.ivPause.visibility = View.VISIBLE
    }

    private fun resumeAutoPlay() {
        isPaused = false
        binding.ivPause.visibility = View.GONE
        exoPlayer?.playWhenReady = true
        if (isPlayerReady && videoDurationMs > 0 && !isAutoPlayRunning) startAutoPlay()
    }

    private fun navigateToNext() {
        if (isTransitioning) return
        if (currentIndex < stories.size - 1) showStory(currentIndex + 1) else finish()
    }

    private fun navigateToPrevious() {
        if (isTransitioning || currentIndex == 0) return
        showStory(currentIndex - 1)
    }

    private fun toggleLike() {
        val story = stories[currentIndex]
        isLiked = !isLiked
        likeCount = if (isLiked) likeCount + 1 else max(0, likeCount - 1)
        binding.btnLike.setImageResource(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
        binding.tvLikeCount.text = likeCount.toString()

        binding.btnLike.animate().scaleX(1.4f).scaleY(1.4f).setDuration(200).withEndAction {
            binding.btnLike.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        }.start()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = if (isLiked) {
                    ApiClient.apiService.likeStory("Bearer $token", story.id)
                } else {
                    ApiClient.apiService.unlikeStory("Bearer $token", story.id)
                }
                if (response.isSuccessful) {
                    response.body()?.let {
                        if (it.success) {
                            val data = it.data as? Map<*, *>
                            val count = (data?.get("like_count") as? Double)?.toInt()
                            if (count != null) {
                                likeCount = count
                                binding.tvLikeCount.text = count.toString()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun sendReply() {
        val text = binding.etReply.text.toString().trim()
        if (text.isEmpty()) return
        val story = stories[currentIndex]

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                binding.btnSend.isEnabled = false
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.sendStoryReply("Bearer $token", story.id, StoryReplyRequest(text))
                }
                binding.btnSend.isEnabled = true
                if (response.isSuccessful) {
                    binding.etReply.text.clear()
                    binding.etReply.clearFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(binding.etReply.windowToken, 0)
                    if (isPaused) resumeAutoPlay()
                }
            } catch (_: Exception) {
                binding.btnSend.isEnabled = true
            }
        }
    }

    private fun formatTime(dateTime: String): String {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = format.parse(dateTime) ?: return ""
            val diff = java.util.Date().time - date.time
            when {
                diff < 60000 -> "только что"
                diff < 3600000 -> "${diff / 60000} мин"
                diff < 86400000 -> "${diff / 3600000} ч"
                else -> "${diff / 86400000} дн"
            }
        } catch (_: Exception) {
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoPlay()
        exoPlayer?.release()
        exoPlayer = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()
        pauseAutoPlay()
    }

    override fun onResume() {
        super.onResume()
        if (!isPaused) resumeAutoPlay()
    }
}
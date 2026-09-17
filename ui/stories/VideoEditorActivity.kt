package com.fitnesslemon.app.ui.stories

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.ActivityVideoEditorBinding
import java.io.File
import java.io.FileOutputStream
import java.util.*

class VideoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEditorBinding
    private var videoUri: Uri? = null
    private var originalVideoPath: String? = null
    private var trimmedVideoPath: String? = null
    private var videoDuration: Int = 0
    private var maxDurationMs: Int = 0
    private var isVideoTrimmed = false
    private var convertedVideoPath: String? = null
    private var isConverting = false
    private var isProcessing = false
    private var currentSpeed = 1.0f
    private var currentRotation = 0
    private var musicFilePath: String? = null
    private var musicVolume = 0.5f
    private var originalVolume = 0.8f
    private var isMusicAdded = false

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null

    private var trimStartPercent = 0f
    private var trimEndPercent = 1f
    private var thumbnailsViews = mutableListOf<ImageView>()
    private var thumbnailsBitmaps = mutableListOf<Bitmap>()
    private var isDragging = false
    private var isSelectingStart = true
    private val THUMBNAIL_COUNT = 20
    private val THUMBNAIL_HEIGHT = 80
    private val MIN_SELECTION_WIDTH = 0.05f
    private var isSeeking = false
    private var thumbnailsWrapper: LinearLayout? = null
    private var isVideoPrepared = false

    // ✅ Текущий VideoView
    private var currentVideoView: VideoView? = null

    companion object {
        private const val TAG = "VideoEditor"
        private const val MAX_VIDEO_DURATION = 15
        private const val MIN_VIDEO_DURATION = 1
        private const val PROGRESS_UPDATE_INTERVAL = 300L
    }

    private val pickMusicLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d(TAG, "🎵 Выбрана музыка: $it")
            musicFilePath = getRealPathFromUri(it)
            if (musicFilePath != null) {
                isMusicAdded = true
                binding.tvMusicStatus.text = "✅ Музыка добавлена"
                binding.tvMusicStatus.setTextColor(Color.GREEN)
                Toast.makeText(this, "🎵 Музыка добавлена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Не удалось добавить музыку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentVideoView = binding.videoView

        val uriString = intent.getStringExtra("video_uri")
        if (uriString != null) {
            videoUri = Uri.parse(uriString)
            originalVideoPath = getRealPathFromUri(videoUri!!)
        }

        if (videoUri == null || originalVideoPath == null) {
            Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "📁 Видео URI: $videoUri")
        Log.d(TAG, "📁 Реальный путь: $originalVideoPath")

        setupUI()
        loadVideo()
        checkAndConvertVideo()
        loadThumbnails()
        startProgressUpdater()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        safeReleaseMediaPlayer()
        currentVideoView?.let {
            binding.videoContainer.removeView(it)
        }
        currentVideoView = null
        thumbnailsBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        thumbnailsBitmaps.clear()
    }

    // ============================================================
    // ✅ БЕЗОПАСНОЕ ОСВОБОЖДЕНИЕ
    // ============================================================

    private fun safeReleaseMediaPlayer() {
        try {
            currentVideoView?.stopPlayback()

            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Ошибка при освобождении MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
            isVideoPrepared = false
        }
    }

    private val VideoView.isPlaying: Boolean
        get() {
            return try {
                isPlaying
            } catch (e: Exception) {
                false
            }
        }

    private fun setupUI() {
        binding.apply {
            toolbar.setNavigationOnClickListener { finish() }

            layoutTrim.visibility = View.VISIBLE
            btnTrim.visibility = View.VISIBLE
            btnTrim.isEnabled = false

            seekBarProgress.max = 1000
            seekBarProgress.progress = 0
            seekBarProgress.visibility = View.VISIBLE

            seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && mediaPlayer != null && videoDuration > 0 && isVideoPrepared) {
                        val position = (progress / 1000f * videoDuration).toInt()
                        mediaPlayer?.seekTo(position)
                        updateThumbnailHighlight(position)
                        scrollToCurrentPosition(position)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = true
                    if (isVideoPrepared) {
                        mediaPlayer?.pause()
                        binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                    }
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = false
                    if (isVideoPrepared && currentVideoView?.isPlaying == true) {
                        mediaPlayer?.start()
                        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                    }
                }
            })

            seekBarVolume.progress = 80
            seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        originalVolume = progress / 100f
                        binding.tvVolumeValue.text = "${progress}%"
                        applyVolume()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            btnAddMusic.setOnClickListener {
                pickMusicLauncher.launch("audio/*")
            }

            btnRemoveMusic.setOnClickListener {
                musicFilePath = null
                isMusicAdded = false
                binding.tvMusicStatus.text = "❌ Музыка не добавлена"
                binding.tvMusicStatus.setTextColor(Color.RED)
                Toast.makeText(this@VideoEditorActivity, "Музыка удалена", Toast.LENGTH_SHORT).show()
            }

            btnRotate.setOnClickListener {
                rotateVideo()
            }

            btnSpeed.setOnClickListener {
                showSpeedDialog()
            }

            btnTrim.setOnClickListener {
                if (trimStartPercent > 0 || trimEndPercent < 1f) {
                    applyTrimFromThumbnails()
                } else {
                    Toast.makeText(this@VideoEditorActivity, "👆 Перемещайте края выделения на миниатюрах", Toast.LENGTH_LONG).show()
                }
            }

            btnUpload.setOnClickListener {
                uploadVideo()
            }

            btnPlayPause.setOnClickListener {
                currentVideoView?.let { view ->
                    if (isVideoPrepared) {
                        if (view.isPlaying) {
                            view.pause()
                            btnPlayPause.setImageResource(R.drawable.ic_play)
                        } else {
                            view.start()
                            btnPlayPause.setImageResource(R.drawable.ic_pause)
                        }
                    } else {
                        view.start()
                        btnPlayPause.setImageResource(R.drawable.ic_pause)
                    }
                }
            }

            layoutAdvanced.visibility = View.GONE

            btnAdvancedToggle.setOnClickListener {
                if (layoutAdvanced.visibility == View.VISIBLE) {
                    layoutAdvanced.visibility = View.GONE
                    btnAdvancedToggle.text = "🔧 Расширенные"
                } else {
                    layoutAdvanced.visibility = View.VISIBLE
                    btnAdvancedToggle.text = "🔧 Скрыть"
                }
            }

            updateTrimInfo()
        }
    }

    private fun startProgressUpdater() {
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (!isSeeking && mediaPlayer != null && videoDuration > 0 && isVideoPrepared) {
                    val currentPosition = mediaPlayer?.currentPosition ?: 0
                    val progress = (currentPosition.toFloat() / videoDuration * 1000).toInt()
                    binding.seekBarProgress.progress = progress.coerceIn(0, 1000)
                    updateThumbnailHighlight(currentPosition)
                    scrollToCurrentPosition(currentPosition)
                }
                handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun scrollToCurrentPosition(position: Int) {
        val scrollView = binding.thumbnailsScrollView
        val container = binding.thumbnailsContainer

        if (container.childCount == 0) return

        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val totalWidth = container.measuredWidth.toFloat()
        val viewWidth = scrollView.width.toFloat()

        if (totalWidth <= 0 || viewWidth <= 0) return

        val percent = position.toFloat() / videoDuration
        val targetX = (percent * totalWidth - viewWidth / 2).toInt()
        val maxScroll = (totalWidth - viewWidth).toInt()
        val scrollX = targetX.coerceIn(0, maxScroll)

        scrollView.smoothScrollTo(scrollX, 0)
    }

    private fun updateThumbnailHighlight(position: Int) {
        val percent = position.toFloat() / videoDuration
        val highlightIndex = (percent * THUMBNAIL_COUNT).toInt().coerceIn(0, THUMBNAIL_COUNT - 1)

        thumbnailsViews.forEachIndexed { index, view ->
            if (index == highlightIndex) {
                view.setColorFilter(Color.parseColor("#33FFD93D"))
                view.scaleX = 1.1f
                view.scaleY = 1.1f
            } else {
                view.clearColorFilter()
                view.scaleX = 1.0f
                view.scaleY = 1.0f
            }
        }
    }

    private fun setupVideoLayout(videoWidth: Int, videoHeight: Int) {
        currentVideoView?.let { view ->
            if (videoWidth <= 0 || videoHeight <= 0) {
                val layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                layoutParams.gravity = android.view.Gravity.CENTER
                view.layoutParams = layoutParams
                view.requestLayout()
                return
            }

            val containerWidth = binding.videoContainer.width
            val containerHeight = binding.videoContainer.height

            val effectiveWidth = if (containerWidth > 0) containerWidth else 1080
            val effectiveHeight = if (containerHeight > 0) containerHeight else 1920

            val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val displayAspect = effectiveWidth.toFloat() / effectiveHeight.toFloat()

            var finalWidth = effectiveWidth
            var finalHeight = effectiveHeight

            if (aspectRatio > displayAspect) {
                finalHeight = (effectiveWidth / aspectRatio).toInt()
            } else {
                finalWidth = (effectiveHeight * aspectRatio).toInt()
            }

            val layoutParams = FrameLayout.LayoutParams(finalWidth, finalHeight)
            layoutParams.gravity = android.view.Gravity.CENTER
            view.layoutParams = layoutParams
            view.requestLayout()
        }
    }

    private fun loadVideo() {
        try {
            val path = originalVideoPath ?: return
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(this, "Файл не существует", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = Uri.fromFile(file)

            currentVideoView?.let {
                binding.videoContainer.removeView(it)
            }

            val newVideoView = VideoView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                setVideoURI(uri)

                val mediaController = android.widget.MediaController(this@VideoEditorActivity)
                setMediaController(mediaController)
                mediaController.setAnchorView(this)

                setOnPreparedListener { player ->
                    mediaPlayer = player
                    videoDuration = player.duration
                    maxDurationMs = if (videoDuration > MAX_VIDEO_DURATION * 1000) {
                        MAX_VIDEO_DURATION * 1000
                    } else {
                        videoDuration
                    }
                    isVideoPrepared = true

                    binding.tvTotalDuration.text = formatTime(maxDurationMs / 1000)
                    updateTrimInfo()
                    setupVideoLayout(player.videoWidth, player.videoHeight)

                    player.start()
                    binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                    loadVideoInfo(file)

                    Log.d(TAG, "✅ Видео загружено, длительность: ${videoDuration / 1000} сек")
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ Ошибка видео: what=$what, extra=$extra")
                    Toast.makeText(this@VideoEditorActivity, "Ошибка воспроизведения видео", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            binding.videoContainer.addView(newVideoView, 0)
            currentVideoView = newVideoView

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки видео", e)
            Toast.makeText(this, "Ошибка загрузки видео: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadVideoInfo(file: File) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            binding.tvVideoInfo.text = "📹 ${width}x${height} | ${file.length() / 1024 / 1024} MB | ${if (bitrate > 0) bitrate / 1000 else 0} kbps"
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось получить информацию о видео: ${e.message}")
        }
    }

    private fun loadThumbnails() {
        try {
            val file = File(originalVideoPath ?: return)
            if (!file.exists()) return

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)

            val duration = videoDuration.toLong()
            val step = duration / (THUMBNAIL_COUNT + 1)
            thumbnailsBitmaps.clear()
            thumbnailsViews.clear()

            for (i in 1..THUMBNAIL_COUNT) {
                val timeUs = step * i * 1000
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                bitmap?.let {
                    thumbnailsBitmaps.add(it)
                }
            }

            retriever.release()

            handler.postDelayed({
                displayThumbnailsWithTrim()
            }, 100)

        } catch (e: Exception) {
            Log.w(TAG, "Не удалось загрузить миниатюры: ${e.message}")
        }
    }

    private fun displayThumbnailsWithTrim() {
        val container = binding.thumbnailsContainer
        container.removeAllViews()
        thumbnailsViews.clear()

        thumbnailsWrapper = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        thumbnailsBitmaps.forEachIndexed { index, bitmap ->
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    THUMBNAIL_HEIGHT * 2,
                    THUMBNAIL_HEIGHT
                )
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
                tag = index

                setOnClickListener {
                    val position = (index.toFloat() / THUMBNAIL_COUNT * videoDuration).toInt()
                    if (isVideoPrepared) {
                        mediaPlayer?.seekTo(position)
                        binding.seekBarProgress.progress = (position.toFloat() / videoDuration * 1000).toInt()
                        scrollToCurrentPosition(position)
                    }
                }
            }
            thumbnailsWrapper!!.addView(imageView)
            thumbnailsViews.add(imageView)
        }

        container.addView(thumbnailsWrapper)

        val scrollView = binding.thumbnailsScrollView
        scrollView.setOnTouchListener { view, event ->
            handleThumbnailTouch(event, view, scrollView)
            true
        }

        if (videoDuration > MAX_VIDEO_DURATION * 1000) {
            trimStartPercent = 0f
            trimEndPercent = MAX_VIDEO_DURATION.toFloat() * 1000 / videoDuration
            binding.tvTrimStatus.text = "👆 Выберите фрагмент до 15 секунд"
            binding.tvTrimStatus.setTextColor(Color.YELLOW)
            binding.btnTrim.isEnabled = true
            binding.btnTrim.text = "✂️ Применить обрезку"
        } else {
            trimStartPercent = 0f
            trimEndPercent = 1f
            binding.tvTrimStatus.text = "👆 Перемещайте края выделения по миниатюрам"
        }

        updateTrimSelection()
        updateTrimInfo()

        scrollView.post {
            scrollView.scrollTo(0, 0)
        }
    }

    private fun updateTrimSelection() {
        val container = binding.thumbnailsContainer
        val totalWidth = container.width.toFloat()

        if (totalWidth <= 0) return

        thumbnailsViews.forEachIndexed { index, view ->
            val pos = index.toFloat() / THUMBNAIL_COUNT
            val isInRange = pos >= trimStartPercent && pos <= trimEndPercent
            view.alpha = if (isInRange) 1.0f else 0.3f
        }

        val startMs = (trimStartPercent * videoDuration).toInt()
        val endMs = (trimEndPercent * videoDuration).toInt()
        val durationMs = endMs - startMs

        binding.tvTrimStart.text = formatTime(startMs / 1000)
        binding.tvTrimEnd.text = formatTime(endMs / 1000)
        binding.tvSelectedRange.text = "${formatTime(startMs / 1000)} - ${formatTime(endMs / 1000)}"
        binding.tvSelectedDuration.text = "${durationMs / 1000} сек"

        if (durationMs >= MIN_VIDEO_DURATION * 1000 && durationMs <= MAX_VIDEO_DURATION * 1000) {
            binding.btnTrim.isEnabled = true
            binding.btnTrim.text = "✂️ Применить обрезку"
        } else {
            binding.btnTrim.isEnabled = false
        }
    }

    private fun handleThumbnailTouch(event: MotionEvent, view: View, scrollView: HorizontalScrollView) {
        val container = binding.thumbnailsContainer
        val totalWidth = container.width.toFloat()
        if (totalWidth <= 0) return

        val scrollX = scrollView.scrollX.toFloat()
        val x = event.x + scrollX
        val percent = (x / totalWidth).coerceIn(0f, 1f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                val startDist = Math.abs(percent - trimStartPercent)
                val endDist = Math.abs(percent - trimEndPercent)
                isSelectingStart = startDist < endDist
                if (isVideoPrepared) {
                    mediaPlayer?.pause()
                    binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    if (isSelectingStart) {
                        trimStartPercent = percent.coerceIn(0f, trimEndPercent - MIN_SELECTION_WIDTH)
                    } else {
                        trimEndPercent = percent.coerceIn(trimStartPercent + MIN_SELECTION_WIDTH, 1f)
                    }
                    updateTrimSelection()
                    updateTrimInfo()

                    val position = (percent * videoDuration).toInt()
                    if (isVideoPrepared) {
                        mediaPlayer?.seekTo(position)
                        binding.seekBarProgress.progress = (position.toFloat() / videoDuration * 1000).toInt()
                        scrollToCurrentPosition(position)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                val durationMs = ((trimEndPercent - trimStartPercent) * videoDuration).toInt()

                if (durationMs < MIN_VIDEO_DURATION * 1000) {
                    Toast.makeText(this, "⚠️ Выберите фрагмент длиннее ${MIN_VIDEO_DURATION} секунды", Toast.LENGTH_SHORT).show()
                } else if (durationMs > MAX_VIDEO_DURATION * 1000) {
                    Toast.makeText(this, "⚠️ Фрагмент должен быть короче ${MAX_VIDEO_DURATION} секунд", Toast.LENGTH_SHORT).show()
                } else {
                    binding.btnTrim.isEnabled = true
                    binding.btnTrim.text = "✂️ Применить обрезку"
                }

                if (isVideoPrepared && currentVideoView?.isPlaying == true) {
                    mediaPlayer?.start()
                    binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                }
            }
        }
    }

    private fun updateTrimInfo() {
        val startMs = (trimStartPercent * videoDuration).toInt()
        val endMs = (trimEndPercent * videoDuration).toInt()
        val durationMs = endMs - startMs

        binding.tvTrimStart.text = formatTime(startMs / 1000)
        binding.tvTrimEnd.text = formatTime(endMs / 1000)
        binding.tvSelectedRange.text = "${formatTime(startMs / 1000)} - ${formatTime(endMs / 1000)}"
        binding.tvSelectedDuration.text = "${durationMs / 1000} сек"

        if (durationMs < MIN_VIDEO_DURATION * 1000) {
            binding.tvTrimStatus.text = "⚠️ Выберите фрагмент длиннее ${MIN_VIDEO_DURATION} секунды"
            binding.tvTrimStatus.setTextColor(binding.root.context.getColor(R.color.lemon_divider))
            binding.btnTrim.isEnabled = false
        } else if (durationMs > MAX_VIDEO_DURATION * 1000) {
            binding.tvTrimStatus.text = "⚠️ Фрагмент должен быть короче ${MAX_VIDEO_DURATION} секунд"
            binding.tvTrimStatus.setTextColor(binding.root.context.getColor(R.color.lemon_divider))
            binding.btnTrim.isEnabled = false
        } else {
            binding.tvTrimStatus.text = "✅ Выбрано: ${durationMs / 1000} сек"
            binding.tvTrimStatus.setTextColor(binding.root.context.getColor(R.color.lemon_primary))
            binding.btnTrim.isEnabled = true
        }
    }

    private fun applyTrimFromThumbnails() {
        val startMs = (trimStartPercent * videoDuration).toInt()
        val endMs = (trimEndPercent * videoDuration).toInt()
        val durationMs = endMs - startMs

        if (durationMs < MIN_VIDEO_DURATION * 1000) {
            Toast.makeText(this, "⚠️ Выберите фрагмент длиннее ${MIN_VIDEO_DURATION} секунды", Toast.LENGTH_SHORT).show()
            return
        }

        if (durationMs > MAX_VIDEO_DURATION * 1000) {
            Toast.makeText(this, "⚠️ Фрагмент должен быть короче ${MAX_VIDEO_DURATION} секунд", Toast.LENGTH_SHORT).show()
            return
        }

        trimVideo(startMs, endMs)
    }

    private fun checkAndConvertVideo() {
        val path = originalVideoPath ?: return
        val file = File(path)

        val extension = file.extension.lowercase()
        val isSupported = extension in listOf("mp4", "mov", "webm")
        val is3gp = checkIf3gp(file)

        Log.d(TAG, "📄 Расширение файла: '$extension'")
        Log.d(TAG, "📄 is3gp: $is3gp")
        Log.d(TAG, "📄 isSupported: $isSupported")

        if (isSupported && !is3gp) {
            Log.d(TAG, "✅ Видео уже в поддерживаемом формате")
            convertedVideoPath = path
            return
        }

        if (is3gp || !isSupported) {
            Log.d(TAG, "🔄 Требуется конвертация видео...")
            convertToMp4(path)
        } else {
            convertedVideoPath = path
        }
    }

    private fun checkIf3gp(file: File): Boolean {
        return try {
            val buffer = ByteArray(12)
            file.inputStream().use { input ->
                input.read(buffer)
            }
            val header = String(buffer, 0, 8)
            header.contains("ftyp3gp") || header.contains("3gp")
        } catch (e: Exception) {
            false
        }
    }

    private fun convertToMp4(inputPath: String) {
        if (isConverting) {
            Log.d(TAG, "⚠️ Конвертация уже выполняется")
            return
        }

        isConverting = true
        showProgress("🔄 Конвертация в MP4...", true)

        val uploadButton = binding.btnUpload
        uploadButton.isEnabled = false

        val outputFile = File(cacheDir, "converted_video_${System.currentTimeMillis()}.mp4")
        val outputPath = outputFile.absolutePath

        val command = arrayOf(
            "-i", inputPath,
            "-c:v", "h264_mediacodec",
            "-pix_fmt", "nv12",
            "-b:v", "1M",
            "-profile:v", "baseline",
            "-level", "3.0",
            "-c:a", "aac",
            "-b:a", "128k",
            "-ac", "2",
            "-movflags", "+faststart",
            "-y",
            outputPath
        ).joinToString(" ")

        Log.d(TAG, "🎬 Команда конвертации: $command")

        startProgressSimulation()

        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode

            runOnUiThread {
                this@VideoEditorActivity.isConverting = false
                hideProgress()
                uploadButton.isEnabled = true

                if (ReturnCode.isSuccess(returnCode) &&
                    outputFile.exists() &&
                    outputFile.length() > 1000) {

                    if (!checkIf3gp(outputFile)) {
                        convertedVideoPath = outputPath
                        Log.d(TAG, "✅ Видео сконвертировано в MP4: ${outputFile.length()} байт")
                        Toast.makeText(this@VideoEditorActivity, "✅ Видео сконвертировано в MP4", Toast.LENGTH_SHORT).show()
                        loadConvertedVideo()
                    } else {
                        Log.e(TAG, "❌ Конвертация не удалась, файл все еще 3GP")
                        Toast.makeText(this@VideoEditorActivity, "⚠️ Не удалось конвертировать видео", Toast.LENGTH_SHORT).show()
                        convertedVideoPath = inputPath
                        loadVideo()
                    }
                } else {
                    Log.e(TAG, "❌ Ошибка конвертации, используем оригинал")
                    Toast.makeText(this@VideoEditorActivity, "⚠️ Используется оригинальный формат", Toast.LENGTH_SHORT).show()
                    convertedVideoPath = inputPath
                    loadVideo()
                }
            }
        }
    }

    private fun loadConvertedVideo() {
        val path = convertedVideoPath ?: return
        try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "❌ Сконвертированный файл не существует или пустой")
                return
            }
            trimmedVideoPath = path
            loadTrimmedVideo()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки конвертированного видео", e)
        }
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            val fileName = getFileNameFromUri(uri)
            val cacheFile = File(cacheDir, "video_${System.currentTimeMillis()}_$fileName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (cacheFile.exists() && cacheFile.length() > 0) {
                Log.d(TAG, "✅ Файл скопирован в кэш: ${cacheFile.absolutePath}")
                return cacheFile.absolutePath
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения пути: ${e.message}")
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst() && nameIndex >= 0) {
                    val name = it.getString(nameIndex)
                    if (!name.isNullOrEmpty()) {
                        return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Не удалось получить имя файла: ${e.message}")
        }
        return "video_${System.currentTimeMillis()}.mp4"
    }

    private fun trimVideo(startMs: Int, endMs: Int) {
        val videoPath = convertedVideoPath ?: originalVideoPath
        if (videoPath == null) {
            Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show()
            return
        }

        val durationMs = endMs - startMs
        if (durationMs < MIN_VIDEO_DURATION * 1000) {
            Toast.makeText(this, "⚠️ Выберите фрагмент длиннее ${MIN_VIDEO_DURATION} секунды", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress("✂️ Обрезка видео...", false)
        binding.btnTrim.isEnabled = false

        val outputFile = File(cacheDir, "trimmed_video_${System.currentTimeMillis()}.mp4")
        val outputPath = outputFile.absolutePath

        val startTime = formatTimeForFFmpeg(startMs / 1000)
        val duration = durationMs / 1000

        val command = arrayOf(
            "-i", videoPath,
            "-ss", startTime,
            "-t", duration.toString(),
            "-c", "copy",
            "-y",
            outputPath
        ).joinToString(" ")

        Log.d(TAG, "🎬 FFmpeg command: $command")

        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode
            runOnUiThread {
                hideProgress()
                binding.btnTrim.isEnabled = true

                if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                    trimmedVideoPath = outputPath
                    isVideoTrimmed = true
                    binding.tvTrimStatus.text = "✅ Видео обрезано!"
                    binding.tvTrimStatus.setTextColor(binding.root.context.getColor(R.color.lemon_primary))
                    Toast.makeText(this@VideoEditorActivity, "✅ Видео обрезано", Toast.LENGTH_SHORT).show()
                    loadTrimmedVideo()
                } else {
                    Toast.makeText(this@VideoEditorActivity, "❌ Ошибка обрезки", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "❌ Ошибка обрезки, returnCode: $returnCode")
                }
            }
        }
    }

    // ============================================================
    // ✅ ЗАГРУЗКА ОБРЕЗАННОГО ВИДЕО - СОЗДАНИЕ НОВОГО VIDEOVIEW
    // ============================================================

    private fun loadTrimmedVideo() {
        val path = trimmedVideoPath ?: return
        try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "❌ Обрезанный файл не существует")
                return
            }

            val uri = Uri.fromFile(file)
            Log.d(TAG, "📁 Загрузка обрезанного видео: ${file.absolutePath}, размер: ${file.length()} байт")

            safeReleaseMediaPlayer()

            currentVideoView?.let {
                binding.videoContainer.removeView(it)
            }

            val newVideoView = VideoView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                setVideoURI(uri)

                val mediaController = android.widget.MediaController(this@VideoEditorActivity)
                setMediaController(mediaController)
                mediaController.setAnchorView(this)

                setOnPreparedListener { player ->
                    mediaPlayer = player
                    videoDuration = player.duration
                    maxDurationMs = videoDuration
                    isVideoTrimmed = true
                    isVideoPrepared = true
                    trimStartPercent = 0f
                    trimEndPercent = 1f

                    Log.d(TAG, "✅ Обрезанное видео загружено, длительность: ${videoDuration / 1000} сек")

                    binding.tvTotalDuration.text = formatTime(videoDuration / 1000)
                    setupVideoLayout(player.videoWidth, player.videoHeight)
                    updateTrimInfo()
                    updateTrimSelection()

                    try {
                        player.start()
                        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка запуска: ${e.message}")
                    }

                    loadThumbnails()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ Ошибка загрузки видео: what=$what, extra=$extra")
                    Toast.makeText(this@VideoEditorActivity, "Ошибка загрузки видео", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            binding.videoContainer.addView(newVideoView, 0)
            currentVideoView = newVideoView

            binding.btnPlayPause.visibility = View.VISIBLE
            binding.btnTrim.text = "✂️ Обрезать"
            binding.btnTrim.visibility = View.VISIBLE

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки обрезанного видео", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rotateVideo() {
        val videoPath = trimmedVideoPath ?: convertedVideoPath ?: originalVideoPath
        if (videoPath == null) {
            Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show()
            return
        }

        currentRotation = (currentRotation + 90) % 360
        showProgress("🔄 Поворот видео (${currentRotation}°)...", false)
        binding.btnRotate.isEnabled = false

        val outputFile = File(cacheDir, "rotated_video_${System.currentTimeMillis()}.mp4")
        val outputPath = outputFile.absolutePath

        val rotateFilter = when (currentRotation) {
            90 -> "transpose=1"
            180 -> "hflip,vflip"
            270 -> "transpose=2"
            else -> "null"
        }

        val command = if (currentRotation == 0) {
            arrayOf("-i", videoPath, "-c", "copy", "-y", outputPath).joinToString(" ")
        } else {
            arrayOf("-i", videoPath, "-vf", rotateFilter, "-c:a", "copy", "-y", outputPath).joinToString(" ")
        }

        Log.d(TAG, "🎬 Команда поворота: $command")

        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode
            runOnUiThread {
                hideProgress()
                binding.btnRotate.isEnabled = true

                if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                    val newPath = outputFile.absolutePath
                    if (isVideoTrimmed) {
                        trimmedVideoPath = newPath
                    } else {
                        convertedVideoPath = newPath
                    }
                    Toast.makeText(this@VideoEditorActivity, "✅ Видео повернуто на ${currentRotation}°", Toast.LENGTH_SHORT).show()
                    loadTrimmedVideo()
                } else {
                    Toast.makeText(this@VideoEditorActivity, "❌ Ошибка поворота", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "1x", "1.5x", "2x", "3x")
        val currentIndex = when (currentSpeed) {
            0.5f -> 0
            1.0f -> 1
            1.5f -> 2
            2.0f -> 3
            3.0f -> 4
            else -> 1
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите скорость")
            .setSingleChoiceItems(speeds, currentIndex) { dialog, which ->
                val newSpeed = when (which) {
                    0 -> 0.5f
                    1 -> 1.0f
                    2 -> 1.5f
                    3 -> 2.0f
                    4 -> 3.0f
                    else -> 1.0f
                }
                applySpeed(newSpeed)
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun applySpeed(speed: Float) {
        val videoPath = trimmedVideoPath ?: convertedVideoPath ?: originalVideoPath
        if (videoPath == null) {
            Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show()
            return
        }

        currentSpeed = speed
        showProgress("⚡ Изменение скорости (${speed}x)...", false)
        binding.btnSpeed.isEnabled = false

        val outputFile = File(cacheDir, "speed_video_${System.currentTimeMillis()}.mp4")
        val outputPath = outputFile.absolutePath

        val command = arrayOf(
            "-i", videoPath,
            "-filter_complex", "[0:v]setpts=${1/speed}*PTS[v];[0:a]atempo=${speed}[a]",
            "-map", "[v]",
            "-map", "[a]",
            "-c:v", "h264_mediacodec",
            "-pix_fmt", "nv12",
            "-b:v", "1M",
            "-c:a", "aac",
            "-b:a", "128k",
            "-y",
            outputPath
        ).joinToString(" ")

        Log.d(TAG, "🎬 Команда скорости: $command")

        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode
            runOnUiThread {
                hideProgress()
                binding.btnSpeed.isEnabled = true

                if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                    val newPath = outputFile.absolutePath
                    if (isVideoTrimmed) {
                        trimmedVideoPath = newPath
                    } else {
                        convertedVideoPath = newPath
                    }
                    binding.tvSpeedValue.text = "${speed}x"
                    Toast.makeText(this@VideoEditorActivity, "✅ Скорость изменена на ${speed}x", Toast.LENGTH_SHORT).show()
                    loadTrimmedVideo()
                } else {
                    Toast.makeText(this@VideoEditorActivity, "❌ Ошибка изменения скорости", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyVolume() {
        Log.d(TAG, "🔊 Громкость: ${originalVolume * 100}%")
    }

    private fun processWithMusicAndEffects(inputPath: String, outputPath: String): Boolean {
        try {
            val commands = mutableListOf<String>()
            commands.add("-i")
            commands.add(inputPath)

            var hasMusic = false

            if (isMusicAdded && musicFilePath != null) {
                val musicFile = File(musicFilePath)
                if (musicFile.exists()) {
                    commands.add("-i")
                    commands.add(musicFilePath!!)
                    hasMusic = true
                }
            }

            val videoFilters = mutableListOf<String>()
            if (currentSpeed != 1.0f) {
                videoFilters.add("setpts=${1/currentSpeed}*PTS")
            }

            val videoFilterStr = if (videoFilters.isNotEmpty()) {
                "[0:v]${videoFilters.joinToString(",")}[v]"
            } else {
                "[0:v]null[v]"
            }

            val audioFilters = mutableListOf<String>()

            if (hasMusic) {
                audioFilters.add("[0:a]volume=${originalVolume}[orig]")
                audioFilters.add("[1:a]volume=${musicVolume}[music]")
                audioFilters.add("[orig][music]amix=inputs=2:duration=first[a]")
            } else {
                audioFilters.add("[0:a]volume=${originalVolume}[a]")
            }

            val audioFilterStr = audioFilters.joinToString(";")

            val fullCommand = mutableListOf<String>()
            fullCommand.addAll(commands)
            fullCommand.add("-filter_complex")
            fullCommand.add("$videoFilterStr;$audioFilterStr")
            fullCommand.add("-map")
            fullCommand.add("[v]")
            fullCommand.add("-map")
            fullCommand.add("[a]")
            fullCommand.add("-c:v")
            fullCommand.add("h264_mediacodec")
            fullCommand.add("-pix_fmt")
            fullCommand.add("nv12")
            fullCommand.add("-b:v")
            fullCommand.add("1M")
            fullCommand.add("-c:a")
            fullCommand.add("aac")
            fullCommand.add("-b:a")
            fullCommand.add("128k")
            fullCommand.add("-movflags")
            fullCommand.add("+faststart")
            fullCommand.add("-y")
            fullCommand.add(outputPath)

            val command = fullCommand.joinToString(" ")
            Log.d(TAG, "🎬 Финальная команда: $command")

            val session = FFmpegKit.execute(command)
            return ReturnCode.isSuccess(session.returnCode)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка финальной обработки: ${e.message}")
            return false
        }
    }

    private fun showProgress(message: String, showIndeterminate: Boolean) {
        binding.progressBar.apply {
            visibility = View.VISIBLE
            isIndeterminate = showIndeterminate
            if (!showIndeterminate) {
                max = 100
                progress = 0
            }
        }
        binding.tvProgress.apply {
            text = message
            visibility = View.VISIBLE
        }
        isProcessing = true
    }

    private fun hideProgress() {
        binding.progressBar.apply {
            visibility = View.GONE
            isIndeterminate = false
            progress = 0
        }
        binding.tvProgress.apply {
            text = ""
            visibility = View.GONE
        }
        isProcessing = false
    }

    private fun startProgressSimulation() {
        if (!isConverting) return

        binding.progressBar.apply {
            visibility = View.VISIBLE
            isIndeterminate = false
            max = 100
            progress = 0
        }
        binding.tvProgress.apply {
            text = "🔄 Конвертация в MP4... 0%"
            visibility = View.VISIBLE
        }

        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (isConverting) {
                    val current = binding.progressBar.progress
                    if (current < 90) {
                        val increment = (3..10).random()
                        val newProgress = (current + increment).coerceAtMost(90)
                        binding.progressBar.progress = newProgress
                        binding.tvProgress.text = "🔄 Конвертация в MP4... $newProgress%"
                        handler.postDelayed(this, 300)
                    } else {
                        handler.postDelayed(this, 500)
                    }
                }
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun uploadVideo() {
        if (isConverting) {
            Toast.makeText(this, "⏳ Подождите, идет конвертация...", Toast.LENGTH_SHORT).show()
            return
        }

        if (isProcessing) {
            Toast.makeText(this, "⏳ Подождите, идет обработка...", Toast.LENGTH_SHORT).show()
            return
        }

        val needProcessing = isMusicAdded || originalVolume < 1.0f

        if (needProcessing) {
            processFinalVideo()
        } else {
            uploadVideoDirect()
        }
    }

    private fun processFinalVideo() {
        val inputPath = trimmedVideoPath ?: convertedVideoPath ?: originalVideoPath
        if (inputPath == null) {
            Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress("🎬 Обработка видео...", false)
        binding.btnUpload.isEnabled = false

        val outputFile = File(cacheDir, "final_video_${System.currentTimeMillis()}.mp4")
        val outputPath = outputFile.absolutePath

        Thread {
            val success = processWithMusicAndEffects(inputPath, outputPath)
            runOnUiThread {
                hideProgress()
                binding.btnUpload.isEnabled = true

                if (success && outputFile.exists() && outputFile.length() > 0) {
                    if (isVideoTrimmed) {
                        trimmedVideoPath = outputPath
                    } else {
                        convertedVideoPath = outputPath
                    }
                    Toast.makeText(this, "✅ Видео обработано", Toast.LENGTH_SHORT).show()
                    uploadVideoDirect()
                } else {
                    Toast.makeText(this, "❌ Ошибка обработки", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun uploadVideoDirect() {
        var videoToUpload = when {
            isVideoTrimmed && trimmedVideoPath != null -> trimmedVideoPath
            convertedVideoPath != null -> convertedVideoPath
            else -> originalVideoPath
        }

        if (videoToUpload == null) {
            Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(videoToUpload)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "Файл поврежден", Toast.LENGTH_SHORT).show()
            return
        }

        if (checkIf3gp(file)) {
            Log.w(TAG, "⚠️ Файл в формате 3GP, конвертируем...")
            Toast.makeText(this, "🔄 Конвертация...", Toast.LENGTH_SHORT).show()
            convertToMp4(videoToUpload)
            return
        }

        Log.d(TAG, "📤 Отправка видео: ${file.absolutePath}")
        Log.d(TAG, "📊 Размер: ${file.length()} байт")

        val intent = Intent().apply {
            putExtra("video_file_path", videoToUpload)
            putExtra("is_video", true)
            putExtra("story_text", binding.etText.text.toString().trim())
            putExtra("story_title", binding.etTitle.text.toString().trim())
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun formatTimeForFFmpeg(seconds: Int): String {
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, mins, secs)
    }
}
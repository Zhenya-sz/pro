package com.fitnesslemon.app.ui.chat.dialogs

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.fitnesslemon.app.R
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VideoPlayerDialog : DialogFragment() {

    private var videoPath: String? = null
    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var progressBar: ProgressBar? = null
    private var tvDuration: TextView? = null
    private var btnClose: ImageButton? = null
    private var tvError: TextView? = null
    private var isDownloading = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val ARG_VIDEO_PATH = "video_path"
        private const val TAG = "VideoPlayerDialog"

        fun newInstance(videoPath: String): VideoPlayerDialog {
            val fragment = VideoPlayerDialog()
            val args = Bundle()
            args.putString(ARG_VIDEO_PATH, videoPath)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        arguments?.let {
            videoPath = it.getString(ARG_VIDEO_PATH)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_video_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = view.findViewById(R.id.playerView)
        progressBar = view.findViewById(R.id.progressBar)
        btnClose = view.findViewById(R.id.btnClose)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvError = view.findViewById(R.id.tvError)

        btnClose?.setOnClickListener {
            dismiss()
        }

        val path = videoPath
        if (path.isNullOrEmpty()) {
            showError("Видео не найдено")
            return
        }

        Log.d(TAG, "🎬 Загрузка видео по пути: $path")

        progressBar?.visibility = View.VISIBLE
        playerView?.visibility = View.GONE

        // Проверяем локальный файл
        val localFile = File(path)
        if (localFile.exists() && localFile.length() > 0) {
            Log.d(TAG, "✅ Видео найдено локально: ${localFile.absolutePath}")
            playLocalFile(localFile)
            return
        }

        // Проверяем кэш по URL
        if (path.startsWith("http")) {
            val cachedFile = getCachedVideoFile(path)
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                Log.d(TAG, "✅ Видео найдено в кэше: ${cachedFile.absolutePath}")
                playLocalFile(cachedFile)
                return
            }

            // Скачиваем видео
            downloadAndPlayVideo(path)
        } else {
            showError("Файл не найден: $path")
        }
    }

    private fun playLocalFile(file: File) {
        try {
            Log.d(TAG, "🎬 Воспроизведение локального файла: ${file.absolutePath}")
            val uri = Uri.fromFile(file)
            val mediaItem = MediaItem.fromUri(uri)
            initializePlayer(mediaItem)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка: ${e.message}", e)
            showError("Ошибка: ${e.message}")
        }
    }

    private fun downloadAndPlayVideo(url: String) {
        if (isDownloading) return
        isDownloading = true

        progressBar?.visibility = View.VISIBLE
        progressBar?.isIndeterminate = true
        tvError?.visibility = View.GONE

        Log.d(TAG, "📥 Загрузка видео: $url")

        Thread {
            try {
                val token = PreferencesManager.getToken()
                Log.d(TAG, "🔑 Токен: ${if (token != null) "найден" else "НЕ НАЙДЕН"}")

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                // Пробуем с авторизацией
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "video/mp4, video/*")
                    .build()

                Log.d(TAG, "📤 Выполняем запрос: $url")
                val response = client.newCall(request).execute()
                Log.d(TAG, "📡 Код ответа: ${response.code}")

                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    handler.post {
                        val errorMsg = when (code) {
                            404 -> "Видео не найдено на сервере"
                            403 -> "Нет доступа к видео"
                            401 -> "Требуется авторизация"
                            else -> "Ошибка загрузки: $code"
                        }
                        showError(errorMsg)
                    }
                    return@Thread
                }

                val fileName = url.substringAfterLast("/")
                val videoDir = File(requireContext().cacheDir, "videos")
                if (!videoDir.exists()) videoDir.mkdirs()

                val videoFile = File(videoDir, fileName)
                Log.d(TAG, "📁 Сохраняем: ${videoFile.absolutePath}")

                response.body?.let { body ->
                    FileOutputStream(videoFile).use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                response.close()

                if (videoFile.exists() && videoFile.length() > 0) {
                    Log.d(TAG, "✅ Видео загружено, размер: ${videoFile.length()}")
                    handler.post {
                        isDownloading = false
                        progressBar?.visibility = View.GONE
                        playLocalFile(videoFile)
                    }
                } else {
                    handler.post { showError("Ошибка загрузки - файл пустой") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка: ${e.message}", e)
                handler.post {
                    isDownloading = false
                    showError("Ошибка: ${e.message}")
                }
            }
        }.start()
    }

    private fun getCachedVideoFile(url: String): File? {
        try {
            val fileName = url.substringAfterLast("/")
            val videoDir = File(requireContext().cacheDir, "videos")
            val file = File(videoDir, fileName)
            return if (file.exists() && file.length() > 0) file else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun initializePlayer(mediaItem: MediaItem) {
        try {
            exoPlayer = ExoPlayer.Builder(requireContext()).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                progressBar?.visibility = View.GONE
                                playerView?.visibility = View.VISIBLE
                                val duration = this@apply.duration / 1000
                                tvDuration?.text = formatDuration(duration.toInt())
                                tvDuration?.visibility = View.VISIBLE
                                play()
                            }
                            Player.STATE_ENDED -> {
                                seekTo(0)
                                pause()
                            }
                            Player.STATE_BUFFERING -> {
                                progressBar?.visibility = View.VISIBLE
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        progressBar?.visibility = View.GONE
                        val msg = when {
                            error.message?.contains("404") == true -> "Видео не найдено"
                            error.message?.contains("403") == true -> "Нет доступа"
                            else -> "Ошибка воспроизведения"
                        }
                        showError(msg)
                    }
                })

                setMediaItem(mediaItem)
                prepare()
            }

            playerView?.player = exoPlayer
            playerView?.setOnClickListener { dismiss() }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка плеера: ${e.message}", e)
            showError("Ошибка: ${e.message}")
        }
    }

    private fun showError(message: String) {
        progressBar?.visibility = View.GONE
        playerView?.visibility = View.GONE
        tvDuration?.visibility = View.GONE
        tvError?.text = message
        tvError?.visibility = View.VISIBLE
        isDownloading = false
        Log.e(TAG, "❌ $message")
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        playerView?.player = null
        handler.removeCallbacksAndMessages(null)
    }
}
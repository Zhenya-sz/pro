package com.fitnesslemon.app.ui.chat.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileInputStream

private const val TAG = "AudioPlayerHelper"

class AudioPlayerHelper(
    private val context: Context,
    private val onPlaybackStateChanged: (Boolean) -> Unit,
    private val onProgress: (Int, Int) -> Unit,
    private val onError: (String) -> Unit,
    private val onCompletion: () -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null
    private var updateRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPaused = false
    private var currentDuration = 0
    private var onWaveformUpdate: ((Int) -> Unit)? = null

    fun setWaveformCallback(callback: (Int) -> Unit) {
        onWaveformUpdate = callback
    }

    fun play(file: File) {
        currentFile = file

        try {
            if (!file.exists()) {
                onError("Файл не найден")
                return
            }

            if (file.length() < 5000) {
                onError("Файл слишком маленький (${file.length()} байт)")
                return
            }

            Log.d(TAG, "🎵 Воспроизведение: ${file.absolutePath}, размер: ${file.length()}, расширение: ${file.extension}")

            if (!isMp3File(file)) {
                onError("Файл не является MP3")
                return
            }

            playAudioFile(file)

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка play: ${e.message}", e)
            onError("Ошибка: ${e.message}")
        }
    }

    fun isConverting(): Boolean = false

    private fun isMp3File(file: File): Boolean {
        return try {
            val header = file.readBytes().take(3)
            val str = String(header.toByteArray())
            str.startsWith("ID3") || (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
        } catch (e: Exception) {
            false
        }
    }

    private fun playAudioFile(file: File) {
        try {
            Log.d(TAG, "🎵 Воспроизведение файла: ${file.absolutePath}")

            if (mediaPlayer != null) {
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.reset()
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка сброса MediaPlayer: ${e.message}")
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }

            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )

                    var success = false

                    // 1. Через FileDescriptor
                    try {
                        val fis = FileInputStream(file)
                        setDataSource(fis.fd)
                        fis.close()
                        success = true
                        Log.d(TAG, "✅ MediaPlayer: через FileDescriptor")
                    } catch (e: Exception) {
                        Log.d(TAG, "MediaPlayer FD ошибка: ${e.message}")
                    }

                    // 2. Через прямой путь
                    if (!success) {
                        try {
                            setDataSource(file.absolutePath)
                            success = true
                            Log.d(TAG, "✅ MediaPlayer: через прямой путь")
                        } catch (e: Exception) {
                            Log.d(TAG, "MediaPlayer путь ошибка: ${e.message}")
                        }
                    }

                    // 3. Через URI
                    if (!success) {
                        try {
                            val uri = Uri.fromFile(file)
                            setDataSource(context, uri)
                            success = true
                            Log.d(TAG, "✅ MediaPlayer: через URI")
                        } catch (e: Exception) {
                            Log.d(TAG, "MediaPlayer URI ошибка: ${e.message}")
                        }
                    }

                    if (!success) {
                        release()
                        onError("Не удалось открыть аудиофайл")
                        return
                    }

                    setOnPreparedListener {
                        currentDuration = it.duration / 1000
                        Log.d(TAG, "✅ MediaPlayer подготовлен, длительность: $currentDuration сек")
                        start()
                        onPlaybackStateChanged(true)
                        startProgressUpdate()
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        onError("Ошибка воспроизведения (код: $what)")
                        true
                    }

                    setOnCompletionListener {
                        Log.d(TAG, "✅ Воспроизведение завершено")
                        onPlaybackStateChanged(false)
                        onCompletion()
                        stopProgressUpdate()
                        resetProgress()
                    }

                    prepareAsync()
                }
            } else {
                mediaPlayer?.apply {
                    setOnPreparedListener {
                        currentDuration = duration / 1000
                        start()
                        onPlaybackStateChanged(true)
                        startProgressUpdate()
                    }
                    prepareAsync()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка playAudioFile: ${e.message}", e)
            onError("Ошибка: ${e.message}")
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            isPaused = true
            onPlaybackStateChanged(false)
            stopProgressUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка pause: ${e.message}")
        }
    }

    fun resume() {
        try {
            mediaPlayer?.start()
            isPaused = false
            onPlaybackStateChanged(true)
            startProgressUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка resume: ${e.message}")
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentFile = null
            isPaused = false
            onPlaybackStateChanged(false)
            stopProgressUpdate()
            resetProgress()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка stop: ${e.message}")
        }
    }

    fun seekTo(progress: Int) {
        try {
            mediaPlayer?.let { mp ->
                val positionMs = progress * 1000L
                val durationMs = mp.duration.toLong()
                if (positionMs <= durationMs) {
                    mp.seekTo(positionMs.toInt())
                    Log.d(TAG, "⏩ Перемотка на $progress сек")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка seekTo: ${e.message}")
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition?.div(1000) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getDuration(): Int {
        return try {
            currentDuration
        } catch (e: Exception) {
            0
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    private fun getAmplitude(): Int {
        return try {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                val position = mp.currentPosition
                val duration = mp.duration
                if (duration > 0) {
                    val progress = position.toFloat() / duration
                    (30 + 50 * (0.5 + 0.5 * kotlin.math.sin(progress * 20 * Math.PI))).toInt()
                } else {
                    50
                }
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        updateRunnable = object : Runnable {
            override fun run() {
                try {
                    val mp = mediaPlayer
                    if (mp != null && mp.isPlaying) {
                        val position = mp.currentPosition / 1000
                        val duration = mp.duration / 1000
                        onProgress(position, duration)

                        // ✅ Обновляем Waveform
                        val amplitude = getAmplitude()
                        onWaveformUpdate?.invoke(amplitude)

                        handler.postDelayed(this, 100)
                    } else if (mp != null && !mp.isPlaying && !isPaused) {
                        stopProgressUpdate()
                    } else {
                        handler.postDelayed(this, 200)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка обновления прогресса: ${e.message}")
                    handler.postDelayed(this, 300)
                }
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopProgressUpdate() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun resetProgress() {
        onProgress(0, 0)
    }

    fun release() {
        try {
            stop()
            handler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка release: ${e.message}")
        }
    }
}
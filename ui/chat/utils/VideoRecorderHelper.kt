package com.fitnesslemon.app.ui.chat.utils

import android.content.Context
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File
import java.io.IOException

private const val TAG = "VideoRecorderHelper"

class VideoRecorderHelper(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val onRecordingComplete: (File, Int) -> Unit,
    private val onError: (String) -> Unit
) {
    private var mediaRecorder: MediaRecorder? = null
    private var camera: Camera? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var isFrontCamera = false
    private var recordingFile: File? = null

    companion object {
        private const val MAX_DURATION_SECONDS = 60
        private const val MIN_DURATION_SECONDS = 1
        private const val VIDEO_FORMAT_EXTENSION = "mp4"
        private const val VIDEO_MIME_TYPE = "video/mp4"
    }

    fun getVideoMimeType(): String = VIDEO_MIME_TYPE
    fun getVideoExtension(): String = VIDEO_FORMAT_EXTENSION

    fun startRecordingWithFile(videoFile: File, useFrontCamera: Boolean = false): Boolean {
        recordingFile = videoFile
        return startRecording(videoFile, useFrontCamera)
    }

    fun startRecording(videoFile: File, useFrontCamera: Boolean = false): Boolean {
        try {
            isFrontCamera = useFrontCamera
            recordingStartTime = System.currentTimeMillis()

            Log.d(TAG, "🎥 Начинаем запись видео в файл: ${videoFile.absolutePath}")

            if (videoFile.exists()) {
                videoFile.delete()
            }

            videoFile.parentFile?.mkdirs()

            val cameraId = if (useFrontCamera) {
                Camera.CameraInfo.CAMERA_FACING_FRONT
            } else {
                Camera.CameraInfo.CAMERA_FACING_BACK
            }

            camera = Camera.open(cameraId)
            camera?.let { cam ->
                val parameters = cam.parameters
                parameters.setPreviewSize(640, 480)
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
                cam.parameters = parameters

                cam.setDisplayOrientation(90)

                val holder = surfaceView.holder
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        try {
                            cam.setPreviewDisplay(holder)
                            cam.startPreview()
                        } catch (e: IOException) {
                            Log.e(TAG, "Ошибка: ${e.message}")
                        }
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) {}
                })

                mediaRecorder = MediaRecorder().apply {
                    cam.unlock()
                    setCamera(cam)
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.CAMERA)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setVideoSize(640, 480)
                    setVideoFrameRate(30)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setVideoEncodingBitRate(2000000)
                    setOutputFile(videoFile.absolutePath)
                    setOrientationHint(90)

                    try {
                        prepare()
                        start()
                        isRecording = true
                        Log.d(TAG, "✅ Запись видео успешно начата")
                        return true
                    } catch (e: IOException) {
                        Log.e(TAG, "IO ошибка: ${e.message}", e)
                        releaseResources()
                        onError("Ошибка записи видео: ${e.message}")
                        return false
                    }
                }
            } ?: run {
                onError("Камера не доступна")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка: ${e.message}", e)
            releaseResources()
            onError("Ошибка: ${e.message}")
            return false
        }
        // ✅ Добавляем return true если все прошло успешно
        return true
    }

    fun stopRecording(): Pair<File, Int>? {
        if (!isRecording) {
            return null
        }

        return try {
            val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            Log.d(TAG, "⏹️ Останавливаем запись видео, длительность: $durationSeconds сек")

            mediaRecorder?.apply {
                try {
                    stop()
                    Log.d(TAG, "✅ Запись видео остановлена")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка остановки: ${e.message}", e)
                }
                release()
            }
            mediaRecorder = null

            camera?.apply {
                stopPreview()
                release()
            }
            camera = null
            isRecording = false

            Thread.sleep(300)

            recordingFile?.let { file ->
                if (!file.exists()) {
                    Log.e(TAG, "❌ Файл не существует")
                    return@let null
                }

                val fileSize = file.length()
                Log.d(TAG, "📊 Файл: ${file.absolutePath}, размер: $fileSize байт, длит: $durationSeconds сек")

                if (durationSeconds < MIN_DURATION_SECONDS) {
                    Log.w(TAG, "⚠️ Запись слишком короткая: $durationSeconds сек")
                    file.delete()
                    onError("Запись слишком короткая (минимум 1 секунда)")
                    return@let null
                }

                if (durationSeconds > MAX_DURATION_SECONDS) {
                    Log.w(TAG, "⚠️ Запись слишком длинная: $durationSeconds сек")
                    file.delete()
                    onError("Запись слишком длинная (максимум 60 секунд)")
                    return@let null
                }

                Log.d(TAG, "✅ Видео записано успешно")
                onRecordingComplete(file, durationSeconds)
                return Pair(file, durationSeconds)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка: ${e.message}", e)
            releaseResources()
            onError("Ошибка остановки записи: ${e.message}")
            null
        }
    }

    private fun releaseResources() {
        try {
            mediaRecorder?.apply {
                try {
                    if (isRecording) stop()
                } catch (e: Exception) {
                    // Игнорируем
                }
                release()
            }
            mediaRecorder = null

            camera?.apply {
                try {
                    stopPreview()
                } catch (e: Exception) {
                    // Игнорируем
                }
                release()
            }
            camera = null
            isRecording = false
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка release: ${e.message}")
        }
    }

    fun cancelRecording() {
        try {
            releaseResources()
            recordingFile?.delete()
            recordingFile = null
            Log.d(TAG, "✅ Запись видео отменена")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены: ${e.message}")
        }
    }

    fun switchCamera(): Boolean {
        if (isRecording) {
            return false
        }
        isFrontCamera = !isFrontCamera
        return true
    }

    fun release() {
        try {
            cancelRecording()
            Log.d(TAG, "🧹 Ресурсы видео освобождены")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка release: ${e.message}")
        }
    }
}
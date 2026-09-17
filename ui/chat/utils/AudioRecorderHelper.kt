package com.fitnesslemon.app.ui.chat.utils

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.log10

private const val TAG = "AudioRecorderHelper"

class AudioRecorderHelper(
    private val context: android.content.Context,
    private val onAmplitudeUpdate: (Int) -> Unit,
    private val onRecordingComplete: (File, Int) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var amplitudeRunnable: Runnable? = null
    private var recordingThread: Thread? = null
    private var isStopped = false
    private var androidLame: AndroidLame? = null
    private var currentAmplitude = 0

    companion object {
        private const val MIN_DURATION_SECONDS = 2
        private const val MIN_FILE_SIZE_BYTES = 5000
        private const val AUDIO_FORMAT_EXTENSION = "mp3"
        private const val AUDIO_MIME_TYPE = "audio/mpeg"

        // Настройки аудио
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BIT_RATE = 128 // kbps
        private const val FRAME_SIZE = 1024
        private const val AMPLITUDE_UPDATE_INTERVAL = 100 // ms
    }

    fun getAudioMimeType(): String = AUDIO_MIME_TYPE
    fun getAudioExtension(): String = AUDIO_FORMAT_EXTENSION

    fun startRecording(audioFile: File): Boolean {
        try {
            // ✅ ПРОВЕРЯЕМ РАЗРЕШЕНИЕ
            if (!hasAudioPermission()) {
                Log.e(TAG, "❌ Нет разрешения на запись аудио")
                return false
            }

            recordingFile = audioFile
            recordingStartTime = System.currentTimeMillis()
            isStopped = false
            currentAmplitude = 0

            Log.d(TAG, "🎙️ Начинаем запись в файл: ${audioFile.absolutePath}")
            Log.d(TAG, "📁 Формат файла: .${AUDIO_FORMAT_EXTENSION}")
            Log.d(TAG, "📊 Используем TAndroidLame MP3 кодирование")

            if (audioFile.exists()) {
                audioFile.delete()
            }

            audioFile.parentFile?.mkdirs()

            // ✅ Инициализируем TAndroidLame
            val lameBuilder = LameBuilder()
                .setInSampleRate(SAMPLE_RATE)
                .setOutChannels(1) // Моно
                .setOutBitrate(BIT_RATE)
                .setOutSampleRate(SAMPLE_RATE)
                .setQuality(5)

            androidLame = AndroidLame(lameBuilder)

            // Создаем AudioRecord с обработкой SecurityException
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Ошибка получения bufferSize")
                return false
            }

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize * 2
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ SecurityException при создании AudioRecord: ${e.message}")
                return false
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord не инициализирован")
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "✅ Запись успешно начата")

            startAmplitudeTracking()
            startRecordingThread()

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка: ${e.message}", e)
            releaseResources()
            return false
        }
    }

    // ✅ ПРОВЕРКА РАЗРЕШЕНИЯ
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecordingThread() {
        recordingThread = thread {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val pcmBuffer = ShortArray(bufferSize)
                val mp3Buffer = ByteArray(FRAME_SIZE * 2)
                val fileOutputStream = FileOutputStream(recordingFile)

                var totalBytes = 0

                while (isRecording && !isStopped) {
                    val readBytes = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0

                    if (readBytes > 0) {
                        // Вычисляем амплитуду
                        var maxSample = 0
                        for (i in 0 until readBytes) {
                            val sample = pcmBuffer[i].toInt()
                            val absSample = if (sample < 0) -sample else sample
                            if (absSample > maxSample) {
                                maxSample = absSample
                            }
                        }
                        currentAmplitude = maxSample

                        // Кодируем PCM в MP3
                        val encodedBytes = androidLame?.encode(
                            pcmBuffer,
                            pcmBuffer,
                            readBytes,
                            mp3Buffer
                        ) ?: 0

                        if (encodedBytes > 0) {
                            fileOutputStream.write(mp3Buffer, 0, encodedBytes)
                            totalBytes += encodedBytes
                        }
                    }
                }

                // Финализируем MP3
                val flushBuffer = ByteArray(FRAME_SIZE * 2)
                val flushedBytes = androidLame?.flush(flushBuffer) ?: 0
                if (flushedBytes > 0) {
                    fileOutputStream.write(flushBuffer, 0, flushedBytes)
                    totalBytes += flushedBytes
                }

                fileOutputStream.close()
                androidLame?.close()

                Log.d(TAG, "✅ Запись завершена, общий размер: $totalBytes байт")

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка в потоке записи: ${e.message}", e)
            }
        }
    }

    private fun releaseResources() {
        try {
            isStopped = true
            isRecording = false
            recordingThread?.join(2000)

            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    try {
                        stop()
                    } catch (e: Exception) {
                        // Игнорируем
                    }
                    release()
                }
            }
            audioRecord = null
            androidLame?.close()
            androidLame = null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка release: ${e.message}")
        }
    }

    private fun startAmplitudeTracking() {
        amplitudeRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val amplitude = currentAmplitude
                    val volume = if (amplitude > 0) {
                        val normalized = (amplitude.toFloat() / 32767.0 * 100).toInt()
                        normalized.coerceIn(0, 100)
                    } else 0
                    onAmplitudeUpdate(volume)
                    handler.postDelayed(this, AMPLITUDE_UPDATE_INTERVAL.toLong())
                }
            }
        }
        handler.post(amplitudeRunnable!!)
    }

    fun stopRecording(): Pair<File, Int>? {
        if (!isRecording) {
            return null
        }

        return try {
            val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            Log.d(TAG, "⏹️ Останавливаем запись, длительность: $durationSeconds сек")

            amplitudeRunnable?.let { handler.removeCallbacks(it) }
            amplitudeRunnable = null
            onAmplitudeUpdate(0)

            isStopped = true
            isRecording = false

            recordingThread?.join(3000)

            audioRecord?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Игнорируем
                }
                release()
            }
            audioRecord = null

            androidLame?.close()
            androidLame = null

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
                    return@let null
                }

                if (fileSize < MIN_FILE_SIZE_BYTES) {
                    Log.w(TAG, "⚠️ Файл слишком маленький: $fileSize байт")
                    file.delete()
                    return@let null
                }

                if (!isMp3File(file)) {
                    Log.e(TAG, "❌ Файл не является MP3")
                    file.delete()
                    return@let null
                }

                Log.d(TAG, "✅ Файл валидный MP3")
                onRecordingComplete(file, durationSeconds)
                return Pair(file, durationSeconds)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка: ${e.message}", e)
            releaseResources()
            null
        }
    }

    private fun isMp3File(file: File): Boolean {
        return try {
            val header = file.readBytes().take(3)
            val str = String(header.toByteArray())
            str.startsWith("ID3") || (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
        } catch (e: Exception) {
            false
        }
    }

    fun cancelRecording() {
        try {
            isStopped = true
            isRecording = false

            amplitudeRunnable?.let { handler.removeCallbacks(it) }
            amplitudeRunnable = null

            audioRecord?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Игнорируем
                }
                release()
            }
            audioRecord = null

            androidLame?.close()
            androidLame = null

            recordingThread?.join(1000)
            recordingThread = null

            recordingFile?.delete()
            recordingFile = null

            Log.d(TAG, "✅ Запись отменена")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены: ${e.message}")
        }
    }

    fun release() {
        try {
            cancelRecording()
            amplitudeRunnable?.let { handler.removeCallbacks(it) }
            amplitudeRunnable = null
            Log.d(TAG, "🧹 Ресурсы освобождены")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка release: ${e.message}")
        }
    }
}
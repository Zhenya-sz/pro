package com.fitnesslemon.app.ui.chat.utils

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoCompressor {

    private const val TAG = "VideoCompressor"
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10 MB

    fun compressVideo(context: Context, inputFile: File, onProgress: (Int) -> Unit, onComplete: (File?) -> Unit) {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "📥 НАЧАЛО СЖАТИЯ ВИДЕО")
            Log.d(TAG, "========================================")

            val fileSize = inputFile.length()
            val fileSizeMB = fileSize / 1024 / 1024
            Log.d(TAG, "📁 Файл: ${inputFile.absolutePath}")
            Log.d(TAG, "📊 Исходный размер: $fileSizeMB MB (${fileSize} байт)")
            Log.d(TAG, "📊 Файл существует: ${inputFile.exists()}")
            Log.d(TAG, "📊 Файл читаемый: ${inputFile.canRead()}")

            // Проверяем, что файл существует
            if (!inputFile.exists()) {
                Log.e(TAG, "❌ Файл не существует!")
                onProgress(100)
                onComplete(null)
                return
            }

            // Проверяем, что файл не пустой
            if (fileSize < 1000) {
                Log.e(TAG, "❌ Файл слишком маленький (${fileSize} байт) - поврежден")
                onProgress(100)
                onComplete(null)
                return
            }

            // Если файл меньше лимита - пропускаем сжатие
            if (fileSize <= MAX_FILE_SIZE) {
                Log.d(TAG, "✅ Видео уже меньше лимита ($fileSizeMB MB <= 10 MB)")
                Log.d(TAG, "✅ Пропускаем сжатие, отправляем оригинал")
                onProgress(100)
                onComplete(inputFile)
                return
            }

            Log.d(TAG, "🔄 Начинаем сжатие видео...")

            val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
            Log.d(TAG, "📁 Выходной файл: ${outputFile.absolutePath}")

            // Определяем параметры сжатия
            val (bitrate, resolution) = when {
                fileSize > 100 * 1024 * 1024 -> {
                    Log.d(TAG, "📊 Файл > 100 MB - используем максимальное сжатие")
                    "500k" to "480x360"
                }
                fileSize > 50 * 1024 * 1024 -> {
                    Log.d(TAG, "📊 Файл > 50 MB - используем сильное сжатие")
                    "800k" to "640x360"
                }
                fileSize > 30 * 1024 * 1024 -> {
                    Log.d(TAG, "📊 Файл > 30 MB - используем среднее сжатие")
                    "1M" to "854x480"
                }
                else -> {
                    Log.d(TAG, "📊 Файл > 10 MB - используем легкое сжатие")
                    "1.5M" to "854x480"
                }
            }

            Log.d(TAG, "🎬 Параметры сжатия: битрейт=$bitrate, разрешение=$resolution")

            val command = arrayOf(
                "-i", inputFile.absolutePath,
                "-c:v", "libx264",
                "-b:v", bitrate,
                "-vf", "scale=$resolution",
                "-c:a", "aac",
                "-b:a", "64k",
                "-ac", "2",
                "-preset", "fast",
                "-crf", "30",
                "-movflags", "+faststart",
                "-y",
                outputFile.absolutePath
            ).joinToString(" ")

            Log.d(TAG, "📤 Команда FFmpeg:")
            Log.d(TAG, "   $command")
            Log.d(TAG, "📤 Длина команды: ${command.length} символов")

            // Запускаем сжатие
            Log.d(TAG, "⏳ Запуск FFmpeg...")
            val startTime = System.currentTimeMillis()

            FFmpegKit.executeAsync(command) { session ->
                val endTime = System.currentTimeMillis()
                val duration = (endTime - startTime) / 1000
                Log.d(TAG, "⏱️ Время выполнения FFmpeg: $duration сек")

                val returnCode = session.returnCode
                Log.d(TAG, "📊 Код возврата FFmpeg: $returnCode")

                val output = session.output
                Log.d(TAG, "📊 Вывод FFmpeg:")
                Log.d(TAG, "   $output")

                val failStackTrace = session.failStackTrace
                if (failStackTrace != null) {
                    Log.e(TAG, "❌ Stack Trace FFmpeg:")
                    Log.e(TAG, "   $failStackTrace")
                }

                // Проверяем результат
                Log.d(TAG, "📁 Проверка выходного файла...")
                Log.d(TAG, "   Путь: ${outputFile.absolutePath}")
                Log.d(TAG, "   Существует: ${outputFile.exists()}")
                Log.d(TAG, "   Размер: ${if (outputFile.exists()) outputFile.length() / 1024 else 0} KB")

                val isSuccess = ReturnCode.isSuccess(returnCode)
                Log.d(TAG, "✅ Статус успеха: $isSuccess")

                if (isSuccess && outputFile.exists() && outputFile.length() > 0) {
                    val compressedSize = outputFile.length()
                    val compressedSizeMB = compressedSize / 1024 / 1024
                    Log.d(TAG, "✅ Сжатие успешно завершено!")
                    Log.d(TAG, "📊 Размер после сжатия: $compressedSizeMB MB (${compressedSize} байт)")
                    Log.d(TAG, "📊 Сжатие в ${fileSize / compressedSize}x раз")

                    if (compressedSize > MAX_FILE_SIZE) {
                        Log.d(TAG, "⚠️ Файл все еще больше лимита ($compressedSizeMB MB > 10 MB)")
                        Log.d(TAG, "🔄 Запускаем дополнительное сжатие...")
                        compressMore(outputFile, onComplete)
                    } else {
                        Log.d(TAG, "✅ Файл после сжатия в пределах лимита")
                        onProgress(100)
                        onComplete(outputFile)
                    }
                } else {
                    Log.e(TAG, "❌ Ошибка сжатия!")
                    Log.e(TAG, "   Код возврата: $returnCode")
                    Log.e(TAG, "   Выходной файл существует: ${outputFile.exists()}")
                    Log.e(TAG, "   Размер выходного файла: ${if (outputFile.exists()) outputFile.length() else 0}")
                    Log.e(TAG, "🔄 Используем оригинальный файл")
                    onProgress(100)
                    onComplete(inputFile)
                }

                Log.d(TAG, "========================================")
                Log.d(TAG, "🏁 ЗАВЕРШЕНИЕ СЖАТИЯ")
                Log.d(TAG, "========================================")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ КРИТИЧЕСКАЯ ОШИБКА в compressVideo:")
            Log.e(TAG, "   ${e.message}")
            Log.e(TAG, "   Stack Trace: ${e.stackTraceToString()}")
            onProgress(100)
            onComplete(inputFile)
        }
    }

    private fun compressMore(inputFile: File, onComplete: (File?) -> Unit) {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "🔄 ДОПОЛНИТЕЛЬНОЕ СЖАТИЕ")
            Log.d(TAG, "========================================")
            Log.d(TAG, "📁 Входной файл: ${inputFile.absolutePath}")
            Log.d(TAG, "📊 Размер: ${inputFile.length() / 1024 / 1024} MB")

            val outputFile = File(inputFile.parent, "compressed_more_${System.currentTimeMillis()}.mp4")
            Log.d(TAG, "📁 Выходной файл: ${outputFile.absolutePath}")

            val command = arrayOf(
                "-i", inputFile.absolutePath,
                "-c:v", "libx264",
                "-b:v", "300k",
                "-vf", "scale=360x240",
                "-c:a", "aac",
                "-b:a", "48k",
                "-ac", "1",
                "-preset", "fast",
                "-crf", "34",
                "-movflags", "+faststart",
                "-y",
                outputFile.absolutePath
            ).joinToString(" ")

            Log.d(TAG, "📤 Команда дополнительного сжатия: $command")

            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode
            Log.d(TAG, "📊 Код возврата: $returnCode")

            if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "✅ Дополнительное сжатие успешно!")
                Log.d(TAG, "📊 Размер после доп. сжатия: ${outputFile.length() / 1024 / 1024} MB")
                onComplete(outputFile)
            } else {
                Log.e(TAG, "❌ Ошибка дополнительного сжатия")
                Log.e(TAG, "   Выходной файл существует: ${outputFile.exists()}")
                Log.e(TAG, "   Размер: ${if (outputFile.exists()) outputFile.length() else 0}")
                Log.e(TAG, "🔄 Используем предыдущий файл")
                onComplete(inputFile)
            }

            Log.d(TAG, "========================================")
            Log.d(TAG, "🏁 ЗАВЕРШЕНИЕ ДОП. СЖАТИЯ")
            Log.d(TAG, "========================================")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка дополнительного сжатия: ${e.message}")
            onComplete(inputFile)
        }
    }

    fun getVideoDuration(file: File): Int {
        return try {
            Log.d(TAG, "📊 Получение длительности видео: ${file.absolutePath}")
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0
            retriever.release()
            val durationSec = (duration / 1000).toInt()
            Log.d(TAG, "📊 Длительность: $durationSec сек")
            durationSec
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения длительности: ${e.message}")
            0
        }
    }
}
package com.fitnesslemon.app.ui.chat.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object MediaSaveHelper {

    private const val AUDIO_EXTENSION = "mp3"
    private const val AUDIO_MIME_TYPE = "audio/mpeg"
    private const val AUDIO_MEDIA_TYPE = "audio/mpeg"

    // ✅ НОВЫЙ МЕТОД ДЛЯ СКАЧИВАНИЯ И СОХРАНЕНИЯ ИЗОБРАЖЕНИЙ
    fun downloadAndSaveImage(context: Context, imageUrl: String, onResult: (Boolean, String) -> Unit) {
        try {
            val fileName = imageUrl.substringAfterLast("/")
            val cacheDir = context.cacheDir
            val tempFile = File(cacheDir, fileName)

            // Скачиваем изображение
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(imageUrl)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                onResult(false, "Ошибка скачивания: ${response.code}")
                return
            }

            response.body?.let { body ->
                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() < 1000) {
                onResult(false, "Файл не скачан или поврежден")
                return
            }

            // Сохраняем в галерею
            saveImageToGallery(context, tempFile.absolutePath) { success, msg ->
                tempFile.delete()
                onResult(success, msg)
            }

        } catch (e: Exception) {
            onResult(false, "Ошибка: ${e.message}")
        }
    }

    fun saveImageToGallery(context: Context, imagePath: String, onResult: (Boolean, String) -> Unit) {
        try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                onResult(false, "Файл не найден")
                return
            }

            val fileName = "fitnesslemon_${System.currentTimeMillis()}.jpg"
            val savedUri: Uri?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FitnessLemon")
                }

                savedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                savedUri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        imageFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        onResult(true, "Изображение сохранено")
                        return
                    }
                }
                onResult(false, "Не удалось сохранить изображение")
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val fitnessLemonDir = File(picturesDir, "FitnessLemon")
                if (!fitnessLemonDir.exists()) {
                    fitnessLemonDir.mkdirs()
                }

                val savedFile = File(fitnessLemonDir, fileName)
                imageFile.copyTo(savedFile, overwrite = true)

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(savedFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                onResult(true, "Изображение сохранено")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            onResult(false, "Ошибка сохранения: ${e.message}")
        }
    }

    fun saveAudioToGallery(context: Context, audioPath: String, onResult: (Boolean, String) -> Unit) {
        try {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                onResult(false, "Файл не найден")
                return
            }

            val fileName = "fitnesslemon_audio_${System.currentTimeMillis()}.$AUDIO_EXTENSION"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, AUDIO_MIME_TYPE)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/FitnessLemon")
                }

                val savedUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)

                savedUri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        audioFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        onResult(true, "Аудио сохранено")
                        return
                    }
                }
                onResult(false, "Не удалось сохранить аудио")
            } else {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val fitnessLemonDir = File(musicDir, "FitnessLemon")
                if (!fitnessLemonDir.exists()) {
                    fitnessLemonDir.mkdirs()
                }

                val savedFile = File(fitnessLemonDir, fileName)
                audioFile.copyTo(savedFile, overwrite = true)

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(savedFile.absolutePath),
                    arrayOf(AUDIO_MEDIA_TYPE),
                    null
                )

                onResult(true, "Аудио сохранено")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            onResult(false, "Ошибка сохранения: ${e.message}")
        }
    }
}
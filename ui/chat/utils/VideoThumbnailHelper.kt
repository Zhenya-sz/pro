package com.fitnesslemon.app.ui.chat.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object VideoThumbnailHelper {

    private const val TAG = "VideoThumbnailHelper"

    /**
     * Создает превью из видеофайла
     */
    fun createThumbnail(videoPath: String, context: Context): Bitmap? {
        return try {
            Log.d(TAG, "🎬 Создание превью для: $videoPath")

            val retriever = MediaMetadataRetriever()

            if (videoPath.startsWith("http")) {
                retriever.release()
                return null
            }

            val file = File(videoPath)
            if (!file.exists()) {
                Log.e(TAG, "❌ Файл не существует: $videoPath")
                retriever.release()
                return null
            }

            retriever.setDataSource(videoPath)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val frameTime = if (duration > 0) duration / 2 else 0

            val bitmap = retriever.getFrameAtTime(frameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()

            if (bitmap != null) {
                val cachedFile = cacheThumbnail(bitmap, videoPath, context)
                Log.d(TAG, "✅ Превью создано: ${cachedFile?.absolutePath}")
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка создания превью: ${e.message}", e)
            null
        }
    }

    private fun cacheThumbnail(bitmap: Bitmap, videoPath: String, context: Context): File? {
        return try {
            val cacheDir = File(context.cacheDir, "video_thumbnails")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val fileName = videoPath.substringAfterLast("/").replace(".", "_") + ".jpg"
            val file = File(cacheDir, fileName)

            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            }

            file
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка кэширования: ${e.message}")
            null
        }
    }
}
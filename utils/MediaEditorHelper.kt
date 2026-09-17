package com.fitnesslemon.app.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MediaEditorHelper(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val TAG = "MediaEditorHelper"
        private const val MAX_IMAGE_SIZE = 1080
        private const val MAX_IMAGE_HEIGHT = 1920
        private const val COMPRESS_QUALITY = 85
        private const val MAX_VIDEO_SIZE = 50 * 1024 * 1024 // 50MB
    }

    /**
     * Получение длительности видео в секундах
     */
    fun getVideoDuration(uri: Uri): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toIntOrNull()?.div(1000) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video duration", e)
            0
        }
    }

    /**
     * Сжатие изображения БЕЗ обрезки (сохраняет пропорции)
     */
    fun compressImage(uri: Uri, maxWidth: Int = MAX_IMAGE_SIZE, maxHeight: Int = MAX_IMAGE_HEIGHT): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Вычисляем коэффициент сжатия
            var scale = 1
            while (options.outWidth / scale > maxWidth || options.outHeight / scale > maxHeight) {
                scale *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val inputStream2 = contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2.close()

            if (bitmap == null) return null

            // Сохраняем в кэш
            val cacheDir = context.cacheDir
            val outputFile = File(cacheDir, "compressed_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, out)
            }
            bitmap.recycle()

            Log.d(TAG, "Image compressed: ${outputFile.length() / 1024} KB")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            null
        }
    }

    /**
     * Сжатие изображения с обрезкой до квадрата (для сторис)
     */
    fun compressAndCropImage(uri: Uri, cropAspectRatio: Float = 1f): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Определяем размеры для обрезки
            val width = options.outWidth
            val height = options.outHeight

            // Вычисляем размер квадрата (меньшая сторона)
            val cropSize = if (width < height) width else height

            // Вычисляем смещение для центральной обрезки
            val x = (width - cropSize) / 2
            val y = (height - cropSize) / 2

            // Загружаем изображение с уменьшением
            var scale = 1
            while (options.outWidth / scale > MAX_IMAGE_SIZE || options.outHeight / scale > MAX_IMAGE_SIZE) {
                scale *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val inputStream2 = contentResolver.openInputStream(uri) ?: return null
            val fullBitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2.close()

            if (fullBitmap == null) return null

            // Обрезаем до квадрата
            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap,
                (x / scale).toInt(),
                (y / scale).toInt(),
                (cropSize / scale).toInt(),
                (cropSize / scale).toInt()
            )

            fullBitmap.recycle()

            val cacheDir = context.cacheDir
            val outputFile = File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, out)
            }
            croppedBitmap.recycle()

            Log.d(TAG, "Image cropped and compressed: ${outputFile.length() / 1024} KB")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping image", e)
            null
        }
    }

    /**
     * Сжатие видео с обрезкой (заглушка, требуется FFmpeg или другая библиотека)
     */
    fun compressAndCropVideo(uri: Uri): File? {
        return try {
            // Просто копируем видео в кэш с ограничением размера
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            val cacheDir = context.cacheDir
            val outputFile = File(cacheDir, "video_${System.currentTimeMillis()}.mp4")

            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalSize = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (totalSize + bytesRead > MAX_VIDEO_SIZE) {
                        Log.w(TAG, "Video too large, truncating")
                        break
                    }
                    output.write(buffer, 0, bytesRead)
                    totalSize += bytesRead
                }
            }
            inputStream.close()

            Log.d(TAG, "Video processed: ${outputFile.length() / 1024 / 1024} MB")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error processing video", e)
            null
        }
    }

    /**
     * Сжатие видео без обрезки
     */
    fun compressVideo(uri: Uri): File? {
        return compressAndCropVideo(uri)
    }

    /**
     * Поворот изображения
     */
    fun rotateImage(uri: Uri, degrees: Int): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            var scale = 1
            while (options.outWidth / scale > MAX_IMAGE_SIZE || options.outHeight / scale > MAX_IMAGE_SIZE) {
                scale *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val inputStream2 = contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2.close()

            if (bitmap == null) return null

            val matrix = Matrix()
            matrix.postRotate(degrees.toFloat())

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }

            val cacheDir = context.cacheDir
            val outputFile = File(cacheDir, "rotated_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, out)
            }
            rotatedBitmap.recycle()

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating image", e)
            null
        }
    }

    /**
     * Получение размера файла в байтах
     */
    fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fd.statSize
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size", e)
            0L
        }
    }

    /**
     * Получение MIME-типа файла
     */
    fun getMimeType(uri: Uri): String? {
        return try {
            contentResolver.getType(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MIME type", e)
            null
        }
    }

    /**
     * Проверка, является ли файл изображением
     */
    fun isImage(uri: Uri): Boolean {
        val mimeType = getMimeType(uri)
        return mimeType?.startsWith("image/") == true
    }

    /**
     * Проверка, является ли файл видео
     */
    fun isVideo(uri: Uri): Boolean {
        val mimeType = getMimeType(uri)
        return mimeType?.startsWith("video/") == true
    }

    /**
     * Создание копии файла в кэше
     */
    fun copyToCache(uri: Uri, fileName: String? = null): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            val cacheDir = context.cacheDir
            val name = fileName ?: "copy_${System.currentTimeMillis()}.jpg"
            val outputFile = File(cacheDir, name)

            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
            inputStream.close()

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file to cache", e)
            null
        }
    }

    /**
     * Удаление файла
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            false
        }
    }

    /**
     * Очистка кэша
     */
    fun clearCache(): Boolean {
        return try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("compressed_") ||
                    file.name.startsWith("cropped_") ||
                    file.name.startsWith("rotated_") ||
                    file.name.startsWith("video_") ||
                    file.name.startsWith("copy_")
                ) {
                    file.delete()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
            false
        }
    }

    /**
     * Получение размера кэша в байтах
     */
    fun getCacheSize(): Long {
        return try {
            val cacheDir = context.cacheDir
            var size = 0L
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
            size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache size", e)
            0L
        }
    }
}
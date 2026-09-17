package com.fitnesslemon.app.ui.chat.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageCompressor {

    private const val MAX_WIDTH = 1280
    private const val MAX_HEIGHT = 1280
    private const val QUALITY = 80

    fun compressImage(
        contentResolver: ContentResolver,
        imageUri: Uri,
        outputFile: File
    ): Boolean {
        return try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val compressedBitmap = if (bitmap.width > MAX_WIDTH || bitmap.height > MAX_HEIGHT) {
                val scale = minOf(
                    MAX_WIDTH.toFloat() / bitmap.width,
                    MAX_HEIGHT.toFloat() / bitmap.height
                )
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            val outputStream = FileOutputStream(outputFile)
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()

            if (compressedBitmap != bitmap) {
                compressedBitmap.recycle()
            }
            bitmap.recycle()

            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun compressImageFromFile(inputFile: File, outputFile: File): Boolean {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)

            var scale = 1
            while (options.outWidth / scale > MAX_WIDTH || options.outHeight / scale > MAX_HEIGHT) {
                scale++
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = scale

            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)

            val outputStream = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()

            bitmap.recycle()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}
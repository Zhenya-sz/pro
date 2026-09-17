package com.fitnesslemon.app.ui.stories

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.ActivityStoryEditorBinding
import com.fitnesslemon.app.utils.MediaEditorHelper
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StoryEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryEditorBinding
    private lateinit var mediaHelper: MediaEditorHelper

    private var sourceUri: Uri? = null
    private var isVideo = false
    private var croppedImageUri: Uri? = null
    private var storyText: String = ""
    private var storyTitle: String = ""

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            sourceUri = it
            isVideo = contentResolver.getType(it)?.startsWith("video/") == true
            loadMedia(it)
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                sourceUri = uri
                isVideo = false
                loadMedia(uri)
            }
        } else {
            Toast.makeText(this, "Фото не сделано", Toast.LENGTH_SHORT).show()
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            resultUri?.let {
                croppedImageUri = it
                binding.ivPreview.setImageURI(it)
                binding.btnCrop.visibility = View.GONE
                binding.btnUpload.isEnabled = true
                Toast.makeText(this, "✅ Изображение обрезано", Toast.LENGTH_SHORT).show()
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = UCrop.getError(result.data!!)
            Toast.makeText(this, "Ошибка обрезки: ${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private var photoUri: Uri? = null

    companion object {
        private const val MAX_FILE_SIZE = 20 * 1024 * 1024 // 20 MB
        private const val TAG = "StoryEditor"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoryEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaHelper = MediaEditorHelper(this)

        setupViews()
        setupClickListeners()

        intent.getStringExtra("media_uri")?.let { uriString ->
            sourceUri = Uri.parse(uriString)
            isVideo = intent.getBooleanExtra("is_video", false)
            loadMedia(sourceUri!!)
        }
    }

    private fun setupViews() {
        binding.apply {
            if (isVideo) {
                btnCrop.visibility = View.GONE
                btnPlayVideo.visibility = View.VISIBLE
                layoutDuration.visibility = View.GONE
                layoutTrim.visibility = View.GONE
                btnUpload.isEnabled = true
            } else {
                btnCrop.visibility = View.VISIBLE
                btnPlayVideo.visibility = View.GONE
                layoutDuration.visibility = View.GONE
                layoutTrim.visibility = View.GONE
                btnUpload.isEnabled = true
            }

            layoutText.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            toolbar.setNavigationOnClickListener {
                finish()
            }

            btnPickImage.setOnClickListener {
                showMediaPicker()
            }

            btnCrop.setOnClickListener {
                sourceUri?.let { uri ->
                    startCrop(uri)
                }
            }

            btnUpload.setOnClickListener {
                uploadStory()
            }

            btnPlayVideo.setOnClickListener {
                sourceUri?.let { uri ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }
            }

            btnAddText.setOnClickListener {
                binding.etText.requestFocus()
                binding.etText.performClick()
            }
        }
    }

    private fun showMediaPicker() {
        val options = arrayOf("📷 Сделать фото", "🎥 Выбрать из галереи", "🎬 Выбрать видео")

        AlertDialog.Builder(this)
            .setTitle("Выберите медиа")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> pickMediaLauncher.launch("image/*")
                    2 -> pickMediaLauncher.launch("video/*")
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            openCamera()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Нет доступа к камере", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        try {
            val photoFile = File(cacheDir, "story_${System.currentTimeMillis()}.jpg")
            photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            takePhotoLauncher.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // ✅ ИСПРАВЛЕННАЯ ЗАГРУЗКА МЕДИА - ВИДЕО НЕ РАСТЯНУТО
    // ============================================================

    private fun loadMedia(uri: Uri) {
        val isImage = contentResolver.getType(uri)?.startsWith("image/") == true

        if (isImage) {
            binding.ivPreview.setImageURI(uri)
            binding.btnUpload.isEnabled = true
            binding.btnCrop.visibility = View.VISIBLE
            binding.ivPreview.visibility = View.VISIBLE
            binding.videoView.visibility = View.GONE
            binding.btnPlayVideo.visibility = View.GONE
            binding.layoutDuration.visibility = View.GONE
            binding.layoutTrim.visibility = View.GONE
        } else {
            // ✅ ПРАВИЛЬНОЕ ОТОБРАЖЕНИЕ ВИДЕО - НЕ РАСТЯНУТО
            binding.videoView.apply {
                visibility = View.VISIBLE
                setVideoURI(uri)

                // Добавляем MediaController для управления
                val mediaController = android.widget.MediaController(this@StoryEditorActivity)
                setMediaController(mediaController)
                mediaController.setAnchorView(this)

                // Настраиваем правильное отображение
                setOnPreparedListener { mediaPlayer ->
                    // Сохраняем пропорции видео
                    val videoWidth = mediaPlayer.videoWidth
                    val videoHeight = mediaPlayer.videoHeight

                    if (videoWidth > 0 && videoHeight > 0) {
                        // ✅ Используем FrameLayout.LayoutParams для правильного отображения
                        val layoutParams = android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        // Центрируем видео
                        layoutParams.gravity = android.view.Gravity.CENTER
                        this.layoutParams = layoutParams
                        this.requestLayout()
                    }

                    // Получаем длительность
                    val duration = mediaHelper.getVideoDuration(uri)
                    binding.tvDuration.text = "Длительность: ${duration} сек"

                    // Автоматически запускаем видео
                    mediaPlayer.start()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ Ошибка видео: what=$what, extra=$extra")
                    Toast.makeText(this@StoryEditorActivity, "Ошибка воспроизведения видео", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            binding.ivPreview.visibility = View.GONE
            binding.btnPlayVideo.visibility = View.VISIBLE
            binding.btnUpload.isEnabled = true
            binding.btnCrop.visibility = View.GONE
            binding.layoutDuration.visibility = View.GONE
            binding.layoutTrim.visibility = View.GONE
        }
    }

    private fun startCrop(uri: Uri) {
        try {
            val destinationFile = File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            val destinationUri = Uri.fromFile(destinationFile)

            val options = UCrop.Options().apply {
                setCircleDimmedLayer(false)
                setShowCropFrame(true)
                setShowCropGrid(true)
                setCompressionQuality(80)
                setToolbarColor(Color.parseColor("#017445"))
                setStatusBarColor(Color.parseColor("#017445"))
                setHideBottomControls(false)
                setFreeStyleCropEnabled(true)
                setToolbarTitle("Обрезка изображения")
            }

            val uCropIntent = UCrop.of(uri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1080, 1080)
                .withOptions(options)
                .getIntent(this)

            cropLauncher.launch(uCropIntent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка открытия редактора: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Video.Media.DATA)
                val cursor = contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    if (it.moveToFirst()) {
                        return it.getString(columnIndex)
                    }
                }
            }

            val fileName = "video_${System.currentTimeMillis()}.mp4"
            val cacheFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists()) {
                cacheFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun addTextToBitmap(source: Bitmap, text: String): Bitmap? {
        if (source == null) {
            return null
        }

        val resultBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 80f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(10f, 0f, 5f, Color.BLACK)
        }

        val bgPaint = Paint().apply {
            color = Color.BLACK
            alpha = 150
        }

        val canvasWidth = canvas.width
        val canvasHeight = canvas.height
        val maxWidth = canvasWidth - 80

        val lines = mutableListOf<String>()
        var currentLine = ""
        var currentWidth = 0f

        text.split(" ").forEach { word ->
            val wordWidth = paint.measureText("$word ")
            if (currentWidth + wordWidth < maxWidth) {
                currentLine += "$word "
                currentWidth += wordWidth
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.trim())
                }
                currentLine = "$word "
                currentWidth = paint.measureText("$word ")
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.trim())
        }

        val lineHeight = paint.textSize + 20
        val totalHeight = lines.size * lineHeight + (lines.size - 1) * 10
        val startY = canvasHeight - totalHeight - 100

        var y = startY
        lines.forEach { line ->
            val rect = android.graphics.Rect()
            paint.getTextBounds(line, 0, line.length, rect)
            val bgRect = android.graphics.Rect(
                (canvasWidth / 2 - rect.width() / 2 - 40),
                (y - paint.textSize - 20).toInt(),
                (canvasWidth / 2 + rect.width() / 2 + 40),
                (y + 20).toInt()
            )
            canvas.drawRect(bgRect, bgPaint)

            canvas.drawText(line, canvasWidth / 2f, y, paint)
            y += lineHeight + 10
        }

        return resultBitmap
    }

    // ============================================================
    // ✅ СЖАТИЕ ВИДЕО С БОЛЕЕ АГРЕССИВНЫМИ НАСТРОЙКАМИ
    // ============================================================

    private fun compressVideo(inputFile: File): File? {
        return try {
            val fileSize = inputFile.length()
            Log.d(TAG, "📊 Исходный размер видео: ${fileSize / 1024 / 1024} MB")

            if (fileSize <= MAX_FILE_SIZE) {
                Log.d(TAG, "✅ Видео уже меньше лимита")
                return inputFile
            }

            val outputFile = File(cacheDir, "compressed_video_${System.currentTimeMillis()}.mp4")

            // ✅ Более агрессивные настройки сжатия
            val bitrate = when {
                fileSize > 150 * 1024 * 1024 -> "1M"   // > 150 MB
                fileSize > 100 * 1024 * 1024 -> "1.5M" // > 100 MB
                fileSize > 50 * 1024 * 1024 -> "2M"    // > 50 MB
                fileSize > 30 * 1024 * 1024 -> "3M"    // > 30 MB
                else -> "4M"                           // > 20 MB
            }

            val resolution = when {
                fileSize > 150 * 1024 * 1024 -> "480x360"   // 360p
                fileSize > 100 * 1024 * 1024 -> "640x360"   // 360p
                fileSize > 50 * 1024 * 1024 -> "854x480"    // 480p
                else -> "854x480"                           // 480p
            }

            // ✅ Добавляем сжатие аудио и более агрессивный CRF
            val command = arrayOf(
                "-i", inputFile.absolutePath,
                "-c:v", "libx264",
                "-b:v", bitrate,
                "-vf", "scale=$resolution",
                "-c:a", "aac",
                "-b:a", "96k",          // Уменьшаем битрейт аудио
                "-ac", "2",             // Стерео
                "-preset", "fast",
                "-crf", "28",           // Более агрессивное сжатие
                "-movflags", "+faststart",
                "-y",
                outputFile.absolutePath
            ).joinToString(" ")

            Log.d(TAG, "🎬 Команда сжатия видео: $command")

            val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode)) {
                val compressedSize = outputFile.length()
                Log.d(TAG, "✅ Видео сжато: ${compressedSize / 1024 / 1024} MB")

                if (outputFile.exists() && outputFile.length() > 0) {
                    // ✅ Если все еще больше лимита, пробуем еще раз с более сильным сжатием
                    if (outputFile.length() > MAX_FILE_SIZE) {
                        Log.d(TAG, "⚠️ Видео все еще больше лимита, пробуем более сильное сжатие...")
                        return compressVideoMore(outputFile)
                    }
                    outputFile
                } else {
                    Log.e(TAG, "❌ Файл после сжатия пустой")
                    inputFile
                }
            } else {
                Log.e(TAG, "❌ Ошибка сжатия видео, код: $returnCode")
                // Пробуем альтернативный метод
                compressVideoAlternative(inputFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка при сжатии: ${e.message}")
            inputFile
        }
    }

    // ✅ ДОПОЛНИТЕЛЬНОЕ СЖАТИЕ ДЛЯ БОЛЬШИХ ФАЙЛОВ
    private fun compressVideoMore(inputFile: File): File? {
        return try {
            val outputFile = File(cacheDir, "compressed_video_more_${System.currentTimeMillis()}.mp4")

            val command = arrayOf(
                "-i", inputFile.absolutePath,
                "-c:v", "libx264",
                "-b:v", "800k",
                "-vf", "scale=480x360",
                "-c:a", "aac",
                "-b:a", "64k",
                "-ac", "1",
                "-preset", "fast",
                "-crf", "32",
                "-movflags", "+faststart",
                "-y",
                outputFile.absolutePath
            ).joinToString(" ")

            Log.d(TAG, "🎬 Дополнительное сжатие: $command")

            val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode) &&
                outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "✅ Дополнительное сжатие завершено: ${outputFile.length() / 1024 / 1024} MB")
                outputFile
            } else {
                inputFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка дополнительного сжатия: ${e.message}")
            inputFile
        }
    }

    // ✅ АЛЬТЕРНАТИВНЫЙ МЕТОД СЖАТИЯ
    private fun compressVideoAlternative(inputFile: File): File? {
        return try {
            val outputFile = File(cacheDir, "compressed_alt_${System.currentTimeMillis()}.mp4")

            val command = arrayOf(
                "-i", inputFile.absolutePath,
                "-c:v", "libx264",
                "-vf", "scale=640:360",
                "-c:a", "copy",
                "-crf", "30",
                "-preset", "ultrafast",
                "-y",
                outputFile.absolutePath
            ).joinToString(" ")

            Log.d(TAG, "🎬 Альтернативное сжатие: $command")

            val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode) &&
                outputFile.exists() && outputFile.length() > 0) {
                outputFile
            } else {
                inputFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка альтернативного сжатия: ${e.message}")
            inputFile
        }
    }

    // ============================================================
    // ✅ СЖАТИЕ ИЗОБРАЖЕНИЯ
    // ============================================================

    private fun compressImage(inputFile: File): File? {
        return try {
            val fileSize = inputFile.length()
            Log.d(TAG, "📊 Исходный размер изображения: ${fileSize / 1024} KB")

            if (fileSize <= MAX_FILE_SIZE) {
                return inputFile
            }

            val outputFile = File(cacheDir, "compressed_image_${System.currentTimeMillis()}.jpg")

            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)

            if (bitmap != null) {
                val quality = when {
                    fileSize > 50 * 1024 * 1024 -> 40
                    fileSize > 30 * 1024 * 1024 -> 50
                    fileSize > 20 * 1024 * 1024 -> 60
                    else -> 70
                }

                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                bitmap.recycle()

                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.d(TAG, "✅ Изображение сжато: ${outputFile.length() / 1024} KB")
                    outputFile
                } else {
                    inputFile
                }
            } else {
                inputFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сжатия изображения: ${e.message}")
            inputFile
        }
    }

    // ============================================================
    // ✅ ИСПРАВЛЕННЫЙ МЕТОД ЗАГРУЗКИ
    // ============================================================

    private fun uploadStory() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                binding.btnUpload.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
                binding.tvProgress.text = "Подготовка медиа..."

                storyText = binding.etText.text.toString().trim()
                storyTitle = binding.etTitle.text.toString().trim()

                val resultFile = withContext(Dispatchers.IO) {
                    if (isVideo) {
                        sourceUri?.let { uri ->
                            val path = getRealPathFromUri(uri)
                            Log.d(TAG, "📁 Путь к видео: $path")
                            if (path != null) {
                                val file = File(path)
                                Log.d(TAG, "📊 Размер файла: ${file.length() / 1024 / 1024} MB")
                                if (file.length() > MAX_FILE_SIZE) {
                                    binding.tvProgress.post {
                                        binding.tvProgress.text = "Сжатие видео..."
                                    }
                                    compressVideo(file)
                                } else {
                                    file
                                }
                            } else {
                                null
                            }
                        }
                    } else {
                        val uriToUse = croppedImageUri ?: sourceUri
                        if (uriToUse != null) {
                            val processedFile = mediaHelper.compressAndCropImage(uriToUse, cropAspectRatio = 1f)
                            if (processedFile != null && processedFile.length() > MAX_FILE_SIZE) {
                                binding.tvProgress.post {
                                    binding.tvProgress.text = "Сжатие изображения..."
                                }
                                compressImage(processedFile)
                            } else {
                                processedFile
                            }
                        } else {
                            null
                        }
                    }
                }

                binding.tvProgress.text = "Загрузка..."

                if (resultFile != null && resultFile.exists() && resultFile.length() > 0) {
                    val finalSize = resultFile.length()
                    Log.d(TAG, "📁 Финальный размер: ${finalSize / 1024} KB (${finalSize / 1024 / 1024} MB)")

                    if (finalSize > MAX_FILE_SIZE) {
                        Toast.makeText(
                            this@StoryEditorActivity,
                            "⚠️ Файл слишком большой (${finalSize / 1024 / 1024} MB). \nПопробуйте выбрать более короткое видео.",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnUpload.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        return@launch
                    }

                    val intent = Intent().apply {
                        putExtra("media_file_path", resultFile.absolutePath)
                        putExtra("is_video", isVideo)
                        putExtra("story_text", storyText)
                        putExtra("story_title", storyTitle)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@StoryEditorActivity,
                        "Не удалось обработать медиа. Попробуйте другое.",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnUpload.isEnabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@StoryEditorActivity,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                binding.btnUpload.isEnabled = true
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.tvProgress.text = ""
            }
        }
    }
}
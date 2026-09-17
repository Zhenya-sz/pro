package com.fitnesslemon.app.ui.chat.dialogs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Chat
import com.fitnesslemon.app.data.models.Message
import com.fitnesslemon.app.utils.PreferencesManager
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class ImageViewerDialog : DialogFragment() {

    private var imageUrl: String? = null
    private var message: Message? = null
    private var photoView: PhotoView? = null
    private var progressBar: ProgressBar? = null
    private var btnClose: ImageButton? = null
    private var btnShare: ImageButton? = null
    private var btnForward: ImageButton? = null
    private var btnSave: ImageButton? = null
    private var tvFileName: TextView? = null
    private var tvFileSize: TextView? = null
    private var isAnimating = false
    private val handler = Handler(Looper.getMainLooper())

    private var coroutineJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val ARG_IMAGE_URL = "image_url"
        private const val ARG_MESSAGE = "message"
        private const val TAG = "ImageViewerDialog"

        fun newInstance(imageUrl: String, message: Message? = null): ImageViewerDialog {
            val fragment = ImageViewerDialog()
            val args = Bundle()
            args.putString(ARG_IMAGE_URL, imageUrl)
            if (message != null) {
                args.putString(ARG_MESSAGE, message.id.toString())
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        arguments?.let {
            imageUrl = it.getString(ARG_IMAGE_URL)
            val messageId = it.getString(ARG_MESSAGE)?.toIntOrNull()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_image_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        photoView = view.findViewById(R.id.photoView)
        progressBar = view.findViewById(R.id.progressBar)
        btnClose = view.findViewById(R.id.btnClose)
        btnShare = view.findViewById(R.id.btnShare)
        btnForward = view.findViewById(R.id.btnForward)
        btnSave = view.findViewById(R.id.btnSave)
        tvFileName = view.findViewById(R.id.tvFileName)
        tvFileSize = view.findViewById(R.id.tvFileSize)

        progressBar?.visibility = View.GONE
        photoView?.alpha = 0f

        setupButtons()
        loadImage()

        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun setupButtons() {
        btnClose?.setOnClickListener {
            dismissWithAnimation()
        }

        btnShare?.setOnClickListener {
            shareImage()
        }

        btnForward?.setOnClickListener {
            showForwardDialog()
        }

        btnSave?.setOnClickListener {
            saveImage()
        }

        photoView?.setOnClickListener {
            dismissWithAnimation()
        }
    }

    private fun loadImage() {
        val url = imageUrl
        if (url.isNullOrEmpty()) {
            val ctx = context
            if (ctx != null) {
                Toast.makeText(ctx, "Ссылка на изображение отсутствует", Toast.LENGTH_SHORT).show()
            }
            dismiss()
            return
        }

        Log.d(TAG, "Загрузка изображения: $url")

        val fileName = url.substringAfterLast("/")
        tvFileName?.text = fileName

        getFileSize(url) { size ->
            tvFileSize?.text = formatFileSize(size)
        }

        Glide.with(this)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e(TAG, "Ошибка загрузки: ${e?.message}", e)
                    val ctx = context
                    if (ctx != null) {
                        Toast.makeText(ctx, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                    }
                    dismiss()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅ Изображение загружено")
                    photoView?.alpha = 0f
                    photoView?.animate()
                        ?.alpha(1f)
                        ?.setDuration(400)
                        ?.setInterpolator(DecelerateInterpolator())
                        ?.start()
                    return false
                }
            })
            .into(photoView!!)
    }

    private fun getFileSize(url: String, callback: (Long) -> Unit) {
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()

                val response = client.newCall(request).execute()
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                response.close()

                handler.post {
                    callback(contentLength)
                }
            } catch (e: Exception) {
                handler.post {
                    callback(0L)
                }
            }
        }.start()
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    // ===== ПЕРЕСЫЛКА =====
    private fun showForwardDialog() {
        loadChatsForForward()
    }

    private fun loadChatsForForward() {
        val ctx = context
        if (ctx == null) {
            return
        }

        Toast.makeText(ctx, "Загрузка чатов...", Toast.LENGTH_SHORT).show()

        coroutineJob = coroutineScope.launch {
            try {
                val chats = getChats()
                withContext(Dispatchers.Main) {
                    if (!isAdded || isDetached || view == null || context == null) {
                        return@withContext
                    }
                    if (chats.isNotEmpty()) {
                        showChatSelectorDialog(chats)
                    } else {
                        Toast.makeText(context, "Нет чатов для пересылки", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded || isDetached || view == null || context == null) {
                        return@withContext
                    }
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getChats(): List<Chat> {
        return withContext(Dispatchers.IO) {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    return@withContext emptyList()
                }

                val response = com.fitnesslemon.app.data.api.ApiClient.apiService.getChats("Bearer $token")
                if (response.isSuccessful) {
                    val chatsResponse = response.body()
                    if (chatsResponse != null && chatsResponse.success) {
                        return@withContext chatsResponse.data.filter { it.id != message?.chatId }
                    }
                }
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки чатов: ${e.message}", e)
                emptyList()
            }
        }
    }

    private fun showChatSelectorDialog(chats: List<Chat>) {
        val ctx = context ?: return

        val chatNames = chats.map { chat ->
            val name = chat.name ?: chat.participant?.name ?: "Чат ${chat.id}"
            "$name (${if (chat.type == "group") "Группа" else "Личный"})"
        }.toTypedArray()

        AlertDialog.Builder(ctx)
            .setTitle("Переслать в чат")
            .setItems(chatNames) { _, which ->
                val selectedChat = chats[which]
                forwardToChat(selectedChat)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun forwardToChat(chat: Chat) {
        val ctx = context
        if (ctx == null) {
            return
        }

        Toast.makeText(ctx, "Пересылка в чат ${chat.name ?: chat.participant?.name ?: chat.id}...", Toast.LENGTH_SHORT).show()

        coroutineJob = coroutineScope.launch {
            try {
                val localFile = downloadImage(imageUrl ?: "")
                if (localFile != null && localFile.exists()) {
                    sendImageToChat(chat.id, localFile)
                } else {
                    if (context != null) {
                        Toast.makeText(context, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (context != null) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun sendImageToChat(chatId: Int, file: File) {
        withContext(Dispatchers.IO) {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (isAdded && !isDetached && view != null && context != null) {
                            Toast.makeText(context, "Токен не найден", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@withContext
                }

                val mimeType = "image/jpeg".toMediaType()
                val requestFile = file.asRequestBody(mimeType)
                val imagePart = MultipartBody.Part.createFormData("image", file.name, requestFile)
                val chatIdPart = chatId.toString().toRequestBody(MultipartBody.FORM)

                val response = com.fitnesslemon.app.data.api.ApiClient.apiService.sendImageMessage(
                    token = "Bearer $token",
                    chatId = chatIdPart,
                    image = imagePart
                )

                withContext(Dispatchers.Main) {
                    if (!isAdded || isDetached || view == null || context == null) {
                        return@withContext
                    }
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(context, "Изображение переслано!", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } else {
                        Toast.makeText(context, "Ошибка пересылки: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded && !isDetached && view != null && context != null) {
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ===== ОТПРАВКА/СОХРАНЕНИЕ =====
    private fun shareImage() {
        val url = imageUrl
        if (url.isNullOrEmpty()) {
            val ctx = context
            if (ctx != null) {
                Toast.makeText(ctx, "Нет изображения для отправки", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val ctx = context
        if (ctx == null) {
            return
        }

        Toast.makeText(ctx, "Загрузка для отправки...", Toast.LENGTH_SHORT).show()

        coroutineJob = coroutineScope.launch {
            try {
                val localFile = downloadImage(url)
                if (localFile != null && localFile.exists()) {
                    shareFile(localFile)
                } else {
                    if (context != null) {
                        Toast.makeText(context, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (context != null) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImage() {
        val url = imageUrl
        if (url.isNullOrEmpty()) {
            val ctx = context
            if (ctx != null) {
                Toast.makeText(ctx, "Нет изображения для сохранения", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val ctx = context
        if (ctx == null) {
            return
        }

        Toast.makeText(ctx, "Сохранение...", Toast.LENGTH_SHORT).show()

        coroutineJob = coroutineScope.launch {
            try {
                val localFile = downloadImage(url)
                if (localFile != null && localFile.exists()) {
                    saveToGallery(localFile)
                } else {
                    if (context != null) {
                        Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (context != null) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun downloadImage(url: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    return@withContext null
                }

                val fileName = url.substringAfterLast("/")
                val cacheDir = requireContext().cacheDir
                val tempFile = File(cacheDir, fileName)

                response.body?.let { body ->
                    tempFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                response.close()

                if (tempFile.exists() && tempFile.length() > 0) {
                    return@withContext tempFile
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки: ${e.message}", e)
                null
            }
        }
    }

    private fun shareFile(file: File) {
        try {
            val ctx = context ?: return

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Отправить изображение"))
        } catch (e: Exception) {
            val ctx = context
            if (ctx != null) {
                Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToGallery(file: File) {
        try {
            val ctx = context ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FitnessLemon")
                }

                val uri = resolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    resolver.openOutputStream(it)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    Toast.makeText(ctx, "Изображение сохранено", Toast.LENGTH_SHORT).show()
                    file.delete()
                    return
                }
                Toast.makeText(ctx, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val fitnessLemonDir = File(picturesDir, "FitnessLemon")
                if (!fitnessLemonDir.exists()) {
                    fitnessLemonDir.mkdirs()
                }

                val savedFile = File(fitnessLemonDir, file.name)
                file.copyTo(savedFile, overwrite = true)

                android.media.MediaScannerConnection.scanFile(
                    ctx,
                    arrayOf(savedFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                Toast.makeText(ctx, "Изображение сохранено", Toast.LENGTH_SHORT).show()
                file.delete()
            }
        } catch (e: Exception) {
            val ctx = context
            if (ctx != null) {
                Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dismissWithAnimation() {
        if (isAnimating) return
        isAnimating = true

        val view = view
        if (view != null) {
            view.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        dismiss()
                    }
                })
                .start()
        } else {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        photoView = null
        progressBar = null
        btnClose = null
        btnShare = null
        btnForward = null
        btnSave = null
        tvFileName = null
        tvFileSize = null
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineJob?.cancel()
        coroutineJob = null
        handler.removeCallbacksAndMessages(null)
    }
}
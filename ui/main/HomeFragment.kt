package com.fitnesslemon.app.ui.main

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentHomeBinding
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.News
import com.fitnesslemon.app.ui.adapters.NewsCardAdapter
import com.fitnesslemon.app.ui.adapters.StoryAdapter
import com.fitnesslemon.app.ui.auth.LoginActivity
import com.fitnesslemon.app.ui.stories.StoryEditorActivity
import com.fitnesslemon.app.ui.stories.StoryViewerActivity
import com.fitnesslemon.app.ui.stories.VideoEditorActivity
import com.fitnesslemon.app.utils.PreferencesManager
import com.fitnesslemon.app.viewmodel.NewsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by lazy { MainViewModel() }
    private lateinit var newsViewModel: NewsViewModel

    private lateinit var storyAdapter: StoryAdapter
    private lateinit var newsAdapter: NewsCardAdapter

    private var isFirstLoad = true
    private var storyText: String = ""
    private var storyTitle: String = ""

    companion object {
        private const val TAG = "HomeFragment"
        private const val MAX_FILE_SIZE = 30 * 1024 * 1024 // 30 MB
        private const val MAX_VIDEO_DURATION_SECONDS = 30
        private const val TARGET_VIDEO_SIZE = 8 * 1024 * 1024 // 8 MB
    }

    // ============================================================
    // ПЕРМИШЕНЫ
    // ============================================================

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCameraForStory()
        } else {
            Toast.makeText(requireContext(), "Нет доступа к камере", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestVideoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            recordVideo()
        } else {
            Toast.makeText(requireContext(), "Нет доступа к камере", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // LAUNCHERS
    // ============================================================

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val isVideo = requireContext().contentResolver.getType(it)?.startsWith("video/") == true
            if (isVideo) {
                openVideoEditor(it)
            } else {
                openStoryEditor(it, false)
            }
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            photoUri?.let { uri ->
                openStoryEditor(uri, false)
            }
        } else {
            Toast.makeText(requireContext(), "Фото не сделано", Toast.LENGTH_SHORT).show()
        }
    }

    private val recordVideoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            videoUri?.let { uri ->
                openVideoEditor(uri)
            } ?: run {
                Toast.makeText(requireContext(), "URI видео не найден", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Видео не записано", Toast.LENGTH_SHORT).show()
        }
    }

    private val storyEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val filePath = data?.getStringExtra("media_file_path")
            val isVideo = data?.getBooleanExtra("is_video", false) ?: false
            val text = data?.getStringExtra("story_text") ?: ""
            val title = data?.getStringExtra("story_title") ?: ""

            if (!filePath.isNullOrEmpty()) {
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    storyText = text
                    storyTitle = title
                    createStoryFromFile(file, isVideo)
                } else {
                    Toast.makeText(requireContext(), "Файл поврежден", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val videoEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val filePath = data?.getStringExtra("video_file_path")
            val isVideo = true
            val text = data?.getStringExtra("story_text") ?: ""
            val title = data?.getStringExtra("story_title") ?: ""

            if (!filePath.isNullOrEmpty()) {
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    storyText = text
                    storyTitle = title
                    createStoryFromFile(file, isVideo)
                } else {
                    Toast.makeText(requireContext(), "Файл поврежден", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var photoUri: Uri? = null
    private var videoUri: Uri? = null

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "========== onViewCreated ==========")

        // Инициализируем NewsViewModel с контекстом
        newsViewModel = NewsViewModel(requireContext())

        setupStories()
        setupNews()
        setupAddStoryButton()
        setupSwipeRefresh()
        observeViewModels()

        if (isFirstLoad) {
            Log.d(TAG, "🔄 Первая загрузка данных")
            viewModel.loadData()
            newsViewModel.loadNews()
            isFirstLoad = false
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 onResume - обновление данных")
        viewModel.refreshStories()
        newsViewModel.refreshNews()
    }

    // ============================================================
    // SETUP
    // ============================================================

    private fun setupStories() {
        Log.d(TAG, "📱 setupStories")
        storyAdapter = StoryAdapter(emptyList()) { story ->
            openStoryViewer(story)
        }
        binding.rvStories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = storyAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupNews() {
        Log.d(TAG, "📰 setupNews")

        newsAdapter = NewsCardAdapter(
            onLikeClick = { news ->
                Log.d(TAG, "❤️ Лайк новости ID: ${news.id}")
                newsViewModel.toggleLike(news.id)
            },
            onCommentClick = { news ->
                Log.d(TAG, "💬 Комментарии к новости ID: ${news.id}")
                Toast.makeText(requireContext(), "Комментарии к: ${news.title}", Toast.LENGTH_SHORT).show()
            },
            onShareClick = { news ->
                Log.d(TAG, "↗️ Поделиться новостью ID: ${news.id}")
                shareNews(news)
            },
            onSaveClick = { news ->
                Log.d(TAG, "🔖 Сохранить новость ID: ${news.id}")
                newsViewModel.toggleSave(news.id)
            },
            onReadMoreClick = { news ->
                Log.d(TAG, "📖 Читать далее новость ID: ${news.id}")
                openFullNews(news)
            },
            onMediaClick = { news, position ->
                Log.d(TAG, "🖼️ Клик по медиа новости ID: ${news.id}, позиция: $position")
                openFullscreenMedia(news, position)
            }
        )

        binding.rvNews.apply {
            // Важно: используем LinearLayoutManager для вертикального скролла
            layoutManager = LinearLayoutManager(requireContext()).apply {
                orientation = LinearLayoutManager.VERTICAL
            }
            adapter = this@HomeFragment.newsAdapter
            setHasFixedSize(false) // Меняем на false для динамического размера

            // Отключаем вложенный скролл
            isNestedScrollingEnabled = false

            // Добавляем отладку скролла
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy != 0) {
                        Log.d(TAG, "📜 Скролл: dy=$dy")
                    }
                }
            })
        }

        Log.d(TAG, "✅ NewsAdapter установлен")
    }

    private fun setupAddStoryButton() {
        val userRole = try {
            PreferencesManager.getUserRole()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения роли пользователя: ${e.message}")
            "user"
        }

        val isTrainer = userRole.contains("fitness_trainer") || userRole == "fitness_trainer"

        Log.d(TAG, "👤 Роль пользователя: '$userRole'")
        Log.d(TAG, "👤 isTrainer: $isTrainer")

        if (isTrainer) {
            binding.llAddStory.visibility = View.VISIBLE
            binding.llAddStory.setOnClickListener {
                showCreateStoryDialog()
            }
        } else {
            binding.llAddStory.visibility = View.GONE
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "🔄 SwipeRefresh - обновление")
            viewModel.refreshData()
            newsViewModel.refreshNews()
        }
    }

    // ============================================================
    // ДИАЛОГ СОЗДАНИЯ СТОРИС
    // ============================================================

    private fun showCreateStoryDialog() {
        val options = arrayOf(
            "📷 Сделать фото",
            "🎬 Записать видео",
            "🖼️ Выбрать фото",
            "🎥 Выбрать видео"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить историю")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> checkVideoPermissionAndRecord()
                    2 -> pickMediaLauncher.launch("image/*")
                    3 -> pickMediaLauncher.launch("video/*")
                }
            }
            .show()
    }

    // ============================================================
    // ФОТО С КАМЕРЫ
    // ============================================================

    private fun checkCameraPermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openCameraForStory()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            openCameraForStory()
        }
    }

    private fun openCameraForStory() {
        try {
            val photoFile = File(requireContext().cacheDir, "story_${System.currentTimeMillis()}.jpg")
            photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            takePhotoLauncher.launch(photoUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка при открытии камеры: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // ВИДЕО С КАМЕРЫ
    // ============================================================

    private fun checkVideoPermissionAndRecord() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                recordVideo()
            } else {
                requestVideoPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            recordVideo()
        }
    }

    private fun recordVideo() {
        try {
            val videoFile = File(requireContext().cacheDir, "video_${System.currentTimeMillis()}.mp4")
            videoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                videoFile
            )

            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
                putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30)
                putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            }

            recordVideoLauncher.launch(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка при открытии камеры: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // ОТКРЫТИЕ РЕДАКТОРОВ
    // ============================================================

    private fun openStoryEditor(uri: Uri, isVideo: Boolean) {
        val intent = Intent(requireContext(), StoryEditorActivity::class.java).apply {
            putExtra("media_uri", uri.toString())
            putExtra("is_video", isVideo)
        }
        storyEditorLauncher.launch(intent)
    }

    private fun openVideoEditor(uri: Uri) {
        val intent = Intent(requireContext(), VideoEditorActivity::class.java).apply {
            putExtra("video_uri", uri.toString())
        }
        videoEditorLauncher.launch(intent)
    }

    // ============================================================
    // ОТКРЫТИЕ ПРОСМОТРЩИКА
    // ============================================================

    private fun openStoryViewer(clickedStory: com.fitnesslemon.app.data.models.Story) {
        val currentStories = viewModel.storiesState.value
        val userStories = currentStories.filter { it.userId == clickedStory.userId }

        if (userStories.isEmpty()) {
            Toast.makeText(requireContext(), "Нет историй для просмотра", Toast.LENGTH_SHORT).show()
            return
        }

        val startIndex = userStories.indexOfFirst { it.id == clickedStory.id }.coerceAtLeast(0)

        val intent = Intent(requireContext(), StoryViewerActivity::class.java).apply {
            putParcelableArrayListExtra(StoryViewerActivity.EXTRA_STORIES, ArrayList(userStories))
            putExtra(StoryViewerActivity.EXTRA_START_INDEX, startIndex)
        }
        startActivity(intent)
    }

    // ============================================================
    // НОВОСТИ: ДЕЙСТВИЯ
    // ============================================================

    private fun shareNews(news: com.fitnesslemon.app.data.models.News) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, news.title)
            putExtra(
                Intent.EXTRA_TEXT,
                "${news.title}\n\n${news.content}\n\nЧитайте в приложении Fitness Lemon"
            )
        }
        startActivity(Intent.createChooser(shareIntent, "Поделиться"))
    }

    private fun openFullNews(news: com.fitnesslemon.app.data.models.News) {
        Log.d(TAG, "📖 Открыть полную новость: ${news.title}")
        Toast.makeText(requireContext(), "Открыть новость: ${news.title}", Toast.LENGTH_SHORT).show()
        // TODO: Открыть экран детальной новости
    }

    private fun openFullscreenMedia(news: com.fitnesslemon.app.data.models.News, position: Int) {
        Log.d(TAG, "🖼️ Открыть полноэкранный режим: ${news.title}, позиция: $position")
        Toast.makeText(requireContext(), "Полноэкранный режим: ${news.title}", Toast.LENGTH_SHORT).show()
        // TODO: Открыть полноэкранный просмотр
    }

    // ============================================================
    // ПОЛУЧЕНИЕ ДЛИТЕЛЬНОСТИ ВИДЕО
    // ============================================================

    private fun getVideoDuration(filePath: String): Int {
        return try {
            val mediaPlayer = android.media.MediaPlayer()
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
            val duration = mediaPlayer.duration / 1000
            mediaPlayer.release()
            duration
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения длительности видео: ${e.message}")
            0
        }
    }

    // ============================================================
    // ПРОВЕРКА ВИДЕО НА ВАЛИДНОСТЬ
    // ============================================================

    private fun validateVideo(filePath: String): Boolean {
        return try {
            val mediaPlayer = android.media.MediaPlayer()
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
            val duration = mediaPlayer.duration
            mediaPlayer.release()
            duration > 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ Видео невалидно: ${e.message}")
            false
        }
    }

    // ============================================================
    // РАЗМЕР ФАЙЛА В ЧИТАЕМОМ ВИДЕ
    // ============================================================

    private fun getFileSize(file: File): String {
        val size = file.length()
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / 1024 / 1024} MB"
        }
    }

    // ============================================================
    // СЖАТИЕ ВИДЕО
    // ============================================================

    private suspend fun compressVideo(inputFile: File): File? = withContext(Dispatchers.IO) {
        try {
            val fileSize = inputFile.length()
            Log.d(TAG, "📊 Исходный размер видео: ${getFileSize(inputFile)}")

            if (fileSize <= TARGET_VIDEO_SIZE) {
                Log.d(TAG, "✅ Видео уже меньше 8 MB")
                return@withContext inputFile
            }

            var outputFile = File(requireContext().cacheDir, "compressed_video_${System.currentTimeMillis()}.mp4")

            var command = arrayOf(
                "-i", inputFile.absolutePath,
                "-c:v", "libx264",
                "-b:v", "1.2M",
                "-vf", "scale=640:360",
                "-c:a", "aac",
                "-b:a", "96k",
                "-ac", "2",
                "-crf", "23",
                "-profile:v", "baseline",
                "-level", "3.0",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                "-y",
                outputFile.absolutePath
            ).joinToString(" ")

            Log.d(TAG, "🎬 Попытка 1 (libx264): $command")

            var session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
            var returnCode = session.returnCode

            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode) &&
                outputFile.exists() && outputFile.length() > 0 &&
                outputFile.length() < inputFile.length() &&
                outputFile.length() <= TARGET_VIDEO_SIZE) {

                Log.d(TAG, "✅ Попытка 1 успешна: ${getFileSize(outputFile)}")
                return@withContext outputFile
            }

            Log.d(TAG, "⚠️ Попытка 1 не удалась, пробуем h264_mediacodec")
            outputFile = File(requireContext().cacheDir, "compressed_video_alt1_${System.currentTimeMillis()}.mp4")

            command = arrayOf(
                "-i", inputFile.absolutePath,
                "-c:v", "h264_mediacodec",
                "-b:v", "1.2M",
                "-vf", "scale=640:360",
                "-c:a", "aac",
                "-b:a", "96k",
                "-ac", "2",
                "-y",
                outputFile.absolutePath
            ).joinToString(" ")

            Log.d(TAG, "🎬 Попытка 2 (h264_mediacodec): $command")

            session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
            returnCode = session.returnCode

            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode) &&
                outputFile.exists() && outputFile.length() > 0 &&
                outputFile.length() < inputFile.length() &&
                outputFile.length() <= TARGET_VIDEO_SIZE) {

                Log.d(TAG, "✅ Попытка 2 успешна: ${getFileSize(outputFile)}")
                return@withContext outputFile
            }

            Log.d(TAG, "⚠️ Попытка 2 не удалась, используем оригинал")
            return@withContext inputFile

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сжатия: ${e.message}")
            return@withContext inputFile
        }
    }

    // ============================================================
    // СЖАТИЕ ИЗОБРАЖЕНИЯ
    // ============================================================

    private suspend fun compressImage(inputFile: File): File? = withContext(Dispatchers.IO) {
        try {
            val fileSize = inputFile.length()
            Log.d(TAG, "📊 Исходный размер изображения: ${fileSize / 1024} KB")

            if (fileSize <= MAX_FILE_SIZE) {
                return@withContext inputFile
            }

            val outputFile = File(requireContext().cacheDir, "compressed_image_${System.currentTimeMillis()}.jpg")

            val options = BitmapFactory.Options().apply {
                inSampleSize = if (fileSize > 50 * 1024 * 1024) 4 else 2
            }
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)

            if (bitmap != null) {
                val quality = when {
                    fileSize > 50 * 1024 * 1024 -> 40
                    fileSize > 30 * 1024 * 1024 -> 50
                    else -> 60
                }

                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                bitmap.recycle()

                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.d(TAG, "✅ Изображение сжато: ${outputFile.length() / 1024} KB")
                    return@withContext outputFile
                } else {
                    return@withContext null
                }
            } else {
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сжатия: ${e.message}")
            return@withContext null
        }
    }

    // ============================================================
    // ПОКАЗ ПРОГРЕССА
    // ============================================================

    private var progressToast: Toast? = null

    private fun showProgress(message: String) {
        try {
            progressToast?.cancel()
            progressToast = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG)
            progressToast?.show()
        } catch (e: Exception) {
            // Игнорируем
        }
    }

    private fun hideProgress() {
        try {
            progressToast?.cancel()
            progressToast = null
        } catch (e: Exception) {
            // Игнорируем
        }
    }

    // ============================================================
    // ПРОВЕРКА ТОКЕНА И ПЕРЕНАПРАВЛЕНИЕ
    // ============================================================

    private fun checkTokenAndRedirect(): Boolean {
        try {
            val token = PreferencesManager.getToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "❌ Токен не найден")
                Toast.makeText(requireContext(), "Токен не найден. Войдите заново.", Toast.LENGTH_LONG).show()
                redirectToLogin()
                return false
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения токена: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            redirectToLogin()
            return false
        }
    }

    private fun redirectToLogin() {
        try {
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка перенаправления на Login: ${e.message}")
        }
    }

    // ============================================================
    // ПУБЛИКАЦИЯ СТОРИС
    // ============================================================

    private fun createStoryFromFile(file: File, isVideo: Boolean) {
        Log.d(TAG, "========== createStoryFromFile ==========")
        Log.d(TAG, "📁 Файл: ${file.absolutePath}, размер: ${getFileSize(file)}")
        Log.d(TAG, "🎬 isVideo: $isVideo")
        Log.d(TAG, "📝 storyTitle: '$storyTitle'")
        Log.d(TAG, "📄 storyText: '$storyText'")

        if (isVideo) {
            val duration = getVideoDuration(file.absolutePath)
            Log.d(TAG, "⏱️ Длительность видео: $duration секунд")

            if (duration > MAX_VIDEO_DURATION_SECONDS) {
                Log.e(TAG, "❌ Видео слишком длинное: $duration сек (максимум $MAX_VIDEO_DURATION_SECONDS сек)")
                activity?.runOnUiThread {
                    AlertDialog.Builder(requireContext())
                        .setTitle("⚠️ Видео слишком длинное")
                        .setMessage("Длительность видео: $duration секунд.\n\n" +
                                "Максимальная допустимая длительность: $MAX_VIDEO_DURATION_SECONDS секунд.\n\n" +
                                "Пожалуйста, выберите видео короче $MAX_VIDEO_DURATION_SECONDS секунд или обрежьте его в редакторе.")
                        .setPositiveButton("OK") { _, _ ->
                            try {
                                file.delete()
                            } catch (e: Exception) {
                                // Игнорируем
                            }
                        }
                        .setCancelable(false)
                        .show()
                }
                return
            }
        }

        lifecycleScope.launch {
            try {
                if (!checkTokenAndRedirect()) {
                    file.delete()
                    return@launch
                }

                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    Log.e(TAG, "❌ Токен не найден")
                    Toast.makeText(requireContext(), "Токен не найден. Войдите заново.", Toast.LENGTH_LONG).show()
                    file.delete()
                    redirectToLogin()
                    return@launch
                }

                Log.d(TAG, "✅ Токен найден: ${token.take(20)}...")

                var processedFile = file

                if (isVideo && file.length() > TARGET_VIDEO_SIZE) {
                    showProgress("Сжатие видео (${getFileSize(file)})...")
                    val compressed = compressVideo(file)
                    hideProgress()

                    if (compressed != null && compressed.exists() && compressed.length() > 0) {
                        processedFile = compressed
                        Log.d(TAG, "✅ Видео обработано: ${getFileSize(processedFile)}")

                        if (isVideo && !validateVideo(processedFile.absolutePath)) {
                            Log.e(TAG, "❌ Видео повреждено, используем оригинал")
                            processedFile = file
                        }
                    } else {
                        Log.e(TAG, "❌ Не удалось обработать видео, используем оригинал")
                        processedFile = file
                    }
                } else if (!isVideo && file.length() > MAX_FILE_SIZE) {
                    showProgress("Сжатие изображения...")
                    val compressed = compressImage(file)
                    hideProgress()

                    if (compressed != null && compressed.exists() && compressed.length() > 0) {
                        processedFile = compressed
                        Log.d(TAG, "✅ Изображение сжато: ${processedFile.length() / 1024} KB")
                    } else {
                        Log.e(TAG, "❌ Не удалось сжать изображение, используем оригинал")
                        processedFile = file
                    }
                }

                sendStoryToServer(processedFile, isVideo, token)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Исключение: ${e.message}", e)
                e.printStackTrace()
                hideProgress()
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                try {
                    file.delete()
                } catch (ex: Exception) {
                    // Игнорируем
                }
            }
        }
    }

    // ============================================================
    // ОТПРАВКА НА СЕРВЕР
    // ============================================================

    private suspend fun sendStoryToServer(file: File, isVideo: Boolean, token: String) {
        try {
            Log.d(TAG, "📤 Отправка файла размером: ${getFileSize(file)}")

            if (file.length() > 10 * 1024 * 1024) {
                Log.w(TAG, "⚠️ Файл большой: ${getFileSize(file)}")
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("⚠️ Большой файл")
                        .setMessage("Размер файла: ${getFileSize(file)}\n\n" +
                                "Рекомендуемый размер до 8 MB для быстрой загрузки.\n\n" +
                                "Продолжить загрузку?")
                        .setPositiveButton("Загрузить") { _, _ ->
                            lifecycleScope.launch {
                                sendStoryToServerInternal(file, isVideo, token)
                            }
                        }
                        .setNegativeButton("Отмена") { _, _ ->
                            file.delete()
                            Toast.makeText(requireContext(), "Загрузка отменена", Toast.LENGTH_SHORT).show()
                        }
                        .show()
                }
                return
            }

            sendStoryToServerInternal(file, isVideo, token)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка отправки: ${e.message}")
            hideProgress()
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun sendStoryToServerInternal(file: File, isVideo: Boolean, token: String) {
        try {
            showProgress("Загрузка на сервер...")

            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            Log.d(TAG, "📄 MIME тип: $mimeType")
            Log.d(TAG, "📤 Размер файла: ${getFileSize(file)}")

            val mediaPart = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody(mimeType.toMediaTypeOrNull())
            )

            val titleBody = if (storyTitle.isNotEmpty()) {
                Log.d(TAG, "📝 Используем заголовок: '$storyTitle'")
                RequestBody.create("text/plain".toMediaType(), storyTitle)
            } else if (storyText.isNotEmpty()) {
                val defaultTitle = storyText.take(50)
                Log.d(TAG, "📝 Заголовок из текста: '$defaultTitle'")
                RequestBody.create("text/plain".toMediaType(), defaultTitle)
            } else {
                Log.d(TAG, "📝 Заголовок по умолчанию: 'Сторис'")
                RequestBody.create("text/plain".toMediaType(), "Сторис")
            }

            val textBody = if (storyText.isNotEmpty()) {
                Log.d(TAG, "📄 Текст: '$storyText'")
                RequestBody.create("text/plain".toMediaType(), storyText)
            } else {
                Log.d(TAG, "📄 Текст пустой")
                RequestBody.create("text/plain".toMediaType(), "")
            }

            val durationBody = RequestBody.create("text/plain".toMediaType(), if (isVideo) "10" else "5")

            Log.d(TAG, "📤 Отправка на сервер...")

            val response = ApiClient.apiService.createStory(
                token = "Bearer $token",
                title = titleBody,
                text = textBody,
                duration = durationBody,
                file = mediaPart
            )

            hideProgress()

            try {
                file.delete()
                Log.d(TAG, "🗑️ Временный файл удален")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Не удалось удалить файл: ${e.message}")
            }

            if (response.isSuccessful) {
                val result = response.body()
                Log.d(TAG, "✅ Ответ сервера: success=${result?.success}, message=${result?.message}")
                if (result != null && result.success) {
                    val storyData = result.data as? Map<*, *>
                    val story = storyData?.get("story") as? Map<*, *>
                    val storyId = story?.get("id")
                    Log.d(TAG, "✅ Сторис создана! ID: $storyId")
                    Toast.makeText(requireContext(), "✅ История опубликована!", Toast.LENGTH_SHORT).show()

                    Log.d(TAG, "🔄 Обновление данных после публикации")
                    viewModel.refreshData()
                    viewModel.refreshStories()
                    newsViewModel.refreshNews()

                    storyText = ""
                    storyTitle = ""
                } else {
                    Log.e(TAG, "❌ Ошибка: ${result?.message ?: "Неизвестная ошибка"}")
                    Toast.makeText(requireContext(), "Ошибка: ${result?.message ?: "Неизвестная ошибка"}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "❌ Ошибка HTTP: ${response.code()}, сообщение: ${response.message()}")
                when (response.code()) {
                    401 -> {
                        Toast.makeText(requireContext(), "Сессия истекла. Войдите заново.", Toast.LENGTH_LONG).show()
                        redirectToLogin()
                    }
                    413 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("⚠️ Файл слишком большой")
                            .setMessage("Видео слишком большое для загрузки.\n\n" +
                                    "Рекомендации:\n" +
                                    "• Используйте видео длительностью до 30 секунд\n" +
                                    "• Выберите видео с меньшим разрешением\n" +
                                    "• Обрежьте видео в редакторе перед публикацией")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    else -> Toast.makeText(requireContext(), "Ошибка загрузки: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка отправки: ${e.message}")
            hideProgress()
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // OBSERVE VIEW MODELS
    // ============================================================

    private fun observeViewModels() {
        Log.d(TAG, "👀 observeViewModels")

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dashboardState.collect { state ->
                        binding.swipeRefresh.isRefreshing = false

                        when (state) {
                            is DashboardState.Loading -> {
                                if (!isFirstLoad) {
                                    binding.progressBar.visibility = View.VISIBLE
                                }
                            }
                            is DashboardState.Success -> {
                                binding.progressBar.visibility = View.GONE

                                val userName = state.user.name
                                binding.tvUserName.text = userName
                                binding.tvUserName.visibility = View.VISIBLE

                                binding.tvWelcome.text = "Добро пожаловать!"
                                binding.tvWelcome.visibility = View.VISIBLE

                                binding.tvRemainingWorkouts.text = "🔥 Осталось: ${state.remainingWorkouts} тренировок"
                                binding.tvRemainingWorkouts.visibility = View.VISIBLE

                                Log.d(TAG, "👤 ФИО: $userName")
                            }
                            is DashboardState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvUserName.text = "Гость"
                                binding.tvUserName.visibility = View.VISIBLE
                                binding.tvWelcome.text = "Добро пожаловать!"
                                binding.tvWelcome.visibility = View.VISIBLE

                                if (state.message.contains("401") || state.message.contains("Unauthorized")) {
                                    redirectToLogin()
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.storiesState.collect { stories ->
                        Log.d(TAG, "📊 Получены сторис: ${stories.size} шт.")

                        if (stories.isNotEmpty()) {
                            binding.rvStories.visibility = View.VISIBLE
                            binding.tvEmptyStories.visibility = View.GONE

                            val groupedStories = stories.distinctBy { it.userId }

                            groupedStories.forEach { story ->
                                story.userAvatar?.let { avatarUrl ->
                                    Glide.with(this@HomeFragment)
                                        .load(avatarUrl)
                                        .circleCrop()
                                        .preload()
                                }
                            }

                            storyAdapter = StoryAdapter(groupedStories) { story ->
                                openStoryViewer(story)
                            }
                            binding.rvStories.adapter = storyAdapter
                            storyAdapter.updateStoryCounts(stories)

                        } else {
                            binding.rvStories.visibility = View.GONE
                            binding.tvEmptyStories.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    newsViewModel.newsState.collect { state ->
                        Log.d(TAG, "📰 Получено состояние новостей: $state")

                        when (state) {
                            is NewsViewModel.NewsState.Loading -> {
                                if (newsAdapter.itemCount == 0) {
                                    binding.progressBar.visibility = View.VISIBLE
                                }
                            }
                            is NewsViewModel.NewsState.Success -> {
                                binding.progressBar.visibility = View.GONE

                                Log.d(TAG, "✅ Загружено ${state.news.size} новостей")
                                logNewsData(state.news)

                                newsAdapter.submitList(state.news)
                                newsAdapter.setLikedNews(state.likedNewsIds)
                                newsAdapter.setSavedNews(state.savedNewsIds)

                                if (state.news.isEmpty()) {
                                    binding.rvNews.visibility = View.GONE
                                    binding.tvEmptyNews.visibility = View.VISIBLE
                                } else {
                                    binding.rvNews.visibility = View.VISIBLE
                                    binding.tvEmptyNews.visibility = View.GONE
                                }
                            }
                            is NewsViewModel.NewsState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Log.e(TAG, "❌ Ошибка загрузки новостей: ${state.message}")
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        // Наблюдаем за toastMessage
        newsViewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Log.d(TAG, "📢 Toast: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                newsViewModel.clearToastMessage()
            }
        }

        loadUserAvatar()
    }

    // ============================================================
    // ОТЛАДОЧНЫЙ МЕТОД
    // ============================================================

    private fun logNewsData(news: List<News>) {
        Log.d(TAG, "========== НОВОСТИ ==========")
        Log.d(TAG, "Всего новостей: ${news.size}")
        news.forEachIndexed { index, item ->
            Log.d(TAG, "Новость #${index + 1}:")
            Log.d(TAG, "  ID: ${item.id}")
            Log.d(TAG, "  Заголовок: ${item.title}")
            Log.d(TAG, "  Изображения: ${item.images}")
            Log.d(TAG, "  Видео: ${item.videos}")
            Log.d(TAG, "  Категория: ${item.categoryName}")
            Log.d(TAG, "  Дата: ${item.formattedDate}")
            Log.d(TAG, "  ---")
        }
    }

    // ============================================================
    // АВАТАР
    // ============================================================

    private fun loadUserAvatar() {
        try {
            val avatarUrl = PreferencesManager.getUserAvatar()
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(binding.ivAvatar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка загрузки аватара: ${e.message}")
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "🗑️ onDestroyView")
        _binding = null
        try {
            hideProgress()
        } catch (e: Exception) {
            // Игнорируем
        }
    }
}
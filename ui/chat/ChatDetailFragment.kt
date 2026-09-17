package com.fitnesslemon.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentChatDetailBinding
import com.fitnesslemon.app.databinding.LayoutAudioRecordingBinding
import com.fitnesslemon.app.ui.chat.adapters.MessageAdapter
import com.fitnesslemon.app.ui.chat.adapters.ParticipantsHorizontalAdapter
import com.fitnesslemon.app.ui.chat.dialogs.ImageViewerDialog
import com.fitnesslemon.app.ui.chat.dialogs.ParticipantsDialog
import com.fitnesslemon.app.ui.chat.dialogs.VideoPlayerDialog
import com.fitnesslemon.app.ui.chat.dialogs.VideoRecordingDialog
import com.fitnesslemon.app.ui.chat.utils.AudioPlayerHelper
import com.fitnesslemon.app.ui.chat.utils.AudioRecorderHelper
import com.fitnesslemon.app.ui.chat.utils.ImageCompressor
import com.fitnesslemon.app.ui.chat.utils.VideoCompressor
import com.fitnesslemon.app.data.models.ChatParticipant
import com.fitnesslemon.app.data.models.Message
import com.fitnesslemon.app.utils.KeyStoreManager
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class ChatDetailFragment : Fragment() {

    private var _binding: FragmentChatDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChatViewModel
    private lateinit var keyStoreManager: KeyStoreManager
    private var currentUserId = 0
    private var chatId = 0
    private var chatName = ""
    private var chatType: String = "private"
    private var participantsCount: Int = 0
    private var chatAvatar: String? = null
    private var participantsList: List<ChatParticipant> = emptyList()
    private var isDataLoaded = false
    private var isLoading = false
    private var keysChecked = false
    private lateinit var participantsAdapter: ParticipantsHorizontalAdapter
    private lateinit var messageAdapter: MessageAdapter

    private var isUserAdminOrTrainerInGroup = false

    private val publicKeyCache = mutableMapOf<Int, String?>()
    private var isFetchingPublicKey = false

    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    private var audioRecorderHelper: AudioRecorderHelper? = null
    private var recordingDialog: BottomSheetDialog? = null
    private var recordingBinding: LayoutAudioRecordingBinding? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private var audioPlayerHelper: AudioPlayerHelper? = null
    private var currentlyPlayingMessage: Message? = null
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackRunnable: Runnable? = null

    // Видео
    private var isSendingVideo = false
    private var uploadProgressDialog: android.app.ProgressDialog? = null

    // ===== ЗАПРОС РАЗРЕШЕНИЙ =====
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Нет доступа к камере", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Теперь вы можете записывать голосовые сообщения", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Нет доступа к микрофону", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestVideoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Теперь вы можете записывать видео", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Нет доступа к камере или микрофону", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoFile?.let { file ->
                saveAndSendImage(file)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            compressAndSendImage(it)
        }
    }

    private val videoGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            sendVideoFromGallery(it)
        }
    }

    private var photoFile: File? = null

    companion object {
        private const val ARG_CHAT_ID = "chat_id"
        private const val ARG_CHAT_NAME = "chat_name"
        private const val TAG = "ChatDetailFragment"
        private const val REQUEST_VIDEO_PERMISSION = 200

        fun newInstance(chatId: Int, chatName: String): ChatDetailFragment {
            require(chatId != 0) { "Chat ID cannot be 0" }
            val fragment = ChatDetailFragment()
            val args = Bundle()
            args.putInt(ARG_CHAT_ID, chatId)
            args.putString(ARG_CHAT_NAME, chatName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            chatId = it.getInt(ARG_CHAT_ID)
            chatName = it.getString(ARG_CHAT_NAME) ?: ""
        }
        Log.d(TAG, "========== onCreate ==========")
        Log.d(TAG, "chatId: $chatId, chatName: $chatName")

        keyStoreManager = KeyStoreManager(requireContext())
        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "========== onCreateView ==========")
        _binding = FragmentChatDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "========== onViewCreated ==========")

        if (chatId == 0) {
            Log.e(TAG, "❌ Ошибка: ID чата = 0")
            Toast.makeText(requireContext(), "Ошибка: ID чата не может быть 0", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        PreferencesManager.forceSetUserId(4)
        currentUserId = 4

        Log.d(TAG, "========================================")
        Log.d(TAG, "🔴 ПРИНУДИТЕЛЬНАЯ УСТАНОВКА currentUserId = $currentUserId")
        Log.d(TAG, "========================================")

        initAudioPlayer()

        clearMessages()
        loadCurrentUserId()
        setupToolbar()
        setupGroupInfo()
        setupRecyclerView()
        setupSendButton()
        setupVoiceButton()
        setupVideoButton()
        setupAttachButton()
        setupSwipeRefresh()
        observeViewModel()
        initAudioRecorder()

        lifecycleScope.launch {
            loadAllChatData()
        }

        lifecycleScope.launch {
            delay(500)
            ensureCurrentUserHasPublicKeyUploaded()
            ensureBothUsersHavePublicKeys()
            preloadParticipantPublicKeys()
        }
    }

    override fun onResume() {
        super.onResume()
        if (chatId != 0) {
            Log.d(TAG, "🔄 onResume: принудительное обновление чата $chatId")
            lifecycleScope.launch {
                viewModel.forceRefreshChat(chatId)
            }
        }
    }

    // ===== ИНИЦИАЛИЗАЦИЯ AUDIO PLAYER =====
    private fun initAudioPlayer() {
        audioPlayerHelper = AudioPlayerHelper(
            context = requireContext(),
            onPlaybackStateChanged = { isPlaying ->
                currentlyPlayingMessage?.let { message ->
                    updatePlayButtonState(message, isPlaying)
                    if (!isPlaying) {
                        stopSeekBarUpdate()
                    }
                }
            },
            onProgress = { progress, duration ->
                currentlyPlayingMessage?.let { message ->
                    messageAdapter.updateSeekBarProgress(message.id, progress, duration)
                }
            },
            onError = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                currentlyPlayingMessage = null
                stopSeekBarUpdate()
            },
            onCompletion = {
                currentlyPlayingMessage?.let { message ->
                    updatePlayButtonState(message, false)
                    messageAdapter.updateSeekBarProgress(message.id, 0, 0)
                }
                currentlyPlayingMessage = null
                stopSeekBarUpdate()
            }
        )
    }

    private fun clearMessages() {
        Log.d(TAG, "🧹 Очищаем сообщения перед загрузкой нового чата")

        if (::messageAdapter.isInitialized) {
            messageAdapter.updateMessages(emptyList())
            binding.rvMessages.adapter = messageAdapter
        }

        publicKeyCache.clear()
        isDataLoaded = false
        isLoading = false
        keysChecked = false
        binding.rvMessages.scrollToPosition(0)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout = binding.swipeRefreshLayout
        swipeRefreshLayout?.setOnRefreshListener {
            lifecycleScope.launch {
                refreshAllData()
            }
        }
        swipeRefreshLayout?.setColorSchemeResources(
            R.color.lemon_primary,
            R.color.lemon_accent,
            R.color.lemon_primary_dark
        )
    }

    private suspend fun refreshAllData() {
        Log.d(TAG, "🔄 Принудительное обновление данных")
        try {
            viewModel.stopChatDetailPolling()
            participantsList = emptyList()
            isDataLoaded = false
            isLoading = false
            keysChecked = false
            publicKeyCache.clear()

            loadAllChatData()
            viewModel.startChatDetailPolling()

            Toast.makeText(requireContext(), "Данные обновлены", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка обновления", Toast.LENGTH_SHORT).show()
        } finally {
            swipeRefreshLayout?.isRefreshing = false
        }
    }

    private suspend fun ensureBothUsersHavePublicKeys() {
        if (keysChecked) {
            Log.d(TAG, "✅ Ключи уже проверены")
            return
        }

        Log.d(TAG, "🔐 Проверка публичных ключей участников чата (фон)")
        keysChecked = true

        val token = PreferencesManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "❌ Токен не найден")
            return
        }

        val myKeyResponse = withTimeoutOrNull(3000) {
            com.fitnesslemon.app.data.api.ApiClient.apiService.checkPublicKey("Bearer $token")
        }

        if (myKeyResponse == null || myKeyResponse.body()?.success != true || myKeyResponse.body()?.public_key == null) {
            Log.d(TAG, "🔐 У текущего пользователя нет ключа, создаем...")
            keyStoreManager.generateAndUploadKeys()
        } else {
            Log.d(TAG, "✅ У текущего пользователя есть ключ")
        }
    }

    private suspend fun preloadParticipantPublicKeys() {
        Log.d(TAG, "🔐 Предзагрузка публичных ключей участников")

        val participantsToLoad = participantsList.filter {
            it.id != currentUserId && !publicKeyCache.containsKey(it.id)
        }

        if (participantsToLoad.isEmpty()) {
            Log.d(TAG, "✅ Все ключи уже загружены")
            return
        }

        for (participant in participantsToLoad) {
            val key = getRecipientPublicKeySync(participant.id)
            publicKeyCache[participant.id] = key
        }
    }

    private suspend fun getRecipientPublicKey(): String? {
        val otherUserId = getOtherParticipantId()
        if (otherUserId == 0) {
            Log.w(TAG, "⚠️ Не удалось определить ID получателя")
            return null
        }

        publicKeyCache[otherUserId]?.let {
            return it
        }

        val key = getRecipientPublicKeySync(otherUserId)
        publicKeyCache[otherUserId] = key
        return key
    }

    private suspend fun getRecipientPublicKeySync(userId: Int): String? {
        Log.d(TAG, "🔐 Загрузка публичного ключа для пользователя ID: $userId")
        isFetchingPublicKey = true

        try {
            val token = PreferencesManager.getToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "❌ Токен не найден")
                return null
            }

            val response = withTimeoutOrNull(3000) {
                com.fitnesslemon.app.data.api.ApiClient.apiService.getUserPublicKey(
                    "Bearer $token",
                    userId
                )
            }

            if (response == null) {
                Log.w(TAG, "⚠️ Таймаут получения ключа")
                return null
            }

            if (response.isSuccessful) {
                val publicKey = response.body()?.public_key
                if (!publicKey.isNullOrEmpty()) {
                    Log.d(TAG, "✅ Публичный ключ получен для ID $userId")
                    return publicKey
                }
            } else {
                when (response.code()) {
                    403, 404 -> {
                        Log.w(TAG, "⚠️ Публичный ключ для ID $userId не найден (${response.code()})")
                        return null
                    }
                }
            }
            return null

        } finally {
            isFetchingPublicKey = false
        }
    }

    private fun getOtherParticipantId(): Int {
        if (chatType == "private" && participantsList.isNotEmpty()) {
            val other = participantsList.firstOrNull { it.id != currentUserId }
            if (other != null) {
                return other.id
            }
        }

        val currentState = viewModel.chatDetailState.value
        if (currentState is ChatDetailState.Success) {
            val other = currentState.messages.firstOrNull { it.senderId != currentUserId }?.senderId
            if (other != null && other != 0) {
                return other
            }
        }

        return 0
    }

    private suspend fun loadAllChatData() {
        if (isLoading) {
            Log.d(TAG, "⏳ Загрузка уже выполняется, пропускаем")
            return
        }

        Log.d(TAG, "🔄 loadAllChatData() - начало")
        isLoading = true

        if (!isDataLoaded) {
            binding.progressBar.visibility = View.VISIBLE
        }

        try {
            val token = PreferencesManager.getToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "❌ Токен не найден")
                showErrorAndLoadMessages()
                return
            }

            val chatResponse = com.fitnesslemon.app.data.api.ApiClient.adminApiService.getChatDetails(
                "Bearer $token", chatId
            )

            if (!chatResponse.isSuccessful) {
                Log.e(TAG, "❌ Ошибка загрузки чата: ${chatResponse.code()}")
                showErrorAndLoadMessages()
                return
            }

            chatResponse.body()?.let { chat ->
                Log.d(TAG, "✅ Чат загружен: тип=${chat.type}, участников=${chat.participantsCount}")
                chatType = chat.type ?: "private"
                participantsCount = chat.participantsCount
                chatAvatar = chat.avatar
                if (chat.type == "group" && !chat.name.isNullOrEmpty()) {
                    chatName = chat.name ?: chatName
                }
                updateToolbarTitle()

                if (chat.type == "group") {
                    Log.d(TAG, "👥 Групповой чат, загружаем участников")
                    val participantsLoaded = loadParticipantsSync()
                    if (!participantsLoaded) {
                        Log.w(TAG, "⚠️ Не удалось загрузить участников")
                    }
                    viewModel.loadCurrentUserRole(chatId)
                    lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.currentUserRoleInGroup.collect { role ->
                                isUserAdminOrTrainerInGroup = role == "admin" || role == "trainer"
                                updateAttachButtonVisibility()
                            }
                        }
                    }
                } else {
                    participantsList = emptyList()
                    isDataLoaded = true
                    updateAttachButtonVisibility()
                }

                updateToolbarSubtitle()
            }

            viewModel.loadChatMessages(chatId, chatName, chatType ?: "private")
            viewModel.startChatDetailPolling()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Исключение: ${e.message}", e)
            showErrorAndLoadMessages()
        } finally {
            binding.progressBar.visibility = View.GONE
            isLoading = false
        }

        Log.d(TAG, "========== loadAllChatData() завершен ==========")
    }

    private fun updateAttachButtonVisibility() {
        if (chatType == "group" && !isUserAdminOrTrainerInGroup) {
            binding.btnAttach.visibility = View.GONE
            binding.btnVoice.visibility = View.GONE
            binding.btnVideo.visibility = View.GONE
        } else {
            binding.btnAttach.visibility = View.VISIBLE
            binding.btnVoice.visibility = View.VISIBLE
            binding.btnVideo.visibility = View.VISIBLE
        }
    }

    private suspend fun loadParticipantsSync(): Boolean {
        Log.d(TAG, "========== loadParticipantsSync() ==========")

        try {
            val token = PreferencesManager.getToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "❌ Токен не найден")
                return false
            }

            val response = com.fitnesslemon.app.data.api.ApiClient.adminApiService.getChatParticipants(
                "Bearer $token", chatId
            )

            Log.d(TAG, "📡 Код ответа: ${response.code()}")

            if (response.isSuccessful) {
                participantsList = response.body() ?: emptyList()
                Log.d(TAG, "✅ Загружено участников: ${participantsList.size}")

                participantsCount = participantsList.size

                participantsAdapter = ParticipantsHorizontalAdapter(participantsList) { participant ->
                    showParticipantInfo(participant)
                }

                binding.rvParticipants.apply {
                    layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                    adapter = participantsAdapter
                    visibility = if (participantsList.isNotEmpty()) View.GONE else View.GONE
                }

                if (participantsList.isNotEmpty()) {
                    val participantsMap = participantsList.associate { participant ->
                        participant.id to Pair(participant.name, participant.getAvatarUrlValue())
                    }
                    viewModel.updateMessagesWithParticipants(participantsMap)
                }

                isDataLoaded = true
                return true

            } else {
                Log.e(TAG, "❌ Ошибка: ${response.code()}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Исключение: ${e.message}", e)
            return false
        }
    }

    private fun showErrorAndLoadMessages() {
        viewModel.loadChatMessages(chatId, chatName, chatType ?: "private")
        viewModel.startChatDetailPolling()
        binding.progressBar.visibility = View.GONE
        isLoading = false
    }

    private fun loadCurrentUserId() {
        currentUserId = PreferencesManager.getUserId()
        Log.d(TAG, "📱 Текущий пользователь ID: $currentUserId")
        if (currentUserId == 0) {
            Log.w(TAG, "⚠️ User ID = 0, пробуем восстановить из токена")
            recoverUserIdFromToken()
        }
    }

    private fun recoverUserIdFromToken() {
        try {
            val token = PreferencesManager.getToken()
            if (token != null) {
                val parts = token.split(".")
                if (parts.size > 1) {
                    val payloadJson = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                    val json = JSONObject(payloadJson)
                    var userIdFromToken = json.optInt("user_id", 0)
                    if (userIdFromToken == 0) {
                        userIdFromToken = json.optInt("id", 0)
                    }
                    if (userIdFromToken != 0) {
                        currentUserId = userIdFromToken
                        PreferencesManager.saveUserId(currentUserId)
                        Log.d(TAG, "✅ User ID восстановлен из токена: $currentUserId")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка восстановления ID: ${e.message}")
        }
    }

    private fun showParticipantInfo(participant: ChatParticipant) {
        val roleText = when (participant.role) {
            "admin" -> "Администратор"
            "moderator" -> "Модератор"
            else -> "Участник"
        }

        val infoText = StringBuilder()
        infoText.append("Роль: $roleText\n")
        if (!participant.email.isNullOrEmpty()) {
            infoText.append("Email: ${participant.email}\n")
        }
        if (!participant.joinedAt.isNullOrEmpty()) {
            infoText.append("Присоединился: ${participant.joinedAt}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(participant.name)
            .setMessage(infoText.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupGroupInfo() {
        binding.groupInfoLayout.visibility = View.GONE
        binding.ivGroupInfo.setOnClickListener {
            showGroupInfoDialog()
        }
        binding.rvParticipants.visibility = View.GONE
    }

    private fun setupToolbar() {
        binding.toolbar.title = chatName
        if (chatType == "group" && participantsCount > 0) {
            binding.toolbar.subtitle = formatParticipantsCount(participantsCount)
        }
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolbar.setOnClickListener {
            if (chatType == "group") {
                showGroupInfoDialog()
            }
        }
    }

    private fun updateToolbarTitle() {
        binding.toolbar.title = chatName
    }

    private fun updateToolbarSubtitle() {
        if (chatType == "group" && participantsCount > 0) {
            binding.toolbar.subtitle = formatParticipantsCount(participantsCount)
        } else {
            binding.toolbar.subtitle = null
        }
    }

    private fun formatParticipantsCount(count: Int): String {
        val lastDigit = count % 10
        val lastTwoDigits = count % 100
        return when {
            lastTwoDigits in 11..19 -> "$count участников"
            lastDigit == 1 -> "$count участник"
            lastDigit in 2..4 -> "$count участника"
            else -> "$count участников"
        }
    }

    private fun showGroupInfoDialog() {
        val items = mutableListOf<String>()
        if (chatName.isNotEmpty()) {
            items.add("📝 Название: $chatName")
        }
        items.add("👥 Участников: $participantsCount")
        items.add("🆔 ID чата: $chatId")
        if (!chatAvatar.isNullOrEmpty()) {
            items.add("🖼️ Аватар установлен")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Информация о группе")
            .setItems(items.toTypedArray(), null)
            .setPositiveButton("OK", null)
            .setNeutralButton("Все участники") { _, _ ->
                showAllParticipantsDialog()
            }
            .show()
    }

    private fun showAllParticipantsDialog() {
        if (participantsList.isNotEmpty()) {
            ParticipantsDialog(requireContext(), participantsList, participantsCount).show()
        } else {
            lifecycleScope.launch {
                loadParticipantsSync()
                if (participantsList.isNotEmpty()) {
                    ParticipantsDialog(requireContext(), participantsList, participantsCount).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось загрузить участников", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun ensureCurrentUserHasPublicKeyUploaded() {
        try {
            val token = PreferencesManager.getToken()
            if (token.isNullOrEmpty()) return

            val response = com.fitnesslemon.app.data.api.ApiClient.apiService.checkPublicKey("Bearer $token")
            if (response.isSuccessful) {
                val result = response.body()
                if (result != null && !result.success && result.pending == true) {
                    keyStoreManager.generateAndUploadKeys()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки ключа: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvMessages.setHasFixedSize(true)

        val userId = PreferencesManager.getUserId()
        if (userId != 0) {
            currentUserId = userId
            Log.d(TAG, "✅ User ID установлен: $currentUserId")
        } else {
            recoverUserIdFromToken()
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "📱 Создание адаптера с currentUserId: $currentUserId")
        Log.d(TAG, "========================================")

        messageAdapter = MessageAdapter(
            messages = emptyList(),
            currentUserId = currentUserId,
            onAudioPlayClick = { message, _ -> playAudio(message) },
            onImageClick = { message ->
                if (message.content.isNotEmpty()) {
                    val content = message.content
                    Log.d(TAG, "🎬 Сообщение: type=${message.type}, content=$content, localUri=${message.localUri}")

                    if (message.type == "video") {
                        // Получаем имя файла из URL
                        val filename = if (content.contains("/")) {
                            content.substringAfterLast("/")
                        } else {
                            content
                        }

                        // Используем новый эндпоинт /video/{filename}
                        val videoUrl = "http://94.159.117.241/video/$filename"
                        Log.d(TAG, "🎬 Видео URL: $videoUrl")

                        // Проверяем localUri
                        val finalPath = if (!message.localUri.isNullOrEmpty()) {
                            val localFile = File(message.localUri)
                            if (localFile.exists()) {
                                Log.d(TAG, "✅ Используем localUri: ${message.localUri}")
                                message.localUri
                            } else {
                                videoUrl
                            }
                        } else {
                            videoUrl
                        }

                        val dialog = VideoPlayerDialog.newInstance(finalPath)
                        dialog.show(parentFragmentManager, "video_player")
                    } else {
                        // Открываем изображение
                        val imageUrl = message.content
                        Log.d(TAG, "🖼️ Открываем изображение: $imageUrl")
                        val dialog = ImageViewerDialog.newInstance(imageUrl, message)
                        dialog.show(parentFragmentManager, "image_viewer")
                    }
                } else {
                    Toast.makeText(requireContext(), "Ссылка отсутствует", Toast.LENGTH_SHORT).show()
                }
            },
            onSeekBarTouch = { message, isTouching ->
                if (isTouching) {
                    audioPlayerHelper?.pause()
                } else {
                    audioPlayerHelper?.resume()
                }
            },
            onSeekBarProgress = { message, progress -> seekTo(message, progress) },
            onMessageLongClick = { message -> showDeleteMessageDialog(message) },
            chatType = chatType
        )
        binding.rvMessages.adapter = messageAdapter
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                binding.etMessage.text?.clear()
                lifecycleScope.launch {
                    sendMessageInternal(text, null)
                }
            }
        }
    }

    private fun sendMessageInternal(text: String, recipientPublicKey: String?) {
        viewModel.sendMessage(chatId, text, recipientPublicKey, chatType)
        binding.etMessage.text?.clear()
    }

    private fun setupVoiceButton() {
        binding.btnVoice.setOnClickListener {
            if (checkAudioPermission()) {
                if (chatType == "group" && !isUserAdminOrTrainerInGroup) {
                    Toast.makeText(requireContext(), "Только администраторы и тренеры могут отправлять голосовые сообщения в групповых чатах", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                showAudioRecordingDialog()
            } else {
                requestAudioPermission()
            }
        }
    }

    // ===== ВИДЕО КНОПКА =====
    private fun setupVideoButton() {
        binding.btnVideo.setOnClickListener {
            if (chatType == "group" && !isUserAdminOrTrainerInGroup) {
                Toast.makeText(requireContext(), "Только администраторы и тренеры могут отправлять видео", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (checkVideoPermissions()) {
                showVideoOptions()
            } else {
                requestVideoPermissions()
            }
        }
    }

    private fun setupAttachButton() {
        binding.btnAttach.setOnClickListener {
            if (chatType == "group" && !isUserAdminOrTrainerInGroup) {
                Toast.makeText(requireContext(), "Только администраторы и тренеры могут отправлять медиафайлы в групповых чатах", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAttachmentOptions()
        }
    }

    private fun showAttachmentOptions() {
        val options = arrayOf("Сделать фото", "Выбрать из галереи", "Выбрать видео")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Прикрепить")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                    2 -> openVideoGallery()
                }
            }
            .show()
    }

    // ===== ВИДЕО ОПЦИИ =====
    private fun showVideoOptions() {
        val options = arrayOf("Записать видео", "Выбрать из галереи")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Видео")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showVideoRecordingDialog()
                    1 -> openVideoGallery()
                }
            }
            .show()
    }

    private fun showVideoRecordingDialog() {
        val dialog = VideoRecordingDialog.newInstance { file, duration ->
            lifecycleScope.launch {
                try {
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "🎬 НАЧАЛО ОТПРАВКИ ВИДЕО")
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "📁 Файл: ${file.absolutePath}")
                    Log.d(TAG, "📊 Размер: ${file.length() / 1024} KB")
                    Log.d(TAG, "⏱️ Длительность: $duration сек")
                    Log.d(TAG, "📊 Файл существует: ${file.exists()}")

                    compressAndSendVideo(file, duration)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка: ${e.message}", e)
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show(parentFragmentManager, "video_recording")
    }

    private fun openVideoGallery() {
        videoGalleryLauncher.launch("video/*")
    }

    private fun sendVideoFromGallery(uri: Uri) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "📥 ВИДЕО ИЗ ГАЛЕРЕИ")
                Log.d(TAG, "========================================")
                Log.d(TAG, "📁 URI: $uri")

                val videoDir = File(requireContext().filesDir, "video_files")
                if (!videoDir.exists()) videoDir.mkdirs()
                Log.d(TAG, "📁 Папка: ${videoDir.absolutePath}")

                val fileName = "video_${System.currentTimeMillis()}.mp4"
                val savedFile = File(videoDir, fileName)
                Log.d(TAG, "📁 Сохраняем в: ${savedFile.absolutePath}")

                val inputStream = requireContext().contentResolver.openInputStream(uri)
                savedFile.outputStream().use { output ->
                    inputStream?.use { input ->
                        input.copyTo(output)
                    }
                }
                inputStream?.close()

                if (savedFile.exists() && savedFile.length() > 0) {
                    Log.d(TAG, "✅ Видео сохранено, размер: ${savedFile.length() / 1024} KB")
                    val duration = getVideoDuration(savedFile)
                    Log.d(TAG, "⏱️ Длительность: $duration сек")
                    compressAndSendVideo(savedFile, duration)
                } else {
                    Log.e(TAG, "❌ Ошибка сохранения видео")
                    Toast.makeText(requireContext(), "Ошибка сохранения видео", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка: ${e.message}", e)
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== СЖАТИЕ И ОТПРАВКА ВИДЕО С ЛОГИРОВАНИЕМ =====
    private fun compressAndSendVideo(videoFile: File, duration: Int) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🎬 НАЧАЛО СЖАТИЯ ВИДЕО")
        Log.d(TAG, "========================================")
        Log.d(TAG, "📁 Файл: ${videoFile.absolutePath}")
        Log.d(TAG, "📊 Размер: ${videoFile.length() / 1024} KB")
        Log.d(TAG, "⏱️ Длительность: $duration сек")
        Log.d(TAG, "📊 Файл существует: ${videoFile.exists()}")

        isSendingVideo = true
        showVideoUploadProgress()

        VideoCompressor.compressVideo(
            context = requireContext(),
            inputFile = videoFile,
            onProgress = { progress ->
                Log.d(TAG, "📊 Прогресс сжатия: $progress%")
                updateVideoUploadProgress(progress)
            },
            onComplete = { compressedFile ->
                isSendingVideo = false
                hideVideoUploadProgress()

                Log.d(TAG, "========================================")
                Log.d(TAG, "📥 РЕЗУЛЬТАТ СЖАТИЯ")
                Log.d(TAG, "========================================")

                if (compressedFile == null) {
                    Log.e(TAG, "❌ Сжатие вернуло null")
                    Toast.makeText(requireContext(), "Ошибка сжатия видео", Toast.LENGTH_SHORT).show()
                    return@compressVideo
                }

                Log.d(TAG, "📁 Сжатый файл: ${compressedFile.absolutePath}")
                Log.d(TAG, "📊 Размер сжатого: ${compressedFile.length() / 1024} KB")
                Log.d(TAG, "📊 Файл существует: ${compressedFile.exists()}")

                if (!compressedFile.exists() || compressedFile.length() <= 0) {
                    Log.e(TAG, "❌ Сжатый файл поврежден или пустой")
                    Toast.makeText(requireContext(), "Ошибка сжатия видео", Toast.LENGTH_SHORT).show()
                    return@compressVideo
                }

                val finalDuration = VideoCompressor.getVideoDuration(compressedFile)
                val finalDurationSec = if (finalDuration > 0) finalDuration else duration
                Log.d(TAG, "⏱️ Финальная длительность: $finalDurationSec сек")

                Log.d(TAG, "📤 ОТПРАВКА ВИДЕО НА СЕРВЕР")
                Log.d(TAG, "📤 chatId: $chatId")
                Log.d(TAG, "📤 chatType: $chatType")
                Log.d(TAG, "📤 Размер файла: ${compressedFile.length() / 1024} KB")

                lifecycleScope.launch {
                    try {
                        val recipientPublicKey = if (chatType == "private") {
                            Log.d(TAG, "🔐 Получаем публичный ключ для private чата")
                            getRecipientPublicKey()
                        } else {
                            Log.d(TAG, "👥 Групповой чат, ключ не требуется")
                            null
                        }

                        Log.d(TAG, "🔑 recipientPublicKey: ${if (recipientPublicKey != null) "получен" else "null"}")

                        viewModel.sendVideoMessage(
                            chatId = chatId,
                            videoFile = compressedFile,
                            duration = finalDurationSec,
                            recipientPublicKey = recipientPublicKey,
                            chatType = chatType
                        )
                        Log.d(TAG, "✅ Видео отправлено в ViewModel")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Ошибка отправки видео: ${e.message}", e)
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                Log.d(TAG, "========================================")
                Log.d(TAG, "🏁 ЗАВЕРШЕНИЕ ОТПРАВКИ ВИДЕО")
                Log.d(TAG, "========================================")
            }
        )
    }

    private fun showVideoUploadProgress() {
        uploadProgressDialog = android.app.ProgressDialog(requireContext()).apply {
            setMessage("Сжатие видео... 0%")
            setCancelable(false)
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            show()
        }
    }

    private fun updateVideoUploadProgress(progress: Int) {
        uploadProgressDialog?.setMessage("Сжатие видео... $progress%")
        uploadProgressDialog?.progress = progress
    }

    private fun hideVideoUploadProgress() {
        uploadProgressDialog?.dismiss()
        uploadProgressDialog = null
    }

    private fun getVideoDuration(file: File): Int {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0
            retriever.release()
            (duration / 1000).toInt()
        } catch (e: Exception) {
            5
        }
    }

    private fun initAudioRecorder() {
        audioRecorderHelper = AudioRecorderHelper(
            context = requireContext(),
            onAmplitudeUpdate = { amplitude ->
                updateRecordingAmplitude(amplitude)
            },
            onRecordingComplete = { file, duration ->
                Log.d(TAG, "🎵 onRecordingComplete: файл ${file.absolutePath}, размер ${file.length()}, длительность $duration сек")

                lifecycleScope.launch {
                    try {
                        if (chatType == "group" && !isUserAdminOrTrainerInGroup) {
                            Toast.makeText(requireContext(), "Только администраторы и тренеры могут отправлять голосовые сообщения", Toast.LENGTH_SHORT).show()
                            file.delete()
                            return@launch
                        }

                        if (!file.exists() || file.length() < 5000) {
                            Log.e(TAG, "❌ Файл не существует или слишком маленький")
                            Toast.makeText(requireContext(), "Ошибка: файл поврежден", Toast.LENGTH_SHORT).show()
                            file.delete()
                            return@launch
                        }

                        val audioDir = File(requireContext().filesDir, "audio_files")
                        if (!audioDir.exists()) {
                            audioDir.mkdirs()
                            Log.d(TAG, "📁 Создана папка: ${audioDir.absolutePath}")
                        }

                        val extension = audioRecorderHelper?.getAudioExtension() ?: "mp3"
                        val fileName = "audio_${System.currentTimeMillis()}.$extension"
                        val savedFile = File(audioDir, fileName)

                        file.inputStream().use { input ->
                            savedFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        Log.d(TAG, "✅ Аудио сохранено: ${savedFile.absolutePath}")
                        Log.d(TAG, "📊 Размер: ${savedFile.length()} байт")
                        Log.d(TAG, "📊 Длительность: $duration сек")

                        if (savedFile.exists() && savedFile.length() > 5000) {
                            Log.d(TAG, "📤 Отправка аудио в чат $chatId")

                            val recipientPublicKey = if (chatType == "private") {
                                getRecipientPublicKey()
                            } else null

                            Log.d(TAG, "🔑 chatType: $chatType, recipientPublicKey: ${if (recipientPublicKey != null) "present" else "null"}")

                            viewModel.sendAudioMessage(chatId, savedFile, duration, recipientPublicKey, chatType)

                            file.delete()
                            Log.d(TAG, "🗑️ Временный файл удален")
                        } else {
                            Log.e(TAG, "❌ Ошибка сохранения аудио: файл невалидный (${savedFile.length()} байт)")
                            Toast.makeText(requireContext(), "Ошибка: аудиофайл поврежден (${savedFile.length()} байт)", Toast.LENGTH_SHORT).show()
                            file.delete()
                            savedFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Ошибка при обработке аудио: ${e.message}", e)
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        file.delete()
                    }
                }
            }
        )
    }

    private fun showAudioRecordingDialog() {
        recordingDialog = BottomSheetDialog(requireContext())
        recordingBinding = LayoutAudioRecordingBinding.inflate(layoutInflater)
        recordingDialog?.setContentView(recordingBinding!!.root)
        recordingDialog?.setCancelable(true)
        recordingDialog?.setOnDismissListener { cancelRecording() }
        setupRecordingControls()
        recordingDialog?.show()
    }

    private fun setupRecordingControls() {
        recordingBinding?.apply {
            btnRecord.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startRecording()
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        stopRecording()
                        true
                    }
                    else -> false
                }
            }
            btnCancel.setOnClickListener { cancelRecording() }
        }
    }

    private fun startRecording() {
        if (isRecording) return
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val extension = audioRecorderHelper?.getAudioExtension() ?: "mp3"
            val audioFileName = "audio_${timeStamp}.$extension"
            audioFile = File(requireContext().cacheDir, audioFileName)

            val success = audioRecorderHelper?.startRecording(audioFile!!) ?: false
            if (success) {
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                startRecordingTimer()
                recordingBinding?.apply {
                    btnRecord.setImageResource(R.drawable.ic_mic_recording)
                    tvRecordingHint.text = "Отпустите для отправки"
                }
            } else {
                Toast.makeText(requireContext(), "Не удалось начать запись", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка записи: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecordingTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                    val minutes = elapsed / 60
                    val seconds = elapsed % 60
                    recordingBinding?.tvRecordingTime?.text = String.format("%02d:%02d", minutes, seconds)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopRecording() {
        if (!isRecording) return
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        val result = audioRecorderHelper?.stopRecording()
        isRecording = false
        if (result != null) {
            val (file, duration) = result
            if (duration >= 2) {
                recordingDialog?.dismiss()
            } else {
                Toast.makeText(requireContext(), "Запись слишком короткая (минимум 2 секунды)", Toast.LENGTH_SHORT).show()
                file.delete()
                recordingDialog?.dismiss()
            }
        } else {
            recordingDialog?.dismiss()
        }
    }

    private fun cancelRecording() {
        if (isRecording) {
            audioRecorderHelper?.cancelRecording()
            isRecording = false
            timerRunnable?.let { handler.removeCallbacks(it) }
            timerRunnable = null
        }
        audioFile?.delete()
        recordingDialog?.dismiss()
    }

    private fun updateRecordingAmplitude(amplitude: Int) {
        recordingBinding?.waveform?.setProgress(amplitude)
    }

    private fun openCamera() {
        if (checkCameraPermission()) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "photo_${timeStamp}.jpg"
            photoFile = File(requireContext().cacheDir, fileName)
            photoFile?.let { file ->
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                cameraLauncher.launch(uri)
            }
        } else {
            requestCameraPermission()
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun checkAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestAudioPermission() {
        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun checkVideoPermissions(): Boolean {
        return checkCameraPermission() && checkAudioPermission()
    }

    private fun requestVideoPermissions() {
        requestVideoPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun saveAndSendImage(file: File) {
        if (chatType == "group" && !isUserAdminOrTrainerInGroup) {
            Toast.makeText(requireContext(), "Только администраторы и тренеры могут отправлять изображения в групповых чатах", Toast.LENGTH_SHORT).show()
            file.delete()
            return
        }
        try {
            val decryptedFilesDir = File(requireContext().filesDir, "decrypted_files")
            if (!decryptedFilesDir.exists()) decryptedFilesDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val savedFile = File(decryptedFilesDir, "img_${timeStamp}.jpg")
            file.copyTo(savedFile, overwrite = true)
            lifecycleScope.launch {
                val recipientPublicKey = if (chatType == "private") {
                    getRecipientPublicKey()
                } else null
                viewModel.sendImageMessage(chatId, savedFile, recipientPublicKey, chatType)
            }
            file.delete()
        } catch (e: IOException) {
            Toast.makeText(requireContext(), "Ошибка сохранения фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressAndSendImage(uri: Uri) {
        if (chatType == "group" && !isUserAdminOrTrainerInGroup) {
            Toast.makeText(requireContext(), "Только администраторы и тренеры могут отправлять изображения в групповых чатах", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val compressedFile = File(requireContext().cacheDir, "compressed_${timeStamp}.jpg")
            val success = ImageCompressor.compressImage(requireContext().contentResolver, uri, compressedFile)
            if (success && compressedFile.exists()) {
                val decryptedFilesDir = File(requireContext().filesDir, "decrypted_files")
                if (!decryptedFilesDir.exists()) decryptedFilesDir.mkdirs()
                val savedFile = File(decryptedFilesDir, "img_${timeStamp}.jpg")
                compressedFile.copyTo(savedFile, overwrite = true)
                compressedFile.delete()
                lifecycleScope.launch {
                    val recipientPublicKey = if (chatType == "private") {
                        getRecipientPublicKey()
                    } else null
                    viewModel.sendImageMessage(chatId, savedFile, recipientPublicKey, chatType)
                }
            } else {
                Toast.makeText(requireContext(), "Не удалось сжать изображение", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка обработки изображения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteMessageDialog(message: Message) {
        val options = mutableListOf<String>()
        if (message.senderId == currentUserId) {
            options.add("Удалить у меня")
            options.add("Удалить у всех")
        } else {
            options.add("Удалить у меня")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить сообщение")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> viewModel.deleteMessageForMe(message.id)
                    1 -> viewModel.deleteMessageForEveryone(message.id)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun playAudio(message: Message) {
        if (currentlyPlayingMessage == message && audioPlayerHelper?.isPlaying() == true) {
            audioPlayerHelper?.pause()
            updatePlayButtonState(message, false)
            stopSeekBarUpdate()
            return
        }

        if (currentlyPlayingMessage == message && audioPlayerHelper?.isPlaying() == false) {
            audioPlayerHelper?.resume()
            updatePlayButtonState(message, true)
            startSeekBarUpdate(message)
            return
        }

        audioPlayerHelper?.stop()
        stopSeekBarUpdate()

        try {
            var audioPath = message.content
            if (!message.localUri.isNullOrEmpty()) {
                val localFile = File(message.localUri)
                if (localFile.exists()) audioPath = message.localUri
            }
            val audioFile = File(audioPath)

            if (!audioFile.exists()) {
                if (message.content.startsWith("http")) {
                    Toast.makeText(requireContext(), "Загрузка аудио...", Toast.LENGTH_SHORT).show()
                    downloadAndPlayAudio(message)
                    return
                }
                Toast.makeText(requireContext(), "Аудиофайл не найден", Toast.LENGTH_SHORT).show()
                return
            }

            if (audioFile.length() < 5000) {
                Toast.makeText(requireContext(), "Аудиофайл поврежден (маленький размер: ${audioFile.length()} байт)", Toast.LENGTH_SHORT).show()
                return
            }

            if (isHtmlFile(audioFile)) {
                Toast.makeText(requireContext(), "Файл поврежден (HTML вместо аудио)", Toast.LENGTH_SHORT).show()
                audioFile.delete()
                return
            }

            Log.d(TAG, "🎵 Воспроизведение: ${audioFile.absolutePath}, размер: ${audioFile.length()}")

            currentlyPlayingMessage = message
            audioPlayerHelper?.play(audioFile)
            startSeekBarUpdate(message)

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка воспроизведения: ${e.message}", e)
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadAndPlayAudio(message: Message) {
        lifecycleScope.launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Токен не найден", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val url = message.content
                val fileName = url.substringAfterLast("/")
                val audioDir = File(requireContext().filesDir, "audio_files")
                if (!audioDir.exists()) audioDir.mkdirs()

                val localFile = File(audioDir, fileName)

                if (localFile.exists() && localFile.length() > 5000) {
                    Log.d(TAG, "✅ Аудио уже скачано: ${localFile.absolutePath}")
                    val updatedMessage = message.copy(localUri = localFile.absolutePath)
                    playAudio(updatedMessage)
                    return@launch
                }

                Log.d(TAG, "📥 Скачиваем аудио: $url")

                val result = withContext(Dispatchers.IO) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer $token")
                            .addHeader("Accept", "audio/mpeg,audio/mp4,audio/3gpp,*/*")
                            .build()

                        val client = OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val contentType = response.header("Content-Type")
                                Log.d(TAG, "📊 Content-Type: $contentType")

                                response.body?.let { body ->
                                    localFile.outputStream().use { output ->
                                        body.byteStream().use { input ->
                                            input.copyTo(output)
                                        }
                                    }
                                }

                                val fileSize = localFile.length()
                                Log.d(TAG, "📊 Скачано байт: $fileSize")

                                if (fileSize > 5000 && !isHtmlFile(localFile)) {
                                    true
                                } else {
                                    localFile.delete()
                                    false
                                }
                            } else {
                                Log.e(TAG, "Ошибка загрузки: ${response.code}")
                                false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при загрузке: ${e.message}", e)
                        false
                    }
                }

                if (result) {
                    Log.d(TAG, "✅ Аудио скачано: ${localFile.absolutePath}, размер: ${localFile.length()}")
                    val updatedMessage = message.copy(localUri = localFile.absolutePath)
                    playAudio(updatedMessage)
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки аудио", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки аудио: ${e.message}", e)
                Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isHtmlFile(file: File): Boolean {
        return try {
            if (file.length() < 100) return false
            val buffer = ByteArray(200)
            file.inputStream().use { input ->
                val read = input.read(buffer)
                if (read <= 0) return false
            }
            val str = String(buffer, Charsets.UTF_8)
            str.contains("<html", ignoreCase = true) ||
                    str.contains("<!DOCTYPE", ignoreCase = true) ||
                    str.contains("<?xml", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun seekTo(message: Message, progress: Int) {
        if (currentlyPlayingMessage == message) {
            audioPlayerHelper?.seekTo(progress)
        }
    }

    private fun startSeekBarUpdate(message: Message) {
        stopSeekBarUpdate()
        playbackRunnable = object : Runnable {
            override fun run() {
                val currentState = viewModel.chatDetailState.value
                if (currentState is ChatDetailState.Success) {
                    val position = currentState.messages.indexOf(message)
                    if (position >= 0 && currentlyPlayingMessage == message) {
                        playbackHandler.postDelayed(this, 100)
                    }
                }
            }
        }
        playbackHandler.post(playbackRunnable!!)
    }

    private fun stopSeekBarUpdate() {
        playbackRunnable?.let { playbackHandler.removeCallbacks(it) }
        playbackRunnable = null
    }

    private fun updatePlayButtonState(message: Message, isPlaying: Boolean) {
        messageAdapter.updatePlayButtonState(message.id, isPlaying)
    }

    private fun stopPlayback() {
        audioPlayerHelper?.stop()
        currentlyPlayingMessage = null
        stopSeekBarUpdate()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chatDetailState.collect { state ->
                    when (state) {
                        is ChatDetailState.Loading -> {
                            if (!isDataLoaded) {
                                binding.progressBar.visibility = View.VISIBLE
                            }
                        }
                        is ChatDetailState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            swipeRefreshLayout?.isRefreshing = false

                            val messages = state.messages

                            if (messages.isNotEmpty() && messages.firstOrNull()?.chatId == chatId) {
                                val hasTempMessages = messages.any { it.id < 0 }

                                if (hasTempMessages) {
                                    messageAdapter.updateMessagesWithoutAnimation(messages)
                                } else {
                                    messageAdapter.updateMessages(messages)
                                }

                                if (messages.isNotEmpty() && !hasTempMessages) {
                                    binding.rvMessages.scrollToPosition(messages.size - 1)
                                }
                            } else if (messages.isEmpty()) {
                                messageAdapter.updateMessages(emptyList())
                            }

                            isDataLoaded = true
                        }
                        is ChatDetailState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            swipeRefreshLayout?.isRefreshing = false
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sendMessageState.collect { state ->
                    when (state) {
                        is SendMessageState.Sending -> {
                            binding.btnSend.isEnabled = false
                            binding.btnVoice.isEnabled = false
                            binding.btnAttach.isEnabled = false
                            binding.btnVideo.isEnabled = false
                            binding.btnSend.alpha = 0.5f
                        }
                        is SendMessageState.Success, is SendMessageState.Error -> {
                            binding.btnSend.isEnabled = true
                            binding.btnVoice.isEnabled = true
                            binding.btnAttach.isEnabled = true
                            binding.btnVideo.isEnabled = true
                            binding.btnSend.alpha = 1.0f
                            if (state is SendMessageState.Error) {
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                viewModel.clearSendState()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteMessageState.collect { state ->
                    when (state) {
                        is DeleteMessageState.Success -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        is DeleteMessageState.Error -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopChatDetailPolling()
        audioRecorderHelper?.release()
        audioPlayerHelper?.release()
        stopPlayback()
        uploadProgressDialog?.dismiss()
        _binding = null
        recordingDialog?.dismiss()
    }
}
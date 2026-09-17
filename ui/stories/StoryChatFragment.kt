package com.fitnesslemon.app.ui.stories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.fitnesslemon.app.R
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitnesslemon.app.databinding.FragmentStoryChatBinding
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.SendStoryMessageRequest
import com.fitnesslemon.app.data.models.Message
import com.fitnesslemon.app.ui.chat.adapters.MessageAdapter
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class StoryChatFragment : Fragment() {

    private var _binding: FragmentStoryChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var messageAdapter: MessageAdapter
    private var currentUserId = 0
    private var storyId = 0
    private var chatId = 0
    private var chatName = ""
    private var messages = mutableListOf<Message>()
    private var likeCount = 0
    private var isLiked = false

    companion object {
        private const val ARG_STORY_ID = "story_id"
        private const val ARG_CHAT_ID = "chat_id"
        private const val ARG_CHAT_NAME = "chat_name"
        private const val ARG_LIKE_COUNT = "like_count"
        private const val ARG_IS_LIKED = "is_liked"

        fun newInstance(
            storyId: Int,
            chatId: Int,
            chatName: String,
            likeCount: Int = 0,
            isLiked: Boolean = false
        ): StoryChatFragment {
            val fragment = StoryChatFragment()
            val args = Bundle()
            args.putInt(ARG_STORY_ID, storyId)
            args.putInt(ARG_CHAT_ID, chatId)
            args.putString(ARG_CHAT_NAME, chatName)
            args.putInt(ARG_LIKE_COUNT, likeCount)
            args.putBoolean(ARG_IS_LIKED, isLiked)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            storyId = it.getInt(ARG_STORY_ID)
            chatId = it.getInt(ARG_CHAT_ID)
            chatName = it.getString(ARG_CHAT_NAME) ?: "Чат"
            likeCount = it.getInt(ARG_LIKE_COUNT, 0)
            isLiked = it.getBoolean(ARG_IS_LIKED, false)
        }
        currentUserId = PreferencesManager.getUserId()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoryChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        setupLikeButton()
        loadMessages()
        startPolling()
    }

    private fun setupToolbar() {
        binding.toolbar.title = chatName
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            messages = emptyList(),
            currentUserId = currentUserId,
            onAudioPlayClick = { _, _ -> },
            onImageClick = { _ -> },
            onSeekBarTouch = { _, _ -> },
            onSeekBarProgress = { _, _ -> },
            onMessageLongClick = { _ -> },
            chatType = "story"
        )
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }

        binding.btnLike.setOnClickListener {
            toggleLike()
        }
    }

    private fun setupLikeButton() {
        updateLikeButton()
    }

    private fun updateLikeButton() {
        binding.btnLike.setImageResource(
            if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
        binding.tvLikeCount.text = likeCount.toString()
    }

    private fun toggleLike() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Токен не найден", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val response = if (isLiked) {
                    ApiClient.apiService.unlikeStory("Bearer $token", storyId)
                } else {
                    ApiClient.apiService.likeStory("Bearer $token", storyId)
                }

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.success) {
                        isLiked = !isLiked
                        val data = result.data as? Map<*, *>
                        likeCount = (data?.get("like_count") as? Double)?.toInt() ?: (likeCount + (if (isLiked) 1 else -1))
                        updateLikeButton()

                        binding.btnLike.animate()
                            .scaleX(1.3f)
                            .scaleY(1.3f)
                            .setDuration(200)
                            .withEndAction {
                                binding.btnLike.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(200)
                                    .start()
                            }
                            .start()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessage(text: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Токен не найден", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Временное сообщение
                val tempMessage = Message(
                    id = -System.currentTimeMillis().toInt(),
                    chatId = chatId,
                    senderId = currentUserId,
                    senderName = "Вы",
                    senderAvatar = null,
                    type = "text",
                    content = text,
                    fileName = null,
                    fileSize = null,
                    mimeType = null,
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    isRead = true,
                    isMe = true,
                    duration = null,
                    localUri = null,
                    fileToken = null
                )

                // Добавляем в UI
                messages.add(tempMessage)
                messageAdapter.updateMessages(messages)
                binding.rvMessages.scrollToPosition(messages.size - 1)
                binding.etMessage.text.clear()

                // Отправляем на сервер
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.sendStoryMessage(
                        "Bearer $token",
                        storyId,
                        SendStoryMessageRequest(text)
                    )
                }

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.success) {
                        val data = result.data
                        if (data is Message) {
                            val index = messages.indexOfFirst { it.id == tempMessage.id }
                            if (index >= 0) {
                                messages[index] = data
                                messageAdapter.updateMessages(messages)
                            }
                        }
                    }
                } else {
                    messages.remove(tempMessage)
                    messageAdapter.updateMessages(messages)
                    Toast.makeText(requireContext(), "Ошибка отправки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMessages() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) return@launch

                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.getStoryMessages("Bearer $token", storyId)
                }

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.success) {
                        val data = result.data
                        if (data is List<*>) {
                            val newMessages = data.filterIsInstance<Message>()
                            messages.clear()
                            messages.addAll(newMessages)
                            messageAdapter.updateMessages(messages)
                            if (messages.isNotEmpty()) {
                                binding.rvMessages.scrollToPosition(messages.size - 1)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Игнорируем
            }
        }
    }

    private fun startPolling() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(5000)
                loadMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.fitnesslemon.app.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.CreateChatRequest
import com.fitnesslemon.app.data.models.Chat
import com.fitnesslemon.app.databinding.FragmentChatListBinding
import com.fitnesslemon.app.ui.chat.adapters.ChatListAdapter
import com.fitnesslemon.app.ui.main.MainActivity
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        observeViewModel()

        viewModel.loadChats()
        viewModel.startChatListPolling()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Чаты"
    }

    private fun setupRecyclerView() {
        adapter = ChatListAdapter(
            chats = emptyList(),
            onChatClick = { chat -> onChatClick(chat) },
            onChatLongClick = { chat -> onChatLongClick(chat) }
        )
        binding.rvChats.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChats.adapter = adapter
        binding.rvChats.setHasFixedSize(true)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadChats()
        }
    }

    private fun setupFab() {
        binding.fabNewChat.setOnClickListener {
            showNewChatDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.chatListState.collect { state ->
                    if (!isAdded || _binding == null) return@collect

                    binding.swipeRefresh.isRefreshing = false

                    when (state) {
                        is ChatListState.Loading -> {
                            if (binding.rvChats.visibility != View.VISIBLE) {
                                binding.progressBar.visibility = View.VISIBLE
                            }
                        }
                        is ChatListState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            val chats = state.chats

                            if (chats.isNotEmpty()) {
                                binding.rvChats.visibility = View.VISIBLE
                                binding.tvEmpty.visibility = View.GONE
                                adapter = ChatListAdapter(
                                    chats = chats,
                                    onChatClick = { chat -> onChatClick(chat) },
                                    onChatLongClick = { chat -> onChatLongClick(chat) }
                                )
                                binding.rvChats.adapter = adapter
                            } else {
                                binding.rvChats.visibility = View.GONE
                                binding.tvEmpty.visibility = View.VISIBLE
                            }
                        }
                        is ChatListState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.rvChats.visibility = View.GONE
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.tvEmpty.text = state.message
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                (activity as? MainActivity)?.updateNotificationBadge(count)
            }
        }
    }

    private fun onChatClick(chat: Chat) {
        viewModel.stopChatListPolling()

        // ✅ Очищаем старые сообщения перед открытием нового чата
        viewModel.clearChatMessages()

        val fragment = ChatDetailFragment.newInstance(chat.id, chat.name ?: "Чат")
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun onChatLongClick(chat: Chat) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Действия с чатом")
            .setItems(arrayOf("Удалить чат")) { _, which ->
                when (which) {
                    0 -> {
                        Toast.makeText(requireContext(), "Удаление чата будет доступно позже", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showNewChatDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_chat, null)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.etPhone)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Новый чат")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val phone = etPhone.text.toString().trim()
                if (phone.isNotEmpty()) {
                    createChatByPhone(phone)
                } else {
                    Toast.makeText(requireContext(), "Введите номер телефона", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createChatByPhone(phone: String) {
        lifecycleScope.launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Токен не найден. Выйдите и зайдите заново.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val userResponse = ApiClient.apiService.findUserByPhone("Bearer $token", phone)

                if (userResponse.isSuccessful) {
                    val responseBody = userResponse.body()
                    val userId = responseBody?.data?.userId ?: 0

                    if (userId > 0) {
                        val currentUserId = PreferencesManager.getUserId()
                        if (userId == currentUserId) {
                            Toast.makeText(requireContext(), "Нельзя создать чат с самим собой", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val request = CreateChatRequest(
                            type = "private",
                            name = "",
                            participants = listOf(userId)
                        )

                        val chatResponse = ApiClient.apiService.createChat(
                            "Bearer $token",
                            request
                        )

                        if (chatResponse.isSuccessful) {
                            val createResponse = chatResponse.body()
                            if (createResponse != null && createResponse.success) {
                                val chat = createResponse.data
                                chat?.let {
                                    Toast.makeText(requireContext(), "Чат создан", Toast.LENGTH_SHORT).show()

                                    // ✅ Очищаем старые сообщения перед открытием нового чата
                                    viewModel.clearChatMessages()

                                    val fragment = ChatDetailFragment.newInstance(it.id, it.name ?: "Чат")
                                    parentFragmentManager.beginTransaction()
                                        .replace(R.id.fragmentContainer, fragment)
                                        .addToBackStack(null)
                                        .commit()
                                }
                            } else {
                                val errorMsg = createResponse?.message ?: "Неизвестная ошибка"
                                Toast.makeText(requireContext(), "Ошибка создания чата: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val errorBody = chatResponse.errorBody()?.string()
                            val errorMessage = when (chatResponse.code()) {
                                400 -> if (errorBody?.contains("already exists") == true) {
                                    "Чат с этим пользователем уже существует"
                                } else {
                                    "Неверный запрос"
                                }
                                403 -> "Нет прав для создания чата"
                                404 -> "Пользователь не найден"
                                409 -> "Чат с этим пользователем уже существует"
                                500 -> "Ошибка сервера. Попробуйте позже."
                                else -> "Ошибка создания чата: ${chatResponse.code()}"
                            }
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Пользователь с таким телефоном не найден", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorMessage = when (userResponse.code()) {
                        404 -> "Пользователь с таким телефоном не найден"
                        400 -> "Неверный формат номера телефона"
                        else -> "Ошибка поиска пользователя: ${userResponse.code()}"
                    }
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: HttpException) {
                Toast.makeText(requireContext(), "Ошибка сети: ${e.code()}", Toast.LENGTH_LONG).show()
            } catch (e: IOException) {
                Toast.makeText(requireContext(), "Ошибка соединения. Проверьте интернет.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopChatListPolling()
        _binding = null
    }
}
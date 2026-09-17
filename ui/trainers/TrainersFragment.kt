package com.fitnesslemon.app.ui.trainers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentTrainersBinding
import com.fitnesslemon.app.databinding.DialogTrainerDetailBinding
import com.fitnesslemon.app.ui.adapters.TrainerAdapter
import com.fitnesslemon.app.data.models.Trainer
import com.fitnesslemon.app.ui.chat.ChatDetailFragment
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.CreateChatRequest
import com.fitnesslemon.app.data.api.CreateChatResponse
import com.fitnesslemon.app.data.models.Chat
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrainersFragment : Fragment() {

    private var _binding: FragmentTrainersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrainersViewModel by lazy { TrainersViewModel() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        binding.rvTrainers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrainers.setHasFixedSize(true)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trainersState.collect { state ->
                    binding.swipeRefresh.isRefreshing = false

                    when (state) {
                        is TrainersState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.rvTrainers.visibility = View.GONE
                            binding.tvEmpty.visibility = View.GONE
                        }
                        is TrainersState.Success -> {
                            binding.progressBar.visibility = View.GONE

                            if (state.trainers.isEmpty()) {
                                binding.rvTrainers.visibility = View.GONE
                                binding.tvEmpty.visibility = View.VISIBLE
                                binding.tvEmpty.text = "Нет данных о тренерах"
                            } else {
                                binding.rvTrainers.visibility = View.VISIBLE
                                binding.tvEmpty.visibility = View.GONE

                                binding.rvTrainers.adapter = TrainerAdapter(
                                    trainers = state.trainers,
                                    onItemClick = { trainer ->
                                        showTrainerDetail(trainer)
                                    },
                                    onChatClick = { trainer ->
                                        startChatWithTrainer(trainer)
                                    }
                                )
                            }
                        }
                        is TrainersState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.rvTrainers.visibility = View.GONE
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.tvEmpty.text = "Ошибка загрузки: ${state.message}"
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showTrainerDetail(trainer: Trainer) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_trainer_detail, null)

        val ivPhoto = dialogView.findViewById<android.widget.ImageView>(R.id.ivTrainerPhoto)
        val tvName = dialogView.findViewById<android.widget.TextView>(R.id.tvTrainerName)
        val tvSpecialization = dialogView.findViewById<android.widget.TextView>(R.id.tvSpecialization)
        val tvExperience = dialogView.findViewById<android.widget.TextView>(R.id.tvExperience)
        val tvBio = dialogView.findViewById<android.widget.TextView>(R.id.tvBio)

        tvName.text = trainer.name
        tvSpecialization.text = trainer.specialization
        tvExperience.text = "Опыт: ${trainer.experience}"
        tvBio.text = trainer.bio.ifEmpty { "Нет информации" }

        if (!trainer.photo.isNullOrEmpty()) {
            Glide.with(this)
                .load(trainer.photo)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(ivPhoto)
        } else {
            ivPhoto.setImageResource(R.drawable.ic_profile)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    // ========== ИСПРАВЛЕННЫЙ МЕТОД startChatWithTrainer ==========
    private fun startChatWithTrainer(trainer: Trainer) {
        lifecycleScope.launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Токен не найден", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val currentUserId = PreferencesManager.getUserId()
                if (currentUserId == 0) {
                    Toast.makeText(requireContext(), "Пользователь не авторизован. Попробуйте выйти и зайти снова.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // ИСПРАВЛЕНО: используем trainer.id
                val trainerId = trainer.id
                println("🔍 Текущий пользователь ID: $currentUserId")
                println("🔍 Тренер ID: $trainerId")

                binding.progressBar.visibility = View.VISIBLE

                val existingChat = findExistingChatWithTrainer(trainerId)

                if (existingChat != null) {
                    binding.progressBar.visibility = View.GONE
                    println("✅ Найден существующий чат с ID: ${existingChat.id}")

                    // ИСПРАВЛЕНО: используем trainer.name
                    val fragment = ChatDetailFragment.newInstance(existingChat.id, trainer.name)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit()
                    return@launch
                }

                println("🔍 Создаем новый чат с тренером ID: $trainerId")

                val request = CreateChatRequest(
                    type = "private",
                    name = null,
                    participants = listOf(trainerId)
                )

                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.createChat("Bearer $token", request)
                }

                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val createResponse = response.body()
                    println("📦 [startChatWithTrainer] Тело ответа: $createResponse")

                    if (createResponse != null && createResponse.success) {
                        // ИСПРАВЛЕНО: получаем чат из data
                        val chat = createResponse.data
                        println("✅ Чат создан: $chat")

                        chat?.let {
                            // ИСПРАВЛЕНО: используем it.id вместо chat.id
                            val chatId = it.id
                            println("✅ ID чата: $chatId")

                            if (chatId == 0) {
                                Toast.makeText(requireContext(), "Ошибка: сервер вернул ID=0", Toast.LENGTH_LONG).show()
                            } else {
                                // ИСПРАВЛЕНО: используем trainer.name
                                val fragment = ChatDetailFragment.newInstance(chatId, trainer.name)
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragmentContainer, fragment)
                                    .addToBackStack(null)
                                    .commit()
                            }
                        } ?: run {
                            Toast.makeText(requireContext(), "Ошибка: данные чата пустые", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorMsg = createResponse?.message ?: "Неизвестная ошибка"
                        println("❌ Ошибка создания чата: $errorMsg")
                        Toast.makeText(requireContext(), "Ошибка создания чата: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка создания чата: ${response.code()} - $errorBody")

                    when (response.code()) {
                        403 -> Toast.makeText(requireContext(), "Нет прав для создания чата", Toast.LENGTH_SHORT).show()
                        401 -> Toast.makeText(requireContext(), "Требуется авторизация", Toast.LENGTH_SHORT).show()
                        409 -> Toast.makeText(requireContext(), "Чат уже существует", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(requireContext(), "Ошибка создания чата: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                println("❌ Ошибка: ${e.message}")
                e.printStackTrace()
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun findExistingChatWithTrainer(trainerId: Int): Chat? {
        try {
            val token = PreferencesManager.getToken() ?: return null
            val currentUserId = PreferencesManager.getUserId()

            println("🔍 Поиск существующего чата с тренером $trainerId")

            val response = ApiClient.apiService.getChats("Bearer $token")

            if (response.isSuccessful) {
                val chatsResponse = response.body()
                if (chatsResponse != null && chatsResponse.success) {
                    val chats = chatsResponse.data
                    println("🔍 Всего чатов: ${chats.size}")

                    val existingChat = chats.find { chat ->
                        val isPrivate = chat.type == "private"
                        val participantId = chat.participant?.id

                        println("🔍 Чат ID: ${chat.id}, тип: ${chat.type}, participantId: $participantId")

                        isPrivate && participantId == trainerId
                    }

                    if (existingChat != null) {
                        println("✅ Найден существующий чат: ${existingChat.id}")
                    } else {
                        println("❌ Существующий чат не найден")
                    }

                    return existingChat
                } else {
                    println("❌ Пустой ответ от сервера")
                }
            } else {
                println("❌ Ошибка получения списка чатов: ${response.code()}")
            }
        } catch (e: Exception) {
            println("❌ Ошибка поиска чата: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
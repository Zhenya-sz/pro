package com.fitnesslemon.app.ui.admin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.AddParticipantRequest
import com.fitnesslemon.app.data.api.CreateChatRequest
import com.fitnesslemon.app.data.api.UpdateChatRequest
import com.fitnesslemon.app.data.models.AdminUser
import com.fitnesslemon.app.data.models.Chat
import com.fitnesslemon.app.data.models.ChatParticipant
import com.fitnesslemon.app.databinding.DialogSelectUserBinding
import com.fitnesslemon.app.databinding.FragmentAdminGroupsBinding
import com.fitnesslemon.app.ui.admin.adapters.AdminGroupsAdapter
import com.fitnesslemon.app.ui.admin.adapters.UserSelectionAdapter
import com.fitnesslemon.app.ui.chat.ChatDetailFragment
import com.fitnesslemon.app.ui.chat.dialogs.ParticipantsDialog
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class AdminGroupsFragment : Fragment() {

    companion object {
        private const val TAG = "AdminGroupsFragment"
    }

    private var _binding: FragmentAdminGroupsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private val groupsAdapter = AdminGroupsAdapter(
        onGroupClick = { group -> openGroupChat(group) },
        onManageClick = { group -> showGroupManagementDialog(group) },
        onEditClick = { group -> showEditGroupDialog(group) },
        onDeleteClick = { group -> confirmDeleteGroup(group) }
    )

    private var allGroups = listOf<Chat>()
    private var selectedGroupForAvatar: Chat? = null

    private val avatarPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedGroupForAvatar?.let { group ->
                startCropActivity(it, group)
            }
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val resultUri = UCrop.getOutput(result.data!!)
            Log.d(TAG, "UCrop output Uri: $resultUri")
            resultUri?.let { uri ->
                selectedGroupForAvatar?.let { group ->
                    uploadAvatar(group, uri)
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(result.data!!)
            Log.e(TAG, "UCrop error: ${cropError?.message}")
            showToast("Ошибка обрезки: ${cropError?.message}")
        }
        selectedGroupForAvatar = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        setupFab()
        loadGroups()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Управление группами"
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupRecyclerView() {
        binding.rvGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGroups.adapter = groupsAdapter
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener {
            filterGroups(binding.etSearch.text.toString())
        }
        binding.swipeRefresh.setOnRefreshListener { loadGroups() }
    }

    private fun setupFab() {
        binding.fabCreateGroup.setOnClickListener {
            showCreateGroupDialog()
        }
    }

    private fun isFragmentAlive(): Boolean = isAdded && _binding != null

    private fun loadGroups() {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            binding.swipeRefresh.isRefreshing = true
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.apiService.getChats("Bearer $token")
                if (response.isSuccessful) {
                    val chatsResponse = response.body()
                    if (chatsResponse != null && chatsResponse.success) {
                        allGroups = chatsResponse.data.filter { it.type == "group" }
                        if (isFragmentAlive()) {
                            groupsAdapter.submitList(allGroups)
                            updateEmptyView(allGroups.isEmpty())
                        }
                    } else {
                        allGroups = emptyList()
                        if (isFragmentAlive()) {
                            groupsAdapter.submitList(emptyList())
                            updateEmptyView(true)
                        }
                    }
                } else {
                    showToast("Ошибка загрузки групп: ${response.code()}")
                }
            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            } finally {
                if (isFragmentAlive()) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun filterGroups(query: String) {
        val filtered = if (query.isEmpty()) allGroups
        else allGroups.filter {
            it.name?.contains(query, ignoreCase = true) == true ||
                    it.description?.contains(query, ignoreCase = true) == true
        }
        groupsAdapter.submitList(filtered)
        updateEmptyView(filtered.isEmpty())
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        if (!isFragmentAlive()) return
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvGroups.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun openGroupChat(group: Chat) {
        val groupId = group.id
        val groupName = group.name ?: "Группа"
        Log.d(TAG, "openGroupChat: id=$groupId, name=$groupName")
        val fragment = ChatDetailFragment.newInstance(groupId, groupName)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showGroupManagementDialog(group: Chat) {
        if (!isFragmentAlive()) return
        val items = mutableListOf(
            "👥 Участники",
            "📊 Статистика",
            "✏️ Редактировать",
            "🖼️ Изменить аватар",
            "➕ Добавить участника",
            "➖ Удалить участника",
            "❌ Удалить группу"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(group.name ?: "Группа")
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showParticipantsDialog(group)
                    1 -> showStatisticsDialog(group)
                    2 -> showEditGroupDialog(group)
                    3 -> pickAvatarImage(group)
                    4 -> showAddUserDialog(group)
                    5 -> showRemoveUserDialog(group)
                    6 -> confirmDeleteGroup(group)
                }
            }
            .show()
    }

    private fun showParticipantsDialog(group: Chat) {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.adminApiService.getChatParticipants("Bearer $token", group.id)
                if (response.isSuccessful) {
                    val participants = response.body() ?: emptyList()
                    if (isFragmentAlive()) {
                        ParticipantsDialog(requireContext(), participants, participants.size).show()
                    }
                } else {
                    showToast("Ошибка загрузки участников: ${response.code()}")
                }
            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun showStatisticsDialog(group: Chat) {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.adminApiService.getChatStatistics("Bearer $token", group.id)
                if (response.isSuccessful) {
                    val stats = response.body()
                    if (stats != null && isFragmentAlive()) {
                        val topUsersText = stats.topUsers.take(5).joinToString("\n") { user ->
                            "${user.name}: ${user.messageCount} сообщ."
                        }
                        val message = buildString {
                            append("Всего сообщений: ${stats.totalMessages}\n")
                            append("Участников: ${stats.participantsCount}\n")
                            append("Активных за 7 дней: ${stats.activeParticipants}\n")
                            append("\nТоп участников:\n$topUsersText")
                        }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Статистика группы")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else {
                    showToast("Ошибка загрузки статистики: ${response.code()}")
                }
            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun showEditGroupDialog(group: Chat) {
        if (!isFragmentAlive()) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_group, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etGroupDescription)

        etName.setText(group.name)
        etDescription.setText(group.description)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактирование группы")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = etName.text.toString().trim()
                val newDesc = etDescription.text.toString().trim()
                if (newName.isNotEmpty()) updateGroup(group.id, newName, newDesc)
                else showToast("Название не может быть пустым")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateGroup(chatId: Int, name: String, description: String) {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val request = UpdateChatRequest(name = name, description = description)
                val response = ApiClient.adminApiService.updateChat("Bearer $token", chatId, request)
                if (response.isSuccessful) {
                    showToast("Группа обновлена")
                    loadGroups()
                } else {
                    showToast("Ошибка обновления: ${response.code()}")
                }
            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun pickAvatarImage(group: Chat) {
        selectedGroupForAvatar = group
        avatarPickerLauncher.launch("image/*")
    }

    private fun startCropActivity(sourceUri: Uri, group: Chat) {
        selectedGroupForAvatar = group
        try {
            val cropFile = File(requireContext().cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
            val destinationUri = Uri.fromFile(cropFile)

            val options = UCrop.Options()
            options.setCircleDimmedLayer(true)
            options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            options.setCompressionQuality(90)

            val uCropIntent = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1024, 1024)
                .withOptions(options)
                .getIntent(requireContext())

            cropLauncher.launch(uCropIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подготовки изображения", e)
            showToast("Ошибка подготовки изображения: ${e.message}")
            selectedGroupForAvatar = null
        }
    }

    private fun uploadAvatar(group: Chat, uri: Uri) {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            try {
                val token = PreferencesManager.getToken() ?: return@launch

                val avatarFile = File(requireContext().cacheDir, uri.lastPathSegment ?: "avatar.jpg")
                if (!avatarFile.exists()) {
                    showToast("Файл аватара не найден")
                    return@launch
                }

                val requestFile = avatarFile.asRequestBody("image/jpeg".toMediaType())
                val avatarPart = MultipartBody.Part.createFormData("avatar", avatarFile.name, requestFile)
                avatarFile.delete()

                val response = ApiClient.adminApiService.uploadChatAvatar("Bearer $token", group.id, avatarPart)
                if (response.isSuccessful()) {
                    showToast("Аватар обновлён")
                    loadGroups()
                } else {
                    showToast("Ошибка загрузки аватара: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки аватара", e)
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun showAddUserDialog(group: Chat) {
        if (!isFragmentAlive()) return

        val dialogBinding = DialogSelectUserBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить участника")
            .setView(dialogBinding.root)
            .create()

        dialogBinding.toolbar.setNavigationOnClickListener { dialog.dismiss() }

        lifecycleScope.launch {
            val token = PreferencesManager.getToken() ?: return@launch
            val response = ApiClient.adminApiService.getUsers("Bearer $token")
            if (response.isSuccessful) {
                val adminResponse = response.body()
                val users = if (adminResponse != null && adminResponse.success) {
                    // КОНВЕРТИРУЕМ ИЗ data.api.AdminUser В data.models.AdminUser
                    adminResponse.data.map { apiUser ->
                        AdminUser(
                            id = apiUser.id,
                            name = apiUser.name,
                            email = apiUser.email,
                            phone = apiUser.phone,
                            role = apiUser.role,
                            subscriptionName = apiUser.subscriptionName,
                            subscriptionExpiry = apiUser.subscriptionExpiry,
                            visitsCount = 0,
                            registrationDate = apiUser.registrationDate,
                            avatar = apiUser.avatar,
                            isActive = apiUser.isActive
                        )
                    }
                } else {
                    emptyList()
                }
                val adapter = UserSelectionAdapter(users) { user ->
                    addParticipant(group.id, user.id)
                    dialog.dismiss()
                }
                dialogBinding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
                dialogBinding.rvUsers.adapter = adapter

                dialogBinding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        adapter.filter.filter(s.toString())
                    }
                })
            }
        }

        dialog.show()
    }

    private fun addParticipant(chatId: Int, userId: Int) {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val request = AddParticipantRequest(userId = userId)
                val response = ApiClient.adminApiService.addParticipant("Bearer $token", chatId, request)
                if (response.isSuccessful) {
                    showToast("Участник добавлен")
                    loadGroups()
                } else {
                    val errorBody = response.errorBody()?.string()
                    if (errorBody?.contains("already_member") == true) {
                        showToast("Пользователь уже в группе")
                    } else {
                        showToast("Ошибка добавления: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun showRemoveUserDialog(group: Chat) {
        if (!isFragmentAlive()) return

        lifecycleScope.launch {
            val token = PreferencesManager.getToken() ?: return@launch
            val response = ApiClient.adminApiService.getChatParticipants("Bearer $token", group.id)
            if (response.isSuccessful) {
                val participants = response.body() ?: emptyList()
                if (participants.isEmpty()) {
                    showToast("Нет участников для удаления")
                    return@launch
                }

                val names = participants.map { "${it.name} (${it.role})" }.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Удалить участника")
                    .setItems(names) { _, which ->
                        val participant = participants[which]
                        if (participant.id == PreferencesManager.getUserId()) {
                            showToast("Нельзя удалить самого себя")
                        } else {
                            confirmRemoveParticipant(group.id, participant.id, participant.name)
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            } else {
                showToast("Ошибка загрузки участников")
            }
        }
    }

    private fun confirmRemoveParticipant(chatId: Int, userId: Int, userName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Подтверждение")
            .setMessage("Удалить пользователя \"$userName\" из группы?")
            .setPositiveButton("Удалить") { _, _ ->
                removeParticipant(chatId, userId)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun removeParticipant(chatId: Int, userId: Int) {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.adminApiService.removeParticipant("Bearer $token", chatId, userId)
                if (response.isSuccessful) {
                    showToast("Участник удалён")
                    loadGroups()
                } else {
                    showToast("Ошибка удаления: ${response.code()}")
                }
            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun confirmDeleteGroup(group: Chat) {
        if (!isFragmentAlive()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление группы")
            .setMessage("Вы уверены, что хотите удалить группу \"${group.name}\"? Все сообщения будут потеряны.")
            .setPositiveButton("Удалить") { _, _ -> deleteGroup(group.id) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteGroup(chatId: Int) {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val response = ApiClient.adminApiService.deleteChat("Bearer $token", chatId)
                if (response.isSuccessful) {
                    showToast("Группа удалена")
                    loadGroups()
                } else {
                    showToast("Ошибка удаления: ${response.code()}")
                }
            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun showCreateGroupDialog() {
        if (!isFragmentAlive()) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_group, null)
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Создание группы")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val name = etGroupName.text.toString().trim()
                val description = etDescription.text.toString().trim()
                if (name.isNotEmpty()) createGroup(name, description)
                else showToast("Введите название группы")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createGroup(name: String, description: String) {
        lifecycleScope.launch {
            if (!isFragmentAlive()) return@launch
            try {
                val token = PreferencesManager.getToken() ?: return@launch
                val currentUserId = PreferencesManager.getUserId()
                val request = CreateChatRequest(type = "group", name = name, participants = listOf(currentUserId))
                val response = ApiClient.apiService.createChat("Bearer $token", request)

                if (response.isSuccessful) {
                    val createResponse = response.body()
                    if (createResponse != null && createResponse.success) {
                        val chat = createResponse.data
                        showToast("Группа \"$name\" создана")

                        if (description.isNotEmpty() && chat != null) {
                            updateGroup(chat.id, name, description)
                        } else {
                            loadGroups()
                        }

                        chat?.let {
                            if (isFragmentAlive()) {
                                val fragment = ChatDetailFragment.newInstance(it.id, it.name ?: name)
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragmentContainer, fragment)
                                    .addToBackStack(null)
                                    .commit()
                            }
                        }
                    } else {
                        val errorMsg = createResponse?.message ?: "Неизвестная ошибка"
                        showToast("Ошибка создания группы: $errorMsg")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    showToast("Ошибка создания группы: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun showToast(message: String) {
        if (isFragmentAlive()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
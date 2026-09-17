package com.fitnesslemon.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentAdminUsersBinding
import com.fitnesslemon.app.ui.admin.adapters.AdminUsersAdapter
import com.fitnesslemon.app.data.models.CreateUserRequest
import com.fitnesslemon.app.data.models.UpdateUserRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AdminUsersFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadUsers()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Управление пользователями"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.setHasFixedSize(true)
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            showCreateUserDialog()
        }

        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString()
            viewModel.searchUsers(query)
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadUsers()
        }
    }

    private fun observeViewModel() {
        viewModel.users.observe(viewLifecycleOwner) { users ->
            if (users.isNotEmpty()) {
                binding.rvUsers.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                binding.rvUsers.adapter = AdminUsersAdapter(
                    users = users,
                    onItemClick = { user -> showUserDetails(user) },
                    onEditClick = { user -> showEditUserDialog(user) },
                    onResetPasswordClick = { user -> showResetPasswordDialog(user) },
                    onDeleteClick = { user -> showDeleteUserDialog(user) }
                )
            } else {
                binding.rvUsers.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading && viewModel.users.value?.isEmpty() == true)
                View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun showCreateUserDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_user, null)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etEmail)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.etPhone)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Создание пользователя")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val name = etName.text.toString()
                val email = etEmail.text.toString()
                val phone = etPhone.text.toString()

                if (name.isNotEmpty() && email.isNotEmpty()) {
                    val request = CreateUserRequest(
                        name = name,
                        email = email,
                        phone = phone,
                        password = "password123",
                        role = "subscriber"
                    )
                    viewModel.createUser(request)
                } else {
                    Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showUserDetails(user: com.fitnesslemon.app.data.models.AdminUser) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_user_details, null)

        // ИСПРАВЛЕНО: Добавлены правильные импорты для TextView и ImageView
        val tvName = dialogView.findViewById<TextView>(R.id.tvName)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvEmail)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvPhone)
        val tvRole = dialogView.findViewById<TextView>(R.id.tvRole)
        val tvSubscription = dialogView.findViewById<TextView>(R.id.tvSubscription)
        val tvVisits = dialogView.findViewById<TextView>(R.id.tvVisits)
        val tvRegistered = dialogView.findViewById<TextView>(R.id.tvRegistered)
        val ivAvatar = dialogView.findViewById<ImageView>(R.id.ivAvatar)

        tvName.text = user.name
        tvEmail.text = user.email
        tvPhone.text = user.phone ?: "Телефон не указан"
        tvRole.text = when (user.role) {
            "administrator" -> "Администратор"
            "fitness_trainer" -> "Тренер"
            else -> "Пользователь"
        }
        tvSubscription.text = user.subscriptionName ?: "Нет абонемента"
        tvVisits.text = "Посещений: ${user.visitsCount}"
        tvRegistered.text = "Зарегистрирован: ${user.registrationDate}"

        // ИСПРАВЛЕНО: Glide для загрузки аватара
        if (!user.avatar.isNullOrEmpty()) {
            Glide.with(this)
                .load(user.avatar)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_profile)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(user.name)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showEditUserDialog(user: com.fitnesslemon.app.data.models.AdminUser) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_user, null)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etEmail)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.etPhone)

        etName.setText(user.name)
        etEmail.setText(user.email)
        etPhone.setText(user.phone)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактирование пользователя")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val request = UpdateUserRequest(
                    name = etName.text.toString(),
                    email = etEmail.text.toString(),
                    phone = etPhone.text.toString()
                )
                viewModel.updateUser(user.id, request)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showResetPasswordDialog(user: com.fitnesslemon.app.data.models.AdminUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Сброс пароля")
            .setMessage("Сбросить пароль для пользователя ${user.name}? Новый пароль будет отправлен на email.")
            .setPositiveButton("Сбросить") { _, _ ->
                viewModel.resetUserPassword(user.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteUserDialog(user: com.fitnesslemon.app.data.models.AdminUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление пользователя")
            .setMessage("Вы уверены, что хотите удалить пользователя ${user.name}?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteUser(user.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
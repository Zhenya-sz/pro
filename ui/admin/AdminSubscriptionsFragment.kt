package com.fitnesslemon.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentAdminSubscriptionsBinding
import com.fitnesslemon.app.databinding.DialogSelectUserBinding
import com.fitnesslemon.app.databinding.DialogSelectSubscriptionBinding
import com.fitnesslemon.app.ui.admin.adapters.AdminSubscriptionsAdapter
import com.fitnesslemon.app.ui.admin.adapters.UserSelectionAdapter
import com.fitnesslemon.app.ui.admin.adapters.SubscriptionSelectionAdapter
import com.fitnesslemon.app.data.api.CreateSubscriptionRequest
import com.fitnesslemon.app.data.api.SellSubscriptionRequest
import com.fitnesslemon.app.data.models.AdminUser
import com.fitnesslemon.app.data.models.AdminSubscription
import com.fitnesslemon.app.ui.chat.ChatDetailFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class AdminSubscriptionsFragment : Fragment() {

    private var _binding: FragmentAdminSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private var selectedUserId: Int? = null
    private var selectedSubscriptionId: Int? = null
    private var selectedUser: AdminUser? = null
    private var selectedSubscription: AdminSubscription? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadSubscriptions()
        viewModel.loadUsers()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Управление абонементами"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        binding.rvSubscriptions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSubscriptions.setHasFixedSize(true)
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            showCreateSubscriptionDialog()
        }

        binding.fabSell.setOnClickListener {
            showSellSubscriptionDialog()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadSubscriptions()
        }
    }

    private fun observeViewModel() {
        viewModel.subscriptions.observe(viewLifecycleOwner) { subscriptions ->
            if (subscriptions.isNotEmpty()) {
                binding.rvSubscriptions.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                binding.rvSubscriptions.adapter = AdminSubscriptionsAdapter(
                    subscriptions = subscriptions,
                    onItemClick = { subscription -> showSubscriptionDetails(subscription) },
                    onEditClick = { subscription -> showEditSubscriptionDialog(subscription) },
                    onDeleteClick = { subscription -> showDeleteSubscriptionDialog(subscription) },
                    onSellClick = { subscription -> showSellToUserDialog(subscription) }
                )
            } else {
                binding.rvSubscriptions.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading && viewModel.subscriptions.value?.isEmpty() == true)
                View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun showCreateSubscriptionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_subscription, null)

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etTitle)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val etWorkoutsCount = dialogView.findViewById<TextInputEditText>(R.id.etWorkoutsCount)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.etPrice)
        val etDurationDays = dialogView.findViewById<TextInputEditText>(R.id.etDurationDays)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Создание абонемента")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val title = etTitle.text.toString()
                val workoutsCount = etWorkoutsCount.text.toString().toIntOrNull() ?: 0
                val price = etPrice.text.toString().toDoubleOrNull() ?: 0.0
                val durationDays = etDurationDays.text.toString().toIntOrNull() ?: 30

                if (title.isNotEmpty() && workoutsCount > 0 && price > 0) {
                    val request = CreateSubscriptionRequest(
                        title = title,
                        description = etDescription.text.toString(),
                        workoutsCount = workoutsCount,
                        price = price,
                        durationDays = durationDays
                    )
                    viewModel.createSubscription(request)
                } else {
                    Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSellSubscriptionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sell_subscription, null)

        val btnSelectUser = dialogView.findViewById<View>(R.id.btnSelectUser)
        val btnSelectSubscription = dialogView.findViewById<View>(R.id.btnSelectSubscription)
        val tvSelectedUser = dialogView.findViewById<TextView>(R.id.tvSelectedUser)
        val tvSelectedSubscription = dialogView.findViewById<TextView>(R.id.tvSelectedSubscription)

        btnSelectUser.setOnClickListener {
            showUserSelectionDialog { user ->
                selectedUser = user
                selectedUserId = user.id
                tvSelectedUser.text = "Пользователь: ${user.name}"
                tvSelectedUser.visibility = View.VISIBLE
            }
        }

        btnSelectSubscription.setOnClickListener {
            showSubscriptionSelectionDialog { subscription ->
                selectedSubscription = subscription
                selectedSubscriptionId = subscription.id
                tvSelectedSubscription.text = "Абонемент: ${subscription.title}"
                tvSelectedSubscription.visibility = View.VISIBLE
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Продажа абонемента")
            .setView(dialogView)
            .setPositiveButton("Продать") { _, _ ->
                if (selectedUserId != null && selectedSubscriptionId != null) {
                    val request = SellSubscriptionRequest(
                        userId = selectedUserId!!,
                        subscriptionId = selectedSubscriptionId!!
                    )
                    viewModel.sellSubscription(request)
                } else {
                    Toast.makeText(requireContext(), "Выберите пользователя и абонемент", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSellToUserDialog(subscription: AdminSubscription) {
        val dialogBinding = DialogSelectUserBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Продажа абонемента")
            .setMessage("Абонемент: ${subscription.title}\nЦена: ${subscription.price} ₽")
            .setView(dialogBinding.root)
            .create()

        dialogBinding.toolbar.setNavigationOnClickListener {
            dialog.dismiss()
        }

        viewModel.users.observe(viewLifecycleOwner) { users ->
            val adapter = UserSelectionAdapter(users) { user ->
                val request = SellSubscriptionRequest(
                    userId = user.id,
                    subscriptionId = subscription.id
                )
                viewModel.sellSubscription(request)
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

        dialog.show()
    }

    private fun showUserSelectionDialog(onUserSelected: (AdminUser) -> Unit) {
        val dialogBinding = DialogSelectUserBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.toolbar.setNavigationOnClickListener {
            dialog.dismiss()
        }

        viewModel.users.observe(viewLifecycleOwner) { users ->
            val adapter = UserSelectionAdapter(users) { user ->
                onUserSelected(user)
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

        dialog.show()
    }

    private fun showSubscriptionSelectionDialog(onSubscriptionSelected: (AdminSubscription) -> Unit) {
        val dialogBinding = DialogSelectSubscriptionBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.toolbar.setNavigationOnClickListener {
            dialog.dismiss()
        }

        viewModel.subscriptions.observe(viewLifecycleOwner) { subscriptions ->
            val adapter = SubscriptionSelectionAdapter(subscriptions) { subscription ->
                onSubscriptionSelected(subscription)
                dialog.dismiss()
            }
            dialogBinding.rvSubscriptions.layoutManager = LinearLayoutManager(requireContext())
            dialogBinding.rvSubscriptions.adapter = adapter

            dialogBinding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    adapter.filter.filter(s.toString())
                }
            })
        }

        dialog.show()
    }

    private fun showSubscriptionDetails(subscription: AdminSubscription) {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(subscription.title)
            .setMessage(
                """
                Описание: ${subscription.description}
                Тренировок: ${subscription.workoutsCount}
                Цена: ${subscription.price} ₽
                Срок: ${subscription.durationDays} дней
                Продано: ${subscription.salesCount}
                Выручка: ${subscription.totalRevenue} ₽
                Статус: ${if (subscription.isActive) "Активен" else "Неактивен"}
                ${if (subscription.chatId != null) "\nЧат ID: ${subscription.chatId}" else ""}
                """.trimIndent()
            )

        if (subscription.chatId != null) {
            builder.setNeutralButton("Перейти в чат") { _, _ ->
                val fragment = ChatDetailFragment.newInstance(subscription.chatId!!, subscription.title)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        builder.setPositiveButton("OK", null)
        builder.show()
    }

    private fun showEditSubscriptionDialog(subscription: AdminSubscription) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_subscription, null)

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etTitle)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val etWorkoutsCount = dialogView.findViewById<TextInputEditText>(R.id.etWorkoutsCount)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.etPrice)
        val etDurationDays = dialogView.findViewById<TextInputEditText>(R.id.etDurationDays)

        etTitle.setText(subscription.title)
        etDescription.setText(subscription.description)
        etWorkoutsCount.setText(subscription.workoutsCount.toString())
        etPrice.setText(subscription.price.toString())
        etDurationDays.setText(subscription.durationDays.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактирование абонемента")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val request = CreateSubscriptionRequest(
                    title = etTitle.text.toString(),
                    description = etDescription.text.toString(),
                    workoutsCount = etWorkoutsCount.text.toString().toIntOrNull() ?: subscription.workoutsCount,
                    price = etPrice.text.toString().toDoubleOrNull() ?: subscription.price,
                    durationDays = etDurationDays.text.toString().toIntOrNull() ?: subscription.durationDays
                )
                viewModel.updateSubscription(subscription.id, request)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteSubscriptionDialog(subscription: AdminSubscription) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление абонемента")
            .setMessage("Вы уверены, что хотите удалить абонемент ${subscription.title}?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteSubscription(subscription.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
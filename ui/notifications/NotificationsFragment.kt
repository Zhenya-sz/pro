package com.fitnesslemon.app.ui.notifications

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
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentNotificationsBinding
import com.fitnesslemon.app.ui.chat.ChatDetailFragment
import com.fitnesslemon.app.ui.main.MainActivity
import com.fitnesslemon.app.ui.workouts.WorkoutsFragment
import com.fitnesslemon.app.data.models.Notification
import com.fitnesslemon.app.data.models.NotificationGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationViewModel by lazy { NotificationViewModel() }
    private lateinit var adapter: NotificationAdapter
    private var isFirstLoad = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()

        if (isFirstLoad) {
            viewModel.loadNotifications()
            isFirstLoad = false
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Уведомления"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.setHasFixedSize(true)

        adapter = NotificationAdapter(
            onChatClick = { group -> handleChatGroupClick(group) },
            onBookingClick = { group -> handleBookingGroupClick(group) },
            onInvitationAction = { notification, action -> handleInvitationAction(notification, action) },
            onItemLongClick = { notification -> showNotificationOptions(notification) }
        )
        binding.rvNotifications.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notificationsState.collect { state ->
                    binding.swipeRefresh.isRefreshing = false

                    when (state) {
                        is NotificationsState.Loading -> {
                            if (!isFirstLoad) {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.rvNotifications.visibility = View.GONE
                                binding.tvEmpty.visibility = View.GONE
                                binding.btnMarkAllRead.visibility = View.GONE
                            }
                        }
                        is NotificationsState.Success -> {
                            binding.progressBar.visibility = View.GONE

                            val chatGroups = state.chatGroups
                            val bookingGroups = state.bookingGroups
                            val invitations = state.invitations
                            val others = state.others

                            val hasItems = chatGroups.isNotEmpty() ||
                                    bookingGroups.isNotEmpty() ||
                                    invitations.isNotEmpty() ||
                                    others.isNotEmpty()

                            if (!hasItems) {
                                binding.rvNotifications.visibility = View.GONE
                                binding.tvEmpty.visibility = View.VISIBLE
                                binding.tvEmpty.text = "Нет уведомлений"
                                binding.btnMarkAllRead.visibility = View.GONE
                                return@collect
                            }

                            binding.rvNotifications.visibility = View.VISIBLE
                            binding.tvEmpty.visibility = View.GONE
                            binding.btnMarkAllRead.visibility = View.VISIBLE

                            val items = mutableListOf<NotificationItem>()

                            if (chatGroups.isNotEmpty()) {
                                items.add(NotificationItem.Section("Чаты"))
                                items.addAll(chatGroups.map { group ->
                                    val fixedMessage = if (group.message.contains("зашифрован") ||
                                        group.message == "encrypted_text" ||
                                        group.message == "Зашифрованное сообщение") {
                                        "Новое сообщение"
                                    } else {
                                        group.message
                                    }
                                    NotificationItem.ChatGroup(group.copy(message = fixedMessage))
                                })
                            }

                            if (bookingGroups.isNotEmpty()) {
                                items.add(NotificationItem.Section("Тренировки"))
                                items.addAll(bookingGroups.map { NotificationItem.BookingGroup(it) })
                            }

                            if (invitations.isNotEmpty()) {
                                items.add(NotificationItem.Section("Приглашения"))
                                items.addAll(invitations.map { NotificationItem.Invitation(it) })
                            }

                            if (others.isNotEmpty()) {
                                items.add(NotificationItem.Section("Другое"))
                                items.addAll(others.map { NotificationItem.Other(it) })
                            }

                            adapter.submitList(items)
                        }
                        is NotificationsState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.rvNotifications.visibility = View.GONE
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.tvEmpty.text = "Ошибка загрузки: ${state.message}"
                            binding.btnMarkAllRead.visibility = View.GONE
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.unreadCountState.collect { state ->
                    when (state) {
                        is UnreadCountState.Success -> {
                            (activity as? MainActivity)?.updateNotificationBadge(state.count)
                        }
                        else -> {}
                    }
                }
            }
        }

        binding.btnMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
            Toast.makeText(requireContext(), "Все уведомления отмечены как прочитанные", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleChatGroupClick(group: NotificationGroup) {
        viewModel.markGroupAsRead(group.groupId, "chat")

        // Пробуем получить chatId из data
        var chatId = group.data?.chatId

        // Если не нашли, пробуем извлечь из groupId
        if (chatId == null || chatId == 0) {
            val match = Regex("chat_(\\d+)").find(group.groupId)
            chatId = match?.groupValues?.get(1)?.toIntOrNull()
        }

        if (chatId != null && chatId > 0) {
            val fragment = ChatDetailFragment.newInstance(chatId, group.title)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        } else {
            Toast.makeText(requireContext(), "Не удалось открыть чат", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleBookingGroupClick(group: NotificationGroup) {
        viewModel.markGroupAsRead(group.groupId, "booking")
        val fragment = WorkoutsFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun handleInvitationAction(notification: Notification, action: String) {
        when (action) {
            "accept" -> {
                notification.data?.invitationId?.let { id ->
                    viewModel.acceptInvitation(id)
                    Toast.makeText(requireContext(), "Приглашение принято", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(requireContext(), "Ошибка: ID приглашения не найден", Toast.LENGTH_SHORT).show()
                }
            }
            "decline" -> {
                notification.data?.invitationId?.let { id ->
                    viewModel.declineInvitation(id)
                    Toast.makeText(requireContext(), "Приглашение отклонено", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(requireContext(), "Ошибка: ID приглашения не найден", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showNotificationOptions(notification: Notification) {
        val options = arrayOf("Удалить")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(notification.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.deleteNotification(notification.id)
                        Toast.makeText(requireContext(), "Уведомление удалено", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
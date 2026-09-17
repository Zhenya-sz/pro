package com.fitnesslemon.app.ui.chat

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
import com.fitnesslemon.app.databinding.FragmentInvitationsBinding
import com.fitnesslemon.app.ui.chat.adapters.InvitationAdapter
import kotlinx.coroutines.launch

class InvitationsFragment : Fragment() {

    private var _binding: FragmentInvitationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InvitationViewModel by lazy { InvitationViewModel() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvitationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()

        viewModel.loadInvitations()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        binding.rvInvitations.layoutManager = LinearLayoutManager(requireContext())
        binding.rvInvitations.setHasFixedSize(true)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadInvitations()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.invitationsState.collect { state ->
                    binding.swipeRefresh.isRefreshing = false

                    when (state) {
                        is InvitationsState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.rvInvitations.visibility = View.GONE
                            binding.tvEmpty.visibility = View.GONE
                        }
                        is InvitationsState.Success -> {
                            binding.progressBar.visibility = View.GONE

                            if (state.invitations.isEmpty()) {
                                binding.rvInvitations.visibility = View.GONE
                                binding.tvEmpty.visibility = View.VISIBLE
                            } else {
                                binding.rvInvitations.visibility = View.VISIBLE
                                binding.tvEmpty.visibility = View.GONE

                                binding.rvInvitations.adapter = InvitationAdapter(
                                    invitations = state.invitations,
                                    onAcceptClick = { invitation ->
                                        viewModel.acceptInvitation(invitation.id)
                                    },
                                    onDeclineClick = { invitation ->
                                        viewModel.declineInvitation(invitation.id)
                                    }
                                )
                            }
                        }
                        is InvitationsState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.rvInvitations.visibility = View.GONE
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.tvEmpty.text = "Ошибка загрузки: ${state.message}"
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.actionState.collect { state ->
                    when (state) {
                        is InvitationActionState.Loading -> {
                            // Показать прогресс (можно добавить индикатор)
                        }
                        is InvitationActionState.Success -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            viewModel.loadInvitations()

                            // Если приглашение принято и есть chatId, открываем чат
                            if (state.chatId != null) {
                                openChat(state.chatId)
                            }
                            viewModel.resetActionState()
                        }
                        is InvitationActionState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetActionState()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun openChat(chatId: Int) {
        val fragment = ChatDetailFragment.newInstance(chatId, "Чат")
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
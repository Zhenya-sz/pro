package com.fitnesslemon.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fitnesslemon.app.data.models.Settings
import com.fitnesslemon.app.databinding.FragmentAdminNotificationsBinding

class AdminNotificationsFragment : Fragment() {

    private var _binding: FragmentAdminNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private var currentSettings: Settings? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        observeViewModel()
        viewModel.loadSettings()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Настройка уведомлений"
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnSave.setOnClickListener { saveSettings() }
    }

    private fun observeViewModel() {
        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings != null) {
                currentSettings = settings
                displaySettings(settings)
            }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun displaySettings(settings: Settings) {
        binding.switchPushEnabled.isChecked = settings.pushEnabled
        binding.switchEmailConfirmations.isChecked = settings.emailConfirmations
        binding.switchSmsReminders.isChecked = settings.smsReminders
        binding.etReminderHours.setText(settings.reminderHours.toString())
    }

    private fun saveSettings() {
        val updated = currentSettings?.copy(
            pushEnabled = binding.switchPushEnabled.isChecked,
            emailConfirmations = binding.switchEmailConfirmations.isChecked,
            smsReminders = binding.switchSmsReminders.isChecked,
            reminderHours = binding.etReminderHours.text.toString().toIntOrNull() ?: 1
        ) ?: return
        viewModel.updateSettings(updated)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
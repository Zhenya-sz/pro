package com.fitnesslemon.app.ui.admin

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Settings
import com.fitnesslemon.app.databinding.FragmentAdminSettingsBinding
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class AdminSettingsFragment : Fragment() {

    private var _binding: FragmentAdminSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private var currentSettings: Settings? = null
    private val workingHours = mutableMapOf<String, String>()

    private val weekDays = listOf(
        "Понедельник" to "mon",
        "Вторник" to "tue",
        "Среда" to "wed",
        "Четверг" to "thu",
        "Пятница" to "fri",
        "Суббота" to "sat",
        "Воскресенье" to "sun"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        // Передаем контекст в ViewModel
        viewModel.setContext(requireContext())

        setupToolbar()
        setupListeners()
        observeViewModel()

        // Загружаем настройки
        viewModel.loadSettings()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Настройки"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadSettings()
        }

        binding.tvWorkingHours.setOnClickListener {
            showWorkingHoursDialog()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            val url = currentSettings?.privacyPolicyUrl ?: "https://fitnesslemon.ru/privacy"
            openUrl(url)
        }

        binding.btnTerms.setOnClickListener {
            val url = currentSettings?.termsUrl ?: "https://fitnesslemon.ru/terms"
            openUrl(url)
        }
    }

    private fun getAppVersionName(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    private fun displaySettings(settings: Settings) {
        // Общие настройки
        binding.etClubName.setText(settings.clubName ?: "")
        binding.etClubAddress.setText(settings.address ?: "")
        binding.etClubPhone.setText(settings.phone ?: "")
        binding.etClubEmail.setText(settings.email ?: "")

        // Настройки бронирования
        binding.etMaxBookingsPerDay.setText(settings.maxBookingsPerDay.toString())
        binding.etCancellationHours.setText(settings.cancellationHours.toString())
        binding.switchAutoCancelNoShow.isChecked = settings.autoCancelNoShow
        binding.etAutoCancelMinutes.setText(settings.autoCancelMinutes.toString())
        binding.switchAllowWithoutSubscription.isChecked = settings.allowBookingWithoutSubscription

        // Уведомления
        binding.switchPushEnabled.isChecked = settings.pushEnabled
        binding.switchEmailEnabled.isChecked = settings.emailConfirmations
        binding.etReminderHours.setText(settings.reminderHours.toString())

        // Версия
        binding.tvVersion.text = "Версия: ${getAppVersionName()}"

        // График работы (показываем тестовые данные, если нет в настройках)
        if (workingHours.isEmpty()) {
            updateWorkingHoursDisplay(mapOf(
                "mon" to "09:00-21:00",
                "tue" to "09:00-21:00",
                "wed" to "09:00-21:00",
                "thu" to "09:00-21:00",
                "fri" to "09:00-21:00",
                "sat" to "10:00-18:00",
                "sun" to "10:00-18:00"
            ))
        }
    }

    private fun updateWorkingHoursDisplay(hours: Map<String, String>) {
        workingHours.clear()
        workingHours.putAll(hours)

        val display = buildString {
            weekDays.forEach { (displayName, key) ->
                val timeRange = hours[key] ?: "Закрыто"
                append("$displayName: $timeRange\n")
            }
        }
        binding.tvWorkingHours.text = display.trim()
    }

    private fun showWorkingHoursDialog() {
        val items = weekDays.map { it.first }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите день для редактирования")
            .setItems(items) { _, which ->
                val dayKey = weekDays[which].second
                val currentValue = workingHours[dayKey] ?: "09:00-21:00"
                showTimeRangeDialog(dayKey, currentValue)
            }
            .show()
    }

    private fun showTimeRangeDialog(dayKey: String, currentValue: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_range, null)
        val etStartTime = dialogView.findViewById<TextInputEditText>(R.id.etStartTime)
        val etEndTime = dialogView.findViewById<TextInputEditText>(R.id.etEndTime)

        val parts = currentValue.split("-")
        etStartTime.setText(parts.getOrNull(0) ?: "09:00")
        etEndTime.setText(parts.getOrNull(1) ?: "21:00")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Время работы")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val start = etStartTime.text.toString()
                val end = etEndTime.text.toString()
                if (start.isNotEmpty() && end.isNotEmpty()) {
                    workingHours[dayKey] = "$start-$end"
                    updateWorkingHoursDisplay(workingHours.toMap())
                }
            }
            .setNegativeButton("Закрыто") { _, _ ->
                workingHours[dayKey] = "Закрыто"
                updateWorkingHoursDisplay(workingHours.toMap())
            }
            .setNeutralButton("Отмена", null)
            .show()
    }

    private fun saveSettings() {
        val current = currentSettings ?: return

        val updated = current.copy(
            clubName = binding.etClubName.text.toString().takeIf { it.isNotBlank() },
            address = binding.etClubAddress.text.toString().takeIf { it.isNotBlank() },
            phone = binding.etClubPhone.text.toString().takeIf { it.isNotBlank() },
            email = binding.etClubEmail.text.toString().takeIf { it.isNotBlank() },

            maxBookingsPerDay = binding.etMaxBookingsPerDay.text.toString().toIntOrNull()
                ?: current.maxBookingsPerDay,

            cancellationHours = binding.etCancellationHours.text.toString().toIntOrNull()
                ?: current.cancellationHours,

            autoCancelNoShow = binding.switchAutoCancelNoShow.isChecked,

            autoCancelMinutes = binding.etAutoCancelMinutes.text.toString().toIntOrNull()
                ?: current.autoCancelMinutes,

            allowBookingWithoutSubscription = binding.switchAllowWithoutSubscription.isChecked,

            pushEnabled = binding.switchPushEnabled.isChecked,
            emailConfirmations = binding.switchEmailEnabled.isChecked,

            reminderHours = binding.etReminderHours.text.toString().toIntOrNull()
                ?: current.reminderHours
        )

        viewModel.updateSettings(updated)
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun observeViewModel() {
        // Наблюдаем за настройками
        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings != null) {
                currentSettings = settings
                displaySettings(settings)
                binding.progressBar.visibility = View.GONE
            }
        }

        // Наблюдаем за состоянием загрузки
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnSave.isEnabled = false
                binding.swipeRefresh.isRefreshing = true
            } else {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Наблюдаем за ошибками
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                showError(error)
                viewModel.clearError()
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
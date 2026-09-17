package com.fitnesslemon.app.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.Workout
import com.fitnesslemon.app.databinding.FragmentCalendarBinding
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var allWorkouts: List<Workout> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        // TODO: Заменить на MaterialDatePicker или добавить календарь
        loadWorkouts()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Календарь занятий"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadWorkouts() {
        lifecycleScope.launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Необходимо авторизоваться", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val response = ApiClient.apiService.getWorkouts()
                if (response.isSuccessful) {
                    val workoutsResponse = response.body()
                    if (workoutsResponse != null && workoutsResponse.success) {
                        allWorkouts = workoutsResponse.data.workouts
                        binding.tvEventsList.text = "Загружено ${allWorkouts.size} тренировок"
                    } else {
                        Toast.makeText(requireContext(), "Ошибка загрузки занятий", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки занятий: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
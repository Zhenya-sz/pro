package com.fitnesslemon.app.ui.admin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.*
import com.fitnesslemon.app.databinding.FragmentAdminScheduleBinding
import com.fitnesslemon.app.databinding.DialogApplyTemplateBinding
import com.fitnesslemon.app.databinding.DialogSaveTemplateBinding
import com.fitnesslemon.app.ui.admin.adapters.ScheduleWeekEditAdapter
import com.fitnesslemon.app.ui.admin.adapters.ScheduleWeekEditAdapter.ScheduleDayData
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class AdminScheduleFragment : Fragment() {

    private var _binding: FragmentAdminScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private var currentWeekStart: Date = Date()
    private var trainers: List<Trainer> = emptyList()
    private var workoutTypes: List<WorkoutType> = emptyList()

    // Фильтры
    private var selectedTrainerId: Int? = null
    private var selectedWorkoutTypeId: Int? = null
    private var searchQuery: String = ""

    // Для выбора изображения
    private var selectedImageUri: Uri? = null
    private var isEditMode: Boolean = false
    private var editingWorkout: AdminWorkout? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("d MMMM", Locale("ru"))
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale("ru"))

    // Launcher для выбора изображения
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            // Показываем превью в диалоге (обновляется через observe)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        setupFilters()
        observeViewModel()
        observeTemplateOperations()

        // Загружаем данные
        viewModel.loadTrainers()
        viewModel.loadTemplates()
        loadWorkoutTypes()

        // Устанавливаем начало недели на основе сегодняшней даты
        val today = Date()
        currentWeekStart = getMondayOfWeek(today)
        println("📅 Текущая дата: ${SimpleDateFormat("yyyy-MM-dd (E)", Locale("ru")).format(today)}")
        println("📅 Начало недели: ${SimpleDateFormat("yyyy-MM-dd (E)", Locale("ru")).format(currentWeekStart)}")

        loadWeeklySchedule()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.admin_schedule_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_save_template -> {
                showSaveTemplateDialog()
                true
            }
            R.id.menu_apply_template -> {
                showApplyTemplateDialog()
                true
            }
            R.id.menu_manage_templates -> {
                showManageTemplatesDialog()
                true
            }
            R.id.menu_history -> {
                showHistoryDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ============================================================
    // НАСТРОЙКА UI
    // ============================================================

    private fun setupToolbar() {
        binding.toolbar.title = "Редактор расписания"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolbar.inflateMenu(R.menu.admin_schedule_menu)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_save_template -> {
                    showSaveTemplateDialog()
                    true
                }
                R.id.menu_apply_template -> {
                    showApplyTemplateDialog()
                    true
                }
                R.id.menu_manage_templates -> {
                    showManageTemplatesDialog()
                    true
                }
                R.id.menu_history -> {
                    showHistoryDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedule.setHasFixedSize(true)
    }

    // ============================================================
    // ФИЛЬТРЫ
    // ============================================================

    private fun setupFilters() {
        binding.btnFilterTrainer.setOnClickListener {
            showTrainerFilterDialog()
        }

        binding.btnFilterType.setOnClickListener {
            showWorkoutTypeFilterDialog()
        }

        binding.btnSearch.setOnClickListener {
            searchQuery = binding.etSearch.text.toString()
            loadWeeklySchedule()
        }

        binding.btnClearFilters.setOnClickListener {
            selectedTrainerId = null
            selectedWorkoutTypeId = null
            searchQuery = ""
            binding.etSearch.text.clear()
            binding.btnFilterTrainer.text = "Все тренеры"
            binding.btnFilterType.text = "Все типы"
            loadWeeklySchedule()
        }
    }

    // ============================================================
    // ДИАЛОГИ ШАБЛОНОВ
    // ============================================================

    private fun showSaveTemplateDialog() {
        val dialogBinding = DialogSaveTemplateBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("💾 Сохранить как шаблон")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = dialogBinding.etTemplateName.text.toString().trim()
                val description = dialogBinding.etTemplateDescription.text.toString().trim()
                val isDefault = dialogBinding.switchIsDefault.isChecked

                if (name.isNotEmpty()) {
                    viewModel.saveWeekAsTemplate(
                        weekStartDate = currentWeekStart,
                        name = name,
                        description = description.takeIf { it.isNotEmpty() },
                        isDefault = isDefault
                    )
                } else {
                    Toast.makeText(requireContext(), "Введите название шаблона", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showApplyTemplateDialog() {
        viewModel.loadTemplates()

        val templates = viewModel.templates.value ?: emptyList()
        if (templates.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("📋 Нет шаблонов")
                .setMessage("У вас нет сохранённых шаблонов. Создайте шаблон из текущего расписания.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val dialogBinding = DialogApplyTemplateBinding.inflate(layoutInflater)
        val spinner = dialogBinding.spTemplates
        val radioReplace = dialogBinding.radioReplace
        val radioMerge = dialogBinding.radioMerge
        val radioAppend = dialogBinding.radioAppend

        val templateNames = templates.map { "${it.name}${if (it.isDefault) " ★" else ""}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, templateNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val defaultIndex = templates.indexOfFirst { it.isDefault }
        if (defaultIndex >= 0) spinner.setSelection(defaultIndex)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📋 Применить шаблон")
            .setView(dialogBinding.root)
            .setPositiveButton("Применить") { _, _ ->
                val position = spinner.selectedItemPosition
                val template = templates[position]

                val strategy = when {
                    radioReplace.isChecked -> "replace"
                    radioMerge.isChecked -> "merge"
                    else -> "append"
                }

                val clearExisting = radioReplace.isChecked

                viewModel.applyTemplate(
                    templateId = template.id,
                    targetWeekStart = currentWeekStart,
                    clearExisting = clearExisting,
                    mergeStrategy = strategy
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showManageTemplatesDialog() {
        val templates = viewModel.templates.value ?: emptyList()
        if (templates.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("📋 Нет шаблонов")
                .setMessage("У вас нет сохранённых шаблонов.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val items = templates.map { template ->
            "${template.name}${if (template.isDefault) " ★" else ""}${if (!template.isActive) " (неактивен)" else ""}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📋 Управление шаблонами")
            .setItems(items) { _, which ->
                val template = templates[which]
                showTemplateActionsDialog(template)
            }
            .setNeutralButton("Закрыть", null)
            .show()
    }

    private fun showTemplateActionsDialog(template: ScheduleTemplate) {
        val actions = mutableListOf<String>()
        if (!template.isDefault) {
            actions.add("⭐ Сделать стандартным")
        }
        actions.add("🗑️ Удалить")
        actions.add("📋 Применить")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(template.name)
            .setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        if (!template.isDefault) {
                            viewModel.setDefaultTemplate(template.id)
                        } else {
                            viewModel.deleteTemplate(template.id)
                        }
                    }
                    1 -> {
                        if (actions.size == 2) {
                            viewModel.deleteTemplate(template.id)
                        } else {
                            viewModel.applyTemplate(
                                templateId = template.id,
                                targetWeekStart = currentWeekStart,
                                clearExisting = true,
                                mergeStrategy = "replace"
                            )
                        }
                    }
                    2 -> {
                        viewModel.applyTemplate(
                            templateId = template.id,
                            targetWeekStart = currentWeekStart,
                            clearExisting = true,
                            mergeStrategy = "replace"
                        )
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ============================================================
    // ДИАЛОГ ИСТОРИИ
    // ============================================================

    private fun showHistoryDialog() {
        viewModel.loadScheduleHistory(currentWeekStart)

        val history = viewModel.scheduleHistory.value ?: emptyList()
        if (history.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("📜 История изменений")
                .setMessage("Нет записей об изменениях расписания")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val items = history.map { entry ->
            val time = entry.timestamp
            val typeIcon = when (entry.changeType) {
                "created" -> "➕"
                "updated" -> "✏️"
                "deleted" -> "🗑️"
                "template_applied" -> "📋"
                else -> "📌"
            }
            "$typeIcon ${entry.changedBy}: ${entry.description ?: entry.changeType} (${entry.workoutCount} занятий) [$time]"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📜 История изменений")
            .setItems(items) { _, _ ->
                // Можно показать детали изменения
            }
            .setPositiveButton("Закрыть", null)
            .show()
    }

    // ============================================================
    // НАБЛЮДАТЕЛИ
    // ============================================================

    private fun observeTemplateOperations() {
        viewModel.templateOperationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AdminViewModel.TemplateOperationState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is AdminViewModel.TemplateOperationState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetTemplateOperationState()
                }
                is AdminViewModel.TemplateOperationState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetTemplateOperationState()
                }
                else -> {}
            }
        }
    }

    // ============================================================
    // ФИЛЬТРЫ - ДИАЛОГИ
    // ============================================================

    private fun showTrainerFilterDialog() {
        if (trainers.isEmpty()) {
            Toast.makeText(requireContext(), "Нет доступных тренеров", Toast.LENGTH_SHORT).show()
            return
        }

        val trainerNames = trainers.map { it.name }.toTypedArray()
        val selectedIndex = trainers.indexOfFirst { it.id == selectedTrainerId }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите тренера")
            .setSingleChoiceItems(trainerNames, selectedIndex) { _, which ->
                selectedTrainerId = trainers[which].id
                binding.btnFilterTrainer.text = trainers[which].name
                loadWeeklySchedule()
            }
            .setPositiveButton("OK", null)
            .setNeutralButton("Сбросить") { _, _ ->
                selectedTrainerId = null
                binding.btnFilterTrainer.text = "Все тренеры"
                loadWeeklySchedule()
            }
            .show()
    }

    private fun showWorkoutTypeFilterDialog() {
        if (workoutTypes.isEmpty()) {
            Toast.makeText(requireContext(), "Нет доступных типов тренировок", Toast.LENGTH_SHORT).show()
            return
        }

        val typeNames = workoutTypes.map { it.name }.toTypedArray()
        val selectedIndex = workoutTypes.indexOfFirst { it.id == selectedWorkoutTypeId }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите тип тренировки")
            .setSingleChoiceItems(typeNames, selectedIndex) { _, which ->
                selectedWorkoutTypeId = workoutTypes[which].id
                binding.btnFilterType.text = workoutTypes[which].name
                loadWeeklySchedule()
            }
            .setPositiveButton("OK", null)
            .setNeutralButton("Сбросить") { _, _ ->
                selectedWorkoutTypeId = null
                binding.btnFilterType.text = "Все типы"
                loadWeeklySchedule()
            }
            .show()
    }

    // ============================================================
    // НАВИГАЦИЯ ПО НЕДЕЛЯМ
    // ============================================================

    private fun setupListeners() {
        binding.btnPrevWeek.setOnClickListener {
            moveWeek(-1)
        }

        binding.btnNextWeek.setOnClickListener {
            moveWeek(1)
        }

        binding.btnToday.setOnClickListener {
            val today = Date()
            currentWeekStart = getMondayOfWeek(today)
            loadWeeklySchedule()
        }

        binding.btnCopySchedule.setOnClickListener {
            showCopyScheduleDialog()
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadWeeklySchedule()
        }
    }

    // ============================================================
    // ЗАГРУЗКА ДАННЫХ
    // ============================================================

    private fun loadWorkoutTypes() {
        lifecycleScope.launch {
            try {
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) return@launch

                val response = ApiClient.adminApiService.getWorkoutTypes("Bearer $token")
                if (response.isSuccessful) {
                    val adminResponse = response.body()
                    workoutTypes = adminResponse?.data ?: emptyList()
                    println("✅ Загружено типов тренировок: ${workoutTypes.size}")
                } else {
                    println("❌ Ошибка загрузки типов тренировок: ${response.code()}")
                }
            } catch (e: Exception) {
                println("❌ Ошибка загрузки типов тренировок: ${e.message}")
            }
        }
    }

    private fun loadWeeklySchedule() {
        println("📅 loadWeeklySchedule: загрузка для недели с ${SimpleDateFormat("yyyy-MM-dd (E)", Locale("ru")).format(currentWeekStart)}")
        viewModel.loadWeeklyScheduleForDate(currentWeekStart)
    }

    private fun moveWeek(offset: Int) {
        val calendar = Calendar.getInstance().apply {
            time = currentWeekStart
            add(Calendar.WEEK_OF_YEAR, offset)
        }
        currentWeekStart = calendar.time
        loadWeeklySchedule()
    }

    private fun getMondayOfWeek(date: Date): Date {
        val calendar = Calendar.getInstance().apply {
            time = date
            firstDayOfWeek = Calendar.MONDAY
        }

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysToMonday = when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> -1
            Calendar.WEDNESDAY -> -2
            Calendar.THURSDAY -> -3
            Calendar.FRIDAY -> -4
            Calendar.SATURDAY -> -5
            Calendar.SUNDAY -> -6
            else -> 0
        }

        calendar.add(Calendar.DAY_OF_MONTH, daysToMonday)
        return calendar.time
    }

    // ============================================================
    // НАБЛЮДАТЕЛИ VIEWMODEL
    // ============================================================

    private fun observeViewModel() {
        viewModel.trainers.observe(viewLifecycleOwner) { trainersList ->
            trainers = trainersList
            println("👥 Тренеров загружено: ${trainersList.size}")
        }

        viewModel.weeklySchedule.observe(viewLifecycleOwner) { schedule ->
            binding.swipeRefresh.isRefreshing = false

            if (schedule.isNotEmpty()) {
                val allDaysData = buildScheduleDaysData(schedule)
                val filteredDaysData = allDaysData.map { dayData ->
                    var filteredWorkouts = dayData.workouts

                    if (selectedTrainerId != null) {
                        filteredWorkouts = filteredWorkouts.filter { it.trainerId == selectedTrainerId }
                    }

                    if (selectedWorkoutTypeId != null) {
                        filteredWorkouts = filteredWorkouts.filter { it.workoutTypeId == selectedWorkoutTypeId }
                    }

                    if (searchQuery.isNotBlank()) {
                        filteredWorkouts = filteredWorkouts.filter {
                            it.title.lowercase().contains(searchQuery.lowercase()) ||
                                    (it.trainerName?.lowercase()?.contains(searchQuery.lowercase()) == true)
                        }
                    }

                    dayData.copy(workouts = filteredWorkouts)
                }

                val hasAnyWorkouts = filteredDaysData.any { it.workouts.isNotEmpty() }

                if (hasAnyWorkouts || filteredDaysData.isNotEmpty()) {
                    binding.rvSchedule.visibility = View.VISIBLE

                    val adapter = ScheduleWeekEditAdapter(
                        days = filteredDaysData,
                        onAddClick = { _, date -> showAddWorkoutDialog(date) },
                        onEditClick = { workout -> showEditWorkoutDialog(workout) },
                        onDeleteClick = { workout -> showDeleteWorkoutDialog(workout) }
                    )

                    binding.rvSchedule.adapter = adapter
                    adapter.notifyDataSetChanged()
                } else {
                    binding.rvSchedule.visibility = View.GONE
                    val message = if (searchQuery.isNotBlank() || selectedTrainerId != null || selectedWorkoutTypeId != null) {
                        "Нет занятий, соответствующих фильтрам"
                    } else {
                        "Нет занятий на эту неделю"
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.rvSchedule.visibility = View.GONE
                Toast.makeText(requireContext(), "Нет занятий на эту неделю", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.weekRange.observe(viewLifecycleOwner) { weekRange ->
            binding.tvWeekTitle.text = weekRange
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    // ============================================================
    // ПОСТРОЕНИЕ ДАННЫХ ДЛЯ АДАПТЕРА
    // ============================================================

    private fun convertServerDayToAndroidDay(serverDay: Int): Int {
        return when (serverDay) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> serverDay
        }
    }

    private fun buildScheduleDaysData(schedule: Map<Int, List<AdminWorkout>>): List<ScheduleDayData> {
        val daysData = mutableListOf<ScheduleDayData>()
        val calendar = Calendar.getInstance().apply {
            time = currentWeekStart
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }

        val mappedSchedule = mutableMapOf<Int, List<AdminWorkout>>()
        schedule.forEach { (serverDay, workouts) ->
            val androidDay = convertServerDayToAndroidDay(serverDay)
            mappedSchedule[androidDay] = workouts
        }

        for (i in 0..6) {
            val currentDate = calendar.time
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val workouts = mappedSchedule[dayOfWeek] ?: emptyList()
            val sortedWorkouts = workouts.sortedBy { it.formattedTime }

            val displayName = "${dayOfWeekFormat.format(currentDate)}, ${displayDateFormat.format(currentDate)}"

            daysData.add(
                ScheduleDayData(
                    dayOfWeek = dayOfWeek,
                    date = currentDate,
                    displayName = displayName,
                    workouts = sortedWorkouts
                )
            )

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return daysData
    }

    // ============================================================
    // ДИАЛОГ ДОБАВЛЕНИЯ ТРЕНИРОВКИ
    // ============================================================

    private fun showAddWorkoutDialog(selectedDate: Date) {
        if (selectedDate.before(Date())) {
            Toast.makeText(requireContext(), "Нельзя создать занятие в прошлом", Toast.LENGTH_SHORT).show()
            return
        }

        isEditMode = false
        editingWorkout = null
        selectedImageUri = null

        viewModel.loadTrainers()

        val dialogView = layoutInflater.inflate(R.layout.dialog_workout_edit, null)

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etTitle)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val etDate = dialogView.findViewById<TextInputEditText>(R.id.etDate)
        val etTime = dialogView.findViewById<TextInputEditText>(R.id.etTime)
        val etDuration = dialogView.findViewById<TextInputEditText>(R.id.etDuration)
        val etTrainer = dialogView.findViewById<TextInputEditText>(R.id.etTrainer)
        val etTrainerId = dialogView.findViewById<TextInputEditText>(R.id.etTrainerId)
        val etMaxParticipants = dialogView.findViewById<TextInputEditText>(R.id.etMaxParticipants)
        val etWorkoutType = dialogView.findViewById<TextInputEditText>(R.id.etWorkoutType)
        val etAgeCategory = dialogView.findViewById<TextInputEditText>(R.id.etAgeCategory)
        val etRoom = dialogView.findViewById<TextInputEditText>(R.id.etRoom)

        // Кнопка выбора изображения
        val btnSelectImage = dialogView.findViewById<MaterialButton>(R.id.btnSelectImage)
        val tvSelectedImage = dialogView.findViewById<TextView>(R.id.tvSelectedImage)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivPreview)

        // Скрываем поле ID тренера
        dialogView.findViewById<View>(R.id.trainerIdLayout)?.visibility = View.GONE

        etDate.setText(dateFormat.format(selectedDate))
        etDate.setOnClickListener { showDatePicker(etDate) }

        etTime.setText("10:00")
        etTime.setOnClickListener { showTimePicker(etTime) }

        etTrainer.setOnClickListener {
            showTrainerPicker(etTrainer, etTrainerId)
        }

        if (trainers.isNotEmpty()) {
            etTrainer.hint = "Выберите тренера"
        }

        etWorkoutType.setOnClickListener { showWorkoutTypePicker(etWorkoutType) }
        etAgeCategory.setOnClickListener { showAgeCategoryPicker(etAgeCategory) }

        // Выбор изображения
        btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Обновление превью при выборе изображения
        selectedImageUri?.let { uri ->
            tvSelectedImage.text = "✅ Изображение выбрано"
            tvSelectedImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(ivPreview)
            ivPreview.visibility = View.VISIBLE
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавление занятия")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val title = etTitle.text.toString()
                val description = etDescription.text.toString()
                val date = etDate.text.toString()
                val time = etTime.text.toString()
                val duration = etDuration.text.toString().toIntOrNull() ?: 60
                val trainerId = if (etTrainerId.text?.isNotEmpty() == true) {
                    etTrainerId.text.toString().toIntOrNull() ?: 0
                } else {
                    etTrainer.tag as? Int ?: 0
                }
                val maxParticipants = etMaxParticipants.text.toString().toIntOrNull() ?: 10
                val workoutTypeId = etWorkoutType.tag as? Int
                val ageCategory = etAgeCategory.text.toString().takeIf { it.isNotEmpty() }
                val room = etRoom.text.toString().takeIf { it.isNotEmpty() }

                if (title.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty() && trainerId != 0) {
                    val dateTime = "$date $time:00"

                    val request = CreateWorkoutRequest(
                        title = title,
                        description = description,
                        date = dateTime,
                        duration = duration,
                        trainerId = trainerId,
                        maxParticipants = maxParticipants,
                        workoutTypeId = workoutTypeId,
                        ageCategory = ageCategory,
                        room = room
                    )

                    viewModel.createWorkoutWithImage(request, selectedImageUri)
                } else {
                    Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ============================================================
    // ДИАЛОГ РЕДАКТИРОВАНИЯ ТРЕНИРОВКИ
    // ============================================================

    private fun showEditWorkoutDialog(workout: AdminWorkout) {
        isEditMode = true
        editingWorkout = workout
        selectedImageUri = null

        viewModel.loadTrainers()

        val dialogView = layoutInflater.inflate(R.layout.dialog_workout_edit, null)

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etTitle)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val etDate = dialogView.findViewById<TextInputEditText>(R.id.etDate)
        val etTime = dialogView.findViewById<TextInputEditText>(R.id.etTime)
        val etDuration = dialogView.findViewById<TextInputEditText>(R.id.etDuration)
        val etTrainer = dialogView.findViewById<TextInputEditText>(R.id.etTrainer)
        val etTrainerId = dialogView.findViewById<TextInputEditText>(R.id.etTrainerId)
        val etMaxParticipants = dialogView.findViewById<TextInputEditText>(R.id.etMaxParticipants)
        val etWorkoutType = dialogView.findViewById<TextInputEditText>(R.id.etWorkoutType)
        val etAgeCategory = dialogView.findViewById<TextInputEditText>(R.id.etAgeCategory)
        val etRoom = dialogView.findViewById<TextInputEditText>(R.id.etRoom)

        // Кнопка выбора изображения
        val btnSelectImage = dialogView.findViewById<MaterialButton>(R.id.btnSelectImage)
        val tvSelectedImage = dialogView.findViewById<TextView>(R.id.tvSelectedImage)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivPreview)

        // Скрываем поле ID тренера
        dialogView.findViewById<View>(R.id.trainerIdLayout)?.visibility = View.GONE

        etTitle.setText(workout.title)
        etDescription.setText(workout.description)
        etDate.setText(workout.formattedDate)
        etTime.setText(workout.formattedTime)
        etDuration.setText(workout.duration.toString())
        etMaxParticipants.setText(workout.maxParticipants.toString())

        val trainer = trainers.find { it.id == workout.trainerId }
        if (trainer != null) {
            etTrainer.setText(trainer.name)
            etTrainer.tag = trainer.id
            etTrainerId.setText(trainer.id.toString())
        } else if (workout.trainerId != null && workout.trainerId > 0) {
            etTrainer.setText("Тренер ID: ${workout.trainerId}")
            etTrainer.tag = workout.trainerId
            etTrainerId.setText(workout.trainerId.toString())
        }

        if (workout.workoutType != null) {
            val workoutType = workoutTypes.find { it.name == workout.workoutType }
            if (workoutType != null) {
                etWorkoutType.setText(workoutType.name)
                etWorkoutType.tag = workoutType.id
            } else {
                etWorkoutType.setText(workout.workoutType)
            }
        }

        etAgeCategory.setText(workout.ageCategory ?: "")
        etRoom.setText(workout.room ?: "")

        // Показываем существующее изображение
        if (!workout.thumbnail.isNullOrEmpty()) {
            tvSelectedImage.text = "✅ Изображение загружено"
            tvSelectedImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(workout.thumbnail)
                .centerCrop()
                .into(ivPreview)
            ivPreview.visibility = View.VISIBLE

            // Кнопка удаления изображения
            btnSelectImage.text = "🔄 Заменить изображение"
        }

        etDate.setOnClickListener { showDatePicker(etDate) }
        etTime.setOnClickListener { showTimePicker(etTime) }
        etTrainer.setOnClickListener { showTrainerPicker(etTrainer, etTrainerId) }
        etWorkoutType.setOnClickListener { showWorkoutTypePicker(etWorkoutType) }
        etAgeCategory.setOnClickListener { showAgeCategoryPicker(etAgeCategory) }

        // Выбор изображения
        btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Обновление превью при выборе изображения
        selectedImageUri?.let { uri ->
            tvSelectedImage.text = "✅ Изображение выбрано"
            tvSelectedImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(ivPreview)
            ivPreview.visibility = View.VISIBLE
            btnSelectImage.text = "🔄 Заменить изображение"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактирование занятия")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val title = etTitle.text.toString()
                val description = etDescription.text.toString()
                val date = etDate.text.toString()
                val time = etTime.text.toString()
                val duration = etDuration.text.toString().toIntOrNull() ?: 60
                val trainerId = if (etTrainerId.text?.isNotEmpty() == true) {
                    etTrainerId.text.toString().toIntOrNull() ?: workout.trainerId
                } else {
                    etTrainer.tag as? Int ?: workout.trainerId
                }
                val maxParticipants = etMaxParticipants.text.toString().toIntOrNull() ?: workout.maxParticipants
                val workoutTypeId = etWorkoutType.tag as? Int
                val ageCategory = etAgeCategory.text.toString().takeIf { it.isNotEmpty() }
                val room = etRoom.text.toString().takeIf { it.isNotEmpty() }

                if (title.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty()) {
                    val dateTime = "$date $time:00"

                    val request = UpdateWorkoutRequest(
                        id = workout.id,
                        title = title,
                        description = description,
                        date = dateTime,
                        duration = duration,
                        maxParticipants = maxParticipants,
                        trainerId = trainerId,
                        workoutTypeId = workoutTypeId,
                        ageCategory = ageCategory,
                        room = room
                    )

                    viewModel.updateWorkoutWithImage(workout.id, request, selectedImageUri)
                } else {
                    Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ ДИАЛОГИ
    // ============================================================

    private fun showDatePicker(etDate: TextInputEditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .parse("$year-${month + 1}-$day")

                if (selectedDate != null && selectedDate.before(Date())) {
                    Toast.makeText(requireContext(), "Нельзя выбрать прошедшую дату", Toast.LENGTH_SHORT).show()
                } else {
                    etDate.setText(String.format("%04d-%02d-%02d", year, month + 1, day))
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(etTime: TextInputEditText) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                etTime.setText(String.format("%02d:%02d", hour, minute))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun showTrainerPicker(etTrainer: TextInputEditText, etTrainerId: TextInputEditText) {
        if (trainers.isEmpty()) {
            Toast.makeText(requireContext(), "Нет доступных тренеров", Toast.LENGTH_SHORT).show()
            return
        }

        val trainerNames = trainers.map { "${it.name} (ID: ${it.id})" }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите тренера")
            .setItems(trainerNames) { _, which ->
                val trainer = trainers[which]
                etTrainer.setText(trainer.name)
                etTrainer.tag = trainer.id
                etTrainerId.setText(trainer.id.toString())
            }
            .show()
    }

    private fun showWorkoutTypePicker(etWorkoutType: TextInputEditText) {
        if (workoutTypes.isEmpty()) {
            Toast.makeText(requireContext(), "Нет доступных типов тренировок", Toast.LENGTH_SHORT).show()
            return
        }

        val typeNames = workoutTypes.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите тип тренировки")
            .setItems(typeNames) { _, which ->
                val type = workoutTypes[which]
                etWorkoutType.setText(type.name)
                etWorkoutType.tag = type.id
            }
            .show()
    }

    private fun showAgeCategoryPicker(etAgeCategory: TextInputEditText) {
        val categories = arrayOf("", "4+", "5+", "7-14")
        val currentText = etAgeCategory.text.toString()
        val currentIndex = categories.indexOfFirst { it == currentText }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите возрастную категорию")
            .setSingleChoiceItems(categories, currentIndex) { _, which ->
                etAgeCategory.setText(categories[which])
            }
            .setPositiveButton("OK", null)
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ============================================================
    // УДАЛЕНИЕ ТРЕНИРОВКИ - ИСПРАВЛЕНО
    // ============================================================

    private fun showDeleteWorkoutDialog(workout: AdminWorkout) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление занятия")
            .setMessage("Вы уверены, что хотите удалить занятие \"${workout.title}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteWorkout(workout.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCopyScheduleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Копирование расписания")
            .setMessage("Скопировать расписание на следующую неделю?")
            .setPositiveButton("Скопировать") { _, _ ->
                viewModel.copyScheduleToNextWeek(currentWeekStart)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
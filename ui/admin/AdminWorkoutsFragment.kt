package com.fitnesslemon.app.ui.admin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentAdminWorkoutsBinding
import com.fitnesslemon.app.databinding.DialogSelectTrainerBinding
import com.fitnesslemon.app.databinding.DialogWorkoutEditBinding
import com.fitnesslemon.app.ui.admin.adapters.AdminWorkoutsAdapter
import com.fitnesslemon.app.ui.admin.adapters.TrainerSelectionAdapter
import com.fitnesslemon.app.data.models.CreateWorkoutRequest
import com.fitnesslemon.app.data.models.UpdateWorkoutRequest
import com.fitnesslemon.app.data.models.Trainer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class AdminWorkoutsFragment : Fragment() {

    private var _binding: FragmentAdminWorkoutsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private var selectedTrainerId: Int? = null
    private var selectedTrainerName: String? = null
    private var selectedImageUri: Uri? = null
    private var isEditMode: Boolean = false
    private var editingWorkout: com.fitnesslemon.app.data.models.AdminWorkout? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Launcher для выбора изображения
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminWorkoutsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadWorkouts()
        viewModel.loadTrainers()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Управление тренировками"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        binding.rvWorkouts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWorkouts.setHasFixedSize(true)
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            showCreateWorkoutDialog()
        }

        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadWorkouts()
        }
    }

    private fun observeViewModel() {
        viewModel.workouts.observe(viewLifecycleOwner) { workouts ->
            if (workouts.isNotEmpty()) {
                binding.rvWorkouts.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                binding.rvWorkouts.adapter = AdminWorkoutsAdapter(
                    workouts = workouts,
                    onItemClick = { workout -> showWorkoutDetails(workout) },
                    onEditClick = { workout -> showEditWorkoutDialog(workout) },
                    onDeleteClick = { workout -> showDeleteWorkoutDialog(workout) }
                )
            } else {
                binding.rvWorkouts.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading && viewModel.workouts.value?.isEmpty() == true)
                View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    // ============================================================
    // ✅ СОЗДАНИЕ ТРЕНИРОВКИ С ИЗОБРАЖЕНИЕМ
    // ============================================================

    private fun showCreateWorkoutDialog() {
        isEditMode = false
        editingWorkout = null
        selectedImageUri = null

        val dialogBinding = DialogWorkoutEditBinding.inflate(layoutInflater)

        // Устанавливаем текущую дату и время по умолчанию
        dialogBinding.etDate.setText(dateFormat.format(Date()))
        dialogBinding.etTime.setText(timeFormat.format(Date()))
        dialogBinding.etDuration.setText("60")
        dialogBinding.etMaxParticipants.setText("10")

        // Обработчики кликов
        dialogBinding.etDate.setOnClickListener { showDatePicker(dialogBinding.etDate) }
        dialogBinding.etTime.setOnClickListener { showTimePicker(dialogBinding.etTime) }
        dialogBinding.etTrainer.setOnClickListener { showTrainerPicker(dialogBinding) }
        dialogBinding.etWorkoutType.setOnClickListener { showWorkoutTypePicker(dialogBinding) }
        dialogBinding.etAgeCategory.setOnClickListener { showAgeCategoryPicker(dialogBinding.etAgeCategory) }

        // ✅ Выбор изображения
        dialogBinding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Показываем превью если изображение уже выбрано
        selectedImageUri?.let { uri ->
            dialogBinding.tvSelectedImage.text = "✅ Изображение выбрано"
            dialogBinding.tvSelectedImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(dialogBinding.ivPreview)
            dialogBinding.ivPreview.visibility = View.VISIBLE
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Создание тренировки")
            .setView(dialogBinding.root)
            .setPositiveButton("Создать") { _, _ ->
                val title = dialogBinding.etTitle.text.toString()
                val date = dialogBinding.etDate.text.toString()
                val time = dialogBinding.etTime.text.toString()

                if (title.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty()) {
                    val dateTime = "$date $time:00"

                    // Получаем ID тренера из тега
                    val trainerId = dialogBinding.etTrainer.tag as? Int ?: 0

                    val request = CreateWorkoutRequest(
                        title = title,
                        description = dialogBinding.etDescription.text.toString(),
                        date = dateTime,
                        duration = dialogBinding.etDuration.text.toString().toIntOrNull() ?: 60,
                        trainerId = trainerId,
                        maxParticipants = dialogBinding.etMaxParticipants.text.toString().toIntOrNull() ?: 10,
                        workoutTypeId = dialogBinding.etWorkoutType.tag as? Int,
                        ageCategory = dialogBinding.etAgeCategory.text.toString().takeIf { it.isNotEmpty() },
                        room = dialogBinding.etRoom.text.toString().takeIf { it.isNotEmpty() }
                    )

                    // ✅ Используем метод с изображением
                    viewModel.createWorkoutWithImage(request, selectedImageUri)
                    selectedImageUri = null
                } else {
                    Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ============================================================
    // ✅ РЕДАКТИРОВАНИЕ ТРЕНИРОВКИ С ИЗОБРАЖЕНИЕМ
    // ============================================================

    private fun showEditWorkoutDialog(workout: com.fitnesslemon.app.data.models.AdminWorkout) {
        isEditMode = true
        editingWorkout = workout
        selectedImageUri = null

        val dialogBinding = DialogWorkoutEditBinding.inflate(layoutInflater)

        // Заполняем данные
        dialogBinding.etTitle.setText(workout.title)
        dialogBinding.etDescription.setText(workout.description ?: "")
        dialogBinding.etDate.setText(workout.formattedDate)
        dialogBinding.etTime.setText(workout.formattedTime)
        dialogBinding.etDuration.setText(workout.duration.toString())
        dialogBinding.etMaxParticipants.setText(workout.maxParticipants.toString())

        // Заполняем тренера
        val trainer = viewModel.trainers.value?.find { it.id == workout.trainerId }
        if (trainer != null) {
            dialogBinding.etTrainer.setText(trainer.name)
            dialogBinding.etTrainer.tag = trainer.id
        } else if (workout.trainerId > 0) {
            dialogBinding.etTrainer.setText("Тренер ID: ${workout.trainerId}")
            dialogBinding.etTrainer.tag = workout.trainerId
        }

        // Заполняем тип тренировки
        if (workout.workoutType != null) {
            dialogBinding.etWorkoutType.setText(workout.workoutType)
        }

        dialogBinding.etAgeCategory.setText(workout.ageCategory ?: "")
        dialogBinding.etRoom.setText(workout.room ?: "")

        // ✅ Показываем существующее изображение
        if (!workout.thumbnail.isNullOrEmpty()) {
            dialogBinding.tvSelectedImage.text = "✅ Изображение загружено"
            dialogBinding.tvSelectedImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(workout.thumbnail)
                .centerCrop()
                .into(dialogBinding.ivPreview)
            dialogBinding.ivPreview.visibility = View.VISIBLE
            dialogBinding.btnSelectImage.text = "🔄 Заменить изображение"
        }

        // Обработчики кликов
        dialogBinding.etDate.setOnClickListener { showDatePicker(dialogBinding.etDate) }
        dialogBinding.etTime.setOnClickListener { showTimePicker(dialogBinding.etTime) }
        dialogBinding.etTrainer.setOnClickListener { showTrainerPicker(dialogBinding) }
        dialogBinding.etWorkoutType.setOnClickListener { showWorkoutTypePicker(dialogBinding) }
        dialogBinding.etAgeCategory.setOnClickListener { showAgeCategoryPicker(dialogBinding.etAgeCategory) }

        // ✅ Выбор изображения
        dialogBinding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // ✅ Обновление превью при выборе изображения
        selectedImageUri?.let { uri ->
            dialogBinding.tvSelectedImage.text = "✅ Изображение выбрано"
            dialogBinding.tvSelectedImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(dialogBinding.ivPreview)
            dialogBinding.ivPreview.visibility = View.VISIBLE
            dialogBinding.btnSelectImage.text = "🔄 Заменить изображение"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактирование тренировки")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val title = dialogBinding.etTitle.text.toString()
                val date = dialogBinding.etDate.text.toString()
                val time = dialogBinding.etTime.text.toString()

                if (title.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty()) {
                    val dateTime = "$date $time:00"

                    val trainerId = dialogBinding.etTrainer.tag as? Int ?: workout.trainerId

                    val request = UpdateWorkoutRequest(
                        id = workout.id,
                        title = title,
                        description = dialogBinding.etDescription.text.toString(),
                        date = dateTime,
                        duration = dialogBinding.etDuration.text.toString().toIntOrNull() ?: workout.duration,
                        maxParticipants = dialogBinding.etMaxParticipants.text.toString().toIntOrNull() ?: workout.maxParticipants,
                        trainerId = trainerId,
                        workoutTypeId = dialogBinding.etWorkoutType.tag as? Int,
                        ageCategory = dialogBinding.etAgeCategory.text.toString().takeIf { it.isNotEmpty() },
                        room = dialogBinding.etRoom.text.toString().takeIf { it.isNotEmpty() }
                    )

                    // ✅ Используем метод с изображением
                    viewModel.updateWorkoutWithImage(workout.id, request, selectedImageUri)
                    selectedImageUri = null

                    // После обновления перезагружаем расписание
                    viewModel.loadWeeklyScheduleForDate(Date())

                } else {
                    Toast.makeText(requireContext(), "Заполните обязательные поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    private fun showTrainerPicker(dialogBinding: DialogWorkoutEditBinding) {
        val trainers = viewModel.trainers.value ?: emptyList()
        if (trainers.isEmpty()) {
            Toast.makeText(requireContext(), "Нет доступных тренеров", Toast.LENGTH_SHORT).show()
            return
        }

        val trainerNames = trainers.map { "${it.name} (ID: ${it.id})" }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите тренера")
            .setItems(trainerNames) { _, which ->
                val trainer = trainers[which]
                dialogBinding.etTrainer.setText(trainer.name)
                dialogBinding.etTrainer.tag = trainer.id
            }
            .show()
    }

    private fun showWorkoutTypePicker(dialogBinding: DialogWorkoutEditBinding) {
        val types = arrayOf("Силовая", "Кардио", "HIIT", "Йога", "Пилатес", "Танцы", "Кроссфит", "Другое")
        val currentText = dialogBinding.etWorkoutType.text.toString()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите тип тренировки")
            .setItems(types) { _, which ->
                dialogBinding.etWorkoutType.setText(types[which])
                dialogBinding.etWorkoutType.tag = which + 1
            }
            .show()
    }

    private fun showAgeCategoryPicker(etAgeCategory: TextInputEditText) {
        val categories = arrayOf("", "4+", "5+", "7-14", "14-18", "18+")
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

    private fun showDatePicker(etDate: TextInputEditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                etDate.setText(String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth))
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
            { _, hourOfDay, minute ->
                etTime.setText(String.format("%02d:%02d", hourOfDay, minute))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun showTrainerSelectionDialog(onTrainerSelected: (Trainer) -> Unit) {
        val binding = DialogSelectTrainerBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

        binding.toolbar.setNavigationOnClickListener {
            dialog.dismiss()
        }

        viewModel.trainers.observe(viewLifecycleOwner) { trainers ->
            val adapter = TrainerSelectionAdapter(trainers) { trainer ->
                onTrainerSelected(trainer)
                dialog.dismiss()
            }
            binding.rvTrainers.layoutManager = LinearLayoutManager(requireContext())
            binding.rvTrainers.adapter = adapter

            binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    adapter.filter.filter(s.toString())
                }
            })
        }

        dialog.show()
    }

    private fun showFilterDialog() {
        val options = arrayOf("Все", "Предстоящие", "Прошедшие", "По тренеру")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Фильтр тренировок")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.loadWorkouts() // Все
                    1 -> viewModel.loadWorkouts("upcoming") // Предстоящие
                    2 -> viewModel.loadWorkouts("past") // Прошедшие
                    3 -> showTrainerFilterDialog() // По тренеру
                }
            }
            .show()
    }

    private fun showTrainerFilterDialog() {
        val binding = DialogSelectTrainerBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

        binding.toolbar.setNavigationOnClickListener {
            dialog.dismiss()
        }
        binding.toolbar.title = "Выберите тренера для фильтра"

        viewModel.trainers.observe(viewLifecycleOwner) { trainers ->
            val adapter = TrainerSelectionAdapter(trainers) { trainer ->
                viewModel.loadWorkouts(trainerId = trainer.id)
                dialog.dismiss()
            }
            binding.rvTrainers.layoutManager = LinearLayoutManager(requireContext())
            binding.rvTrainers.adapter = adapter

            binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    adapter.filter.filter(s.toString())
                }
            })
        }

        dialog.show()
    }

    private fun showWorkoutDetails(workout: com.fitnesslemon.app.data.models.AdminWorkout) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_workout_details, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDescription)
        val tvDateTime = dialogView.findViewById<TextView>(R.id.tvDateTime)
        val tvTrainer = dialogView.findViewById<TextView>(R.id.tvTrainer)
        val tvDuration = dialogView.findViewById<TextView>(R.id.tvDuration)
        val tvParticipants = dialogView.findViewById<TextView>(R.id.tvParticipants)
        val tvWorkoutType = dialogView.findViewById<TextView>(R.id.tvWorkoutType)
        val ivThumbnail = dialogView.findViewById<ImageView>(R.id.ivThumbnail)

        tvTitle.text = workout.title
        tvDescription.text = workout.description ?: ""
        tvDateTime.text = "${workout.formattedDate} ${workout.formattedTime}"
        tvTrainer.text = "Тренер: ${workout.trainerName}"
        tvDuration.text = "Длительность: ${workout.duration} мин"
        tvParticipants.text = "Участники: ${workout.currentParticipants}/${workout.maxParticipants}"
        tvWorkoutType.text = "Тип: ${workout.workoutType ?: "Не указан"}"

        // Загружаем изображение
        if (!workout.thumbnail.isNullOrEmpty()) {
            Glide.with(this)
                .load(workout.thumbnail)
                .centerCrop()
                .into(ivThumbnail)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(workout.title)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDeleteWorkoutDialog(workout: com.fitnesslemon.app.data.models.AdminWorkout) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление тренировки")
            .setMessage("Вы уверены, что хотите удалить тренировку ${workout.title}?")
            .setPositiveButton("Удалить") { _, _ ->
                println("🔍 Удаление тренировки ID: ${workout.id}")
                viewModel.deleteWorkout(workout.id)
                // После удаления перезагружаем расписание
                viewModel.loadWeeklyScheduleForDate(Date())
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
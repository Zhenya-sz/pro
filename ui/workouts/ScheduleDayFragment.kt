package com.fitnesslemon.app.ui.workouts

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentScheduleDayBinding
import com.fitnesslemon.app.ui.adapters.ScheduleDayAdapter
import com.fitnesslemon.app.ui.views.DynamicBackgroundView
import com.fitnesslemon.app.utils.ThemeManager
import java.text.SimpleDateFormat
import java.util.*

class ScheduleDayFragment : Fragment() {

    private var _binding: FragmentScheduleDayBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: WorkoutsViewModel
    private lateinit var date: Date
    private var onClassClick: ((Int) -> Unit)? = null
    private var onQuickBook: ((Int) -> Unit)? = null

    private var adapter: ScheduleDayAdapter? = null
    private var lastClasses: List<com.fitnesslemon.app.data.models.ScheduleItem> = emptyList()
    private var lastBookedIds: List<Int> = emptyList()
    private var lastRemaining: Int = 0
    private var lastClassesHash: Int = 0

    private var isFirstLoad = true
    private var isExpanded = true
    private var isShowingAll = true
    private var progressAnimator: android.animation.ValueAnimator? = null

    companion object {
        private const val ARG_DATE = "date"
        private const val ARG_IS_EXPANDED = "is_expanded"

        fun newInstance(
            date: Date,
            onClassClick: (Int) -> Unit,
            onQuickBook: (Int) -> Unit,
            isExpanded: Boolean = true
        ): ScheduleDayFragment {
            val fragment = ScheduleDayFragment()
            val args = Bundle()
            args.putSerializable(ARG_DATE, date)
            args.putBoolean(ARG_IS_EXPANDED, isExpanded)
            fragment.arguments = args
            fragment.onClassClick = onClassClick
            fragment.onQuickBook = onQuickBook
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            date = it.getSerializable(ARG_DATE) as Date
            isExpanded = it.getBoolean(ARG_IS_EXPANDED, true)
        }
        viewModel = ViewModelProvider(requireActivity())[WorkoutsViewModel::class.java]
        println("📅 [ScheduleDayFragment] Создан для даты: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleDayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBackground()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        println("📅 [ScheduleDayFragment] Загрузка расписания для: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)}")
        viewModel.loadScheduleForDate(date)

        binding.rvClasses.post {
            println("📏 [RV] width=${binding.rvClasses.width}, height=${binding.rvClasses.height}")
            println("📏 [RV] visibility=${binding.rvClasses.visibility}")
            println("📏 [Adapter] itemCount=${adapter?.itemCount}")
        }
    }

    private fun setupBackground() {
        val backgroundView = binding.dynamicBackground
        val timeOfDay = ThemeManager.getTimeOfDay()

        when (timeOfDay) {
            ThemeManager.TimeOfDay.MORNING -> {
                backgroundView.updateColors(
                    ContextCompat.getColor(requireContext(), R.color.morning_start),
                    ContextCompat.getColor(requireContext(), R.color.morning_end)
                )
                backgroundView.setViewAlpha(0.3f)
            }
            ThemeManager.TimeOfDay.AFTERNOON -> {
                backgroundView.updateColors(
                    ContextCompat.getColor(requireContext(), R.color.afternoon_start),
                    ContextCompat.getColor(requireContext(), R.color.afternoon_end)
                )
                backgroundView.setViewAlpha(0.2f)
            }
            ThemeManager.TimeOfDay.EVENING -> {
                backgroundView.updateColors(
                    ContextCompat.getColor(requireContext(), R.color.evening_start),
                    ContextCompat.getColor(requireContext(), R.color.evening_end)
                )
                backgroundView.setViewAlpha(0.4f)
            }
            ThemeManager.TimeOfDay.NIGHT -> {
                backgroundView.updateColors(
                    ContextCompat.getColor(requireContext(), R.color.night_start),
                    ContextCompat.getColor(requireContext(), R.color.night_end)
                )
                backgroundView.setViewAlpha(0.5f)
            }
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvClasses.layoutManager = layoutManager
        binding.rvClasses.setHasFixedSize(true)

        adapter = ScheduleDayAdapter(
            classes = emptyList(),
            onClassClick = { item ->
                println("📅 [ScheduleDayFragment] Клик по занятию: ${item.title} (ID: ${item.id})")
                animateCardClick(binding.rvClasses)
                onClassClick?.invoke(item.id)
            },
            onQuickBook = { item ->
                println("📅 [ScheduleDayFragment] Быстрая запись: ${item.title} (ID: ${item.id})")
                onQuickBook?.invoke(item.id)
                animateBookButton()
            },
            bookedClassIds = emptyList(),
            remainingWorkouts = 0,
            isExpanded = isExpanded
        )
        binding.rvClasses.adapter = adapter
        println("📅 [ScheduleDayFragment] RecyclerView настроен с LinearLayoutManager")
    }

    private fun setupListeners() {
        binding.btnToggleMode.setOnClickListener {
            isExpanded = !isExpanded
            adapter?.setExpanded(isExpanded)

            val icon = if (isExpanded) R.drawable.ic_compress else R.drawable.ic_expand
            binding.btnToggleMode.setImageResource(icon)

            binding.btnToggleMode.animate()
                .rotation(180f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        binding.btnShowAll.setOnClickListener {
            isShowingAll = !isShowingAll
            updateAdapter()
            val text = if (isShowingAll) "Показать только предстоящие" else "Показать все"
            binding.btnShowAll.text = text
        }
    }

    private fun observeViewModel() {
        viewModel.scheduleForDate.observe(viewLifecycleOwner) { items ->
            println("📅 [ScheduleDayFragment] scheduleForDate получено ${items?.size ?: 0} занятий")

            if (items == null || items.isEmpty()) {
                showEmptyState()
                return@observe
            }

            lastClasses = items
            showClassesState()
            // ✅ СТАТИСТИКА УБРАНА — НЕ ВЫЗЫВАЕМ updateDayStats()

            val bookedIds = viewModel.getBookedClassIds()
            val remaining = viewModel.getRemainingWorkouts()
            lastBookedIds = bookedIds
            lastRemaining = remaining

            updateAdapter()

            if (isFirstLoad) {
                animateItemsAppearance()
                isFirstLoad = false
            }
        }

        viewModel.bookedClassIds.observe(viewLifecycleOwner) { ids ->
            println("📅 [ScheduleDayFragment] bookedClassIds получено ${ids.size} ID")
            lastBookedIds = ids
            if (lastClasses.isNotEmpty()) {
                updateAdapter()
            }
        }

        viewModel.remainingWorkouts.observe(viewLifecycleOwner) { remaining ->
            println("📅 [ScheduleDayFragment] remainingWorkouts получено $remaining")
            lastRemaining = remaining
            if (lastClasses.isNotEmpty()) {
                updateAdapter()
            }
        }
    }

    private fun updateAdapter() {
        if (!isAdded || _binding == null) return

        val classesToShow = if (isShowingAll) {
            lastClasses
        } else {
            lastClasses.filter { !isWorkoutPast(it.date) }
        }

        println("📅 [ScheduleDayFragment] updateAdapter: classesToShow.size = ${classesToShow.size}")

        val currentHash = classesToShow.hashCode()
        if (adapter?.itemCount == classesToShow.size && lastClassesHash == currentHash) {
            println("📅 [ScheduleDayFragment] Данные не изменились, пропускаем обновление")
            return
        }
        lastClassesHash = currentHash

        if (adapter == null) {
            adapter = ScheduleDayAdapter(
                classes = classesToShow,
                onClassClick = { item ->
                    animateCardClick(binding.rvClasses)
                    onClassClick?.invoke(item.id)
                },
                onQuickBook = { item ->
                    onQuickBook?.invoke(item.id)
                    animateBookButton()
                },
                bookedClassIds = lastBookedIds,
                remainingWorkouts = lastRemaining,
                isExpanded = isExpanded
            )
            binding.rvClasses.adapter = adapter
            println("📅 [ScheduleDayFragment] НОВЫЙ адаптер создан с ${classesToShow.size} элементами")
        } else {
            adapter?.updateData(classesToShow, lastBookedIds, lastRemaining)
            println("📅 [ScheduleDayFragment] Адаптер обновлён данными ${classesToShow.size}")
        }

        if (classesToShow.isNotEmpty()) {
            binding.rvClasses.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            println("📅 [ScheduleDayFragment] RecyclerView VISIBLE, элементов: ${classesToShow.size}")

            binding.rvClasses.post {
                binding.rvClasses.requestLayout()
                binding.rvClasses.invalidate()
                println("📏 [RV после обновления] width=${binding.rvClasses.width}, height=${binding.rvClasses.height}")
            }
        } else {
            binding.rvClasses.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            println("📅 [ScheduleDayFragment] RecyclerView GONE, данных нет")
        }
    }

    private fun isWorkoutPast(workoutDate: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val workoutDateTime = format.parse(workoutDate)
            val currentTime = Date()
            workoutDateTime?.before(currentTime) ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun showEmptyState() {
        if (!isAdded || _binding == null) return

        binding.rvClasses.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = formatDate(date)
        binding.progressDay.visibility = View.GONE
        binding.tvDayStats.visibility = View.GONE
        binding.tvLoadStatus.visibility = View.GONE
        binding.layoutDayStats.visibility = View.GONE
        println("📅 [ScheduleDayFragment] Нет занятий, показываем пустое состояние")
    }

    private fun showClassesState() {
        if (!isAdded || _binding == null) return

        binding.rvClasses.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        println("📅 [ScheduleDayFragment] Показываем список занятий")
    }

    private fun formatDate(date: Date): String {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        return "Нет занятий на ${dateFormat.format(date)}"
    }

    // ================================
    // АНИМАЦИИ
    // ================================

    private fun animateItemsAppearance() {
        if (!isAdded || _binding == null) return

        val recyclerView = binding.rvClasses
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            child?.apply {
                translationY = 100f
                alpha = 0f
                animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setStartDelay((i * 50).toLong())
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun animateCardClick(view: View) {
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(150)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun animateBookButton() {
        if (!isAdded || _binding == null) return

        val btn = binding.btnToggleMode
        btn.animate()
            .rotation(360f)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    override fun onDestroyView() {
        progressAnimator?.cancel()
        progressAnimator = null

        super.onDestroyView()
        _binding = null
    }
}
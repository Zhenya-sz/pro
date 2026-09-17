package com.fitnesslemon.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.ScheduleItem
import com.fitnesslemon.app.databinding.ItemScheduleDayBinding
import java.text.SimpleDateFormat
import java.util.*

class ScheduleDayAdapter(
    private var classes: List<ScheduleItem>,
    private val onClassClick: (ScheduleItem) -> Unit,
    private val onQuickBook: (ScheduleItem) -> Unit,
    private var bookedClassIds: List<Int> = emptyList(),
    private var remainingWorkouts: Int = 0,
    private var isExpanded: Boolean = true
) : RecyclerView.Adapter<ScheduleDayAdapter.ViewHolder>() {

    private var isShowingAll = true
    private var allClasses: List<ScheduleItem> = classes
    private var displayClasses: List<ScheduleItem> = classes

    init {
        println("📊 [ScheduleDayAdapter] Инициализация, классов: ${classes.size}")
        updateDisplayList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleDayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        println("📊 [ScheduleDayAdapter] onCreateViewHolder")
        return ViewHolder(binding, onClassClick, onQuickBook)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = displayClasses[position]
        val isBooked = bookedClassIds.contains(item.id)
        println("📊 [ScheduleDayAdapter] onBindViewHolder position=$position, title=${item.title}")
        holder.bind(item, isBooked, remainingWorkouts, isExpanded)
    }

    override fun getItemCount(): Int {
        val count = displayClasses.size
        println("📊 [ScheduleDayAdapter] getItemCount = $count")
        return count
    }

    fun updateData(
        newClasses: List<ScheduleItem>,
        newBookedIds: List<Int>,
        newRemaining: Int
    ) {
        println("📊 [ScheduleDayAdapter] updateData: ${newClasses.size} классов")
        allClasses = newClasses
        bookedClassIds = newBookedIds
        remainingWorkouts = newRemaining
        updateDisplayList()
        notifyDataSetChanged()
    }

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        notifyDataSetChanged()
    }

    fun showAllItems() {
        isShowingAll = true
        updateDisplayList()
    }

    fun showUpcomingOnly() {
        isShowingAll = false
        updateDisplayList()
    }

    private fun updateDisplayList() {
        displayClasses = if (isShowingAll) {
            allClasses
        } else {
            allClasses.filter { !isWorkoutPast(it.date) }
        }
        println("📊 [ScheduleDayAdapter] updateDisplayList: ${displayClasses.size} классов для отображения")
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

    class ViewHolder(
        private val binding: ItemScheduleDayBinding,
        private val onClassClick: (ScheduleItem) -> Unit,
        private val onQuickBook: (ScheduleItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ScheduleItem,
            isBooked: Boolean,
            remainingWorkouts: Int,
            isExpanded: Boolean
        ) {
            println("📊 [ScheduleDayAdapter.ViewHolder] bind: ${item.title}")
            val context = binding.root.context
            val isPast = isWorkoutPast(item.date)

            binding.apply {
                // Основная информация
                tvTime.text = item.formattedTime ?: "00:00"
                tvTitle.text = item.title
                tvTrainer.text = "👤 ${item.trainerName}"
                tvParticipants.text = "${item.currentParticipants}/${item.maxParticipants}"

                // Тип тренировки
                if (!item.workoutType.isNullOrEmpty()) {
                    tvWorkoutType.text = "🏋️ ${item.workoutType}"
                    tvWorkoutType.visibility = android.view.View.VISIBLE
                } else {
                    tvWorkoutType.visibility = android.view.View.GONE
                }

                // Возрастная категория
                if (!item.ageCategory.isNullOrEmpty()) {
                    tvAgeCategory.text = item.ageCategory
                    tvAgeCategory.visibility = android.view.View.VISIBLE
                } else {
                    tvAgeCategory.visibility = android.view.View.GONE
                }

                // Сброс стилей карточки
                cvCard.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.lemon_background)
                )
                cvCard.cardElevation = 4f
                cvCard.alpha = 1f

                // СТАТУС ЗАНЯТИЯ
                when {
                    isPast -> {
                        cvCard.alpha = 0.6f
                        cvCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_background_secondary)
                        )

                        tvStatus.text = "✅ Проведено"
                        tvStatus.setTextColor(ContextCompat.getColor(context, R.color.lemon_text_secondary))
                        tvStatus.visibility = android.view.View.VISIBLE

                        btnBook.isEnabled = false
                        btnBook.text = "Завершено"
                        btnBook.setBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_text_secondary)
                        )
                        btnBook.alpha = 0.5f
                    }
                    isBooked -> {
                        cvCard.alpha = 1f
                        cvCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_primary_surface)
                        )
                        cvCard.cardElevation = 6f

                        tvStatus.text = "✅ Вы записаны"
                        tvStatus.setTextColor(ContextCompat.getColor(context, R.color.lemon_primary))
                        tvStatus.visibility = android.view.View.VISIBLE

                        btnBook.isEnabled = false
                        btnBook.text = "✅ Записаны"
                        btnBook.setBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_primary)
                        )
                        btnBook.alpha = 0.8f
                    }
                    item.availableSpots <= 0 -> {
                        cvCard.alpha = 1f
                        cvCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_background)
                        )

                        tvStatus.text = "🔴 Мест нет"
                        tvStatus.setTextColor(ContextCompat.getColor(context, R.color.lemon_error))
                        tvStatus.visibility = android.view.View.VISIBLE

                        btnBook.isEnabled = false
                        btnBook.text = "Нет мест"
                        btnBook.setBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_error)
                        )
                        btnBook.alpha = 0.7f
                    }
                    remainingWorkouts <= 0 -> {
                        cvCard.alpha = 1f
                        cvCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_background)
                        )

                        tvStatus.text = "⚠️ Нет тренировок"
                        tvStatus.setTextColor(ContextCompat.getColor(context, R.color.lemon_warning))
                        tvStatus.visibility = android.view.View.VISIBLE

                        btnBook.isEnabled = false
                        btnBook.text = "Нет тренировок"
                        btnBook.setBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_text_secondary)
                        )
                        btnBook.alpha = 0.5f
                    }
                    else -> {
                        cvCard.alpha = 1f
                        cvCard.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_background)
                        )

                        tvStatus.visibility = android.view.View.GONE

                        btnBook.isEnabled = true
                        btnBook.text = "Записаться"
                        btnBook.setBackgroundColor(
                            ContextCompat.getColor(context, R.color.lemon_primary)
                        )
                        btnBook.alpha = 1f
                    }
                }

                // Прогресс заполненности
                val percentage = if (item.maxParticipants > 0) {
                    (item.currentParticipants * 100) / item.maxParticipants
                } else 0
                progressBar.progress = percentage

                // Свободные места
                tvAvailableSpots.text = "👥 Свободно: ${item.availableSpots}"

                // Миниатюра
                if (!item.thumbnail.isNullOrEmpty()) {
                    Glide.with(binding.root.context)
                        .load(item.thumbnail)
                        .centerCrop()
                        .placeholder(R.drawable.ic_workout)
                        .error(R.drawable.ic_workout)
                        .into(ivThumbnail)
                } else {
                    ivThumbnail.setImageResource(R.drawable.ic_workout)
                }

                // Обработка кликов
                root.setOnClickListener {
                    onClassClick(item)
                }

                btnBook.setOnClickListener {
                    if (btnBook.isEnabled) {
                        onQuickBook(item)
                    }
                }

                // Показываем/скрываем детали в зависимости от режима
                if (isExpanded) {
                    tvDescription.visibility = android.view.View.VISIBLE
                    tvDescription.text = item.description ?: ""
                } else {
                    tvDescription.visibility = android.view.View.GONE
                }
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
    }
}
package com.fitnesslemon.app.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.ScheduleItem

class ScheduleTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = Color.WHITE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private var items: List<ScheduleItem> = emptyList()
    private val startHour = 8
    private val endHour = 22
    private val totalHours = endHour - startHour

    private var animProgress = 1f

    fun setSchedule(items: List<ScheduleItem>, animate: Boolean = true) {
        this.items = items
        if (animate) {
            animProgress = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 800
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animProgress = 1f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val hourHeight = height / totalHours
        val marginLeft = 80f
        val marginRight = 20f

        // Рисуем сетку
        for (hour in startHour..endHour) {
            val y = (hour - startHour) * hourHeight
            gridPaint.color = Color.parseColor("#40FFFFFF")
            canvas.drawLine(marginLeft, y, width - marginRight, y, gridPaint)

            textPaint.color = Color.parseColor("#80FFFFFF")
            textPaint.textSize = 28f
            textPaint.textAlign = Paint.Align.RIGHT
            val timeStr = String.format("%02d:00", hour)
            canvas.drawText(timeStr, marginLeft - 10f, y + 10f, textPaint)
        }

        if (items.isEmpty()) {
            textPaint.color = Color.parseColor("#80FFFFFF")
            textPaint.textSize = 36f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Нет занятий", width / 2f, height / 2f, textPaint)
            return
        }

        items.forEach { item ->
            try {
                val time = parseTime(item.formattedTime ?: "00:00")
                val duration = item.duration ?: 60

                val startY = (time.hour - startHour) * hourHeight +
                        (time.minute / 60f) * hourHeight
                val endY = startY + (duration / 60f) * hourHeight * animProgress

                if (endY > startY) {
                    // Исправлено: безопасная работа с nullable списком
                    val types = item.workoutTypes ?: emptyList()
                    val workoutType = types.firstOrNull() ?: "Тренировка"

                    paint.color = getWorkoutColor(workoutType)
                    paint.style = Paint.Style.FILL

                    val radius = 16f
                    val rect = RectF(
                        marginLeft,
                        startY + 2f,
                        width - marginRight,
                        endY - 2f
                    )
                    canvas.drawRoundRect(rect, radius, radius, paint)

                    textPaint.color = Color.WHITE
                    textPaint.textSize = 26f
                    textPaint.textAlign = Paint.Align.LEFT

                    val textY = startY + (endY - startY) / 2 + 10f
                    val title = if (item.title.length > 15)
                        item.title.substring(0, 15) + "…"
                    else item.title

                    canvas.drawText(title, marginLeft + 16f, textY, textPaint)

                    textPaint.color = Color.parseColor("#CCFFFFFF")
                    textPaint.textSize = 20f
                    val info = "${item.formattedTime} • ${item.availableSpots} мест"
                    canvas.drawText(info, marginLeft + 16f, textY + 30f, textPaint)
                }
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }
    }

    private fun parseTime(timeStr: String): Time {
        return try {
            val parts = timeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            Time(hour, minute)
        } catch (e: Exception) {
            Time(0, 0)
        }
    }

    private fun getWorkoutColor(type: String): Int {
        val colorMap = mapOf(
            "йога" to R.color.workout_yoga,
            "силовая" to R.color.workout_strength,
            "кардио" to R.color.workout_cardio,
            "пилатес" to R.color.workout_pilates,
            "медитация" to R.color.workout_meditation,
            "растяжка" to R.color.workout_stretching,
            "функциональная" to R.color.workout_functional,
            "танцы" to R.color.workout_dance,
            "бокс" to R.color.workout_boxing
        )

        val key = type.lowercase().trim()
        val colorRes = colorMap[key] ?: R.color.workout_default
        return ContextCompat.getColor(context, colorRes)
    }

    data class Time(val hour: Int, val minute: Int)
}
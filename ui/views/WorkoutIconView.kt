package com.fitnesslemon.app.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.fitnesslemon.app.R
import kotlin.math.min

class WorkoutIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var workoutType: String = ""
    private var scaleProgress = 1f

    private val typeEmojis = mapOf(
        "йога" to "🧘",
        "силовая" to "💪",
        "кардио" to "🏃",
        "пилатес" to "🤸",
        "медитация" to "🧘‍♀️",
        "растяжка" to "🧘‍♂️",
        "функциональная" to "🏋️",
        "танцы" to "💃",
        "бокс" to "🥊"
    )

    private val typeColors = mapOf(
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

    fun setWorkoutType(type: String) {
        this.workoutType = type
        invalidate()
    }

    fun playAnimation() {
        scaleProgress = 0.7f

        ValueAnimator.ofFloat(0.7f, 1.2f, 1f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                scaleProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height).toFloat()
        val radius = size / 2f * 0.8f * scaleProgress

        val colorRes = typeColors[workoutType.lowercase().trim()] ?: R.color.workout_default
        paint.color = ContextCompat.getColor(context, colorRes)
        paint.style = Paint.Style.FILL

        canvas.drawCircle(cx, cy, radius, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, radius, paint)

        val emoji = typeEmojis[workoutType.lowercase().trim()] ?: "🏋️"
        textPaint.color = Color.WHITE
        textPaint.textSize = radius * 0.9f
        val textBounds = Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, textBounds)

        canvas.drawText(emoji, cx, cy + textBounds.height() / 2f, textPaint)
    }
}
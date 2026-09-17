package com.fitnesslemon.app.ui.chat.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    private var amplitude = 0
    private var animating = false
    private var phase = 0f
    private val bars = 35
    private val barWidth = 5f
    private val barSpacing = 4f

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (animating) {
                phase += 0.1f
                invalidate()
                postDelayed(this, 50)
            }
        }
    }

    fun setProgress(amplitude: Int) {
        this.amplitude = amplitude.coerceIn(0, 100)
        if (!animating && amplitude > 5) {
            animating = true
            post(updateRunnable)
        } else if (animating && amplitude <= 5) {
            animating = false
            removeCallbacks(updateRunnable)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2
        val totalWidth = bars * (barWidth + barSpacing)
        val startX = (width - totalWidth) / 2

        val baseHeight = height * 0.15f
        val maxHeight = height * 0.85f

        for (i in 0 until bars) {
            val position = i.toFloat() / bars
            val angle = position * Math.PI * 4 + phase

            val ampFactor = amplitude / 100f
            val sineWave = sin(angle).toFloat()
            val barHeight = if (ampFactor > 0.05f) {
                baseHeight + (maxHeight - baseHeight) * ampFactor * (0.5f + 0.5f * sineWave)
            } else {
                baseHeight * (0.5f + 0.5f * sineWave)
            }

            val x = startX + i * (barWidth + barSpacing)
            val top = centerY - barHeight / 2

            // ✅ Цвет для своих сообщений
            val green = (0x88 + (0xFF - 0x88) * ampFactor).toInt()
            paint.color = 0xFF000000.toInt() or (green shl 8)

            canvas.drawRoundRect(
                x,
                top,
                x + barWidth,
                top + barHeight,
                3f,
                3f,
                paint
            )
        }
    }

    fun stopAnimation() {
        animating = false
        removeCallbacks(updateRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
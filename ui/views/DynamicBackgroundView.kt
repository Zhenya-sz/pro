package com.fitnesslemon.app.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class DynamicBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startColor: Int = Color.parseColor("#FF6B6B")
    private var endColor: Int = Color.parseColor("#FFE66D")
    private var currentStartColor: Int = startColor
    private var currentEndColor: Int = endColor
    private var viewAlpha: Float = 1f

    fun updateColors(start: Int, end: Int, animate: Boolean = true) {
        if (animate) {
            animateColors(start, end)
        } else {
            this.startColor = start
            this.endColor = end
            currentStartColor = start
            currentEndColor = end
            invalidate()
        }
    }

    private fun animateColors(targetStart: Int, targetEnd: Int) {
        val oldStart = currentStartColor
        val oldEnd = currentEndColor

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                currentStartColor = blendColors(oldStart, targetStart, t)
                currentEndColor = blendColors(oldEnd, targetEnd, t)
                invalidate()
            }
            start()
        }
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val a1 = Color.alpha(color1)
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val a2 = Color.alpha(color2)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        return Color.argb(
            (a1 + (a2 - a1) * ratio).toInt(),
            (r1 + (r2 - r1) * ratio).toInt(),
            (g1 + (g2 - g1) * ratio).toInt(),
            (b1 + (b2 - b1) * ratio).toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val gradient = LinearGradient(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            currentStartColor, currentEndColor,
            Shader.TileMode.CLAMP
        )

        paint.shader = gradient
        paint.alpha = (viewAlpha * 255).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    // Переименовано, чтобы не конфликтовать с методом View
    fun setViewAlpha(alpha: Float) {
        this.viewAlpha = alpha.coerceIn(0f, 1f)
        invalidate()
    }
}
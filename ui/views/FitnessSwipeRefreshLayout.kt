package com.fitnesslemon.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.fitnesslemon.app.R

class FitnessSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    init {
        // Настройка цветов индикатора обновления
        setColorSchemeColors(
            ContextCompat.getColor(context, R.color.lemon_primary),
            ContextCompat.getColor(context, R.color.lemon_accent),
            ContextCompat.getColor(context, R.color.lemon_success)
        )

        // Размер индикатора
        setSize(SwipeRefreshLayout.DEFAULT)
    }
}
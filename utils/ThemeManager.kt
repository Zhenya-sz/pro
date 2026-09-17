package com.fitnesslemon.app.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import com.fitnesslemon.app.R
import java.util.*

object ThemeManager {

    enum class TimeOfDay {
        MORNING, AFTERNOON, EVENING, NIGHT
    }

    fun applyTheme(activity: Activity) {
        val timeOfDay = getTimeOfDay()
        val isDark = timeOfDay in listOf(TimeOfDay.EVENING, TimeOfDay.NIGHT)

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                statusBarColor = if (isDark) {
                    ContextCompat.getColor(activity, R.color.status_bar_dark)
                } else {
                    ContextCompat.getColor(activity, R.color.status_bar_light)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val decorView = decorView
                    if (!isDark) {
                        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    } else {
                        decorView.systemUiVisibility = 0
                    }
                }
            }
        }
    }

    fun getTimeOfDay(): TimeOfDay {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when {
            hour in 5..11 -> TimeOfDay.MORNING
            hour in 12..17 -> TimeOfDay.AFTERNOON
            hour in 18..22 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    fun getTimeOfDayString(context: Context): String {
        return when (getTimeOfDay()) {
            TimeOfDay.MORNING -> context.getString(R.string.good_morning)
            TimeOfDay.AFTERNOON -> context.getString(R.string.good_afternoon)
            TimeOfDay.EVENING -> context.getString(R.string.good_evening)
            TimeOfDay.NIGHT -> context.getString(R.string.good_night)
        }
    }
}
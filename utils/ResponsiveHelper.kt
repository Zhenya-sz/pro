package com.fitnesslemon.app.utils

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics

object ResponsiveHelper {

    fun isTablet(context: Context): Boolean {
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val heightDp = metrics.heightPixels / metrics.density
        return widthDp >= 600 || heightDp >= 600
    }

    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun getScreenWidthDp(context: Context): Float {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels / metrics.density
    }

    fun getScreenHeightDp(context: Context): Float {
        val metrics = context.resources.displayMetrics
        return metrics.heightPixels / metrics.density
    }

    fun getColumnCount(context: Context): Int {
        return when {
            isTablet(context) && !isLandscape(context) -> 2
            isTablet(context) && isLandscape(context) -> 3
            isLandscape(context) -> 2
            else -> 1
        }
    }
}
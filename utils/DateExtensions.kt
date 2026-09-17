package com.fitnesslemon.app.utils

import java.util.*

val Date.hours: Int
    get() {
        val calendar = Calendar.getInstance()
        calendar.time = this
        return calendar.get(Calendar.HOUR_OF_DAY)
    }

val Date.minutes: Int
    get() {
        val calendar = Calendar.getInstance()
        calendar.time = this
        return calendar.get(Calendar.MINUTE)
    }
package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class ScheduleWeekResponse(
    val success: Boolean,
    val message: String,
    val data: ScheduleWeekData
)

data class ScheduleWeekData(
    val week: ScheduleWeekInfo,
    val days: Map<String, List<ScheduleItem>> // ← ключи как String от сервера
)

data class ScheduleWeekInfo(
    @SerializedName("start")
    val start: String,
    @SerializedName("end")
    val end: String,
    val display: String,
    @SerializedName("week_number")
    val weekNumber: Int? = null,
    val year: Int? = null
)
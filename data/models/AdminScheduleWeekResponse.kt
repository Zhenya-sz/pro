package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class AdminScheduleWeekResponse(
    val success: Boolean,
    val message: String,
    val data: AdminScheduleWeekData
)

data class AdminScheduleWeekData(
    val week: ScheduleWeekInfo?,
    val days: Map<String, List<ScheduleItem>>
)
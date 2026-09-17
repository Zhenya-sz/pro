package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class AttendanceReport(
    val period: String,
    val labels: List<String>,
    val datasets: List<ReportDataset>
)

data class ReportDataset(
    val label: String,
    val data: List<Int>,
    val color: String
)
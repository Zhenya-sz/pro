package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class FinancialReport(
    val period: String,
    @SerializedName("total_revenue")
    val totalRevenue: Double,
    @SerializedName("subscriptions_revenue")
    val subscriptionsRevenue: Double,
    @SerializedName("single_payments")
    val singlePayments: Double,
    @SerializedName("transactions_count")
    val transactionsCount: Int,
    val chart: AttendanceReport
)
package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class DashboardData(
    val user: User,
    val stats: Stats,
    @SerializedName("upcoming_bookings")
    val upcomingBookings: List<Booking>
)

data class Stats(
    @SerializedName("remaining_workouts")
    val remainingWorkouts: Int,
    @SerializedName("upcoming_count")
    val upcomingCount: Int,
    @SerializedName("unread_notifications")
    val unreadNotifications: Int
)
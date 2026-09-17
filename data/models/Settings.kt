package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

data class Settings(
    @SerializedName("club_name")
    val clubName: String? = null,

    @SerializedName("address")
    val address: String? = null,

    @SerializedName("phone")
    val phone: String? = null,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("max_bookings_per_day")
    val maxBookingsPerDay: Int = 2,

    @SerializedName("cancellation_hours")
    val cancellationHours: Int = 2,

    @SerializedName("auto_cancel_no_show")
    val autoCancelNoShow: Boolean = true,

    @SerializedName("auto_cancel_minutes")
    val autoCancelMinutes: Int = 15,

    @SerializedName("allow_booking_without_subscription")
    val allowBookingWithoutSubscription: Boolean = false,

    @SerializedName("push_enabled")
    val pushEnabled: Boolean = true,

    @SerializedName("email_confirmations")
    val emailConfirmations: Boolean = true,

    @SerializedName("reminder_hours")
    val reminderHours: Int = 1,

    @SerializedName("privacy_policy_url")
    val privacyPolicyUrl: String? = null,

    @SerializedName("terms_url")
    val termsUrl: String? = null,

    @SerializedName("sms_reminders")
    val smsReminders: Boolean = false,

    @SerializedName("telegram_bot_active")
    val telegramBotActive: Boolean = false,

    @SerializedName("telegram_bot_token")
    val telegramBotToken: String? = null
)
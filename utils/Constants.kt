package com.fitnesslemon.app.utils

object Constants {
    // Новый API URL (без WordPress)
    const val BASE_URL = "http://94.159.117.241/"

    // API Endpoints (НОВЫЕ, без /wp-json и /fitnesslemon/v1/)
    const val LOGIN_ENDPOINT = "auth/login"
    const val TRAINERS_ENDPOINT = "trainers"
    const val WORKOUTS_ENDPOINT = "workouts"
    const val NEWS_ENDPOINT = "news"
    const val USER_PROFILE_ENDPOINT = "users/profile"
    const val MY_BOOKINGS_ENDPOINT = "my-bookings"
    const val DASHBOARD_ENDPOINT = "dashboard"
    const val BOOK_CLASS_ENDPOINT = "book-class"
    const val CANCEL_BOOKING_ENDPOINT = "cancel-booking"
    const val REMAINING_WORKOUTS_ENDPOINT = "remaining-workouts"
    const val USER_HISTORY_ENDPOINT = "user-history"
    const val UPDATE_PROFILE_ENDPOINT = "users/profile"
    const val UPLOAD_AVATAR_ENDPOINT = "users/upload-avatar"
    const val DELETE_AVATAR_ENDPOINT = "users/delete-avatar"

    // Chat endpoints
    const val CHATS_ENDPOINT = "chats"
    const val MESSAGES_ENDPOINT = "messages"
    const val SEND_MESSAGE_ENDPOINT = "chats/send"
    const val CHAT_PARTICIPANTS_ENDPOINT = "chats/{chatId}/participants"

    // Subscription endpoints
    const val SUBSCRIPTIONS_ENDPOINT = "subscriptions"

    // Schedule endpoints
    const val SCHEDULE_WEEK_ENDPOINT = "schedule/week"
    const val SCHEDULE_DAY_ENDPOINT = "schedule/day"

    // Admin endpoints
    const val ADMIN_STATS_ENDPOINT = "admin/stats"
    const val ADMIN_USERS_ENDPOINT = "admin/users"
    const val ADMIN_WORKOUTS_ENDPOINT = "admin/workouts"
    const val ADMIN_SUBSCRIPTIONS_ENDPOINT = "admin/subscriptions"
    const val ADMIN_SETTINGS_ENDPOINT = "admin/settings"

    // Preferences Keys
    const val PREF_NAME = "fitness_lemon_prefs"
    const val KEY_TOKEN = "jwt_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_USER_ROLE = "user_role"
    const val KEY_USER_PHONE = "user_phone"
    const val KEY_USER_AVATAR = "user_avatar"
    const val KEY_REMAINING_WORKOUTS = "remaining_workouts"

    // Roles
    const val ROLE_USER = "user"
    const val ROLE_TRAINER = "fitness_trainer"
    const val ROLE_MANAGER = "fitness_manager"
    const val ROLE_ADMIN = "administrator"
}
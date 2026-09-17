// ============================================
// LoginModels.kt
// ============================================

package com.fitnesslemon.app.data.models

import com.google.gson.annotations.SerializedName

// Запрос на логин
data class LoginRequest(
    @SerializedName("phone")
    val phone: String,
    @SerializedName("password")
    val password: String
)

// Ответ сервера (обёртка)
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val data: LoginData
)

// Данные внутри ответа
data class LoginData(
    val token: String,
    @SerializedName("refresh_token")
    val refreshToken: String? = null, // ✅ ДОБАВЛЕНО
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("user_display_name")
    val userDisplayName: String,
    @SerializedName("user_email")
    val userEmail: String,
    @SerializedName("user_role")
    val userRole: String
)

// Универсальный ответ API
data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val id: Int? = null,
    @SerializedName("chat_id")
    val chatId: Int? = null
)
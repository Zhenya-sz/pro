package com.fitnesslemon.app.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

object PreferencesManager {

    private const val PREFS_NAME = "fitness_lemon_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_USER_EMAIL = "user_email"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveRefreshToken(refreshToken: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, refreshToken).apply()
    }

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveExpiresAt(expiresAtEpoch: Long) {
        prefs.edit().putLong(KEY_EXPIRES_AT, expiresAtEpoch).apply()
    }

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun isTokenExpired(now: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = getExpiresAt()
        if (expiresAt <= 0L) return true
        return now >= expiresAt
    }

    fun saveUserProfile(
        id: Int,
        name: String,
        role: String,
        email: String
    ) {
        prefs.edit()
            .putInt(KEY_USER_ID, id)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_ROLE, role)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun clearUserData() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun setAuthToken(token: String, refreshToken: String?, expiresInSeconds: Long) {
        saveToken(token)
        if (!refreshToken.isNullOrBlank()) saveRefreshToken(refreshToken)
        val expiryEpoch = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresInSeconds)
        saveExpiresAt(expiryEpoch)
    }
}

package com.fitnesslemon.app.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TokenManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun refreshToken(): Boolean {
        val refreshToken = PreferencesManager.getRefreshToken() ?: return false
        val request = Request.Builder()
            .url("${Constants.BASE_URL}auth/refresh")
            .post(okhttp3.FormBody.Builder().add("token", refreshToken).build())
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            if (!response.isSuccessful) return false
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
            val newToken = data?.optString("access_token") ?: data?.optString("token") ?: return false
            val newRefresh = data.optString("refresh_token")
            PreferencesManager.saveToken(newToken)
            if (newRefresh.isNotEmpty()) PreferencesManager.saveRefreshToken(newRefresh)
            true
        } catch (_: Exception) {
            false
        }
    }
}

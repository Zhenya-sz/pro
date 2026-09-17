package com.fitnesslemon.app.data.api

import com.fitnesslemon.app.utils.Constants
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val context: android.content.Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        val publicEndpoints = listOf(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/news",
            "/trainers",
            "/workouts",
            "/schedule"
        )

        val isPublic = publicEndpoints.any { url.contains(it) }
        var token: String? = null
        if (!isPublic) {
            token = try {
                com.fitnesslemon.app.utils.PreferencesManager.getToken()
            } catch (_: Exception) {
                null
            }
        }

        val requestBuilder = originalRequest.newBuilder()
        if (!isPublic && !token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 401 && !isPublic) {
            response.close()
            try {
                com.fitnesslemon.app.utils.PreferencesManager.clearUserData()
            } catch (_: Exception) {
            }
            return response
        }

        return response
    }
}

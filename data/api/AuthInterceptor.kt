package com.fitnesslemon.app.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.content.Intent
import com.fitnesslemon.app.ui.auth.LoginActivity
import com.fitnesslemon.app.utils.PreferencesManager
import okhttp3.Interceptor
import okhttp3.Response

/** Adds auth headers without logging or exposing credentials. */
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isPublic = PUBLIC_PATHS.any { path == it || path.startsWith("$it/") }
        val token = if (isPublic) null else runCatching { PreferencesManager.getToken() }.getOrNull()
        val authenticatedRequest = request.newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        }.build()

        val response = chain.proceed(authenticatedRequest)
        if (response.code == 401 && !isPublic) {
            response.close()
            runCatching { PreferencesManager.clearUserData() }
            Handler(Looper.getMainLooper()).post {
                val intent = Intent(context, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
            }
            // Re-run the request is intentionally avoided: it would repeat an invalid credential.
            return response.newBuilder().code(401).message("Unauthorized").build()
        }
        return response
    }

    private companion object {
        val PUBLIC_PATHS = setOf(
            "/auth/login", "/auth/register", "/auth/refresh",
            "/news", "/trainers", "/workouts", "/schedule"
        )
    }
}

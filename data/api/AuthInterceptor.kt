package com.fitnesslemon.app.data.api

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.fitnesslemon.app.ui.auth.LoginActivity
import com.fitnesslemon.app.utils.PreferencesManager
import okhttp3.Interceptor
import okhttp3.Response

/** Adds credentials only to endpoints that are not explicitly public. */
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.trimEnd('/').ifEmpty { "/" }
        val isPublic = isPublicRequest(request.method, path)
        val token = if (isPublic) null else runCatching { PreferencesManager.getToken() }.getOrNull()
        val authenticatedRequest = request.newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        }.build()

        val response = chain.proceed(authenticatedRequest)
        if (response.code == 401 && !isPublic) {
            response.close()
            runCatching { PreferencesManager.clearUserData() }
            Handler(Looper.getMainLooper()).post {
                context.startActivity(Intent(context, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            return response
        }
        return response
    }

    private fun isPublicRequest(method: String, path: String): Boolean {
        if (method == "POST" && path in setOf("/auth/login", "/auth/register", "/auth/refresh")) return true
        if (method != "GET") return false
        return path == "/news" || path.matches(Regex("/news/[0-9]+")) ||
            path == "/trainers" || path == "/workouts" || path.matches(Regex("/workouts/[0-9]+")) ||
            path == "/schedule/week" || path == "/schedule/day"
    }
}

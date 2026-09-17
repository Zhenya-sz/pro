package com.fitnesslemon.app.data.api

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.fitnesslemon.app.ui.auth.LoginActivity
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType

class AuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        // Публичные эндпоинты
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

        // ✅ Только для авторизованных запросов пытаемся получить токен
        var token: String? = null
        if (!isPublic) {
            try {
                token = PreferencesManager.getToken()
            } catch (e: Exception) {
                println("⚠️ Ошибка получения токена: ${e.message}")
            }
        }

        val requestBuilder = originalRequest.newBuilder()

        if (!isPublic && !token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val newRequest = requestBuilder.build()

        return try {
            val response = chain.proceed(newRequest)

            // Если получили 401 и это не публичный эндпоинт
            if (response.code == 401 && !isPublic) {
                println("⚠️ Получен 401 Unauthorized")
                response.close()

                // ✅ Очищаем данные пользователя в безопасном режиме
                try {
                    runBlocking {
                        PreferencesManager.clearUserData()
                    }
                    println("✅ Данные пользователя очищены после 401")
                } catch (e: Exception) {
                    println("❌ Ошибка очистки данных: ${e.message}")
                }

                // ✅ Перенаправляем на экран входа
                Handler(Looper.getMainLooper()).post {
                    try {
                        val intent = Intent(context, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                        Toast.makeText(context, "Сессия истекла. Войдите заново.", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        println("❌ Ошибка перенаправления на Login: ${e.message}")
                    }
                }

                // ✅ Возвращаем ответ с 401
                val jsonResponse = "{\"success\":false,\"message\":\"Unauthorized\"}"
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonResponse.toResponseBody(mediaType)

                return Response.Builder()
                    .code(401)
                    .message("Unauthorized")
                    .request(originalRequest)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .body(body)
                    .build()
            }

            response
        } catch (e: Exception) {
            println("❌ Ошибка выполнения запроса: ${e.message}")
            throw e
        }
    }
}
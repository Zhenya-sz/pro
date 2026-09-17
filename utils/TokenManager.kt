package com.fitnesslemon.app.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TokenManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Проверить токен и обновить если необходимо
     * @return true если токен валиден или успешно обновлен
     */
    suspend fun ensureValidToken(): Boolean {
        val token = PreferencesManager.getToken()
        if (token.isNullOrEmpty()) {
            println("⚠️ Токен отсутствует")
            return false
        }

        if (!PreferencesManager.isTokenExpired()) {
            println("✅ Токен действителен")
            return true
        }

        println("🔄 Пробуем обновить токен...")
        return refreshToken()
    }

    /**
     * Обновить токен через /auth/refresh
     */
    suspend fun refreshToken(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Сначала пробуем получить refresh token
                var refreshToken = PreferencesManager.getRefreshToken()

                // Если нет refresh token, используем старый токен
                if (refreshToken.isNullOrEmpty()) {
                    refreshToken = PreferencesManager.getToken()
                    println("⚠️ Refresh токен не найден, используем старый токен")
                }

                if (refreshToken.isNullOrEmpty()) {
                    println("❌ Токен отсутствует")
                    return@withContext false
                }

                println("🔄 Отправка запроса на обновление токена...")

                val request = Request.Builder()
                    .url("${Constants.BASE_URL}/auth/refresh")
                    .post(okhttp3.FormBody.Builder()
                        .add("token", refreshToken)
                        .build()
                    )
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                println("📥 Ответ от /auth/refresh: ${response.code}")
                println("📄 Тело ответа: $responseBody")

                if (!response.isSuccessful) {
                    println("❌ Ошибка обновления токена: ${response.code}")
                    response.close()
                    return@withContext false
                }

                val json = JSONObject(responseBody ?: "{}")
                if (!json.optBoolean("success", false)) {
                    val message = json.optString("message", "Неизвестная ошибка")
                    println("❌ Сервер вернул ошибку: $message")
                    response.close()
                    return@withContext false
                }

                val data = json.optJSONObject("data")
                val newToken = data?.optString("access_token") ?: data?.optString("token")
                val newRefreshToken = data?.optString("refresh_token")

                if (newToken.isNullOrEmpty()) {
                    println("❌ Новый токен не получен")
                    response.close()
                    return@withContext false
                }

                // Сохраняем новый токен
                println("🔄 Сохранение нового токена...")
                PreferencesManager.saveToken(newToken)

                // Сохраняем refresh token если есть
                if (!newRefreshToken.isNullOrEmpty()) {
                    PreferencesManager.saveRefreshToken(newRefreshToken)
                    println("✅ Новый refresh токен сохранен")
                }

                response.close()
                println("✅ Токен успешно обновлен: ${newToken.take(20)}...")
                return@withContext true

            } catch (e: Exception) {
                println("❌ Ошибка при обновлении токена: ${e.message}")
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    /**
     * Получить информацию о токене для отладки
     */
    fun getTokenDebugInfo(): String {
        return PreferencesManager.getTokenInfo()
    }
}
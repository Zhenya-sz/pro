package com.fitnesslemon.app.utils

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

private val Context.dataStore by preferencesDataStore(name = Constants.PREF_NAME)

object PreferencesManager {

    private lateinit var context: Context
    private var cacheDir: File? = null

    fun init(context: Context) {
        this.context = context.applicationContext
        this.cacheDir = context.applicationContext.cacheDir
    }

    // ========== КЛЮЧИ ДЛЯ ХРАНЕНИЯ ==========
    private val TOKEN_KEY = stringPreferencesKey(Constants.KEY_TOKEN)
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val USER_ID_LONG_KEY = longPreferencesKey(Constants.KEY_USER_ID)
    private val USER_ID_INT_KEY = intPreferencesKey(Constants.KEY_USER_ID)
    private val USER_NAME_KEY = stringPreferencesKey(Constants.KEY_USER_NAME)
    private val USER_EMAIL_KEY = stringPreferencesKey(Constants.KEY_USER_EMAIL)
    private val USER_PHONE_KEY = stringPreferencesKey("user_phone")
    private val USER_AVATAR_KEY = stringPreferencesKey("user_avatar")
    private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
    private val NONCE_KEY = stringPreferencesKey("wp_rest_nonce")
    private val USER_ROLE_KEY = stringPreferencesKey("user_role")
    private val REMAINING_WORKOUTS_KEY = intPreferencesKey("remaining_workouts")

    // ===== НОВЫЕ КЛЮЧИ ДЛЯ FCM =====
    private val FCM_TOKEN_KEY = stringPreferencesKey("fcm_token")
    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")

    // Ключи для черновиков
    private const val DRAFTS_PREFIX = "draft_"

    // ========== СОХРАНЕНИЕ ДАННЫХ ПОЛЬЗОВАТЕЛЯ ==========
    suspend fun saveUserData(
        token: String,
        refreshToken: String? = null,
        userId: Long,
        userName: String,
        userEmail: String,
        userRole: String = "user"
    ) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
            if (!refreshToken.isNullOrEmpty()) {
                preferences[REFRESH_TOKEN_KEY] = refreshToken
            }
            preferences[USER_ID_LONG_KEY] = userId
            preferences[USER_ID_INT_KEY] = userId.toInt()
            preferences[USER_NAME_KEY] = userName
            preferences[USER_EMAIL_KEY] = userEmail
            preferences[USER_ROLE_KEY] = userRole
            preferences[IS_LOGGED_IN_KEY] = true
        }
        println("✅ Данные пользователя сохранены: ID=$userId, Name=$userName, Role=$userRole")
        println("✅ Токен: ${token.take(20)}...")
        if (!refreshToken.isNullOrEmpty()) {
            println("✅ Refresh токен: ${refreshToken.take(20)}...")
        }
    }

    suspend fun saveNonce(nonce: String) {
        context.dataStore.edit { preferences ->
            preferences[NONCE_KEY] = nonce
        }
        println("✅ Nonce сохранен: $nonce")
    }

    // ========== ID ПОЛЬЗОВАТЕЛЯ ==========
    fun saveUserId(userId: Int) {
        runBlocking {
            context.dataStore.edit { preferences ->
                preferences[USER_ID_INT_KEY] = userId
                preferences[USER_ID_LONG_KEY] = userId.toLong()
            }
        }
        println("✅ User ID сохранен: $userId")
    }

    suspend fun saveUserIdSuspend(userId: Int) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_INT_KEY] = userId
            preferences[USER_ID_LONG_KEY] = userId.toLong()
        }
        println("✅ User ID сохранен: $userId")
    }

    // PreferencesManager.kt

    fun getUserId(): Int {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    // ✅ Сначала пробуем получить Int
                    val intValue = preferences[USER_ID_INT_KEY]
                    if (intValue != null && intValue != 0) {
                        return@map intValue
                    }
                    // ✅ Если Int нет, пробуем Long и конвертируем
                    val longValue = preferences[USER_ID_LONG_KEY]
                    if (longValue != null && longValue != 0L) {
                        return@map longValue.toInt()
                    }
                    0
                }.first()
            }
        } catch (e: Exception) {
            println("❌ Ошибка получения userId: ${e.message}")
            0
        }
    }

    // Добавить метод для принудительной установки ID
    fun forceSetUserId(userId: Int) {
        runBlocking {
            context.dataStore.edit { preferences ->
                preferences[USER_ID_INT_KEY] = userId
                preferences[USER_ID_LONG_KEY] = userId.toLong()
            }
        }
        println("✅ User ID принудительно установлен: $userId")
    }

    fun getUserIdLong(): Long {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[USER_ID_LONG_KEY] ?: 0L
                }.first()
            }
        } catch (e: Exception) {
            println("❌ Ошибка получения userIdLong: ${e.message}")
            0L
        }
    }

    // ========== ТОКЕН ==========
    fun getToken(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[TOKEN_KEY]
                }.first()
            }
        } catch (e: Exception) {
            println("❌ Ошибка получения токена: ${e.message}")
            null
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
        println("✅ Токен сохранен: ${token.take(20)}...")
    }

    // ========== REFRESH TOKEN ==========
    suspend fun saveRefreshToken(refreshToken: String) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_TOKEN_KEY] = refreshToken
        }
        println("✅ Refresh токен сохранен: ${refreshToken.take(20)}...")
    }

    fun getRefreshToken(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[REFRESH_TOKEN_KEY]
                }.first()
            }
        } catch (e: Exception) {
            println("❌ Ошибка получения refresh токена: ${e.message}")
            null
        }
    }

    suspend fun clearRefreshToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(REFRESH_TOKEN_KEY)
        }
        println("✅ Refresh токен очищен")
    }

    // ========== СТАТУС ВХОДА ==========
    suspend fun saveIsLoggedIn(isLoggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN_KEY] = isLoggedIn
        }
        println("✅ Статус входа сохранен: $isLoggedIn")
    }

    fun isLoggedIn(): Boolean {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[IS_LOGGED_IN_KEY] ?: false
                }.first()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isLoggedInFlow(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[IS_LOGGED_IN_KEY] ?: false
        }
    }

    // ========== ИМЯ ПОЛЬЗОВАТЕЛЯ ==========
    fun getUserName(): String {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[USER_NAME_KEY] ?: ""
                }.first()
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun saveUserName(name: String) {
        runBlocking {
            context.dataStore.edit { preferences ->
                preferences[USER_NAME_KEY] = name
            }
        }
        println("✅ Имя пользователя сохранено: $name")
    }

    suspend fun saveUserNameSuspend(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name
        }
        println("✅ Имя пользователя сохранено (suspend): $name")
    }

    fun getUserNameFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_NAME_KEY] ?: ""
        }
    }

    // ========== EMAIL ==========
    fun getUserEmail(): String {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[USER_EMAIL_KEY] ?: ""
                }.first()
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ========== ТЕЛЕФОН ==========
    fun getUserPhone(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[USER_PHONE_KEY]
                }.first()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserPhone(phone: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_PHONE_KEY] = phone
        }
    }

    // ========== АВАТАР ==========
    fun getUserAvatar(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[USER_AVATAR_KEY]
                }.first()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserAvatar(avatar: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_AVATAR_KEY] = avatar
        }
    }

    fun getUserAvatarFlow(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_AVATAR_KEY]
        }
    }

    // ========== ОСТАТОК ТРЕНИРОВОК ==========
    fun getRemainingWorkouts(): Int {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[REMAINING_WORKOUTS_KEY] ?: 0
                }.first()
            }
        } catch (e: Exception) {
            println("❌ Ошибка получения остатка тренировок: ${e.message}")
            0
        }
    }

    fun saveRemainingWorkouts(count: Int) {
        runBlocking {
            context.dataStore.edit { preferences ->
                preferences[REMAINING_WORKOUTS_KEY] = count
            }
        }
        println("✅ Остаток тренировок сохранен: $count")
    }

    suspend fun saveRemainingWorkoutsSuspend(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[REMAINING_WORKOUTS_KEY] = count
        }
        println("✅ Остаток тренировок сохранен: $count")
    }

    fun getRemainingWorkoutsFlow(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[REMAINING_WORKOUTS_KEY] ?: 0
        }
    }

    // ========== РОЛЬ ПОЛЬЗОВАТЕЛЯ ==========
    fun getUserRole(): String {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[USER_ROLE_KEY] ?: "user"
                }.first()
            }
        } catch (e: Exception) {
            "user"
        }
    }

    suspend fun saveUserRole(role: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ROLE_KEY] = role
        }
        println("✅ Роль пользователя сохранена: $role")
    }

    fun getUserRoleFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_ROLE_KEY] ?: "user"
        }
    }

    fun isAdmin(): Boolean {
        val role = getUserRole()
        return role.contains("administrator") || role == "administrator"
    }

    fun isTrainer(): Boolean {
        val role = getUserRole()
        return role.contains("fitness_trainer") || role == "fitness_trainer"
    }

    // ========== FCM TOKEN ==========
    fun getFcmToken(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[FCM_TOKEN_KEY]
                }.first()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[FCM_TOKEN_KEY] = token
        }
        println("✅ FCM токен сохранен: ${token.take(20)}...")
    }

    fun getFcmTokenFlow(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[FCM_TOKEN_KEY]
        }
    }

    // ========== DEVICE ID ==========
    fun getDeviceId(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[DEVICE_ID_KEY]
                }.first()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_ID_KEY] = deviceId
        }
        println("✅ Device ID сохранен: $deviceId")
    }

    fun getDeviceIdFlow(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[DEVICE_ID_KEY]
        }
    }

    // ========== NONCE ==========
    fun getNonce(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[NONCE_KEY]
                }.first()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getNonceFlow(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[NONCE_KEY]
        }
    }

    // ========== КЭШ ДИРЕКТОРИЯ ==========
    fun getCacheDir(): File? {
        return cacheDir
    }

    // ========== ЧЕРНОВИКИ ==========
    suspend fun saveDraft(newsId: Int, content: String) {
        val draftKey = stringPreferencesKey("${DRAFTS_PREFIX}${newsId}")
        context.dataStore.edit { preferences ->
            preferences[draftKey] = content
        }
        println("✅ Черновик сохранен для новости $newsId")
    }

    fun getDraft(newsId: Int): String? {
        return try {
            val draftKey = stringPreferencesKey("${DRAFTS_PREFIX}${newsId}")
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[draftKey]
                }.first()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun removeDraft(newsId: Int) {
        val draftKey = stringPreferencesKey("${DRAFTS_PREFIX}${newsId}")
        context.dataStore.edit { preferences ->
            preferences.remove(draftKey)
        }
        println("✅ Черновик удален для новости $newsId")
    }

    fun getAllDrafts(): Map<Int, String> {
        val drafts = mutableMapOf<Int, String>()
        return try {
            runBlocking {
                val prefs = context.dataStore.data.first()
                for ((key, value) in prefs.asMap()) {
                    if (key.name.startsWith(DRAFTS_PREFIX)) {
                        val newsId = key.name.removePrefix(DRAFTS_PREFIX).toIntOrNull()
                        if (newsId != null) {
                            drafts[newsId] = (value as? String) ?: ""
                        }
                    }
                }
            }
            drafts
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ========== FLOW ВЕРСИИ ==========
    fun getTokenFlow(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[TOKEN_KEY]
        }
    }

    fun getUserIdFlow(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_ID_INT_KEY] ?: 0
        }
    }

    fun getRefreshTokenFlow(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[REFRESH_TOKEN_KEY]
        }
    }

    fun hasValidUserData(): Boolean {
        val isLoggedIn = isLoggedIn()
        val token = getToken()
        val userId = getUserId()
        val valid = isLoggedIn && !token.isNullOrEmpty() && userId != 0
        println("🔍 Проверка данных пользователя: isLoggedIn=$isLoggedIn, token=${if (token != null) "PRESENT" else "NULL"}, userId=$userId, valid=$valid")
        return valid
    }

    /**
     * Проверить, валиден ли токен (не истек и не пустой)
     */
    fun hasValidToken(): Boolean {
        val token = getToken()
        if (token.isNullOrEmpty()) {
            println("⚠️ Токен отсутствует")
            return false
        }
        val expired = isTokenExpired()
        if (expired) {
            println("⚠️ Токен истек")
            return false
        }
        println("✅ Токен валиден")
        return true
    }

    /**
     * Получить детальную информацию о токене для отладки
     */
    fun getTokenInfo(): String {
        val token = getToken()
        if (token.isNullOrEmpty()) {
            return "Токен отсутствует"
        }

        return buildString {
            appendLine("📋 Информация о токене:")
            appendLine("  - Длина: ${token.length} символов")
            appendLine("  - Префикс: ${token.take(20)}...")
            appendLine("  - Создан: ${getTokenIssuedAt()?.let { java.util.Date(it) } ?: "неизвестно"}")
            appendLine("  - Истекает: ${getTokenExpiration()?.let { java.util.Date(it) } ?: "неизвестно"}")
            appendLine("  - Истек: ${if (isTokenExpired()) "ДА" else "НЕТ"}")
            appendLine("  - Роль из токена: ${getTokenRole() ?: "неизвестно"}")
            appendLine("  - User ID из токена: ${getTokenUserId() ?: "неизвестно"}")
            appendLine("  - Роль из хранилища: ${getUserRole()}")
            appendLine("  - User ID из хранилища: ${getUserId()}")
            appendLine("  - Refresh token: ${if (getRefreshToken() != null) "ПРИСУТСТВУЕТ" else "ОТСУТСТВУЕТ"}")
        }
    }

    /**
     * Получить время истечения токена в миллисекундах
     */
    fun getTokenExpiration(): Long? {
        val token = getToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                println("⚠️ Неверный формат JWT токена")
                return null
            }

            val payload = parts[1]
            val decoded = String(Base64.decode(payload, Base64.URL_SAFE))
            val json = JSONObject(decoded)
            val exp = json.getLong("exp") * 1000
            println("📅 Токен истекает: ${java.util.Date(exp)}")
            exp
        } catch (e: Exception) {
            println("❌ Ошибка парсинга токена: ${e.message}")
            null
        }
    }

    /**
     * Проверить, истек ли токен
     */
    fun isTokenExpired(): Boolean {
        val exp = getTokenExpiration() ?: return true
        val currentTime = System.currentTimeMillis()
        val isExpired = currentTime > exp
        if (isExpired) {
            val diffSeconds = (currentTime - exp) / 1000
            println("⚠️ ТОКЕН ИСТЕК! Разница: ${diffSeconds} секунд (${diffSeconds / 60} минут)")
        } else {
            val diffSeconds = (exp - currentTime) / 1000
            println("✅ Токен действителен. Осталось: ${diffSeconds} секунд (${diffSeconds / 60} минут)")
        }
        return isExpired
    }

    /**
     * Получить время создания токена в миллисекундах
     */
    fun getTokenIssuedAt(): Long? {
        val token = getToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payload = parts[1]
            val decoded = String(Base64.decode(payload, Base64.URL_SAFE))
            val json = JSONObject(decoded)
            json.getLong("iat") * 1000
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получить роль пользователя из токена (без декодирования)
     */
    fun getTokenRole(): String? {
        val token = getToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payload = parts[1]
            val decoded = String(Base64.decode(payload, Base64.URL_SAFE))
            val json = JSONObject(decoded)
            json.optString("role", null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получить ID пользователя из токена (без декодирования)
     */
    fun getTokenUserId(): Int? {
        val token = getToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payload = parts[1]
            val decoded = String(Base64.decode(payload, Base64.URL_SAFE))
            val json = JSONObject(decoded)
            json.optInt("user_id", 0).takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    // ========== ОЧИСТКА ДАННЫХ ==========
    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        println("✅ Данные пользователя очищены")
    }

    fun logout() {
        runBlocking {
            context.dataStore.edit { preferences ->
                preferences.remove(TOKEN_KEY)
                preferences.remove(REFRESH_TOKEN_KEY)
                preferences.remove(IS_LOGGED_IN_KEY)
                preferences.remove(USER_ID_INT_KEY)
                preferences.remove(USER_ID_LONG_KEY)
                preferences.remove(USER_ROLE_KEY)
                preferences.remove(USER_NAME_KEY)
                preferences.remove(USER_EMAIL_KEY)
                preferences.remove(USER_PHONE_KEY)
                preferences.remove(USER_AVATAR_KEY)
                preferences.remove(REMAINING_WORKOUTS_KEY)
                preferences.remove(FCM_TOKEN_KEY)
                preferences.remove(DEVICE_ID_KEY)
            }
        }
        println("✅ Выход выполнен, данные пользователя очищены")
    }

    /**
     * Логирование всех сохраненных данных для отладки
     */
    fun logAllPreferences() {
        runBlocking {
            try {
                val prefs = context.dataStore.data.first()
                println("📋 Все сохраненные данные:")
                for ((key, value) in prefs.asMap()) {
                    println("  $key = $value")
                }
            } catch (e: Exception) {
                println("❌ Ошибка при логировании: ${e.message}")
            }
        }
    }
}
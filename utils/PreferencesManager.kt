package com.fitnesslemon.app.utils

import android.content.Context
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
    private val FCM_TOKEN_KEY = stringPreferencesKey("fcm_token")
    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    private const val DRAFTS_PREFIX = "draft_"

    private fun secretStorageEnabled(): Boolean = ::context.isInitialized

    private fun encryptStoredValue(value: String): String {
        if (!secretStorageEnabled()) return value
        return try {
            val keyStoreManager = KeyStoreManager(context)
            keyStoreManager.generateUserKeys()
            val publicKey = keyStoreManager.getPublicKey() ?: return value
            val secretKey = EncryptionHelper.generateAESKey()
            val encryptedKey = EncryptionHelper.encryptAESKeyWithRSA(
                secretKey,
                EncryptionHelper.publicKeyToBase64(publicKey)
            )
            val encryptedValue = EncryptionHelper.encryptText(value, secretKey) ?: value
            "${SecureStoragePolicy.TOKEN_PREFIX}${encryptedKey}:${encryptedValue}"
        } catch (_: Exception) {
            value
        }
    }

    private fun decryptStoredValue(value: String): String {
        if (!value.startsWith(SecureStoragePolicy.TOKEN_PREFIX)) return value
        return try {
            val payload = value.removePrefix(SecureStoragePolicy.TOKEN_PREFIX)
            val parts = payload.split(":", limit = 2)
            if (parts.size != 2) return value
            val keyStoreManager = KeyStoreManager(context)
            val privateKey = keyStoreManager.getPrivateKey() ?: return value
            val secretKey = EncryptionHelper.decryptAESKeyWithRSA(parts[0], privateKey) ?: return value
            EncryptionHelper.decryptText(parts[1], secretKey) ?: value
        } catch (_: Exception) {
            value
        }
    }

    suspend fun saveUserData(
        token: String,
        refreshToken: String? = null,
        userId: Long,
        userName: String,
        userEmail: String,
        userRole: String = "user"
    ) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = encryptStoredValue(token)
            if (!refreshToken.isNullOrEmpty()) preferences[REFRESH_TOKEN_KEY] = encryptStoredValue(refreshToken)
            preferences[USER_ID_LONG_KEY] = userId
            preferences[USER_ID_INT_KEY] = userId.toInt()
            preferences[USER_NAME_KEY] = userName
            preferences[USER_EMAIL_KEY] = userEmail
            preferences[USER_ROLE_KEY] = userRole
            preferences[IS_LOGGED_IN_KEY] = true
        }
    }

    fun getToken(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    val raw = preferences[TOKEN_KEY] ?: return@map null
                    decryptStoredValue(raw)
                }.first()
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = encryptStoredValue(token)
        }
    }

    suspend fun saveRefreshToken(refreshToken: String) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_TOKEN_KEY] = encryptStoredValue(refreshToken)
        }
    }

    fun getRefreshToken(): String? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    val raw = preferences[REFRESH_TOKEN_KEY] ?: return@map null
                    decryptStoredValue(raw)
                }.first()
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
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
    }

    fun getTokenInfo(): String {
        val token = getToken() ?: return "Token missing"
        return "Token present (${token.length} chars)"
    }

    fun getUserId(): Int {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    val intValue = preferences[USER_ID_INT_KEY]
                    if (intValue != null && intValue != 0) return@map intValue
                    val longValue = preferences[USER_ID_LONG_KEY]
                    if (longValue != null && longValue != 0L) return@map longValue.toInt()
                    0
                }.first()
            }
        } catch (_: Exception) {
            0
        }
    }

    fun getUserRole(): String {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[USER_ROLE_KEY] ?: "user"
                }.first()
            }
        } catch (_: Exception) {
            "user"
        }
    }

    fun getUserRoleFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_ROLE_KEY] ?: "user"
        }
    }

    fun isLoggedIn(): Boolean {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[IS_LOGGED_IN_KEY] ?: false
                }.first()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isTokenExpired(): Boolean {
        val token = getToken() ?: return true
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            val payload = parts[1]
            val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
            val json = JSONObject(decoded)
            val exp = json.getLong("exp") * 1000L
            System.currentTimeMillis() > exp
        } catch (_: Exception) {
            true
        }
    }

    fun getTokenExpiration(): Long? {
        val token = getToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            val payload = parts[1]
            val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
            val json = JSONObject(decoded)
            json.getLong("exp") * 1000L
        } catch (_: Exception) {
            null
        }
    }

    fun getRefreshTokenFlow(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            val raw = preferences[REFRESH_TOKEN_KEY] ?: return@map null
            decryptStoredValue(raw)
        }
    }

    fun getTokenFlow(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            val raw = preferences[TOKEN_KEY] ?: return@map null
            decryptStoredValue(raw)
        }
    }
}

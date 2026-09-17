package com.fitnesslemon.app.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey

class KeyStoreManager(private val context: Context) {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    private val lifecycleScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val RSA_ALIAS = "user_rsa_key"
    }

    init {
        if (!hasKeys()) {
            generateAndUploadKeys()
        }
    }

    fun hasKeys(): Boolean {
        return try {
            keyStore.containsAlias(RSA_ALIAS) && getPublicKey() != null
        } catch (e: Exception) {
            false
        }
    }

    fun generateAndUploadKeys() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                println("🔐 Генерируем RSA ключи...")
                val keyPair = generateUserKeys()
                if (keyPair != null) {
                    println("✅ Ключи сгенерированы")
                    delay(1000)
                    val success = uploadPublicKeyToServerSync()
                    if (success) {
                        println("✅ Публичный ключ успешно загружен на сервер")
                    } else {
                        println("❌ Не удалось загрузить ключ на сервер")
                        forceCreatePublicKeyOnServer()
                    }
                } else {
                    println("❌ Не удалось сгенерировать ключи")
                }
            } catch (e: Exception) {
                println("❌ Ошибка генерации/загрузки ключей: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun generateUserKeys(): KeyPair? {
        try {
            if (keyStore.containsAlias(RSA_ALIAS)) {
                println("⚠️ Ключ уже существует")
                return getKeyPair()
            }

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )

            val spec = KeyGenParameterSpec.Builder(
                RSA_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(2048)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setUserAuthenticationRequired(false)
                .build()

            keyPairGenerator.initialize(spec)
            return keyPairGenerator.generateKeyPair()

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun getPrivateKey(): PrivateKey? {
        return try {
            keyStore.getKey(RSA_ALIAS, null) as? PrivateKey
        } catch (e: Exception) {
            null
        }
    }

    fun getPublicKey(): PublicKey? {
        return try {
            keyStore.getCertificate(RSA_ALIAS)?.publicKey
        } catch (e: Exception) {
            null
        }
    }

    private fun getKeyPair(): KeyPair? {
        val privateKey = getPrivateKey()
        val publicKey = getPublicKey()
        return if (privateKey != null && publicKey != null) {
            KeyPair(publicKey, privateKey)
        } else null
    }

    suspend fun uploadPublicKeyToServerSync(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val publicKey = getPublicKey()
                if (publicKey == null) {
                    println("❌ Публичный ключ не найден")
                    return@withContext false
                }

                val publicKeyBase64 = EncryptionHelper.publicKeyToBase64(publicKey)
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    println("❌ Токен авторизации не найден")
                    return@withContext false
                }

                println("📤 Отправляем публичный ключ на сервер...")
                println("📝 Ключ (первые 50 символов): ${publicKeyBase64.take(50)}...")

                val response = com.fitnesslemon.app.data.api.ApiClient.apiService.uploadPublicKey(
                    "Bearer $token",
                    com.fitnesslemon.app.data.api.UploadPublicKeyRequest(publicKeyBase64)
                )

                if (response.isSuccessful) {
                    println("✅ Публичный ключ успешно отправлен на сервер")
                    val currentUserId = PreferencesManager.getUserId()
                    if (currentUserId != 0) {
                        val createResponse = com.fitnesslemon.app.data.api.ApiClient.apiService.createPublicKey(
                            "Bearer $token",
                            currentUserId
                        )
                        if (createResponse.isSuccessful) {
                            println("✅ Статус pending удален")
                        }
                    }
                    return@withContext true
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка отправки ключа: ${response.code()} - $errorBody")
                    return@withContext false
                }
            } catch (e: Exception) {
                println("❌ Ошибка при отправке ключа: ${e.message}")
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    /**
     * Принудительное создание записи публичного ключа на сервере
     */
    suspend fun forceCreatePublicKeyOnServer(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val publicKey = getPublicKey()
                if (publicKey == null) {
                    println("❌ Нет публичного ключа в KeyStore")
                    return@withContext false
                }

                val publicKeyBase64 = EncryptionHelper.publicKeyToBase64(publicKey)
                val token = PreferencesManager.getToken()
                if (token.isNullOrEmpty()) {
                    println("❌ Токен не найден")
                    return@withContext false
                }

                val currentUserId = PreferencesManager.getUserId()
                if (currentUserId == 0) {
                    println("❌ User ID не найден")
                    return@withContext false
                }

                val createResponse = com.fitnesslemon.app.data.api.ApiClient.apiService.createPublicKey(
                    "Bearer $token",
                    currentUserId
                )

                if (createResponse.isSuccessful) {
                    println("✅ Запись публичного ключа создана")
                    return@withContext true
                }

                val uploadResponse = com.fitnesslemon.app.data.api.ApiClient.apiService.uploadPublicKey(
                    "Bearer $token",
                    com.fitnesslemon.app.data.api.UploadPublicKeyRequest(publicKeyBase64)
                )

                val success = uploadResponse.isSuccessful
                if (success) {
                    println("✅ Публичный ключ загружен")
                } else {
                    println("❌ Ошибка загрузки: ${uploadResponse.code()}")
                }
                success

            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
                false
            }
        }
    }
}
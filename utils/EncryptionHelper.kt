package com.fitnesslemon.app.utils

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {

    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val AES_KEY_SIZE = 256
    private const val RSA_KEY_SIZE = 2048
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    /**
     * Генерация AES ключа для файла
     */
    fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE, SecureRandom())
        return keyGenerator.generateKey()
    }

    /**
     * Шифрование текста с помощью AES-GCM
     * Использует GCMParameterSpec (правильный подход для GCM)
     */
    fun encryptText(text: String, secretKey: SecretKey): String? {
        try {
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val iv = ByteArray(GCM_IV_LENGTH).apply {
                SecureRandom().nextBytes(this)
            }
            // ✅ Правильно: используем GCMParameterSpec
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val encryptedBytes = cipher.doFinal(text.toByteArray(Charsets.UTF_8))

            // Объединяем IV и зашифрованный текст
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Расшифровка текста с помощью AES-GCM
     * Использует GCMParameterSpec (правильный подход для GCM)
     */
    fun decryptText(encryptedDataBase64: String, secretKey: SecretKey): String? {
        try {
            val combined = Base64.decode(encryptedDataBase64, Base64.DEFAULT)

            // Извлекаем IV (первые 12 байт)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(AES_ALGORITHM)
            // ✅ Правильно: используем GCMParameterSpec
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Шифрование файла AES-GCM
     * Использует GCMParameterSpec для корректной работы с медиафайлами
     */
    fun encryptFile(file: File, secretKey: SecretKey): Pair<File, ByteArray> {
        val encryptedFile = File(file.parent, "encrypted_${file.name}")

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val iv = ByteArray(GCM_IV_LENGTH).apply {
            SecureRandom().nextBytes(this)
        }
        // ✅ Правильно: используем GCMParameterSpec
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        FileInputStream(file).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                // Записываем IV в начало файла
                output.write(iv)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val encrypted = cipher.update(buffer, 0, bytesRead)
                    if (encrypted != null) {
                        output.write(encrypted)
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock.isNotEmpty()) {
                    output.write(finalBlock)
                }
            }
        }

        return Pair(encryptedFile, iv)
    }

    /**
     * Расшифровка файла AES-GCM
     * Использует GCMParameterSpec для корректной работы с медиафайлами
     */
    fun decryptFile(encryptedFile: File, secretKey: SecretKey): File? {
        try {
            val decryptedFile = File(encryptedFile.parent, "decrypted_${encryptedFile.name}")

            FileInputStream(encryptedFile).use { input ->
                // Читаем IV из начала файла
                val iv = ByteArray(GCM_IV_LENGTH)
                input.read(iv)
                // ✅ Правильно: используем GCMParameterSpec
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

                val cipher = Cipher.getInstance(AES_ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                FileOutputStream(decryptedFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        if (decrypted != null) {
                            output.write(decrypted)
                        }
                    }
                    val finalBlock = cipher.doFinal()
                    if (finalBlock.isNotEmpty()) {
                        output.write(finalBlock)
                    }
                }
            }

            return decryptedFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Шифрование AES ключа публичным RSA ключом получателя
     */
    fun encryptAESKeyWithRSA(aesKey: SecretKey, publicKeyBase64: String): String {
        try {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

            val cipher = Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            val encryptedBytes = cipher.doFinal(aesKey.encoded)
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * Расшифровка AES ключа приватным RSA ключом
     */
    fun decryptAESKeyWithRSA(encryptedKeyBase64: String, privateKey: Key): SecretKey? {
        try {
            val encryptedBytes = Base64.decode(encryptedKeyBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return SecretKeySpec(decryptedBytes, "AES")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Генерация пары RSA ключей для пользователя
     */
    fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(RSA_KEY_SIZE)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Преобразование публичного ключа в Base64
     */
    fun publicKeyToBase64(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }
}
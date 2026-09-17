package com.fitnesslemon.app.utils

import android.content.Context

/**
 * Central token adapter. Existing callers can migrate incrementally from the legacy
 * DataStore values without duplicating encryption logic across ViewModels.
 */
class SecureTokenRepository(context: Context) {
    private val encryptedStore = EncryptedTokenStore(context.applicationContext)

    fun protect(value: String): String = encryptedStore.encrypt(value)
    fun reveal(value: String): String? = encryptedStore.decrypt(value)
}

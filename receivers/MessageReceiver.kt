// app/src/main/java/com/fitnesslemon/app/receivers/MessageReceiver.kt

package com.fitnesslemon.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

class MessageReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MessageReceiver"

        // Колбэк для уведомлений
        var onNewMessage: ((Map<String, String>) -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != "NEW_MESSAGE_EVENT") return

        val data = intent.getStringExtra("data") ?: return

        Log.d(TAG, "📩 Получено локальное уведомление: $data")

        try {
            val json = JSONObject(data)
            val map = mutableMapOf<String, String>()

            json.keys().forEach { key ->
                map[key] = json.getString(key)
            }

            onNewMessage?.invoke(map)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка парсинга: ${e.message}")
        }
    }
}
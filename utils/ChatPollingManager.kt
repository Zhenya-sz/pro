package com.fitnesslemon.app.utils

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*

class ChatPollingManager(
    private val onPoll: suspend () -> Unit,
    private val intervalMs: Long = 8000 // ⬆️ Увеличено с 5 до 8 секунд
) {
    private val tag = "ChatPollingManager"
    private var pollingJob: Job? = null
    private val _isPolling = MutableLiveData(false)
    val isPolling: LiveData<Boolean> = _isPolling

    private val _lastError = MutableLiveData<String?>(null)
    val lastError: LiveData<String?> = _lastError

    private var isPollingInProgress = false

    fun startPolling(scope: CoroutineScope) {
        if (pollingJob?.isActive == true) {
            Log.d(tag, "Polling уже запущен")
            return
        }

        Log.d(tag, "Запуск polling с интервалом ${intervalMs}ms")
        _isPolling.postValue(true)
        _lastError.postValue(null)

        pollingJob = scope.launch {
            while (isActive) {
                try {
                    // ✅ Предотвращаем одновременные запросы
                    if (!isPollingInProgress) {
                        isPollingInProgress = true
                        onPoll.invoke()
                        isPollingInProgress = false
                    }
                    delay(intervalMs)
                } catch (e: CancellationException) {
                    Log.d(tag, "Polling отменен")
                    break
                } catch (e: Exception) {
                    Log.e(tag, "Ошибка при polling: ${e.message}")
                    _lastError.postValue(e.message)
                    isPollingInProgress = false
                    delay(intervalMs)
                }
            }
        }
    }

    fun stopPolling() {
        Log.d(tag, "Остановка polling")
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.postValue(false)
        isPollingInProgress = false
    }

    fun setInterval(newIntervalMs: Long) {
        Log.d(tag, "Интервал изменен на ${newIntervalMs}ms")
    }

    fun isActive(): Boolean = pollingJob?.isActive == true
}
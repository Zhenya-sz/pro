package com.fitnesslemon.app.data.repository

import com.fitnesslemon.app.data.api.ApiService
import com.fitnesslemon.app.data.models.TrainerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TrainerRepository(
    private val api: ApiService
) {
    private val _trainers = MutableStateFlow<List<TrainerResponse>>(emptyList())
    val trainers: StateFlow<List<TrainerResponse>> = _trainers.asStateFlow()

    private val _lastRefreshTime = MutableStateFlow(0L)

    suspend fun loadTrainers(force: Boolean = false): Result<List<TrainerResponse>> =
        withContext(Dispatchers.IO) {
            try {
                if (!force && CachePolicy.isFresh(_lastRefreshTime.value)) {
                    return@withContext Result.success(_trainers.value)
                }

                val response = api.getTrainers()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки тренеров"))
                }

                val list = response.body().orEmpty()
                _trainers.value = list
                _lastRefreshTime.value = System.currentTimeMillis()
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

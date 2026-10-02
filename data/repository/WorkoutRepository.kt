package com.fitnesslemon.app.data.repository

import com.fitnesslemon.app.data.api.ApiService
import com.fitnesslemon.app.data.models.WorkoutResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class WorkoutRepository(
    private val api: ApiService
) {
    private val _workouts = MutableStateFlow<List<WorkoutResponse>>(emptyList())
    val workouts: StateFlow<List<WorkoutResponse>> = _workouts.asStateFlow()

    private val _lastRefreshTime = MutableStateFlow(0L)

    suspend fun loadWorkouts(force: Boolean = false): Result<List<WorkoutResponse>> =
        withContext(Dispatchers.IO) {
            try {
                if (!force && CachePolicy.isFresh(_lastRefreshTime.value)) {
                    return@withContext Result.success(_workouts.value)
                }

                val response = api.getWorkouts()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки тренировок"))
                }

                val list = response.body().orEmpty()
                _workouts.value = list
                _lastRefreshTime.value = System.currentTimeMillis()
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getWorkout(id: Int): Result<WorkoutResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getWorkout(id)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки тренировки"))
                }

                val workout = response.body() ?: throw RuntimeException("Пустой ответ сервера")
                Result.success(workout)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

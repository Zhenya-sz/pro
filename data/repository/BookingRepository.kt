package com.fitnesslemon.app.data.repository

import com.fitnesslemon.app.data.api.ApiService
import com.fitnesslemon.app.data.models.BookingResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class BookingRepository(
    private val api: ApiService
) {
    private val _bookings = MutableStateFlow<List<BookingResponse>>(emptyList())
    val bookings: StateFlow<List<BookingResponse>> = _bookings.asStateFlow()

    suspend fun loadMyBookings(): Result<List<BookingResponse>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getMyBookings()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки бронирований"))
                }

                val list = response.body().orEmpty()
                _bookings.value = list
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun bookClass(workoutId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.bookClass(
                    mapOf("workout_id" to workoutId)
                )
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка бронирования"))
                }
                loadMyBookings()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun cancelBooking(bookingId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.cancelBooking(
                    mapOf("booking_id" to bookingId)
                )
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка отмены бронирования"))
                }
                loadMyBookings()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
